package com.mecheri636.offline.ui.screens

import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mecheri636.offline.security.AppLockManager
import com.mecheri636.offline.security.LockType
import com.mecheri636.offline.security.VerifyResult
import kotlinx.coroutines.delay
import kotlin.math.hypot

@Composable
fun AppLockGateScreen(
    lockManager: AppLockManager,
    title: String = "636mecheri Locked",
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val config = remember { lockManager.getConfig() }
    var pinInput by remember { mutableStateOf("") }
    var passInput by remember { mutableStateOf("") }
    var recoveryInput by remember { mutableStateOf("") }
    var showRecovery by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lockoutSec by remember { mutableStateOf((lockManager.remainingLockoutMs() / 1000L).toInt()) }

    LaunchedEffect(lockoutSec) {
        if (lockoutSec > 0) {
            delay(1000L)
            lockoutSec = ((lockManager.remainingLockoutMs() + 999L) / 1000L).toInt()
        }
    }

    LaunchedEffect(Unit) {
        if (config.biometricEnabled && lockoutSec == 0 && lockManager.hasBiometricHardware()) {
            launchNativeFingerprint(context, title, "Unlock offline with fingerprint", onUnlocked) {
                errorMsg = it
            }
        }
    }

    fun submitSecret(raw: String) {
        when (val res = lockManager.verifySecret(raw.toCharArray())) {
            is VerifyResult.Success -> { errorMsg = null; onUnlocked() }
            is VerifyResult.Failed -> {
                errorMsg = "Wrong code (${res.attemptsLeftBeforeCooldown} tries left)"
                pinInput = ""; passInput = ""
            }
            is VerifyResult.LockedOut -> {
                lockoutSec = ((res.remainingMs + 999L) / 1000L).toInt()
                errorMsg = "Too many tries. Wait ${lockoutSec}s"
                pinInput = ""; passInput = ""
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("100% Offline Vault Lock", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            if (lockoutSec > 0) {
                Text("Locked for ${lockoutSec}s", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            } else if (errorMsg != null) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))

            if (showRecovery) {
                OutlinedTextField(
                    value = recoveryInput,
                    onValueChange = { recoveryInput = it.uppercase() },
                    label = { Text("Paper Recovery Code (636M-XXXX-XXXX)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showRecovery = false; errorMsg = null }) { Text("Back") }
                    Button(onClick = {
                        if (lockManager.verifyRecoveryAndReset(recoveryInput)) onUnlocked()
                        else errorMsg = "Invalid recovery code"
                    }) { Text("Reset Lock") }
                }
            } else if (lockoutSec == 0) {
                when (config.type) {
                    LockType.PIN -> PinKeypadBlock(
                        pin = pinInput,
                        shuffle = config.shuffleKeypad,
                        onDigit = { if (pinInput.length < 8) pinInput += it },
                        onDelete = { if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1) },
                        onSubmit = { if (pinInput.length >= 4) submitSecret(pinInput) }
                    )
                    LockType.PATTERN -> PatternLockGrid(
                        hideLine = config.hidePatternLine,
                        onPatternComplete = { submitSecret(it) }
                    )
                    LockType.PASSPHRASE -> {
                        OutlinedTextField(
                            value = passInput,
                            onValueChange = { passInput = it },
                            label = { Text("Passphrase") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { if (passInput.length >= 4) submitSecret(passInput) }) {
                            Text("Unlock")
                        }
                    }
                    LockType.NONE -> LaunchedEffect(Unit) { onUnlocked() }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (config.biometricEnabled && lockManager.hasBiometricHardware() && lockoutSec == 0) {
                    FilledTonalButton(onClick = {
                        launchNativeFingerprint(context, title, "Touch sensor to unlock", onUnlocked) { errorMsg = it }
                    }) { Text("Fingerprint") }
                }
                if (!showRecovery) {
                    TextButton(onClick = { showRecovery = true; errorMsg = null }) {
                        Text("Forgot? Recovery Code")
                    }
                }
            }
        }
    }
}

@Composable
fun PinKeypadBlock(
    pin: String,
    shuffle: Boolean,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit
) {
    val digits = remember(shuffle) {
        val base = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        if (shuffle) base.shuffled() else base
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
        repeat(maxOf(4, pin.length)) { idx ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        if (idx < pin.length) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f),
                        CircleShape
                    )
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (col in 0..2) {
                    val d = digits[row * 3 + col]
                    FilledTonalButton(onClick = { onDigit(d) }, modifier = Modifier.size(64.dp), shape = CircleShape) {
                        Text(d, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedButton(onClick = onDelete, modifier = Modifier.size(64.dp), shape = CircleShape) { Text("⌫") }
            FilledTonalButton(onClick = { onDigit(digits[9]) }, modifier = Modifier.size(64.dp), shape = CircleShape) {
                Text(digits[9], fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onSubmit, enabled = pin.length >= 4, modifier = Modifier.size(64.dp), shape = CircleShape) {
                Text("OK", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun PatternLockGrid(
    hideLine: Boolean,
    onPatternComplete: (String) -> Unit
) {
    val selected = remember { mutableStateListOf<Int>() }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    val latestSubmit by rememberUpdatedState(onPatternComplete)
    val primary = MaterialTheme.colorScheme.primary

    fun centerOf(idx: Int, size: IntSize): Offset {
        val cw = size.width / 3f
        val ch = size.height / 3f
        return Offset((idx % 3) * cw + cw / 2f, (idx / 3) * ch + ch / 2f)
    }

    fun tryAddPoint(pos: Offset) {
        if (gridSize.width == 0 || gridSize.height == 0) return
        val hitRadius = minOf(gridSize.width, gridSize.height) / 6f * 0.72f
        for (i in 0..8) {
            if (i in selected) continue
            val c = centerOf(i, gridSize)
            if (hypot((pos.x - c.x).toDouble(), (pos.y - c.y).toDouble()) <= hitRadius) {
                if (selected.isNotEmpty()) {
                    val prev = selected.last()
                    val r1 = prev / 3; val c1 = prev % 3
                    val r2 = i / 3; val c2 = i % 3
                    if ((r1 + r2) % 2 == 0 && (c1 + c2) % 2 == 0) {
                        val mid = ((r1 + r2) / 2) * 3 + ((c1 + c2) / 2)
                        if (mid !in selected) selected.add(mid)
                    }
                }
                selected.add(i)
                break
            }
        }
    }

    Canvas(
        modifier = Modifier
            .size(250.dp)
            .onSizeChanged { gridSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start -> selected.clear(); dragPos = start; tryAddPoint(start) },
                    onDrag = { change, _ -> change.consume(); dragPos = change.position; tryAddPoint(change.position) },
                    onDragEnd = {
                        dragPos = null
                        if (selected.size >= 4) latestSubmit(selected.joinToString(""))
                        selected.clear()
                    },
                    onDragCancel = { dragPos = null; selected.clear() }
                )
            }
    ) {
        val sz = IntSize(size.width.toInt(), size.height.toInt())
        if (!hideLine && selected.size >= 2) {
            for (i in 0 until selected.lastIndex) {
                drawLine(primary, centerOf(selected[i], sz), centerOf(selected[i + 1], sz), strokeWidth = 10f, cap = StrokeCap.Round)
            }
        }
        if (!hideLine && selected.isNotEmpty() && dragPos != null) {
            drawLine(primary.copy(alpha = 0.5f), centerOf(selected.last(), sz), dragPos!!, strokeWidth = 8f, cap = StrokeCap.Round)
        }
        for (i in 0..8) {
            val c = centerOf(i, sz)
            val active = i in selected && !hideLine
            drawCircle(if (active) primary.copy(alpha = 0.25f) else Color.Gray.copy(alpha = 0.2f), radius = 26f, center = c)
            drawCircle(if (active) primary else Color.Gray, radius = 10f, center = c)
        }
    }
    Text("Draw pattern (min 4 dots)", style = MaterialTheme.typography.bodySmall)
}

private fun launchNativeFingerprint(
    context: Context,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (Build.VERSION.SDK_INT < 28) {
        onError("Fingerprint requires Android 9+")
        return
    }
    try {
        val executor = context.mainExecutor
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButton("Use PIN / Pattern", executor) { _, _ -> }
            .build()
        prompt.authenticate(
            CancellationSignal(),
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                    ) {
                        onError(errString?.toString() ?: "Fingerprint failed")
                    }
                }
            }
        )
    } catch (e: Exception) {
        onError(e.message ?: "Biometric unavailable")
    }
}
