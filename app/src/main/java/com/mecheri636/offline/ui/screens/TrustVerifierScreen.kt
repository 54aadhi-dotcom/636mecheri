package com.mecheri636.offline.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mecheri636.offline.BackupVault
import com.mecheri636.offline.data.db.AppDatabase
import com.mecheri636.offline.security.*

private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L

@Composable
fun TrustVerifierScreen(
    report: OfflineAuditReport?,
    onReRunAudit: () -> Unit,
    lockManager: AppLockManager,
    onLockNow: () -> Unit,
    onFlagSecureChanged: (Boolean) -> Unit = {},
    lastBackupEpochMs: Long = 0L,
    onBackupExported: () -> Unit = {}
) {
    val ctx = LocalContext.current
    var lockOn by remember { mutableStateOf(lockManager.isLockEnabled()) }
    var cfg by remember { mutableStateOf(lockManager.getConfig()) }
    var chosenType by remember { mutableStateOf(if (cfg.type == LockType.NONE) LockType.PIN else cfg.type) }
    var secret by remember { mutableStateOf("") }
    val hasBio = remember { lockManager.hasBiometricHardware() }
    var recCode by remember { mutableStateOf<String?>(null) }
    var lockMsg by remember { mutableStateOf<String?>(null) }
    var vaultPass by remember { mutableStateOf("") }
    var vaultMsg by remember { mutableStateOf<String?>(null) }
    var showRelaunchDialog by remember { mutableStateOf(false) }

    val backupDue = lastBackupEpochMs == 0L || (System.currentTimeMillis() - lastBackupEpochMs) > SEVEN_DAYS_MS

    fun applySettings(
        scope: LockScope = cfg.scope, bio: Boolean = cfg.biometricEnabled,
        shuf: Boolean = cfg.shuffleKeypad, hideP: Boolean = cfg.hidePatternLine,
        fSec: Boolean = cfg.flagSecureEnabled, tMs: Long = cfg.timeoutMs,
        staff: Boolean = cfg.staffModeEnabled
    ) {
        lockManager.updateSettings(scope, bio, shuf, hideP, fSec, tMs, staff)
        cfg = lockManager.getConfig()
        onFlagSecureChanged(cfg.flagSecureEnabled)
    }

    val expL = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) {
            runCatching { AppDatabase.getInstance(ctx).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close() }
            vaultMsg = BackupVault.exportDbToSafUri(ctx, AppDatabase.DB_NAME, uri, vaultPass.toCharArray())
                .fold({ onBackupExported(); "Backup saved locally ($it B)" }, { "Export failed: ${it.message}" })
        }
    }
    val impL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            AppDatabase.checkpointAndClose()
            BackupVault.importDbFromSafUri(ctx, AppDatabase.DB_NAME, uri, vaultPass.toCharArray()).fold(
                onSuccess = { vaultMsg = "Verified & restored ($it B)"; showRelaunchDialog = true },
                onFailure = { vaultMsg = "Restore failed: ${it.message}" }
            )
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("100% Offline Trust Verifier\nஆஃப்லைன் பாதுகாப்பு சரிபார்ப்பு", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("0% Upload & 0% Download (Mobile Data, Wi-Fi, USB, Bluetooth).", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AppLock & Staff Mode / செயலி பூட்டு", fontWeight = FontWeight.Bold)
                    Text(if (lockOn) "Status: ON (${cfg.type.name} • ${cfg.scope.name}${if (cfg.staffModeEnabled) " • STAFF" else ""})" else "Status: OFF (Unlocked)", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(LockType.PIN, LockType.PATTERN, LockType.PASSPHRASE).forEach { t -> FilterChip(chosenType == t, { chosenType = t; secret = "" }, { Text(t.name) }) }
                    }
                    if (chosenType == LockType.PATTERN) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            PatternLockGrid(hideLine = cfg.hidePatternLine) { pat -> secret = pat; lockMsg = "Pattern captured (${pat.length} dots) — tap Save" }
                        }
                    } else {
                        OutlinedTextField(secret, { secret = it }, label = { Text("New ${chosenType.name} (min 4)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(cfg.scope == LockScope.FULL_APP, { applySettings(scope = LockScope.FULL_APP) }, { Text("Full App") })
                        FilterChip(cfg.scope == LockScope.PROFIT_AND_VAULT_ONLY, { applySettings(scope = LockScope.PROFIT_AND_VAULT_ONLY) }, { Text("Profit+Vault") })
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0L to "0s", 30_000L to "30s", 120_000L to "2m", 300_000L to "5m").forEach { (ms, l) -> FilterChip(cfg.timeoutMs == ms, { applySettings(tMs = ms) }, { Text("Auto $l") }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(cfg.staffModeEnabled, { applySettings(staff = it) }); Spacer(Modifier.width(8.dp)); Text("Staff Mode (Lock Admin, Prices, Profit & Vault)") }
                    if (hasBio) Row(verticalAlignment = Alignment.CenterVertically) { Switch(cfg.biometricEnabled, { applySettings(bio = it) }); Spacer(Modifier.width(8.dp)); Text("Fingerprint / கைரேகை") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(cfg.shuffleKeypad, { applySettings(shuf = it) }); Spacer(Modifier.width(8.dp)); Text("Shuffle PIN Keypad") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(cfg.hidePatternLine, { applySettings(hideP = it) }); Spacer(Modifier.width(8.dp)); Text("Hide Pattern Line") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(cfg.flagSecureEnabled, { applySettings(fSec = it) }); Spacer(Modifier.width(8.dp)); Text("Anti-Screenshot (FLAG_SECURE)") }
                    Button({
                        runCatching { lockManager.setupLock(chosenType, secret.toCharArray(), cfg.scope, cfg.biometricEnabled) }
                            .onSuccess { c -> lockOn = true; cfg = lockManager.getConfig(); recCode = c; secret = ""; lockMsg = "AppLock ON / பூட்டு இயக்கப்பட்டது" }
                            .onFailure { lockMsg = it.message }
                    }, Modifier.fillMaxWidth()) { Text(if (lockOn) "Update Lock / மாற்று" else "Enable AppLock / இயக்கு") }
                    if (lockOn) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onLockNow, Modifier.weight(1f)) { Text("Lock Now") }
                        OutlinedButton({ lockManager.disableLock(); lockOn = false; cfg = lockManager.getConfig(); recCode = null; lockMsg = "Lock OFF" }, Modifier.weight(1f)) { Text("Disable") }
                    }
                    recCode?.let { Text("Recovery Code: $it", fontWeight = FontWeight.Bold) }
                    lockMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Offline BackupVault (.m636) / பேக்கப்", fontWeight = FontWeight.Bold)
                    if (backupDue) Text("⚠ Reminder: Export an offline .m636 backup (7-day safety check).", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(vaultPass, { vaultPass = it }, label = { Text("Passphrase (min 4)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ if (vaultPass.length < 4) vaultMsg = "Min 4 chars" else expL.launch(OfflineExportEngine.createLocalOnlySaveIntent("application/octet-stream", "636mecheri.m636")) }, Modifier.weight(1f)) { Text("Export .m636") }
                        OutlinedButton({ if (vaultPass.length < 4) vaultMsg = "Min 4 chars" else impL.launch(arrayOf("*/*")) }, Modifier.weight(1f)) { Text("Restore") }
                    }
                    vaultMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (report == null) item { CircularProgressIndicator() } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("1. Manifest Permission Audit", fontWeight = FontWeight.Bold)
                        Text(if (report.isManifestZeroNetwork) "PASS ✅ Zero Network permissions!" else "FAIL: ${report.forbiddenPermissionsFound}")
                        Text("Declared (${report.declaredPermissions.size}):${report.declaredPermissions.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("2. Kernel Socket & SHA-256", fontWeight = FontWeight.Bold)
                        Text(report.kernelSocketProofMessage, style = MaterialTheme.typography.bodySmall)
                        Text("SHA-256: ${report.apkSha256}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Button(onClick = onReRunAudit, modifier = Modifier.fillMaxWidth()) { Text("Re-Run Audit / மீண்டும் சோதி") } }
        }
    }
    if (showRelaunchDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Backup Restored / பேக்கப் மீட்கப்பட்டது") },
            text = { Text("Database verified & replaced safely. Tap below to relaunch 636mecheri cleanly.") },
            confirmButton = {
                Button({
                    showRelaunchDialog = false
                    ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let { i ->
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK); ctx.startActivity(i)
                    }
                    (ctx as? Activity)?.finishAffinity()
                }) { Text("Relaunch App") }
            }
        )
    }
}
