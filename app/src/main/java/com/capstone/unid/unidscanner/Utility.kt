package com.capstone.unid.unidscanner

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*


/**
 * Utility - contains useful classes and functions
 *
 * Created by nathanvandervoort on 5/10/17.
 */

const val DEFAULT_QR_VERSION = 0

/**
 * @author Dídac Pérez Parera
 * @see <a href="this post">https://stackoverflow.com/questions/18676471/best-way-to-avoid-toast-accumulation-in-android</a>
 */
object SingleToast {

    private var mToast: Toast? = null

    fun show(context: Context, text: String, duration: Int=Toast.LENGTH_SHORT) {
        hide()
        mToast = Toast.makeText(context, text, duration)
        mToast!!.show()
    }

    fun hide() {
        mToast?.cancel()
    }
}

/*
 * Extensions
 */

fun Boolean.toInt() : Int = if (this) 1 else 0

fun Int.toBoolean() : Boolean = this != 0

val Activity.privatePrefs: SharedPreferences
    get() = getPreferences(Context.MODE_PRIVATE)

fun Activity.showToast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, text, duration).show()
}

fun Activity.showToastOnUiThread(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    runOnUiThread { showToast(text, duration) }
}

val Activity.ANIM_TIME_SHORT: Long
    get() = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

fun Context.showToast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, text, duration).show()
}

fun Context.shortVibrate() {
    if (Build.VERSION.SDK_INT >= 26)
        (getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(150,10))
    else
        (getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator).vibrate(150)
}

fun Context.showBearerTokenLostAlert() {
    showToast("Bearer token lost, please press back and log in again")
}


/* Date/String extensions */

/** Creates a Date object from a string with the format MM-dd-yyyy HH:mm */
fun String.getSimpleDate(): Date = SimpleDateFormat("MM-dd-yyyy HH:mm", Locale.getDefault()).parse(this)

fun Date.getSimpleDateFormat(): String = SimpleDateFormat("MM-dd-yyyy", Locale.getDefault()).format(this)

fun Date.getFancyDateFormat(): String = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(this)

fun Date.getFancyTimeFormat(): String = SimpleDateFormat("h:mm aa", Locale.getDefault()).format(this)

/** Reverse of [getSimpleDate] */
fun Date.getSimpleDateTimeFormat(): String = SimpleDateFormat("MM-dd-yyyy HH:mm", Locale.getDefault()).format(this)

/** Creates a well-formatted string from a Date object */
fun Date.getFancyDateTimeFormat(): String = SimpleDateFormat("EEE MMM d, yyyy h:mm aa", Locale.getDefault()).format(this)

fun Date.getLongFancyTimeDateFormat(): String = SimpleDateFormat("h:mm aa on EEE MMM d, yyyy", Locale.getDefault()).format(this)

fun Int.permNumFormat() = this.toString().let { it.substring(0..2) + "-" + it.substring(3..6)}
fun Float.format(digits: Int): String = java.lang.String.format("%.${digits}f", this)
fun Double.format(digits: Int): String? = java.lang.String.format("%.${digits}f", this)


/* Array extensions */
fun <E> Array<E>.getOrElse(index: Int, defaultIndex: Int) = this.getOrElse(index, { this[defaultIndex] })
fun <E> Array<E>.distinctValues(): Boolean = this.distinct().size == this.size

/* List extensions */
fun <E> List<E>.getOrElse(index: Int, defaultIndex: Int) = this.getOrElse(index, { this[defaultIndex] })
