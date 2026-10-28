package cl.autopaw.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Protocolo AutoPaw v2: AP2|secuencia|IV|cifrado AES-GCM|HMAC-SHA256. */
class SecureSensorProtocol(passphrase: String) {
    private val aesKey: SecretKey
    private val macKey: SecretKey
    private var lastReceivedSequence = 0L
    init {
        require(passphrase.length >= 12) { "La clave compartida debe tener al menos 12 caracteres" }
        val spec = PBEKeySpec(passphrase.toCharArray(), "AutoPaw-Sensor-Protocol-v2".toByteArray(), 210_000, 512)
        val material = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
        aesKey = SecretKeySpec(material.copyOfRange(0, 32), "AES")
        macKey = SecretKeySpec(material.copyOfRange(32, 64), "HmacSHA256")
        material.fill(0)
    }

    @Synchronized fun encode(sequence: Long, distanceCm: Int, movement: Boolean): String {
        require(sequence > 0 && distanceCm in 2..400)
        val payload = "DISTANCIA: $distanceCm cm;MOVIMIENTO:${if (movement) 1 else 0}".toByteArray(StandardCharsets.UTF_8)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        cipher.updateAAD("AP2|$sequence".toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(payload)
        val unsigned = "AP2|$sequence|${b64(iv)}|${b64(encrypted)}"
        val mac = hmac(unsigned.toByteArray(StandardCharsets.UTF_8))
        val line = "$unsigned|${b64(mac)}"
        require(line.length <= MAX_LINE) { "Mensaje demasiado grande" }
        return line
    }

    @Synchronized fun decode(line: String): SensorReading? {
        if (line.length !in 1..MAX_LINE || line.any { it == '\r' || it == '\n' }) return null
        val parts = line.split('|')
        if (parts.size != 5 || parts[0] != "AP2") return null
        val seq = parts[1].toLongOrNull() ?: return null
        if (seq <= lastReceivedSequence || seq <= 0) return null
        val iv = decode64(parts[2]) ?: return null
        val encrypted = decode64(parts[3]) ?: return null
        val providedMac = decode64(parts[4]) ?: return null
        if (iv.size != 12 || encrypted.size !in 17..256 || providedMac.size != 32) return null
        val unsigned = parts.take(4).joinToString("|")
        val expectedMac = hmac(unsigned.toByteArray(StandardCharsets.UTF_8))
        if (!MessageDigest.isEqual(expectedMac, providedMac)) return null
        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            cipher.updateAAD("AP2|$seq".toByteArray(StandardCharsets.UTF_8))
            cipher.doFinal(encrypted)
        } catch (_: Exception) { return null }
        val payload = String(plaintext, StandardCharsets.UTF_8)
        val match = Regex("^DISTANCIA: ([0-9]{1,3}) cm;MOVIMIENTO:([01])$").matchEntire(payload) ?: return null
        val distance = match.groupValues[1].toIntOrNull() ?: return null
        if (distance !in 2..400) return null
        lastReceivedSequence = seq
        return SensorReading(seq, distance, match.groupValues[2] == "1")
    }

    private fun hmac(bytes: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(macKey); doFinal(bytes) }
    private fun b64(bytes: ByteArray) = java.util.Base64.getEncoder().withoutPadding().encodeToString(bytes)
    private fun decode64(value: String): ByteArray? = try { java.util.Base64.getDecoder().decode(value) } catch (_: Exception) { null }
    companion object { const val MAX_LINE = 512 }
}

data class SensorReading(val sequence: Long, val distanceCm: Int, val movement: Boolean)

object ReadingFreshness {
    const val TIMEOUT_MILLIS = 2_000L
    fun hasTimedOut(lastValidReadingAt: Long, now: Long): Boolean =
        lastValidReadingAt > 0L && now >= lastValidReadingAt && now - lastValidReadingAt > TIMEOUT_MILLIS
}

/** Protege la clave compartida guardada localmente usando una clave no exportable de Android Keystore. */
class PairingSecretVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pairing_secret_vault", Context.MODE_PRIVATE)
    private val alias = "AutoPawPairingVaultV1"
    fun save(secret: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(secret.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString("iv", android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP))
            .putString("value", android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)).apply()
    }
    fun load(): String? = try {
        val ivText = prefs.getString("iv", null) ?: throw IllegalStateException("No hay clave guardada")
        val valueText = prefs.getString("value", null) ?: throw IllegalStateException("No hay valor guardado")
        val iv = android.util.Base64.decode(ivText, android.util.Base64.NO_WRAP)
        val value = android.util.Base64.decode(valueText, android.util.Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(value), StandardCharsets.UTF_8)
    } catch (_: Exception) { null }
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}
