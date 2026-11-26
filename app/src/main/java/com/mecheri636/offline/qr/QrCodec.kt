package com.mecheri636.offline.qr

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

object QrCodec {
    private const val MAX_SINGLE_QR_CHARS = 560
    private const val MAX_DECOMPRESSED_BYTES = 65_536
    private const val B85_ALPHABET =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#$%&()*+-;<=>?@^_`{|}~"

    data class SellerBoardPayload(
        val catalogVersion: Int,
        val shopId: String,
        val shopName: String,
        val epochMinutes: Long,
        val items: List<SellerItemQuote>,
        val boardValidUntil: Long = 0L,
        val upiVpa: String = ""
    )

    data class SellerItemQuote(
        val productId: Int,
        val pricePaise: Long,
        val qualityGrade: String,
        val stockStatus: Int,
        val stockQtyMilli: Long = 0L,
        val bulkMinQtyMilli: Long = 0L,
        val bulkPricePaise: Long = 0L,
        val minOrderMilli: Long = 100L,
        val stepQtyMilli: Long = 100L,
        val varietyTag: String = ""
    )

    data class BuyerCartPayload(
        val shopId: String,
        val buyerAlias: String,
        val epochMinutes: Long,
        val items: List<CartLine>,
        val cartNonce: String = ""
    )

    data class CartLine(val productId: Int, val qtyMilli: Long)

    data class ReceiptPayload(
        val shopId: String,
        val receiptCode: String,
        val epochMinutes: Long,
        val totalPaise: Long,
        val lines: List<ReceiptLine>,
        val originalTotalPaise: Long = totalPaise,
        val paidPaise: Long = totalPaise,
        val varianceReason: String = "EXACT",
        val paymentMode: String = "CASH"
    )

    data class ReceiptLine(val productId: Int, val qtyMilli: Long, val unitPricePaise: Long)

    data class SosRelayChunk(
        val kind: String,
        val relaySessionId: String,
        val shopId: String,
        val shopName: String,
        val upiVpa: String,
        val epochMinutes: Long,
        val body: String
    )

    fun encodeSellerBoard(board: SellerBoardPayload): List<String> {
        val safeShop = QrFirewall.sanitizeText(board.shopName, 28)
        val safeUpi = QrFirewall.sanitizeText(board.upiVpa, 48)
        val itemPart = board.items.joinToString(",") { q ->
            val tag = QrFirewall.sanitizeText(q.varietyTag, 12).replace(":", "").replace(",", "")
            "${q.productId}:${q.pricePaise}:${q.qualityGrade.take(1)}${q.stockStatus}:${q.stockQtyMilli}:${q.bulkMinQtyMilli}:${q.bulkPricePaise}:${q.minOrderMilli}:${q.stepQtyMilli}:$tag"
        }
        val raw = "S2|${board.catalogVersion}|${board.shopId}|$safeShop|${board.epochMinutes}|${board.boardValidUntil}|$safeUpi|$itemPart"
        return compressAndFrame(raw)
    }

    fun decodeSellerBoard(assembledPayload: String): SellerBoardPayload {
        val unpacked = unpackIfCompressed(assembledPayload)
        val (body, expectedCrc) = verifyAndStripCrc(unpacked)
        require(crc16Hex(body) == expectedCrc) { "Corrupted Shop QR checksum" }
        val parts = body.split("|")
        return when {
            parts.size == 6 && parts[0] == "S1" -> {
                val items = if (parts[5].isBlank()) emptyList() else parts[5].split(",").map { token ->
                    val seg = token.split(":")
                    SellerItemQuote(
                        productId = seg[0].toInt(), pricePaise = seg[1].toLong(),
                        qualityGrade = seg[2].substring(0, 1), stockStatus = seg[2].substring(1, 2).toInt()
                    )
                }
                SellerBoardPayload(parts[1].toInt(), parts[2], parts[3], parts[4].toLong(), items)
            }
            parts.size == 8 && parts[0] == "S2" -> {
                val items = if (parts[7].isBlank()) emptyList() else parts[7].split(",").map { token ->
                    val seg = token.split(":")
                    SellerItemQuote(
                        productId = seg[0].toInt(),
                        pricePaise = seg[1].toLong(),
                        qualityGrade = seg[2].substring(0, 1),
                        stockStatus = seg[2].substring(1, 2).toInt(),
                        stockQtyMilli = seg.getOrNull(3)?.toLongOrNull() ?: 0L,
                        bulkMinQtyMilli = seg.getOrNull(4)?.toLongOrNull() ?: 0L,
                        bulkPricePaise = seg.getOrNull(5)?.toLongOrNull() ?: 0L,
                        minOrderMilli = seg.getOrNull(6)?.toLongOrNull() ?: 100L,
                        stepQtyMilli = seg.getOrNull(7)?.toLongOrNull() ?: 100L,
                        varietyTag = seg.getOrNull(8).orEmpty()
                    )
                }
                SellerBoardPayload(
                    catalogVersion = parts[1].toInt(), shopId = parts[2], shopName = parts[3],
                    epochMinutes = parts[4].toLong(), items = items,
                    boardValidUntil = parts[5].toLongOrNull() ?: 0L, upiVpa = parts[6]
                )
            }
            else -> error("Invalid Shop QR header")
        }
    }

