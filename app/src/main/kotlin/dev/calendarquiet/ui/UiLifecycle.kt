package dev.calendarquiet.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle

/** UI clocks and provider observers have no work while this activity is stopped. */
@Composable
internal fun rememberUiStarted(context: Context): Boolean {
    val lifecycle = (context as ComponentActivity).lifecycle
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return started
}

internal fun Context.uiLifecycle(): Lifecycle = (this as ComponentActivity).lifecycle

internal suspend fun Lifecycle.withVisibleUi(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
    repeatOnLifecycle(Lifecycle.State.STARTED, block)
}
