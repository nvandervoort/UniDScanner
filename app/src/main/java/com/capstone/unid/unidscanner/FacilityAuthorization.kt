package com.capstone.unid.unidscanner

/**
 * FacilityAuthorization.kt
 * Created by nathanvandervoort on 10/23/17.
 */
class FacilityAuthorization(title: String, id: String, description: String, creatorName: String)
    : Authorization(title, id, description, creatorName) {

    constructor() : this("title", "id", "description", "creator")
}