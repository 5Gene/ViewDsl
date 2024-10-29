package osp.spark.view.wings

import android.util.Log
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
