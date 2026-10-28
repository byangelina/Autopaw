package cl.autopaw.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionEngineTest {
    @Test fun requiresContinuousMovementAndProximity() {
        val e=DetectionEngine(0)
        assertEquals(SensorState.WATCHING,e.evaluate(84,false,30,2,true,0))
        assertEquals(SensorState.OBSERVATION,e.evaluate(25,false,30,2,true,0))
        assertEquals(SensorState.OBSERVATION,e.evaluate(18,true,30,2,true,100))
        assertEquals(SensorState.OBSERVATION,e.evaluate(18,true,30,2,true,2099))
        assertEquals(SensorState.ALERT,e.evaluate(18,true,30,2,true,2100))
    }
    @Test fun losingMotionRestartsConfirmation() {
        val e=DetectionEngine(0)
        e.evaluate(18,true,30,2,true,0)
        e.evaluate(18,false,30,2,true,1500)
        assertEquals(SensorState.OBSERVATION,e.evaluate(18,true,30,2,true,2500))
        assertEquals(SensorState.ALERT,e.evaluate(18,true,30,2,true,4500))
    }
    @Test fun pauseAndResumeNeverReuseElapsedConfirmation() {
        val e=DetectionEngine(0)
        e.evaluate(18,true,30,2,true,0)
        assertEquals(SensorState.PAUSED,e.evaluate(18,true,30,2,false,1500))
        assertEquals(SensorState.OBSERVATION,e.evaluate(18,true,30,2,true,8000))
        assertEquals(SensorState.ALERT,e.evaluate(18,true,30,2,true,10000))
    }
    @Test fun respectsConfiguredThresholdBoundaryAndTime() {
        val e=DetectionEngine(0)
        assertEquals(SensorState.WATCHING,e.evaluate(31,true,30,1,true,0))
        assertEquals(SensorState.OBSERVATION,e.evaluate(30,true,30,1,true,100))
        assertEquals(SensorState.ALERT,e.evaluate(30,true,30,1,true,1100))
        e.reset()
        assertEquals(SensorState.WATCHING,e.evaluate(18,true,10,2,true,1200))
    }
    @Test fun invalidDistanceHasItsOwnState() {
        assertEquals(SensorState.INVALID, DetectionEngine(0).evaluate(-1, false, 30, 2, true, 0))
    }
    @Test fun pirStabilizationWaitsTwentySeconds() {
        val e = DetectionEngine()
        assertEquals(SensorState.STABILIZING, e.evaluate(18, true, 30, 2, true, 0))
        assertEquals(SensorState.STABILIZING, e.evaluate(18, true, 30, 2, true, 19_999))
        assertEquals(SensorState.OBSERVATION, e.evaluate(18, true, 30, 2, true, 20_000))
        assertEquals(SensorState.ALERT, e.evaluate(18, true, 30, 2, true, 22_000))
    }
    @Test fun repeatsAlertEveryFiveSeconds() {
        val e = DetectionEngine(0)
        e.evaluate(18, true, 30, 1, true, 0)
        assertEquals(SensorState.ALERT, e.evaluate(18, true, 30, 1, true, 1_000))
        assertEquals(false, e.shouldRepeatAlert(5_999))
        assertEquals(true, e.shouldRepeatAlert(6_000))
        assertEquals(false, e.shouldRepeatAlert(10_999))
        assertEquals(true, e.shouldRepeatAlert(11_000))
    }
}
