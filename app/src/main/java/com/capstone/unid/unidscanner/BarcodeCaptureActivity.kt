/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file and all BarcodeXXX and CameraXXX files in this project edited by
 * Daniell Algar (included due to copyright reason)
 *
 * Converted to Kotlin, extended, and edited by unidscanner.unid.capstone.com (Worthday) team
 */
package com.capstone.unid.unidscanner

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationCompat
import android.support.v7.app.AppCompatActivity
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.creativityapps.gmailbackgroundlibrary.BackgroundMail
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.vision.MultiProcessor
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.varvet.barcodereadersample.barcode.BarcodeTracker
import com.varvet.barcodereadersample.barcode.BarcodeTrackerFactory
import com.varvet.barcodereadersample.camera.CameraSource
import com.varvet.barcodereadersample.camera.CameraSourcePreview
import kotlinx.android.synthetic.main.activity_barcode_capture.*
import java.io.IOException
import java.util.*
import java.util.concurrent.CountDownLatch

class BarcodeCaptureActivity : AppCompatActivity(), BarcodeTracker.BarcodeGraphicTrackerCallback {

    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var flashOn = false

    private var bearerToken: String? = null
    private lateinit var handler: WorkdayApiHandler

    private lateinit var authId: String
    private var isEvent: Boolean = true
    private lateinit var checkInKey: String
    private val authDocRef: DocumentReference
        get() = db.collection(if (isEvent) "events" else "facilities").document(authId)
    private var isDiningCommons = false
    private val remSwipesMap: MutableMap<String, Int> = mutableMapOf()  // id to remSwipes

    @Volatile private var displayingStudentInfo = false
    private var postponeHidingStudentInfo = false
    private lateinit var notifyMgr: NotificationManager

    private var lastStudentInfo: Pair<String, String>? = null  // preferredName, email
    private var lastStudentPhoto: Bitmap? = null
    private var lastStudentId: String? = null
    private var lastScannedValid: Boolean = false
    private val studentCheckInHistory: ArrayDeque<Pair<String?, String?>> = ArrayDeque()  // name, id


