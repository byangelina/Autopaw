package cl.autopaw.app

import org.junit.Assert.*
import org.junit.Test

class SecureSensorProtocolTest {
    @Test fun encryptedMessageRoundTripsAndChecksSequence() {
        val sender = SecureSensorProtocol("clave-compartida-segura")
        val receiver = SecureSensorProtocol("clave-compartida-segura")
        val line = sender.encode(1, 25, true)
        assertFalse(line.contains("DISTANCIA"))
        val reading = receiver.decode(line)
        assertNotNull(reading)
        assertEquals(1L, reading!!.sequence)
        assertEquals(25, reading.distanceCm)
        assertTrue(reading.movement)
        assertNull("No debe aceptar una secuencia repetida", receiver.decode(line))
    }

    @Test fun modifiedMessageIsRejected() {
        val sender = SecureSensorProtocol("clave-compartida-segura")
        val receiver = SecureSensorProtocol("clave-compartida-segura")
        val line = sender.encode(1, 25, false)
        val changed = line.dropLast(2) + if (line.last() == 'A') "B" else "A"
        assertNull(receiver.decode(changed))
    }

    @Test fun staleReadingTimeoutIsTwoSeconds() {
        assertFalse(ReadingFreshness.hasTimedOut(1_000L, 3_000L))
        assertTrue(ReadingFreshness.hasTimedOut(1_000L, 3_001L))
        assertFalse(ReadingFreshness.hasTimedOut(0L, 10_000L))
    }

    @Test fun unknownAndOversizedMessagesAreRejected() {
        val receiver = SecureSensorProtocol("clave-compartida-segura")
        assertNull(receiver.decode("DISTANCIA: 25 cm;MOVIMIENTO:1"))
        assertNull(receiver.decode("X".repeat(SecureSensorProtocol.MAX_LINE + 1)))
    }
}
