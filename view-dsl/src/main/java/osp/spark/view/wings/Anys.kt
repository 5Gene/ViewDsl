package osp.spark.view.wings

import java.lang.reflect.InvocationHandler
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy


inline fun <reified T> Any?.safeAs(): T? = this as? T

fun <T> Any.firstActualType(): Class<T> {
    val clazz = this::class.java
    val superClass = clazz.genericSuperclass
    return (superClass as ParameterizedType).actualTypeArguments.first() as Class<T>
}

internal inline fun <reified T : Any> noOpDelegate(): T {
    val javaClass = T::class.java
    return Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(javaClass), NO_OP_HANDLER
    ) as T
}

private val NO_OP_HANDLER = InvocationHandler { _, _, _ ->
    // no op
}