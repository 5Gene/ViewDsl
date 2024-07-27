package osp.june.wings


inline fun <reified T> Any?.safeAs(): T? = this as? T