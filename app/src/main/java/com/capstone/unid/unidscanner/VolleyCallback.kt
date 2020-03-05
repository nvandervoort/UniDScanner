package com.capstone.unid.unidscanner

/**
 * VolleyCallback.kt
 * Created by nathanvandervoort on 11/15/17.
 *
 * used with Volley HTTP requests to retrieve response
 */
abstract class VolleyCallback<in K> {
    abstract fun onSuccess(response: K)
    open fun onFailure() {}
}