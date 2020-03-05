package com.capstone.unid.unidscanner

/**
 * QrCodeId.kt
 *
 * Pulls out student verificiation info from raw QR code
 * QR codes are in format `[studentId]-[qrHash] ([qrVersion])`
 * Created by nathanvandervoort on 1/8/18.
 */

class QrCodeId(origCode: String) {
    val studentId: String
    val qrVersion: Int
    val valid = origCode.count { it == '-' } == 1

    init {
        val studentInfos = origCode.split('-')
        studentId = studentInfos.getOrElse(0, { "" })
        qrVersion = qrHash(studentInfos.getOrElse(1, { "-1" }).toInt() )
    }

    /** Involution, verified unique to at least 2^20 */
    private fun qrHash(n: Int): Int {
        return ((0x00FF and n) shl 8)+((0xFF00 and n) shr 8)
    }
}
