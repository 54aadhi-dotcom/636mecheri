package com.mecheri636.offline

import android.content.Context
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupVault {
    private val MAGIC = byteArrayOf('6'.code.toByte(), '3'.code.toByte(), '6'.code.toByte(), 'V'.code.toByte())
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION: Byte = 1
    private const val KDF_SHA256: Byte = 1
    private const val KDF_SHA1: Byte = 2
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val HEADER_LEN = 4 + 1 + 1 + SALT_LEN + IV_LEN
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val ITERATIONS = 100_000
    private const val MAX_VAULT_BYTES = 64 * 1024 * 1024

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, kdfId: Byte): ByteArray {
        val algo = if (kdfId == KDF_SHA256) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(algo).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encryptBytes(plain: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 4) { "Passphrase must be at least 4 characters" }
        val rng = SecureRandom()
        val salt = ByteArray(SALT_LEN).also(rng::nextBytes)
        val iv = ByteArray(IV_LEN).also(rng::nextBytes)
        val kdfId = if (Build.VERSION.SDK_INT >= 26) KDF_SHA256 else KDF_SHA1
        val keyBytes = deriveKey(passphrase, salt, kdfId)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
            val encrypted = cipher.doFinal(plain)
            ByteBuffer.allocate(HEADER_LEN + encrypted.size)
                .put(MAGIC).put(FORMAT_VERSION).put(kdfId).put(salt).put(iv).put(encrypted)
                .array()
        } finally {
            keyBytes.fill(0)
        }
    }

    fun decryptBytes(vaultData: ByteArray, passphrase: CharArray): ByteArray {
        require(vaultData.size > HEADER_LEN + 16) { "Invalid or truncated .636vault file" }
        for (i in MAGIC.indices) {
            require(vaultData[i] == MAGIC[i]) { "Not a valid 636mecheri backup file" }
        }
        require(vaultData[4] == FORMAT_VERSION) { "Unsupported vault version" }
        val kdfId = vaultData[5]
        val salt = vaultData.copyOfRange(6, 6 + SALT_LEN)
        val iv = vaultData.copyOfRange(6 + SALT_LEN, HEADER_LEN)
        val cipherText = vaultData.copyOfRange(HEADER_LEN, vaultData.size)
        val keyBytes = deriveKey(passphrase, salt, kdfId)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(cipherText)
        } finally {
            keyBytes.fill(0)
        }
    }

    // Caller must run WAL checkpoint & close Room DB before export/import.
    fun exportDbToSafUri(
        context: Context,
        dbName: String,
        targetUri: Uri,
        passphrase: CharArray
    ): Result<Long> = runCatching {
        val dbFile = context.getDatabasePath(dbName)
        require(dbFile.exists() && dbFile.length() > 0L) { "Database file not found" }
        require(dbFile.length() <= MAX_VAULT_BYTES) { "Database exceeds 64 MB vault limit" }
        val plain = dbFile.readBytes()
        require(isSqliteHeader(plain)) { "Database header check failed" }
        val vaultBytes = encryptBytes(plain, passphrase)
        val out = context.contentResolver.openOutputStream(targetUri, "wt")
            ?: error("Unable to open selected backup destination")
        out.use { it.write(vaultBytes); it.flush() }
        vaultBytes.size.toLong()
    }

    fun importDbFromSafUri(
        context: Context,
        dbName: String,
        sourceUri: Uri,
        passphrase: CharArray
    ): Result<Long> = runCatching {
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: error("Unable to read selected backup file")
        val vaultBytes = input.use { readBoundedBytes(it, MAX_VAULT_BYTES) }
        val plain = decryptBytes(vaultBytes, passphrase)
        require(isSqliteHeader(plain)) { "Decrypted data is not a valid SQLite database" }
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        val tmpFile = File(dbFile.parentFile, "$dbName.restore.tmp")
        tmpFile.outputStream().use { it.write(plain); it.fd.sync() }
        File(dbFile.parentFile, "$dbName-wal").delete()
        File(dbFile.parentFile, "$dbName-shm").delete()
        if (dbFile.exists()) dbFile.delete()
        check(tmpFile.renameTo(dbFile)) { "Atomic database replace failed" }
        plain.size.toLong()
    }

    private fun readBoundedBytes(stream: java.io.InputStream, maxBytes: Int): ByteArray {
        val buf = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = stream.read(buf)
            if (read == -1) break
            total += read
            require(total <= maxBytes) { "Backup file exceeds 64 MB limit" }
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }

    private fun isSqliteHeader(bytes: ByteArray): Boolean {
        if (bytes.size < SQLITE_MAGIC.size) return false
        for (i in SQLITE_MAGIC.indices) {
            if (bytes[i] != SQLITE_MAGIC[i]) return false
        }
        return true
    }
}
