package com.capstone.unid.unidscanner

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.ImageView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.ImageRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import org.json.JSONObject

/**
 * WorkdayApiHandler.kt
 *
 * Helps connect the user application to the Workday API
 * All `get...` methods use various [VolleyCallback] instances to pass back data
 * Created by arthurpan on 11/13/17.
 */

class WorkdayApiHandler(val context: Context, private val token: String) {

    fun getNumStudentsInDatabase(callback: VolleyCallback<Int>) {
        val studentsRequest = OAuth2JsonObjectRequest(token, Request.Method.GET, STUDENTS_ENDPOINT, null,
                Response.Listener {
                    callback.onSuccess(it.getInt("total"))
                },
                Response.ErrorListener {
                    Log.d("HANDLER_NUM_STUDENTS", "error is ${String(it.networkResponse.data ?: it.networkResponse.statusCode.toString().toByteArray()) }")
                    callback.onFailure()
                })
        Log.d("HANDLER_STUDENTS", "url of request is ${studentsRequest.url}")
        Volley.newRequestQueue(context).add(studentsRequest)
    }

    fun isConnected(callback: VolleyCallback<Boolean>) {
        getNumStudentsInDatabase(object: VolleyCallback<Int>() {
            override fun onSuccess(response: Int) {
                callback.onSuccess(response == EXPECTED_NUM_STUDENTS)
            }

            override fun onFailure() {
                callback.onFailure()
            }
        })
    }

    /** Puts a pair in the callback containing the student's name and email */
    fun getStudentInfo(studentId: String, callback: VolleyCallback<Pair<String, String>>) {
        val studentRequest = OAuth2JsonObjectRequest(token, Request.Method.GET, STUDENTS_ENDPOINT, null,
                Response.Listener {
                    if (it.has("preferredName"))
                        callback.onSuccess(Pair(it.getString("preferredName"), studentId.substring(0..7)))
                    else callback.onFailure()
                },
                Response.ErrorListener {
                    callback.onFailure()
                }, listOf(studentId))
        Volley.newRequestQueue(context).add(studentRequest)
    }

    fun getStudentPhotoId(studentId: String, callback: VolleyCallback<String>) {
        val studentRequest = OAuth2JsonObjectRequest(token, Request.Method.GET, STUDENTS_ENDPOINT, null,
                Response.Listener {
                    callback.onSuccess(it.getJSONArray("data").getJSONObject(0).getString("id"))
                },
                Response.ErrorListener {
                    callback.onFailure()
                }, listOf("$studentId/photo"))
        Volley.newRequestQueue(context).add(studentRequest)
    }

    fun getStudentPhoto(studentId: String, photoId: String, callback: VolleyCallback<Bitmap>) {
        val photoRequest = OAuth2ImageRequest(token, STUDENTS_ENDPOINT, Response.Listener {
            callback.onSuccess(it)
        }, 0, 0, ImageView.ScaleType.CENTER_CROP, Bitmap.Config.RGB_565,
                Response.ErrorListener {
                    callback.onFailure()
                }, "$studentId/photo/$photoId")
        Volley.newRequestQueue(context).add(photoRequest)
    }

    fun getQrVersion(studentId: String, callback: VolleyCallback<Int>) {
        val versionRequest = OAuth2JsonObjectRequest(token, Request.Method.GET, COMMON_ENDPOINT, null,
                Response.Listener {
                    callback.onSuccess(it.optString("id")?.toInt() ?: DEFAULT_QR_VERSION)
                },
                Response.ErrorListener {
                    if (it.networkResponse.statusCode == 404)
                        callback.onSuccess(DEFAULT_QR_VERSION)  // default (error -> not found)
                    else callback.onFailure()
        }, listOf("customObjects", "qrVersion", studentId))

        Volley.newRequestQueue(context).add(versionRequest)
    }

    /** For the callback, -2 means unlimited swipes, -1 means not enough swipes (i.e. had zero) */
    fun changeRemSwipes(studentId: String?, change: Int, callback: VolleyCallback<Int>, changeFrom: Int? = null) {
        if (studentId == null) { callback.onFailure(); return }
        val bodyParams: (Int) -> JSONObject = {
            JSONObject(
                    """
            {
                "student": {
                    "id": $studentId
                },
                "remSwipes": $it
            }
                    """.trimIndent())
        }

        fun requestChangeSwipes(newRemSwipes: Int, callback: VolleyCallback<Int>) {
            Volley.newRequestQueue(context).add(OAuth2JsonObjectRequest(token, Request.Method.POST, COMMON_ENDPOINT, bodyParams(newRemSwipes),
                    Response.Listener {
                        callback.onSuccess(newRemSwipes)
                    },
                    Response.ErrorListener {
                        Log.d("HANDLER", "errormsg is ${String(it.networkResponse.data)}")
                        callback.onFailure()
                    }, listOf("customObjects", "dcSwipes", studentId), mapOf("updateIfExists" to true)))
        }

        if (changeFrom != null) requestChangeSwipes(changeFrom + change, callback)
        else Volley.newRequestQueue(context).add(
                OAuth2JsonObjectRequest(token, Request.Method.GET, COMMON_ENDPOINT, null,
                        Response.Listener {
                            var remSwipes = 0
                            try {
                                remSwipes = it.getInt("remSwipes")
                            } catch (ignored: JSONException) {}
                            if (change < 0 && remSwipes == 0) callback.onSuccess(-1)
                            else if (remSwipes < 0) callback.onSuccess(-2)
                            else requestChangeSwipes(remSwipes + change, callback)
                        }, Response.ErrorListener {
                    if (it.networkResponse.statusCode == 404) callback.onSuccess(-1)
                    else callback.onFailure()
                }, listOf("customObjects", "dcSwipes", studentId)))

    }

    private class OAuth2JsonObjectRequest(val token: String, method: Int, url: String?,
                                          jsonRequest: JSONObject?, listener: Response.Listener<JSONObject>?,
                                          errorListener: Response.ErrorListener?,
                                          pathParams: List<String> = listOf(),
                                          params: Map<String, Any> =  mapOf())
        : JsonObjectRequest(method, url + formatParams(pathParams, params), jsonRequest, listener, errorListener) {



        override fun getHeaders(): Map<String, String> = mapOf("Authorization" to "Bearer $token")

        companion object {
            fun formatParams(pathParams: List<String>, params: Map<String, Any>): String {
                val pathParamsFormatted = pathParams.joinToString("") { "/$it" }
                val paramsFormatted = params.entries.joinToString("") {
                    "?${it.key}=${if (it.value is String) "\"$it.value\"" else it.value}"
                }
                return pathParamsFormatted + paramsFormatted
            }
        }

    }

    private class OAuth2ImageRequest(val token: String, url: String?, listener: Response.Listener<Bitmap>?,
                                     maxWidth: Int, maxHeight: Int, scaleType: ImageView.ScaleType?,
                                     decodeConfig: Bitmap.Config?, errorListener: Response.ErrorListener?,
                                     pathParam: String? = null,
                                     private val params: Map<String, String>? = null)
        : ImageRequest("$url/${pathParam ?: ""}", listener, maxWidth, maxHeight, scaleType, decodeConfig, errorListener) {

        override fun getHeaders(): Map<String, String> = mapOf("Authorization" to "Bearer $token")

        override fun getParams(): Map<String, String> = params ?: super.getParams()
    }


    companion object {
        const val STUDENTS_ENDPOINT = "STUDENTS_ENDPOINT"  // placeholder
        const val COMMON_ENDPOINT = "COMMON_ENDPOINT"  // placeholder
        const val EXPECTED_NUM_STUDENTS = 52
    }
}
