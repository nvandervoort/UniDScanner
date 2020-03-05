package com.capstone.unid.unidscanner

import android.test.AndroidTestCase
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.Volley
import org.junit.Assert
import org.junit.Test

/**
 * WorkdayApiHandlerTest
 * Created by nathanvandervoort on 11/15/17.
 */
class WorkdayApiHandlerTest : AndroidTestCase() {
    lateinit var handler: WorkdayApiHandler

    override fun setUp() {
        super.setUp()
        handler = WorkdayApiHandler(getContext(), BEARER_TOKEN)  // fixme
    }

    @Test
    fun sizeOfStudentsList() {
        handler.getNumStudentsInDatabase(object: VolleyCallback<Int>() {
            override fun onSuccess(response: Int) {
                Assert.assertEquals(52, response)
            }
        })
    }

    companion object {
        // this will have to be changed periodically maybe
        val BEARER_TOKEN = "eyJraWQiOiI2Y2IwYjU0NC0yYTM4LTQ5ZjUtYTc4Yi00MjU0YmUzOTYxMjciLCJhbGciOi" +
                "JSUzUxMiJ9.eyJpc3MiOiJDXHUwMDNkVVMsU1RcdTAwM2RDQSxMXHUwMDNkUGxlYXNhbnRvbixPVVx1MD" +
                "AzZERldmVsb3BtZW50LE9cdTAwM2RXb3JrZGF5LENOXHUwMDNkT0NUT1BBQVMiLCJhdXRoX3RpbWUiOjE" +
                "1MTA3OTIwMDYsImF1dGhfdHlwZSI6IlBhYVMiLCJzeXNfYWNjdF90eXAiOiJFUyIsInRva2VuVHlwZSI6" +
                "IklkZW50aXR5Iiwic3ViIjoid2NwLWRldmVsb3BlciIsImF1ZCI6IndkIiwiZXhwIjoxNTEwODc4NDA2L" +
                "CJpYXQiOjE1MTA3OTIwMDYsImp0aSI6IjFpcjYxdzFiang2NXFxMXE3ZWhoNG5waXgxb2c1Y3h3d2Fobm" +
                "E1dWtiNmkwYTZjOW15NTczc3RzbjZ0MjFlMHgzYmFtbHQ3dWZnbGUwOWc3Zmh4YXk4ZnptdWF0YjZiaGt" +
                "3am9ueHh4dm4ydHoxa3FiYWIxcHpyYnVnamU1NXR1OTUzdGhyM2w5ODd1MG1xcXJxZDJtdmFpbDd2MGho" +
                "bTgzbHlybmNlbGRmNnpnMm9scWxuN2V4YXV5aXc2MmQ5cjlqZzRjZmpjd29iMWNjd3VpMHVxcG10cWttd" +
                "2c0YmxsOHBjaDI3anp2bWdxbHJhdThremdhYm96aG8wcW81azlzbHgzZndlcng1Z3d2ZmZ4ZGNyaTcwaH" +
                "ljeGI5dGszaXVuNzAyamQwMms1ZnpjNTg5eWowN3AzOXo5MzJkc2JzbG5qZWd6bzlkOGtjNXRpZmwzZHZ" +
                "4M3BvZzF3N21tN2l4Nnl5dzdxODNzMGFzdzc5Y3N5cm5yNnJtenA0OTF4a3hvYndpeXY3dWl2bWMiLCJ0" +
                "ZW5hbnQiOiJjYXBzdG9uZWFtdSJ9.EyXc2TlEGh3c0dzjEjFW7U-Qb03JKjecmfTSXppzWlAK_Mfz0uLE" +
                "0Q2clJS0b1NMPzE8dIgGp_aI0_xgjaxsvh7oCEBL6ZfD-kux7_wJe6SEtBVeFuxwq4HAF9L_NMLABI_Gg" +
                "zT2t-kQTCq-WErTDWoOdQvwkTKDzt060IiswdwDFnS9LhQKp2PG0vLBnXXZFzWlMazxwuskFejCJPjoIM" +
                "QAPbTd6hrWAlWUqXJe3x_Ri_pcLLnohOvl-JBZreohGrniNYKet4UVtwet06F0jnFuJvTgqKJNmjeTvIB" +
                "LGBdC1J1MJps9aOtL21ri_w3ouSpULxBDkYWa3NLtYaVD1w"
    }
}