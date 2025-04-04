package osp.spark.view.wings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData


inline fun <R, T> LiveData<T>.focusOn(
    crossinline transform: T.() -> R
): LiveData<R> {
    if (value == null) {
        throw RuntimeException("LiveData must init data")
    }
    val outerLiveData = MediatorLiveData<R>()
    outerLiveData.addSource(this) {
        val pre = outerLiveData.value
        val curr = it.transform()
//        val curr = transform(it)
        if (pre != curr) {
            outerLiveData.value = curr!!
        }
    }
    return outerLiveData
}

inline fun <R, T> LiveData<T>.mapNotNull(
    crossinline transform: T.() -> R?
): LiveData<R> {
    if (value == null) {
        throw RuntimeException("LiveData must init data")
    }
    val outerLiveData = MediatorLiveData<R>()
    outerLiveData.addSource(this) {
        val pre = outerLiveData.value
        val curr = it.transform()
//        val curr = transform(it)
        if (curr != null && pre != curr) {
            outerLiveData.value = curr
        }
    }
    return outerLiveData
}