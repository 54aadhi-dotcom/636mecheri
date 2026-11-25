package com.mecheri636.offline.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mecheri636.offline.security.OfflineAuditReport

@Composable
fun TrustVerifierScreen(
    report: OfflineAuditReport?,
    onReRunAudit: () -> Unit
) {
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
