package cl.autopaw.app

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Cuentas locales guardadas en SQLite mediante Room. */
class LocalAccounts(context: Context) {
    private val appContext = context.applicationContext
    private val lockPrefs = appContext.getSharedPreferences("login_security", Context.MODE_PRIVATE)
    private val dao = AutoPawDatabase.get(context).dao()
    private fun key(email: String) = email.trim().lowercase(Locale.ROOT)
    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun register(name: String, email: String, password: String): Boolean {
        val id = key(email)
        if (AutoPawDatabase.call { dao.account(id) } != null) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val account = AccountEntity(id, name.trim(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(derive(password, salt), Base64.NO_WRAP))
        return runCatching { AutoPawDatabase.call { dao.insertAccount(account) }; true }.getOrDefault(false)
    }
    @Synchronized fun login(email: String, password: String): String? {
        val id = key(email)
        val now = System.currentTimeMillis()
        val lockedUntil = lockPrefs.getLong("$id.locked_until", 0L)
        if (now < lockedUntil) return null
        val account = AutoPawDatabase.call { dao.account(id) }
        val valid = if (account == null) false else {
            val salt = Base64.decode(account.salt, Base64.NO_WRAP)
            val hash = Base64.decode(account.passwordHash, Base64.NO_WRAP)
            MessageDigest.isEqual(hash, derive(password, salt))
        }
        if (valid) {
            lockPrefs.edit().remove("$id.failures").remove("$id.locked_until").apply()
            return account!!.name
        }
        val failures = lockPrefs.getInt("$id.failures", 0) + 1
        if (failures >= 5) {
            lockPrefs.edit().putInt("$id.failures", 0).putLong("$id.locked_until", now + 5 * 60_000L).apply()
        } else lockPrefs.edit().putInt("$id.failures", failures).apply()
        return null
    }
    fun changePassword(email: String, currentPassword: String, newPassword: String): Boolean {
        val account = AutoPawDatabase.call { dao.account(key(email)) } ?: return false
        val saltOld = Base64.decode(account.salt, Base64.NO_WRAP)
        val oldHash = Base64.decode(account.passwordHash, Base64.NO_WRAP)
        if (!MessageDigest.isEqual(oldHash, derive(currentPassword, saltOld))) return false
        val newSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val updated = AccountEntity(
            account.email,
            account.name,
            Base64.encodeToString(newSalt, Base64.NO_WRAP),
            Base64.encodeToString(derive(newPassword, newSalt), Base64.NO_WRAP)
        )
        return runCatching {
            AutoPawDatabase.call { dao.updateAccount(updated) }
            true
        }.getOrDefault(false)
    }
}
