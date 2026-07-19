package com.gazecontrol.app

data class GazePoint(
    val screenX: Float,
    val screenY: Float,
    val blinkLeft: Boolean,
    val blinkRight: Boolean,
    val faceFound: Boolean
)

/** Простая шина в пределах процесса: GazeTrackingService публикует точки,
 *  GazeAccessibilityService подписывается и рисует курсор / кликает. */
object GazeBus {
    private val listeners = mutableListOf<(GazePoint) -> Unit>()

    fun subscribe(listener: (GazePoint) -> Unit) {
        listeners.add(listener)
    }

    fun unsubscribe(listener: (GazePoint) -> Unit) {
        listeners.remove(listener)
    }

    fun emit(point: GazePoint) {
        listeners.forEach { it(point) }
    }

    var dwellMillis: Long = 800L
    var trackingActive: Boolean = false
}
