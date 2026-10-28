package cl.autopaw.app

enum class SensorState(val label: String) {
    PAUSED("Monitoreo detenido"),
    STABILIZING("Estabilizando sensor"),
    INVALID("Lectura inválida"),
    WATCHING("En vigilancia"),
    OBSERVATION("En observación"),
    ALERT("Alerta confirmada")
}

/** Lógica de detección. El tiempo recibido es monotónico y está expresado en milisegundos. */
class DetectionEngine(private val stabilizationMillis: Long = 20_000L) {
    private var candidateSince: Long? = null
    private var startedAt: Long? = null
    private var alertSince: Long? = null
    private var lastRepeatAt: Long? = null

    fun reset() {
        candidateSince = null
        startedAt = null
        alertSince = null
        lastRepeatAt = null
    }

    fun evaluate(distance: Int, movement: Boolean, threshold: Int, seconds: Int,
                 monitoring: Boolean, now: Long): SensorState {
        require(threshold in 2..400 && seconds in 1..30)
        if (!monitoring) { reset(); return SensorState.PAUSED }
        if (startedAt == null || now < startedAt!!) {
            startedAt = now
            candidateSince = null
        }
        if (distance !in 2..400) {
            candidateSince = null
            alertSince = null
            lastRepeatAt = null
            return SensorState.INVALID
        }
        if (now - startedAt!! < stabilizationMillis) {
            candidateSince = null
            return SensorState.STABILIZING
        }
        if (distance > threshold) {
            candidateSince = null
            alertSince = null
            lastRepeatAt = null
            return SensorState.WATCHING
        }
        if (!movement) {
            candidateSince = null
            alertSince = null
            lastRepeatAt = null
            return SensorState.OBSERVATION
        }
        val start = candidateSince
        if (start == null || now < start) candidateSince = now
        return if (now - candidateSince!! >= seconds * 1000L) {
            if (alertSince == null) {
                alertSince = now
                lastRepeatAt = now
            }
            SensorState.ALERT
        } else SensorState.OBSERVATION
    }

    /** True cada cinco segundos mientras la condición confirmada siga activa. */
    fun shouldRepeatAlert(now: Long): Boolean {
        val last = lastRepeatAt ?: return false
        if (now - last >= 5_000L) {
            lastRepeatAt = now
            return true
        }
        return false
    }
}
