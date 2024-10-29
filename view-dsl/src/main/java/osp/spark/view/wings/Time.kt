package osp.spark.view.wings

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

private const val MILLIS_IN_A_DAY = 24 * 60 * 60 * 1000

/**
 * 获取今天零点时间戳
 */
fun getTodayStartMillis(): Long {
    // 一天的毫秒数
    val currentTimeMillis = System.currentTimeMillis()
    // 获取当前时间的时区偏移（包括夏令时）
    val timeZone = java.util.TimeZone.getDefault()
    val offset = timeZone.getOffset(currentTimeMillis)

    // 计算今天开始时间的毫秒数
    return (currentTimeMillis + offset) / MILLIS_IN_A_DAY * MILLIS_IN_A_DAY - offset
}

object ago

object fromNow

//这个注解标志着用于测量时间和处理持续时间的标准库API的实验性预览。
//请注意,这个API处于预览状态,并且在未来有很大的机会被改变。如果你开发了一个库,请不要使用它,因为你的库将成为与未来版本的标准库不兼容的二进制库。
//必须通过使用OptIn批注（例如 @OptIn(ExperimentalTime::class) 对声明的使用或通过使用编译器参数 -Xopt-in=kotlin.time.ExperimentalTime 来接受使用 @ExperimentalTime 注释的声明。
//@ExperimentalTime
val Int.nanoseconds: Duration
    get() = Duration.ofNanos(toLong())

val Int.microseconds: Duration
    get() = Duration.ofNanos(toLong() * 1000L)

val Int.milliseconds: Duration
    get() = Duration.ofMillis(toLong())

val Int.seconds: Duration
    get() = Duration.ofSeconds(toLong())

val Int.minutes: Duration
    get() = Duration.ofMinutes(toLong())

val Int.hours: Duration
    get() = Duration.ofHours(toLong())

val Int.days: Period
    get() = Period.ofDays(this)

val Int.weeks: Period
    get() = Period.ofWeeks(this)

val Int.months: Period
    get() = Period.ofMonths(this)

val Int.years: Period
    get() = Period.ofYears(this)

val Duration.ago: LocalDateTime
    get() = baseTime() - this

val Duration.fromNow: LocalDateTime
    get() = baseTime() + this

val Period.ago: LocalDate
    get() = baseDate() - this

val Period.fromNow: LocalDate
    get() = baseDate() + this

infix fun Int.nanoseconds(fromNow: fromNow) = baseTime().plusNanos(toLong())

infix fun Int.nanoseconds(ago: ago) = baseTime().minusNanos(toLong())

infix fun Int.microseconds(fromNow: fromNow) = baseTime().plusNanos(1000L * toLong())

infix fun Int.microseconds(ago: ago) = baseTime().minusNanos(1000L * toLong())

infix fun Int.milliseconds(fromNow: fromNow) = baseTime().plusNanos(1000000L * toLong())

infix fun Int.milliseconds(ago: ago) = baseTime().minusNanos(1000000L * toLong())

infix fun Int.seconds(fromNow: fromNow) = baseTime().plusSeconds(toLong())

infix fun Int.seconds(ago: ago) = baseTime().minusSeconds(toLong())

infix fun Int.minutes(fromNow: fromNow) = baseTime().plusMinutes(toLong())

infix fun Int.minutes(ago: ago) = baseTime().minusMinutes(toLong())

infix fun Int.hours(fromNow: fromNow) = baseTime().plusHours(toLong())

infix fun Int.hours(ago: ago) = baseTime().minusHours(toLong())

infix fun Int.days(fromNow: fromNow) = baseDate().plusDays(toLong())

infix fun Int.days(ago: ago) = baseDate().minusDays(toLong())

infix fun Int.weeks(fromNow: fromNow) = baseDate().plusWeeks(toLong())

infix fun Int.weeks(ago: ago) = baseDate().minusWeeks(toLong())

infix fun Int.months(fromNow: fromNow) = baseDate().plusMonths(toLong())

infix fun Int.months(ago: ago) = baseDate().minusMonths(toLong())

infix fun Int.years(fromNow: fromNow) = baseDate().plusYears(toLong())

infix fun Int.years(ago: ago) = baseDate().minusYears(toLong())

private fun baseDate() = LocalDate.now()

private fun baseTime() = LocalDateTime.now()