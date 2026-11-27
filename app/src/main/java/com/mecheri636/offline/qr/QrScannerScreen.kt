package com.mecheri636.offline.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun OfflineQrScannerScreen(
    title: String = "Offline QR Scanner",
    onQrComplete: (String) -> Unit,
    onCancel: () -> Unit
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
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var alertText by remember { mutableStateOf<String?>(null) }
    var upiInfo by remember { mutableStateOf<ParsedUpiPayment?>(null) }
    var statusMsg by remember { mutableStateOf("Align QR in frame (100% offline)") }
    var frameProgress by remember { mutableStateOf<MultiFrameQrAssembler.Result.Progress?>(null) }
    var manualText by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }

    val assembler = remember { MultiFrameQrAssembler() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val isDisposed = remember { AtomicBoolean(false) }

    val reqCam = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> hasCam = ok }

    LaunchedEffect(Unit) {
        if (!hasCam) reqCam.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(torchOn, camRef) {
        val cam = camRef ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) {
            runCatching { cam.cameraControl.enableTorch(torchOn) }
        }
    }
    LaunchedEffect(zoomVal, camRef) {
        camRef?.let { runCatching { it.cameraControl.setZoomRatio(zoomVal) } }
    }

    // CameraX teardown safety: ALWAYS call unbindAll() BEFORE executor.shutdown().
    DisposableEffect(lifecycleOwner) {
        onDispose {
            isDisposed.set(true)
            runCatching { camRef?.cameraControl?.enableTorch(false) }
            runCatching { cameraProviderRef?.unbindAll() }
            camRef = null
            cameraProviderRef = null
            runCatching { analysisExecutor.shutdown() }
        }
    }

    fun handleRaw(raw: String) {
        when (val v = QrFirewall.inspect(raw)) {
            is QrFirewallVerdict.AllowedMecheri -> {
                alertText = null
                upiInfo = null
                when (val assembled = assembler.feed(v.payload)) {
                    is MultiFrameQrAssembler.Result.Progress -> {
                        frameProgress = assembled
                        statusMsg = "Frame ${assembled.received} of ${assembled.total} — hold steady"
                    }
                    is MultiFrameQrAssembler.Result.Complete -> {
                        frameProgress = null
                        when (val finalVerdict = QrFirewall.inspect(assembled.fullPayload)) {
                            is QrFirewallVerdict.AllowedMecheri -> {
                                statusMsg = "Verified (${finalVerdict.payload.length} chars)"
                                onQrComplete(finalVerdict.payload)
                            }
                            is QrFirewallVerdict.AllowedUpi -> {
                                upiInfo = finalVerdict.upi
                                statusMsg = "UPI: ${finalVerdict.upi.vpa}"
                            }
                            is QrFirewallVerdict.Blocked -> {
                                alertText = "${finalVerdict.reason}: ${finalVerdict.preview}"
                                statusMsg = "Blocked unsafe QR"
                            }
                        }
                    }
                }
            }
            is QrFirewallVerdict.AllowedUpi -> {
                alertText = null
                frameProgress = null
                assembler.reset()
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
            TextButton(onClick = onCancel) { Text("Close") }
        }

        frameProgress?.let { fp ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Frame ${fp.received} of ${fp.total} / பிரேம் ${fp.received} / ${fp.total}",
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = {
                            assembler.reset()
                            frameProgress = null
                            statusMsg = "Align QR in frame (100% offline)"
                        }) { Text("Reset") }
                    }
                    LinearProgressIndicator(
                        progress = { fp.received.toFloat() / fp.total.coerceAtLeast(1).toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
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
                        val reader = MultiFormatReader().apply {
                            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                        }
                        var reusableYuv = ByteArray(0)
                        var lastMs = 0L
                        var lastText = ""

                        fut.addListener({
                            if (isDisposed.get()) return@addListener
                            val prov = runCatching { fut.get() }.getOrNull() ?: return@addListener
                            cameraProviderRef = prov
                            val prev = Preview.Builder().build().also {
                                it.setSurfaceProvider(pv.surfaceProvider)
                            }
                            @Suppress("DEPRECATION")
                            val ana = ImageAnalysis.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build().also { a ->
                                    a.setAnalyzer(analysisExecutor) { proxy ->
                                        try {
                                            if (isDisposed.get()) return@setAnalyzer
                                            val plane = proxy.planes[0]
                                            val w = proxy.width
                                            val h = proxy.height
                                            reusableYuv = extractLuminanceInto(plane, w, h, reusableYuv)
                                            val src = PlanarYUVLuminanceSource(
                                                reusableYuv, w, h, 0, 0, w, h, false
                                            )
                                            val bin = BinaryBitmap(HybridBinarizer(src))
                                            val txt = reader.decodeWithState(bin).text ?: ""
                                            val now = System.currentTimeMillis()
                                            if (txt.isNotBlank() && (txt != lastText || now - lastMs > 700L)) {
                                                lastText = txt
                                                lastMs = now
                                                ContextCompat.getMainExecutor(ctx).execute {
                                                    if (!isDisposed.get()) handleRaw(txt)
                                                }
                                            }
                                        } catch (_: Exception) {
                                        } finally {
                                            reader.reset()
                                            proxy.close()
                                        }
                                    }
                                }
                            runCatching {
                                prov.unbindAll()
                                camRef = prov.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    prev,
                                    ana
                                )
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
                    Text(
                        statusMsg,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { torchOn = !torchOn }, modifier = Modifier.weight(1f)) {
                Text(if (torchOn) "🔦 Torch ON" else "🔦 Torch OFF")
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

private fun extractLuminanceInto(
    plane: ImageProxy.PlaneProxy,
    width: Int,
    height: Int,
    existing: ByteArray
): ByteArray {
    val size = width * height
    val out = if (existing.size == size) existing else ByteArray(size)
    val buf = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    buf.rewind()
    val limit = buf.limit()
    if (pixelStride == 1 && rowStride == width && limit >= size) {
        buf.get(out, 0, size)
    } else if (pixelStride == 1) {
        for (y in 0 until height) {
            val rowStart = y * rowStride
            if (rowStart >= limit) break
            val copyLen = minOf(width, limit - rowStart)
            buf.position(rowStart)
            buf.get(out, y * width, copyLen)
        }
    } else {
        for (y in 0 until height) {
            val rowStart = y * rowStride
            val outRow = y * width
            for (x in 0 until width) {
                val pos = rowStart + x * pixelStride
                if (pos >= limit) break
                out[outRow + x] = buf.get(pos)
            }
        }
    }
    return out
}
