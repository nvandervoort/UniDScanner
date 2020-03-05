package com.capstone.unid.unidscanner

import java.util.*

/**
 * EventAuthorization.kt
 * Created by nathanvandervoort on 10/23/17.
 */
class EventAuthorization(name: String, id: String, description: String, creator: String,
                         var location: String, var startTime: Date, var endTime: Date)
    : Authorization(name, id, description, creator) {

    constructor() : this("name", "id", "description", "creator",
            "location", Date(), Date())
}