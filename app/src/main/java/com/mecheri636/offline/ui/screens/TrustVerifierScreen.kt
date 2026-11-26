package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mecheri636.offline.security.OfflineAuditReport
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.mecheri636.offline.BackupVault
import com.mecheri636.offline.data.db.AppDatabase
import com.mecheri636.offline.security.AppLockManager
import com.mecheri636.offline.security.LockScope
import com.mecheri636.offline.security.LockType

@Composable
fun TrustVerifierScreen(
    report: OfflineAuditReport?,
    onReRunAudit: () -> Unit,
    lockManager: AppLockManager,
    onLockNow: () -> Unit
) {
    val context = LocalContext.current
    var lockEnabled by remember { mutableStateOf(lockManager.isLockEnabled()) }
    var lockCfg by remember { mutableStateOf(lockManager.getConfig()) }
    var chosenType by remember { mutableStateOf(if (lockCfg.type == LockType.PASSPHRASE) LockType.PASSPHRASE else LockType.PIN) }
    var secretInput by remember { mutableStateOf("") }
    var bioEnabled by remember { mutableStateOf(lockCfg.biometricEnabled) }
    val hasBio = remember { lockManager.hasBiometricHardware() }
    var recoveryShown by remember { mutableStateOf<String?>(null) }
    var lockMsg by remember { mutableStateOf<String?>(null) }
    var vaultPass by remember { mutableStateOf("") }
    var vaultMsg by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            runCatching { AppDatabase.getInstance(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close() }
            val res = BackupVault.exportDbToSafUri(context, AppDatabase.DB_NAME, uri, vaultPass.toCharArray())
            vaultMsg = res.fold(
                onSuccess = { b -> "Backup saved ($b bytes) / பேக்கப் சேமிக்கப்பட்டது" },
                onFailure = { e -> "Export failed: ${e.message}" }
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            AppDatabase.checkpointAndClose()
            val res = BackupVault.importDbFromSafUri(context, AppDatabase.DB_NAME, uri, vaultPass.toCharArray())
            res.fold(
                onSuccess = {
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(intent)
                    }
                    android.os.Process.killProcess(android.os.Process.myPid())
                },
                onFailure = { e -> vaultMsg = "Restore failed: ${e.message}" }
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "100% Offline Trust Verifier\n100% ஆஃப்லைன் பாதுகாப்புச் சரிபார்ப்பு",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "0% Upload & 0% Download over Mobile Data, Wi-Fi, USB-C Ethernet, USB Tethering & Satellite Internet.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AppLock Security / செயலி பூட்டு", fontWeight = FontWeight.Bold)
                    Text(if (lockEnabled) "Status: ON (${lockCfg.type.name})" else "Status: OFF (Unlocked)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = chosenType == LockType.PIN, onClick = { chosenType = LockType.PIN }, label = { Text("PIN") })
                        FilterChip(selected = chosenType == LockType.PASSPHRASE, onClick = { chosenType = LockType.PASSPHRASE }, label = { Text("Passphrase") })
                    }
                    OutlinedTextField(
                        value = secretInput, onValueChange = { secretInput = it },
                        label = { Text("New ${chosenType.name} (min 4)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    if (hasBio) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(checked = bioEnabled, onCheckedChange = { bioEnabled = it })
                            Text("Fingerprint Unlock / கைரேகை")
                        }
                    }
                    Button(onClick = {
                        runCatching {
                            lockManager.setupLock(chosenType, secretInput.toCharArray(), LockScope.FULL_APP, bioEnabled)
                        }.onSuccess { code ->
                            lockEnabled = true; lockCfg = lockManager.getConfig(); recoveryShown = code; secretInput = ""; lockMsg = "AppLock enabled / பூட்டு இயக்கப்பட்டது"
                        }.onFailure { e -> lockMsg = e.message }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (lockEnabled) "Update Lock / பூட்டு மாற்று" else "Enable AppLock / பூட்டு இயக்கு") }
                    if (lockEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onLockNow, modifier = Modifier.weight(1f)) { Text("Lock Now / பூட்டு") }
                            OutlinedButton(onClick = {
                                lockManager.disableLock(); lockEnabled = false; lockCfg = lockManager.getConfig(); recoveryShown = null; lockMsg = "AppLock disabled / பூட்டு நீக்கப்பட்டது"
                            }, modifier = Modifier.weight(1f)) { Text("Disable / நீக்கு") }
                        }
                    }
                    recoveryShown?.let { Text("Save Recovery Code / மீட்புக் குறியீடு: $it", fontWeight = FontWeight.Bold) }
                    lockMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Offline BackupVault / பாதுகாப்பான பேக்கப்", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = vaultPass, onValueChange = { vaultPass = it },
                        label = { Text("Backup Passphrase / பேக்கப் கடவுச்சொல்") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (vaultPass.length < 4) vaultMsg = "Enter passphrase (min 4 chars)"
                            else exportLauncher.launch("636mecheri_backup.m636")
                        }, modifier = Modifier.weight(1f)) { Text("Export / சேமி") }
                        OutlinedButton(onClick = {
                            if (vaultPass.length < 4) vaultMsg = "Enter passphrase (min 4 chars)"
                            else importLauncher.launch(arrayOf("*/*"))
                        }, modifier = Modifier.weight(1f)) { Text("Restore / மீட்டெடு") }
                    }
                    vaultMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (report == null) {
            item { CircularProgressIndicator() }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("1. Android Manifest Permission Audit", fontWeight = FontWeight.Bold)
                        Text(
                            if (report.isManifestZeroNetwork) "PASS ✅ Zero Network, Wi-Fi, USB, Bluetooth or Location permissions declared!"
                            else "FAIL ❌ Found: ${report.forbiddenPermissionsFound}"
                        )
                        Text("Declared Permissions (${report.declaredPermissions.size}): ${report.declaredPermissions.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("2. Live Linux Kernel Socket Block Test", fontWeight = FontWeight.Bold)
                        Text(report.kernelSocketProofMessage)
                        Text("தமிழ்: Android கர்னல் இணைய இணைப்பை நேரடியாகத் தடுக்கிறது.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("3. Installed APK SHA-256 Fingerprint", fontWeight = FontWeight.Bold)
                        Text(report.apkSha256, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("4. Airplane Mode Live Test (விமானப் பயன்முறை சோதனை)", fontWeight = FontWeight.Bold)
                        Text("Turn ON Airplane Mode and switch OFF Wi-Fi/SIM. Every feature of 636mecheri continues working 100% smoothly. Open Phone Settings → Apps → 636mecheri → Permissions to confirm ONLY Camera is requested.")
                    }
                }
            }
            item {
                Button(onClick = onReRunAudit, modifier = Modifier.fillMaxWidth()) {
                    Text("Re-Run Live Audit / மீண்டும் சோதி")
                }
            }
        }
    }
}