    private val db = FirebaseFirestore.getInstance()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_capture)

        bearerToken = intent?.getStringExtra(getString(R.string.admin_bearer_token))
        bearerTokenLost
        handler = WorkdayApiHandler(this, bearerToken!!)

        authId = intent.getStringExtra(getString(R.string.AUTH_DOC_ID))
        isEvent = intent.getBooleanExtra(getString(R.string.IS_EVENT), true)
        checkInKey = "checkIns${if (isEvent) "" else " " + Date().getSimpleDateFormat()}"
        if (!isEvent) authDocRef.get()
                .addOnSuccessListener { isDiningCommons = it.getString("type") == "dc" }
                .addOnFailureListener { showToast("Error retreiving data, restarting scanner", Toast.LENGTH_LONG); recreate() }

        preview = findViewById<View>(R.id.barcode_preview) as CameraSourcePreview

        notifyMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val autoFocus = true
        val useFlash = false

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        val rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(autoFocus, useFlash)
        } else {
            requestCameraPermission()
        }

        val flashAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        flash_toggle_button.setOnClickListener {
            if (flashAvailable)
                if (!flashOn) {
                    cameraSource?.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                    flash_toggle_button.setImageResource(R.drawable.ic_flash_on_white_48dp)
                    flashOn = true
                }
                else {
                    cameraSource?.flashMode = Camera.Parameters.FLASH_MODE_OFF
                    flash_toggle_button.setImageResource(R.drawable.ic_flash_off_white_48dp)
                    flashOn = false
                }
        }

        show_last_student.setOnClickListener { showLastStudentIdFromUiThread() }

        // refresh of views ensures fix of camera preview stretching issue
        student_id_linear_layout.visibility = View.VISIBLE
        student_id_linear_layout.postDelayed({ student_id_linear_layout.visibility = View.GONE }, 100)

        val postponeHidingTouchListener = View.OnTouchListener { _, motionEvent ->
            when (motionEvent.action) {
                0 -> postponeHidingStudentInfo = true
                1 -> postponeHidingStudentInfo = false
            }
            true
        }

        barcode_preview.setOnTouchListener(postponeHidingTouchListener)
        student_id_linear_layout.setOnTouchListener(postponeHidingTouchListener)

        undo_last_scan.setOnClickListener { undoLastScan() }

    }

    private fun undoLastScan() {
        val lastStudent = studentCheckInHistory.peekFirst()
        val remSwipes = remSwipesMap[lastStudent.second]
        if (isDiningCommons && remSwipes != null && remSwipes >= 0)
            handler.changeRemSwipes(lastStudent.second, 1, object: VolleyCallback<Int>() {
                override fun onSuccess(response: Int) {
                    remSwipesMap[lastStudent.second ?: ""] = remSwipes + 1
                }
            }, remSwipes)

        authDocRef.update("$checkInKey.${lastStudent.second}", FieldValue.delete())
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        showToastOnUiThread("Student ${lastStudent.first}'s check-in undone")
                        studentCheckInHistory.removeFirst()
                        if (studentCheckInHistory.isEmpty()) runOnUiThread { undo_last_scan.visibility = View.GONE }
                    } else showToastOnUiThread("Error connecting to database")
                }
    }

    private val bearerTokenLost: Boolean
        get() {
            if (bearerToken == null) {
                showBearerTokenLostAlert()
                return true
            }
            return false
        }

    override fun onDetectedQrCode(barcode: Barcode?) {
        if (barcode != null && !barcode.displayValue.all { it.isDigit() } && !displayingStudentInfo) {
            Log.d("BARCODE_CAPTURE", "barcode captured: '${barcode.displayValue}'")
            if (bearerTokenLost) return
            displayingStudentInfo = true
            shortVibrate()

            var expectedQrVersion = -1
            val qrInfo = QrCodeId(barcode.displayValue)

            val countdown = CountDownLatch(3)
            val onInvalidId: () -> Unit = {
                lastScannedValid = false
                countdown.countDown()
            }

            if (!qrInfo.valid) {
                showToastOnUiThread("Invalid ID")
                displayingStudentInfo = false
                return
            }

            runOnUiThread { showConnecting(true) }

            // get student info (name, email)
            handler.getStudentInfo(qrInfo.studentId, object: VolleyCallback<Pair<String, String>>() {
                override fun onSuccess(response: Pair<String, String>) {
                    lastStudentInfo = response
                    lastScannedValid = true
                    countdown.countDown()
                }

                override fun onFailure() = onInvalidId()
            })

            // get student photo
            handler.getStudentPhotoId(qrInfo.studentId, object: VolleyCallback<String>() {
                override fun onSuccess(response: String) {
                    handler.getStudentPhoto(qrInfo.studentId, response, object: VolleyCallback<Bitmap>() {
                        override fun onSuccess(response: Bitmap) {
                            lastStudentPhoto = response
                            countdown.countDown()
                        }
                    })
                }

                override fun onFailure() = onInvalidId()
            })

            // get expected QR version
            handler.getQrVersion(qrInfo.studentId, object: VolleyCallback<Int>() {
                override fun onSuccess(response: Int) {
                    expectedQrVersion = response
                    countdown.countDown()
                }

                override fun onFailure() = onInvalidId()
            })

            countdown.await()
            if (expectedQrVersion == qrInfo.qrVersion && lastScannedValid) {
                lastStudentId = qrInfo.studentId
                showConnecting(false)
                showLastStudentIdFromUiThread()

                if (isDiningCommons) {
                    handler.changeRemSwipes(lastStudentId, -1,
                            object: VolleyCallback<Int>() {
                                override fun onSuccess(response: Int) {
                                    if (response >= 0 || response == -2) checkInStudent()
                                    else showToast("No swipes remaining")
                                    if (lastStudentId != null) remSwipesMap[lastStudentId ?: ""] = response
                                }

                                override fun onFailure() {
                                    showToast("Error retreieving data")
                                }
                            })
                } else checkInStudent()

//                sendCheckInNotif()
            }
            else showToastOnUiThread("Invalid ID or QR version")
            displayingStudentInfo = false
        }
    }

    private fun checkInStudent() {
        studentCheckInHistory.addFirst(Pair(lastStudentInfo?.first, lastStudentId))
        runOnUiThread { undo_last_scan.visibility = View.VISIBLE }

        fun checkIn() {
            authDocRef.update("$checkInKey.$lastStudentId", FieldValue.serverTimestamp())
                    .addOnSuccessListener { showToastOnUiThread("Student checked in") }
        }

        if (isEvent) authDocRef.get()
                .addOnSuccessListener {
                    if (it.contains("$checkInKey.$lastStudentId")) {
                        showToastOnUiThread("Student already checked in to this event", Toast.LENGTH_LONG)
                    } else if (it.getBoolean("restricted") == true) {
                            if (it.contains("ticketOwners.$lastStudentId")) checkIn()
                            else showToastOnUiThread("Student does not have a ticket to this event", Toast.LENGTH_LONG)
                    } else checkIn()
                }
                .addOnFailureListener { showToastOnUiThread("Error checking in") }
        else checkIn()
    }

    /**
     * Needed for when [showLastStudentId] is called from the UI thread
     * (i.e. any time other than when a barcode is scanned)
     */
    private fun showLastStudentIdFromUiThread() {
        Thread(Runnable {
            showLastStudentId()
        }).start()
    }

    /** @author nvandervoort */
    @SuppressLint("SetTextI18n")
    private fun showLastStudentId() {
        if (lastStudentInfo == null) {
            showToastOnUiThread("No students scanned in this session")
            return
        }
        val (lastStudentIdName, lastStudentEmail) = lastStudentInfo!!

        // create and show notification
        val notifBuilder = NotificationCompat.Builder(this, getString(R.string.last_scanned_notif_id))
                .setSubText("Last student scanned")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(lastStudentPhoto ?: BitmapFactory.decodeResource(resources, R.drawable.ic_perm_identity_black_48dp))
                .setContentTitle(lastStudentIdName)
                .setContentText("Perm: $lastStudentEmail")
                .setPriority(NotificationCompat.PRIORITY_MAX)

        runOnUiThread {
            if (lastStudentPhoto != null) student_id_pic.setImageBitmap(lastStudentPhoto)
            else student_id_pic.setImageResource(R.drawable.ic_perm_identity_black_48dp)
            student_id_info.text = "$lastStudentIdName\n$lastStudentEmail"
            student_id_linear_layout.visibility = View.VISIBLE

            // Builds the notification and issues it.
            notifyMgr.notify(studentIdNotifId, notifBuilder.build())
        }

        Thread.sleep(3000)
        while (postponeHidingStudentInfo) Thread.sleep(200)
        Thread.sleep(500)

        runOnUiThread {
            student_id_linear_layout.visibility = View.GONE
        }

     }

    private fun showConnecting(show: Boolean) {
        connecting_spinkit.animate()
                .setDuration(ANIM_TIME_SHORT)
                .alpha(if (show) 1f else 0f)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        connecting_spinkit.visibility = if (show) View.VISIBLE else View.GONE
                    }
                })
    }

    // Handles the requesting of the camera permission.
    private fun requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission")

        val permissions = arrayOf(Manifest.permission.CAMERA)

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM)
        }
    }

    /**
     * Creates and starts the camera.
     *
     * Suppressing InlinedApi since there is a check that the minimum version is met before using
     * the constant.
     */
    @SuppressLint("InlinedApi")
    private fun createCameraSource(autoFocus: Boolean, useFlash: Boolean) {
        val context = applicationContext

        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
        val barcodeDetector = BarcodeDetector.Builder(context)
                .setBarcodeFormats(Barcode.ALL_FORMATS)
                .build()
        val barcodeFactory = BarcodeTrackerFactory(this)
        barcodeDetector.setProcessor(MultiProcessor.Builder(barcodeFactory).build())

        if (!barcodeDetector.isOperational) {
            // Note: The first time that an app using the barcode or face API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any barcodes
            // and/or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.")

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            val lowstorageFilter = IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW)
            val hasLowStorage = registerReceiver(null, lowstorageFilter) != null

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error,
                        Toast.LENGTH_LONG).show()
                Log.w(TAG, getString(R.string.low_storage_error))
            }
        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        var builder: CameraSource.Builder = CameraSource.Builder(applicationContext, barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(metrics.widthPixels, metrics.heightPixels)
                .setRequestedFps(24.0f)

        // make sure that auto focus is an available option
        builder = builder.setFocusMode(
                if (autoFocus) Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE else null)

        cameraSource = builder
                .setFlashMode(if (useFlash) Camera.Parameters.FLASH_MODE_TORCH else null)
                .build()
    }

    // Restarts the camera
    override fun onResume() {
        super.onResume()
        startCameraSource()
    }

    // Stops the camera
    override fun onPause() {
        super.onPause()
        if (preview != null) {
            preview!!.stop()
        }
    }

    override fun onStop() {
        super.onStop()
        notifyMgr.cancel(studentIdNotifId)
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (preview != null) {
            preview!!.release()
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on [.requestPermissions].
     *
     *
     * **Note:** It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     *
     *
     * @param requestCode  The request code passed in [.requestPermissions].
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     * which is either [PackageManager.PERMISSION_GRANTED]
     * or [PackageManager.PERMISSION_DENIED]. Never null.
     * @see .requestPermissions
     */
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: $requestCode")
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source")
            // we have permission, so create the camerasource
            val autoFocus = true
            val useFlash = false
            createCameraSource(autoFocus, useFlash)
            return
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.size +
                " Result code = " + if (grantResults.isNotEmpty()) grantResults[0] else "(empty)")

        val listener = DialogInterface.OnClickListener { dialog, id -> finish() }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Multitracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show()
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    @Throws(SecurityException::class)
    private fun startCameraSource() {
        // check that the device has play services available.
        val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                applicationContext)
        if (code != ConnectionResult.SUCCESS) {
            val dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS)
            dlg.show()
        }

        if (cameraSource != null) {
            try {
                preview!!.start(cameraSource)
            } catch (e: IOException) {
                Log.e(TAG, "Unable to start camera source.", e)
                cameraSource!!.release()
                cameraSource = null
            }

        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.barcode_capture_activity_options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.show_last_student -> showLastStudentIdFromUiThread()
            R.id.undo_last_scan -> undoLastScan()
            R.id.logout -> {
                val intent = Intent(this, SelectAuthorizationActivity::class.java)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }
        return true
    }

//    private fun CameraSource.getCamera(cameraSource: CameraSource) {
//        Field[] val declaredFields: Field[] = CameraSource.class.getDeclaredFields()
//
//        for (Field field : declaredFields) {
//            if (field.getType() == Camera.class) {
//                field.setAccessible(true)
//                try {
//                    Camera camera = (Camera) field.get(cameraSource)
//                    if (camera != null) {
//                        return camera
//                    }
//                    return null;
//                } catch (IllegalAccessException e) {
//                    e.printStackTrace()
//                }
//                break
//            }
//        }
//        return null
//}

    companion object {

        private const val TAG = "Barcode-reader"

        // Intent request code to handle updating play services if needed.
        private const val RC_HANDLE_GMS = 9001

        // Permission request codes need to be < 256
        private const val RC_HANDLE_CAMERA_PERM = 2

        private const val studentIdNotifId = 1
    }
}
