package com.mecheri636.offline.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class LockType { NONE, PIN, PATTERN, PASSPHRASE }
enum class LockScope { FULL_APP, PROFIT_AND_VAULT_ONLY }

data class LockConfig(
    val type: LockType,
    val scope: LockScope,
    val biometricEnabled: Boolean,
    val shuffleKeypad: Boolean,
    val hidePatternLine: Boolean,
    val flagSecureEnabled: Boolean,
    val timeoutMs: Long,
    val staffModeEnabled: Boolean = false
)

sealed class VerifyResult {
    object Success : VerifyResult()
    data class Failed(val attemptsLeftBeforeCooldown: Int) : VerifyResult()
    data class LockedOut(val remainingMs: Long) : VerifyResult()
}

class AppLockManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("mecheri636_lock_vault", Context.MODE_PRIVATE)
    private val rng = SecureRandom()

    companion object {
        private const val K_TYPE = "lock_type"
        private const val K_SCOPE = "lock_scope"
        private const val K_BIO = "lock_bio"
        private const val K_SHUFFLE = "lock_shuffle"
        private const val K_HIDE_PAT = "lock_hide_pat"
        private const val K_FLAG_SEC = "lock_flag_sec"
        private const val K_TIMEOUT = "lock_timeout_ms"
        private const val K_STAFF = "lock_staff_mode"
        private const val K_EXT_SWITCH = "ext_switch_elapsed"
        private const val K_SALT = "sec_salt"
        private const val K_HASH = "sec_hash"
        private const val K_KDF = "sec_kdf"
        private const val K_REC_SALT = "rec_salt"
        private const val K_REC_HASH = "rec_hash"
        private const val K_FAILS = "fail_count"
        private const val K_LOCK_ELAPSED = "lock_until_elapsed"
        private const val K_LOCK_WALL = "lock_until_wall"
        private const val K_LOCK_DUR = "lock_dur_ms"
        private const val ITERATIONS = 100_000
        private const val GRACE_PERIOD_MS = 30_000L
        private const val RECOVERY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }

    fun getConfig(): LockConfig = LockConfig(
        type = runCatching { LockType.valueOf(prefs.getString(K_TYPE, "NONE") ?: "NONE") }.getOrDefault(LockType.NONE),
        scope = runCatching { LockScope.valueOf(prefs.getString(K_SCOPE, "FULL_APP") ?: "FULL_APP") }.getOrDefault(LockScope.FULL_APP),
        biometricEnabled = prefs.getBoolean(K_BIO, false),
        shuffleKeypad = prefs.getBoolean(K_SHUFFLE, true),
        hidePatternLine = prefs.getBoolean(K_HIDE_PAT, false),
        flagSecureEnabled = prefs.getBoolean(K_FLAG_SEC, true),
        timeoutMs = prefs.getLong(K_TIMEOUT, 30_000L),
        staffModeEnabled = prefs.getBoolean(K_STAFF, false)
    )

    fun isLockEnabled(): Boolean = getConfig().type != LockType.NONE && !prefs.getString(K_HASH, null).isNullOrEmpty()

    fun hasBiometricHardware(): Boolean =
        Build.VERSION.SDK_INT >= 28 && context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)

    fun setupLock(type: LockType, secret: CharArray, scope: LockScope = LockScope.FULL_APP, biometricEnabled: Boolean = false): String {
        require(type != LockType.NONE) { "Select PIN, Pattern, or Passphrase" }
        require(secret.size >= 4) { "Secret must be at least 4 characters/dots" }
        val kdf = if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val secSalt = ByteArray(16).also(rng::nextBytes)
        val secHash = hashSecret(secret, secSalt, kdf)
        val recoveryCode = generateRecoveryCode()
        val recSalt = ByteArray(16).also(rng::nextBytes)
        val recHash = hashSecret(normalizeRecovery(recoveryCode).toCharArray(), recSalt, kdf)
        prefs.edit()
            .putString(K_TYPE, type.name).putString(K_SCOPE, scope.name)
            .putBoolean(K_BIO, biometricEnabled && hasBiometricHardware())
            .putString(K_KDF, kdf).putString(K_SALT, b64(secSalt)).putString(K_HASH, b64(secHash))
            .putString(K_REC_SALT, b64(recSalt)).putString(K_REC_HASH, b64(recHash))
            .putInt(K_FAILS, 0).putLong(K_LOCK_ELAPSED, 0L).putLong(K_LOCK_WALL, 0L).putLong(K_LOCK_DUR, 0L)
            .apply()
        return recoveryCode
    }

    fun updateSettings(
        scope: LockScope = getConfig().scope,
        biometricEnabled: Boolean = getConfig().biometricEnabled,
        shuffleKeypad: Boolean = getConfig().shuffleKeypad,
        hidePatternLine: Boolean = getConfig().hidePatternLine,
        flagSecureEnabled: Boolean = getConfig().flagSecureEnabled,
        timeoutMs: Long = getConfig().timeoutMs,
        staffModeEnabled: Boolean = getConfig().staffModeEnabled
    ) {
        prefs.edit()
            .putString(K_SCOPE, scope.name)
            .putBoolean(K_BIO, biometricEnabled && hasBiometricHardware())
            .putBoolean(K_SHUFFLE, shuffleKeypad)
            .putBoolean(K_HIDE_PAT, hidePatternLine)
            .putBoolean(K_FLAG_SEC, flagSecureEnabled)
            .putLong(K_TIMEOUT, timeoutMs.coerceAtLeast(0L))
            .putBoolean(K_STAFF, staffModeEnabled)
            .apply()
    }

    fun disableLock() {
        prefs.edit()
            .putString(K_TYPE, LockType.NONE.name).putBoolean(K_STAFF, false).putLong(K_EXT_SWITCH, 0L)
            .remove(K_SALT).remove(K_HASH).remove(K_REC_SALT).remove(K_REC_HASH)
            .putInt(K_FAILS, 0).putLong(K_LOCK_ELAPSED, 0L).putLong(K_LOCK_WALL, 0L).putLong(K_LOCK_DUR, 0L)
            .apply()
    }

    fun markExternalAppSwitch() {
        prefs.edit().putLong(K_EXT_SWITCH, SystemClock.elapsedRealtime()).apply()
    }

    fun remainingLockoutMs(): Long {
        val dur = prefs.getLong(K_LOCK_DUR, 0L)
        if (dur <= 0L) return 0L
        val elapsedRem = prefs.getLong(K_LOCK_ELAPSED, 0L) - SystemClock.elapsedRealtime()
        if (elapsedRem in 1L..dur) return elapsedRem
        val wallRem = prefs.getLong(K_LOCK_WALL, 0L) - System.currentTimeMillis()
        return if (wallRem in 1L..dur) wallRem else 0L
    }

    fun verifySecret(input: CharArray): VerifyResult {
        val rem = remainingLockoutMs()
        if (rem > 0L) return VerifyResult.LockedOut(rem)
        val salt = prefs.getString(K_SALT, null)?.let(::unb64) ?: return VerifyResult.Success
        val expected = prefs.getString(K_HASH, null)?.let(::unb64) ?: return VerifyResult.Success
        val kdf = prefs.getString(K_KDF, "PBKDF2WithHmacSHA256") ?: "PBKDF2WithHmacSHA256"
        val actual = hashSecret(input, salt, kdf)
        return if (MessageDigest.isEqual(expected, actual)) { clearFailures(); VerifyResult.Success } else recordFailure()
    }

    fun verifyRecoveryAndReset(codeInput: String): Boolean {
        val salt = prefs.getString(K_REC_SALT, null)?.let(::unb64) ?: return false
        val expected = prefs.getString(K_REC_HASH, null)?.let(::unb64) ?: return false
        val kdf = prefs.getString(K_KDF, "PBKDF2WithHmacSHA256") ?: "PBKDF2WithHmacSHA256"
        val actual = hashSecret(normalizeRecovery(codeInput).toCharArray(), salt, kdf)
        return if (MessageDigest.isEqual(expected, actual)) { disableLock(); true } else false
    }

    // 30-second GPay/SMS app-switch grace period is STRICTLY ignored if Auto Lock = 0s (Immediate).
    fun shouldLockAfterBackground(lastActiveElapsedMs: Long): Boolean {
        if (!isLockEnabled()) return false
        val cfg = getConfig()
        val now = SystemClock.elapsedRealtime()
        if (cfg.timeoutMs == 0L) {
            prefs.edit().putLong(K_EXT_SWITCH, 0L).apply()
            return true
        }
        val extSwitchMs = prefs.getLong(K_EXT_SWITCH, 0L)
        if (extSwitchMs > 0L) {
            prefs.edit().putLong(K_EXT_SWITCH, 0L).apply()
            if ((now - extSwitchMs) in 0L until GRACE_PERIOD_MS) return false
        }
        return (now - lastActiveElapsedMs) >= cfg.timeoutMs
    }

    private fun recordFailure(): VerifyResult {
        val fails = prefs.getInt(K_FAILS, 0) + 1
        val cooldown = when { fails >= 10 -> 300_000L; fails >= 8 -> 120_000L; fails >= 5 -> 30_000L; else -> 0L }
        val ed = prefs.edit().putInt(K_FAILS, fails)
        if (cooldown > 0L) {
            ed.putLong(K_LOCK_DUR, cooldown).putLong(K_LOCK_ELAPSED, SystemClock.elapsedRealtime() + cooldown)
                .putLong(K_LOCK_WALL, System.currentTimeMillis() + cooldown).apply()
            return VerifyResult.LockedOut(cooldown)
        }
        ed.apply()
        return VerifyResult.Failed((5 - fails).coerceAtLeast(0))
    }

    private fun clearFailures() {
        prefs.edit().putInt(K_FAILS, 0).putLong(K_LOCK_ELAPSED, 0L).putLong(K_LOCK_WALL, 0L).putLong(K_LOCK_DUR, 0L).apply()
    }

    private fun hashSecret(secret: CharArray, salt: ByteArray, kdf: String): ByteArray {
        val spec = PBEKeySpec(secret, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance(kdf).generateSecret(spec).encoded } finally { spec.clearPassword(); secret.fill('\u0000') }
    }

    private fun generateRecoveryCode(): String {
        val sb = StringBuilder("636M-")
        repeat(8) { idx -> if (idx == 4) sb.append('-'); sb.append(RECOVERY_CHARS[rng.nextInt(RECOVERY_CHARS.length)]) }
        return sb.toString()
    }

    private fun normalizeRecovery(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }
    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(str: String): ByteArray = Base64.decode(str, Base64.NO_WRAP)
}
