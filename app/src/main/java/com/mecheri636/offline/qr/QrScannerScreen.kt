package com.mecheri636.offline.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun OfflineQrScannerScreen(
    title: String = "Offline QR Scanner",
    onQrComplete: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCam by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchOn by remember { mutableStateOf(false) }
    var zoomVal by remember { mutableFloatStateOf(1.0f) }
    var camRef by remember { mutableStateOf<Camera?>(null) }
    var alertText by remember { mutableStateOf<String?>(null) }
    var upiInfo by remember { mutableStateOf<ParsedUpiPayment?>(null) }
    var statusMsg by remember { mutableStateOf("Align QR in frame (100% offline)") }
    var manualText by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }

    val reqCam = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> hasCam = ok }

    LaunchedEffect(Unit) {
        if (!hasCam) reqCam.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(torchOn, camRef) {
        camRef?.cameraControl?.enableTorch(torchOn)
    }
    LaunchedEffect(zoomVal, camRef) {
        camRef?.cameraControl?.setZoomRatio(zoomVal)
    }

    fun handleRaw(raw: String) {
        when (val v = QrFirewall.inspect(raw)) {
            is QrFirewallVerdict.AllowedMecheri -> {
                alertText = null
                upiInfo = null
                statusMsg = "Verified (${v.payload.length} chars)"
                onQrComplete(v.payload)
            }
            is QrFirewallVerdict.AllowedUpi -> {
                alertText = null
                upiInfo = v.upi
                statusMsg = "UPI: ${v.upi.vpa}"
            }
            is QrFirewallVerdict.Blocked -> {
                upiInfo = null
                alertText = "${v.reason}: ${v.preview}"
                statusMsg = "Blocked unsafe QR"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClose) { Text("Close") }
        }

        if (alertText != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(alertText!!, modifier = Modifier.padding(12.dp))
            }
        }

        upiInfo?.let { u ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${u.payeeName} (${u.vpa})", fontWeight = FontWeight.Bold)
                    u.amountPaise?.let { Text("Amt: Rs. ${"%.2f".format(it / 100.0)}") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            runCatching { context.startActivity(QrFirewall.buildUpiAppSwitchIntent(u.rawUri)) }
                        }) { Text("Open UPI") }
                        OutlinedButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("UPI", u.vpa))
                        }) { Text("Copy ID") }
                    }
                }
            }
        }

        if (!hasCam) {
            Button(onClick = { reqCam.launch(Manifest.permission.CAMERA) }) {
                Text("Grant Camera Permission")
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val pv = PreviewView(ctx)
                        val fut = ProcessCameraProvider.getInstance(ctx)
                        val exec = Executors.newSingleThreadExecutor()
                        val opts = BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                        val det = BarcodeScanning.getClient(opts)
                        fut.addListener({
                            val prov = fut.get()
                            val prev = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                            @OptIn(ExperimentalGetImage::class)
                            val ana = ImageAnalysis.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build().also { a ->
                                    a.setAnalyzer(exec) { p ->
                                        val m = p.image
                                        if (m != null) {
                                            val img = InputImage.fromMediaImage(m, p.imageInfo.rotationDegrees)
                                            det.process(img)
                                                .addOnSuccessListener { b ->
                                                    b.firstOrNull()?.rawValue?.takeIf { it.isNotBlank() }?.let(::handleRaw)
                                                }
                                                .addOnCompleteListener { p.close() }
                                        } else {
                                            p.close()
                                        }
                                    }
                                }
                            runCatching {
                                prov.unbindAll()
                                camRef = prov.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, prev, ana)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        pv
                    }
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)
                ) {
                    Text(statusMsg, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { torchOn = !torchOn }, modifier = Modifier.weight(1f)) {
                Text(if (torchOn) "Torch ON" else "Torch OFF")
            }
            OutlinedButton(onClick = { zoomVal = if (zoomVal < 1.8f) 2.0f else 1.0f }, modifier = Modifier.weight(1f)) {
                Text("Zoom ${zoomVal.toInt()}x")
            }
            OutlinedButton(onClick = { showManual = !showManual }, modifier = Modifier.weight(1f)) {
                Text(if (showManual) "Hide" else "Paste")
            }
        }

        if (showManual) {
            OutlinedTextField(
                value = manualText,
                onValueChange = { manualText = it },
                label = { Text("QR payload") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
            Button(
                onClick = { if (manualText.isNotBlank()) handleRaw(manualText.trim()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Verify & Process") }
        }
    }
}