    fun encodeBuyerCart(cart: BuyerCartPayload): String {
        val safeAlias = QrFirewall.sanitizeText(cart.buyerAlias, 24)
        val nonce = cart.cartNonce.ifBlank { "${cart.epochMinutes}_${cart.items.size}" }
        val itemPart = cart.items.joinToString(",") { "${it.productId}:${it.qtyMilli}" }
        val raw = "C2|${cart.shopId}|$safeAlias|${cart.epochMinutes}|$nonce|$itemPart"
        return "$raw|${crc16Hex(raw)}"
    }

    fun decodeBuyerCart(qrText: String): BuyerCartPayload {
        val unpacked = unpackIfCompressed(qrText)
        val (body, expectedCrc) = verifyAndStripCrc(unpacked)
        require(crc16Hex(body) == expectedCrc) { "Corrupted Cart QR checksum" }
        val parts = body.split("|")
        return when {
            parts.size == 5 && parts[0] == "C1" -> BuyerCartPayload(
                shopId = parts[1], buyerAlias = parts[2], epochMinutes = parts[3].toLong(),
                items = parseCartLines(parts[4]), cartNonce = "${parts[1]}_${parts[3]}"
            )
            parts.size == 6 && parts[0] == "C2" -> BuyerCartPayload(
                shopId = parts[1], buyerAlias = parts[2], epochMinutes = parts[3].toLong(),
                items = parseCartLines(parts[5]), cartNonce = parts[4]
            )
            else -> error("Invalid Cart QR header")
        }
    }

    fun encodeReceipt(receipt: ReceiptPayload): String {
        val linesPart = receipt.lines.joinToString(",") { "${it.productId}:${it.qtyMilli}:${it.unitPricePaise}" }
        val raw = "R2|${receipt.shopId}|${receipt.receiptCode}|${receipt.epochMinutes}|${receipt.totalPaise}|${receipt.originalTotalPaise}|${receipt.paidPaise}|${receipt.varianceReason}|${receipt.paymentMode}|$linesPart"
        return "$raw|${crc16Hex(raw)}"
    }

    fun decodeReceipt(qrText: String): ReceiptPayload {
        val unpacked = unpackIfCompressed(qrText)
        val (body, expectedCrc) = verifyAndStripCrc(unpacked)
        require(crc16Hex(body) == expectedCrc) { "Corrupted Receipt QR checksum" }
        val parts = body.split("|")
        return when {
            parts.size == 6 && parts[0] == "R1" -> ReceiptPayload(
                parts[1], parts[2], parts[3].toLong(), parts[4].toLong(), parseReceiptLines(parts[5])
            )
            parts.size == 10 && parts[0] == "R2" -> ReceiptPayload(
                shopId = parts[1], receiptCode = parts[2], epochMinutes = parts[3].toLong(),
                totalPaise = parts[4].toLong(), lines = parseReceiptLines(parts[9]),
                originalTotalPaise = parts[5].toLong(), paidPaise = parts[6].toLong(),
                varianceReason = parts[7], paymentMode = parts[8]
            )
            else -> error("Invalid Receipt QR header")
        }
    }

    // Independent priority-chunked Battery-SOS relay frames (X1=Seller, B1=Buyer, X3=Return Sync).
    fun encodeSosChunk(chunk: SosRelayChunk): List<String> {
        val safeName = QrFirewall.sanitizeText(chunk.shopName, 24)
        val safeUpi = QrFirewall.sanitizeText(chunk.upiVpa, 48)
        val raw = "${chunk.kind}|${chunk.relaySessionId}|${chunk.shopId}|$safeName|$safeUpi|${chunk.epochMinutes}|${chunk.body}"
        return compressAndFrame(raw)
    }

