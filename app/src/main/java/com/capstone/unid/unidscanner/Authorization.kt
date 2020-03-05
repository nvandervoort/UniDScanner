package com.capstone.unid.unidscanner

/**
 * Authorization.kt
 * Created by nathanvandervoort on 10/23/17.
 * contains a simple class that encapsulates an Authorization
 */

open class Authorization(var name:String, var id: String, var description: String, var creator:String) {
    var error = false
    constructor() : this("error", "error", "", "") {
        error = true
    }
}