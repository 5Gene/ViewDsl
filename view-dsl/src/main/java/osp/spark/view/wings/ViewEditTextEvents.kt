@file:OptIn(FlowPreview::class)

package osp.spark.view.wings

import android.text.Editable
import android.widget.EditText
import androidx.annotation.CheckResult
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doBeforeTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlin.coroutines.cancellation.CancellationException

/**
 *
 * ViewEditTextEventExt 是对 [androidx.core.widget.TextView.kt] 的二次封装，有效的避免内存泄漏
 *
 */

fun <E> SendChannel<E>.safeOffer(value: E) = !isClosedForSend && try {
    trySend(value).isSuccess
} catch (e: CancellationException) {
    false
}

@CheckResult
fun EditText.doAfterTextChangeFlow(): Flow<Editable?> = callbackFlow {
    val textChangedListener = doAfterTextChanged {
        safeOffer(it)
    }
    awaitClose {
        removeTextChangedListener(textChangedListener)
    }
}

@CheckResult
fun EditText.doBeforeTextChangeFlow(): Flow<CharSequence?> = callbackFlow {
    val textChangeListener = doBeforeTextChanged { text, start, count, after ->
        safeOffer(text)
    }
    awaitClose { removeTextChangedListener(textChangeListener) }
}

@CheckResult
fun EditText.doTextChangedFlow(): Flow<CharSequence?> = callbackFlow {
    val textChangeListener = doOnTextChanged { text, start, count, after ->
        safeOffer(text)
    }
    awaitClose { removeTextChangedListener(textChangeListener) }
}

/**
 * Example：
 *
 * editText.textChange(lifecycleScope) {
 *
 * }
 */
inline fun EditText.textChange(
    lifecycle: LifecycleCoroutineScope,
    crossinline onChange: (s: String) -> Unit
) {
    doAfterTextChangeFlow()
        .onEach {
            onChange(it.toString())
        }.launchIn(lifecycle)
}

/**
 * Example：
 *
 * editText.textChange(
 *      lifecycle = lifecycleScope,
 *      timeoutMillis = 500
 * ) {
 *
 * }
 */
inline fun EditText.textChange(
    lifecycle: LifecycleCoroutineScope,
    timeoutMillis: Long = 500,
    crossinline onChange: (s: String) -> Unit
) {
    doAfterTextChangeFlow()
        .debounce(timeoutMillis)
        .onEach {
            onChange(it.toString())
        }.launchIn(lifecycle)
}

/**
 * Example：
 *
 * editText.textChangeWithbefore(lifecycleScope) {
 *
 * }
 */
inline fun EditText.textChangeWithbefore(
    lifecycle: LifecycleCoroutineScope,
    crossinline onChange: (s: String) -> Unit
) {
    doBeforeTextChangeFlow()
        .onEach {
            onChange(it.toString())
        }.launchIn(lifecycle)
}

/**
 * Example：
 *
 * editText.textChangeWithbefore(
 *      lifecycle = lifecycleScope,
 *      timeoutMillis = 500
 * ) {
 *
 * }
 */
inline fun EditText.textChangeWithbefore(
    lifecycle: LifecycleCoroutineScope,
    timeoutMillis: Long = 500,
    crossinline onChange: (s: String) -> Unit
) {
    doBeforeTextChangeFlow()
        .debounce(timeoutMillis)
        .onEach {
            onChange(it.toString())
        }.launchIn(lifecycle)
}

/**
 * Example：
 *
 * editText.textChangeWithAfter(lifecycleScope) {
 *
 * }
 */
inline fun EditText.textChangeWithAfter(
    lifecycle: LifecycleCoroutineScope,
    crossinline onChange: (s: String) -> Unit
) {
    doAfterTextChangeFlow()
        .onEach {
            onChange(it.toString())
        }.launchIn(lifecycle)
}

/**
 * Example：
 *
 * editText.textChangeWithAfter(
 *      lifecycle = lifecycleScope,
 *      timeoutMillis = 500
 * ) {
 *
 * }
 */
inline fun EditText.textChangeWithAfter(
    lifecycle: LifecycleCoroutineScope,
    timeoutMillis: Long = 500,
    crossinline onChange: (s: String) -> Unit
) {
    doAfterTextChangeFlow()
        .debounce(timeoutMillis)
        .onEach {
            onChange(it.toString())
        }.launchIn(lifecycle)
}
