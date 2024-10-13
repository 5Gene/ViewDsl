@file:OptIn(ExperimentalContracts::class)
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package osp.june.wings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.graphics.toColorInt
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class JSONObjectBuilder : JSONObject() {

    infix fun String.kv(build: JSONObjectBuilder.() -> Unit) = put(this, JSONObjectBuilder().build())

    /**
     *  buildJson {
     *     "key" kv "value"
     *  }
     */
    infix fun String.kv(value: Any) = put(this, value)
}

fun buildJson(build: JSONObjectBuilder.() -> Unit) = JSONObjectBuilder().apply { build() }

val String.Companion.Empty
    inline get() = ""

//kotlin/text/Strings.kt 有很多扩展方法
// isNullOrEmpty
// orEmpty
// ifEmpty
fun String?.orDefault(def: String) = this ?: def

@OptIn(ExperimentalContracts::class)
fun String?.isNotNullOrEmpty(): Boolean {
    contract {
        returns(true) implies (this@isNotNullOrEmpty != null)
    }
    return this != null && !this.trim().equals("null", true) && this.trim().isNotEmpty()
}

fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(toByteArray())).toString(16).padStart(32, '0')
}

fun String.log(tag: String = "") {
    if (tag.isNotEmpty()) {
        Log.d("uispark", "$tag >> $this")
    } else {
        Log.d("uispark", this)
    }
}

//android.graphics.Color  //这里也有很多扩展
inline val String.toColor: Int
    get() = toColorInt()

@SuppressLint("DiscouragedApi")
fun String.toResId(defType: String = "drawable", context: Context): Int {
    return context.resources.getIdentifier(this, defType, context.packageName)
}

fun String.toString(activity: Activity) {
    activity.findString(this)
}

fun String.toDrawable(activity: Activity) {
    activity.findDrawable(this)
}