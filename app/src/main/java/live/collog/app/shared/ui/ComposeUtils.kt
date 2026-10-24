package live.collog.app.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * SwiftUI `.onChange(of: value) { previous, current in }`: 처음 그릴 때는 부르지 않고,
 * 값이 바뀔 때만 부른다.
 */
@Composable
fun <T> OnChange(value: T, action: suspend (previous: T, current: T) -> Unit) {
    val holder = remember { arrayOf<Any?>(value) }
    LaunchedEffect(value) {
        @Suppress("UNCHECKED_CAST")
        val previous = holder[0] as T
        holder[0] = value
        if (previous != value) action(previous, value)
    }
}
