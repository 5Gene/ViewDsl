package osp.spark.view.wings

import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.reflect.KProperty1


inline fun <R, T> LiveData<T>.focusOn(
    crossinline transform: (T) -> R
): LiveData<R> {
    val outerLiveData = MediatorLiveData<R>()
    outerLiveData.addSource(this) {
        val pre = outerLiveData.value
//        val curr = it.transform()
        val curr = transform(it)
        if (pre != curr) {
            outerLiveData.value = curr!!
        }
    }
    return outerLiveData
}

inline fun <R, T> LiveData<T>.mapNotNull(
    crossinline transform: (T) -> R?
): LiveData<R> {
    val outerLiveData = MediatorLiveData<R>()
    outerLiveData.addSource(this) {
        val pre = outerLiveData.value
//        val curr = it.transform()
        val curr = transform(it)
        if (curr != null && pre != curr) {
            outerLiveData.value = curr
        }
    }
    return outerLiveData
}

fun <T> LiveData<T>.classChange(): LiveData<T> {
//    val outerLiveData = if (isInitialized) {
//        MediatorLiveData<T>(value)
//    } else {
//        MediatorLiveData<T>()
//    }

    val outerLiveData = MediatorLiveData<T>()
    outerLiveData.addSource(this) {
        val preClass = outerLiveData.value?.javaClass
        val currClass = it?.javaClass
        if (preClass != currClass) {
            outerLiveData.value = it
        }
    }
    return outerLiveData
}

fun <R, T> LiveData<T>.observeOn(
    owner: LifecycleOwner,
    transform: (T) -> R,
    observer: Observer<R?>
) {
    focusOn(transform).observe(owner, observer)
}

@Suppress("UNCHECKED_CAST")
inline fun <R, T> LiveData<T>.observeOn(
    view: View,
    crossinline transform: (T) -> R = { this as R },
    observer: Observer<R>
) {
    /**
     * 在ComponentActivity的setContent()方法中 会调用initViewTreeOwners() 之后才可以用findxxx方法
     * findxxx 不能在view的构造方法中使用
     */
    (view.findViewTreeLifecycleOwner() ?: view.context.safeAs<ComponentActivity>())?.apply {
        focusOn(transform).observe(this, observer)
    } ?: focusOn(transform).observeForever(observer)

}

fun <R, T> LiveData<T>.property(
    property: KProperty1<T, R>
): LiveData<R?> {
    if (value == null) {
        throw RuntimeException("LiveData must init data")
    }
    val outerLiveData = MediatorLiveData<R?>()
    outerLiveData.addSource(this) {
        val pre = outerLiveData.value
        val curr = property.get(it)
//        val curr = transform(it)
        if (pre != curr || curr == null) {
            outerLiveData.value = curr
        }
    }
    return outerLiveData
}

fun <R, T> LiveData<T>.observeProp(
    owner: LifecycleOwner,
    property: KProperty1<T, R>,
    observer: Observer<R?>
) {
    property(property).observe(owner, observer)
}

fun <R, T> LiveData<T>.observeProp(
    view: View,
    property: KProperty1<T, R>,
    observer: Observer<R?>
) {
    /**
     * 在ComponentActivity的setContent()方法中 会调用initViewTreeOwners() 之后才可以用findxxx方法
     * findxxx 不能在view的构造方法中使用
     */
    val lifecycleOwner = view.findViewTreeLifecycleOwner() ?: view.context.safeAs<ComponentActivity>()!!
    (view.findViewTreeLifecycleOwner() ?: view.context.safeAs<ComponentActivity>())?.apply {
        property(property).observe(lifecycleOwner, observer)
    } ?: property(property).observeForever(observer)

}

fun <R, T> Flow<T>.property(
    property: KProperty1<T, R>
): Flow<R?> {
    return this.map { property.get(it) }.distinctUntilChanged()
}

inline fun <R, T> Flow<T>.focusOn(
    crossinline transform: (T) -> R
): Flow<R> {
    return this.map { transform(it) }.distinctUntilChanged()
}

inline fun <R, T> Flow<T>.collectOn(
    view: View,
    crossinline transform: (T) -> R,
    collector: FlowCollector<R>
) {
    (view.findViewTreeLifecycleOwner() ?: view.context.safeAs<ComponentActivity>())?.apply {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                this@collectOn.focusOn(transform).collect(collector)
            }
        }
    } ?: CoroutineScope(Dispatchers.IO).launch {
        this@collectOn.focusOn(transform).collect(collector)
    }
}