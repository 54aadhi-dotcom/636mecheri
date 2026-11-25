package com.mecheri636.offline.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@Composable
fun OfflineQrScannerScreen(
    title: String,
    onQrComplete: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var frameStatus by remember { mutableStateOf("Point camera at 636mecheri QR / QR-ஐ நோக்கவும்") }
    val assembler = remember { MultiFrameQrAssembler() }
    var alreadyDelivered by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPerm = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm) launcher.launch(Manifest.permission.CAMERA)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onCancel) { Text("Close / மூடு") }
            }
        }

        if (!hasCameraPerm) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("Camera permission is required for 100% offline QR scanning.")
                    Text("ஆஃப்லைன் QR ஸ்கேன் செய்ய கேமரா அனுமதி அவசியம்.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text("Allow Camera / கேமரா அனுமதி")
                    }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        val executor = Executors.newSingleThreadExecutor()
                        val reader = MultiFormatReader().apply {
                            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                        }

                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            analysis.setAnalyzer(executor) { imageProxy ->
                                try {
                                    if (!alreadyDelivered) {
                                        val buffer = imageProxy.planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        val source = PlanarYUVLuminanceSource(
                                            bytes,
                                            imageProxy.width,
                                            imageProxy.height,
                                            0,
                                            0,
                                            imageProxy.width,
                                            imageProxy.height,
                                            false
                                        )
                                        val bitmap = BinaryBitmap(HybridBinarizer(source))
                                        val result = reader.decodeWithState(bitmap)
                                        val text = result.text
                                        if (!text.isNullOrBlank()) {
                                            when (val assembled = assembler.feed(text)) {
                                                is MultiFrameQrAssembler.Result.Progress -> {
                                                    frameStatus = "Multi-QR frame ${assembled.received}/${assembled.total} scanned — hold steady..."
                                                }
                                                is MultiFrameQrAssembler.Result.Complete -> {
                                                    alreadyDelivered = true
                                                    ContextCompat.getMainExecutor(ctx).execute {
                                                        onQrComplete(assembled.fullPayload)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (_: NotFoundException) {
                                    // No QR in this frame
                                } catch (_: Exception) {
                                    // Ignore transient decode error
                                } finally {
                                    reader.reset()
                                    imageProxy.close()
                                }
                            }

                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            } catch (_: Exception) {
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    }
                )

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = frameStatus,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
