package com.mecheri636.offline.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

data class OfflineAuditReport(
    val declaredPermissions: List<String>,
    val forbiddenPermissionsFound: List<String>,
    val isManifestZeroNetwork: Boolean,
    val kernelSocketBlocked: Boolean,
    val kernelSocketProofMessage: String,
    val apkSha256: String
)

object OfflineTrustVerifier {

    private val FORBIDDEN_PERMISSIONS = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_WIFI_STATE",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.BLUETOOTH",
        "android.permission.BLUETOOTH_ADMIN",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.READ_PHONE_STATE",
        "android.permission.NFC",
        "com.google.android.gms.permission.AD_ID"
    )

    suspend fun runLiveAudit(context: Context): OfflineAuditReport = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }

        val declared = pkgInfo.requestedPermissions?.toList().orEmpty()
        val forbiddenFound = declared.filter { it in FORBIDDEN_PERMISSIONS }
        val (blocked, proofMsg) = testKernelSocketBlock()
        val sha256 = computeApkSha256(context.applicationInfo.sourceDir)

        OfflineAuditReport(
            declaredPermissions = declared,
            forbiddenPermissionsFound = forbiddenFound,
            isManifestZeroNetwork = forbiddenFound.isEmpty(),
            kernelSocketBlocked = blocked,
            kernelSocketProofMessage = proofMsg,
            apkSha256 = sha256
        )
    }

    private fun testKernelSocketBlock(): Pair<Boolean, String> {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", 80), 300)
            }
            false to "WARNING: Socket succeeded unexpectedly"
        } catch (se: SecurityException) {
            true to "BLOCKED BY ANDROID KERNEL (${se.message ?: "Permission denied"})"
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val isDenied = msg.contains("EACCES", ignoreCase = true) ||
                msg.contains("Permission denied", ignoreCase = true)
            isDenied to if (isDenied) {
                "BLOCKED BY LINUX KERNEL (EACCES)"
            } else {
                "Socket blocked/rejected offline (${e.javaClass.simpleName})"
            }
        }
    }

    private fun computeApkSha256(apkPath: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(File(apkPath)).use { fis ->
                val buf = ByteArray(8192)
                var read: Int
                while (fis.read(buf).also { read = it } > 0) {
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "Unavailable"
        }
    }
}
