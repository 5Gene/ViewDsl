package osp.spark.view.wings

class MutableDSLMap<K, V> : LinkedHashMap<K, V> {

    //1-> 次构造函数通过 constructor 关键字定义
    //2-> Kotlin 规定所有次构造函数必须调用主构造函数 (主构造函数定义再类名的位置)
    //3-> 构造函数分别要调用父类对应构造函数的时候 不能有主构造函数
    constructor() : super()

    constructor(initialCapacity: Int) : super(initialCapacity)

    constructor(initialCapacity: Int, loadFactor: Float) : super(initialCapacity, loadFactor)

    infix fun K.put(value: V) {
        put(this, value)
    }

    infix fun K.kv(value: V) {
        put(this, value)
    }

    infix fun K.with(value: V) {
        put(this, value)
    }

    /**
     *  a * b
     */
    operator fun K.times(value: V) {
        put(this, value)
    }

    /**
     *  a - b
     */
    operator fun K.minus(value: V) {
        put(this, value)
    }

    /**
     *  a + b
     */
    operator fun K.plus(value: V) {
        put(this, value)
    }
}

public fun <K, V> dslMapOf(vararg pairs: Pair<K, V>): MutableDSLMap<K, V> =
    if (pairs.isNotEmpty()) pairs.toMap(MutableDSLMap()) else MutableDSLMap()


private const val INT_MAX_POWER_OF_TWO: Int = 1 shl (Int.SIZE_BITS - 2)

fun mapCapacity(expectedSize: Int): Int = when {
    // We are not coercing the value to a valid one and not throwing an exception. It is up to the caller to
    // properly handle negative values.
    expectedSize < 0 -> expectedSize
    expectedSize < 3 -> expectedSize + 1
    expectedSize < INT_MAX_POWER_OF_TWO -> ((expectedSize / 0.75F) + 1.0F).toInt()
    // any large value
    else -> Int.MAX_VALUE
}