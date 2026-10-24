package live.collog.app.features.call.views

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.PI

/** SwiftUI `.spring(response:dampingFraction:)`을 Compose 스프링으로 바꾼다. */
fun <T> responseSpring(response: Float, damping: Float): SpringSpec<T> {
    val omega = (2 * PI / response).toFloat()
    return spring(dampingRatio = damping, stiffness = omega * omega)
}