    fun decodeSosChunk(qrText: String): SosRelayChunk {
        val unpacked = unpackIfCompressed(qrText)
        val (body, expectedCrc) = verifyAndStripCrc(unpacked)
        require(crc16Hex(body) == expectedCrc) { "Corrupted SOS QR checksum" }
        val parts = body.split("|", limit = 7)
        require(parts.size == 7 && parts[0] in setOf("X1", "B1", "X3")) { "Invalid SOS QR header" }
        return SosRelayChunk(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5].toLong(), parts[6])
    }

    private fun parseCartLines(raw: String): List<CartLine> =
        if (raw.isBlank()) emptyList() else raw.split(",").map { token ->
            val (idStr, qtyStr) = token.split(":")
            CartLine(idStr.toInt(), qtyStr.toLong())
        }

    private fun parseReceiptLines(raw: String): List<ReceiptLine> =
        if (raw.isBlank()) emptyList() else raw.split(",").map { token ->
            val seg = token.split(":")
            ReceiptLine(seg[0].toInt(), seg[1].toLong(), seg[2].toLong())
        }

    private fun compressAndFrame(raw: String): List<String> {
        val signed = "$raw|${crc16Hex(raw)}"
        val compressed = "Z1:" + encodeBase85(zlibCompress(signed.toByteArray(Charsets.UTF_8)))
        if (compressed.length <= MAX_SINGLE_QR_CHARS) return listOf(compressed)
        val chunks = compressed.chunked(MAX_SINGLE_QR_CHARS)
        return chunks.mapIndexed { idx, chunk -> "F${idx + 1}/${chunks.size}:$chunk" }
    }

    private fun unpackIfCompressed(text: String): String =
        if (text.startsWith("Z1:")) zlibDecompress(decodeBase85(text.removePrefix("Z1:"))).toString(Charsets.UTF_8) else text

    private fun verifyAndStripCrc(text: String): Pair<String, String> {
        val lastBar = text.lastIndexOf('|')
        require(lastBar > 0) { "Missing CRC delimiter" }
        return text.substring(0, lastBar) to text.substring(lastBar + 1)
    }

    private fun crc16Hex(text: String): String {
        val crc = CRC32()
        crc.update(text.toByteArray(Charsets.UTF_8))
        return "%04X".format(crc.value and 0xFFFFL)
    }

    private fun zlibCompress(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(input); deflater.finish()
        val out = ByteArrayOutputStream(input.size)
        val buf = ByteArray(256)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return out.toByteArray()
    }

    private fun zlibDecompress(input: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(input)
        val out = ByteArrayOutputStream(minOf(input.size * 2, MAX_DECOMPRESSED_BYTES))
        val buf = ByteArray(256)
        var total = 0
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0 && inflater.needsInput()) break
                total += count
                require(total <= MAX_DECOMPRESSED_BYTES) { "QR payload exceeds safe decompressed limit" }
                out.write(buf, 0, count)
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    private fun encodeBase85(data: ByteArray): String {
        val sb = StringBuilder((data.size * 5) / 4 + 4)
        var i = 0
        while (i < data.size) {
            val rem = data.size - i
            var value = 0L
            for (j in 0 until 4) value = (value shl 8) or (if (j < rem) (data[i + j].toInt() and 0xFF) else 0).toLong()
            val chars = CharArray(5)
            for (k in 4 downTo 0) { chars[k] = B85_ALPHABET[(value % 85L).toInt()]; value /= 85L }
            sb.append(chars, 0, if (rem < 4) rem + 1 else 5)
            i += 4
        }
        return sb.toString()
    }

    private fun decodeBase85(encoded: String): ByteArray {
        val out = ByteArrayOutputStream((encoded.length * 4) / 5)
        var i = 0
        while (i < encoded.length) {
            val rem = minOf(5, encoded.length - i)
            var value = 0L
            for (j in 0 until 5) {
                val idx = if (j < rem) B85_ALPHABET.indexOf(encoded[i + j]) else 84
                require(idx >= 0) { "Invalid Base85 char" }
                value = value * 85L + idx.toLong()
            }
            val bytes = byteArrayOf(((value shr 24) and 0xFF).toByte(), ((value shr 16) and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
            out.write(bytes, 0, if (rem < 5) rem - 1 else 4)
            i += 5
        }
        return out.toByteArray()
    }
}

class MultiFrameQrAssembler {
    private var expectedTotal = 0
    private var lastFrameMs = 0L
    private val collected = mutableMapOf<Int, String>()

    sealed class Result {
        data class Progress(val received: Int, val total: Int, val receivedIndices: Set<Int> = emptySet()) : Result()
        data class Complete(val fullPayload: String) : Result()
    }

    fun feed(rawQr: String): Result {
        val now = System.currentTimeMillis()
        if (lastFrameMs > 0L && (now - lastFrameMs) > 60_000L) reset()
        lastFrameMs = now
        val match = Regex("^F(\\d{1,2})/(\\d{1,2}):(.*)$", RegexOption.DOT_MATCHES_ALL).matchEntire(rawQr)
        if (match == null) { reset(); return Result.Complete(rawQr) }
        val idx = match.groupValues[1].toInt()
        val total = match.groupValues[2].toInt()
        if (total !in 1..20 || idx !in 1..total) { reset(); return Result.Complete(rawQr) }
        if (total != expectedTotal) { collected.clear(); expectedTotal = total }
        collected[idx] = match.groupValues[3]
        return if (collected.size == expectedTotal) {
            val merged = (1..expectedTotal).joinToString("") { collected[it].orEmpty() }
            reset()
            Result.Complete(merged)
        } else {
            Result.Progress(collected.size, expectedTotal, collected.keys.toSet())
        }
    }

    fun reset() { expectedTotal = 0; lastFrameMs = 0L; collected.clear() }
}
