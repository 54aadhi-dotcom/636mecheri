package com.mecheri636.offline.qr

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

object QrCodec {
    private const val MAX_SINGLE_QR_CHARS = 560
    private const val B85_ALPHABET =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#$%&()*+-;<=>?@^_`{|}~"

    data class SellerBoardPayload(
        val catalogVersion: Int,
        val shopId: String,
        val shopName: String,
        val epochMinutes: Long,
        val items: List<SellerItemQuote>
    )

    data class SellerItemQuote(
        val productId: Int,
        val pricePaise: Long,
        val qualityGrade: String,
        val stockStatus: Int
    )

    data class BuyerCartPayload(
        val shopId: String,
        val buyerAlias: String,
        val epochMinutes: Long,
        val items: List<CartLine>
    )

    data class CartLine(
        val productId: Int,
        val qtyMilli: Long
    )

    data class ReceiptPayload(
        val shopId: String,
        val receiptCode: String,
        val epochMinutes: Long,
        val totalPaise: Long,
        val lines: List<ReceiptLine>
    )

    data class ReceiptLine(
        val productId: Int,
        val qtyMilli: Long,
        val unitPricePaise: Long
    )

    fun encodeSellerBoard(board: SellerBoardPayload): List<String> {
        val safeShopName = board.shopName.replace("|", " ").replace("~", " ").trim().take(28)
        val itemPart = board.items.joinToString(",") {
            "${it.productId}:${it.pricePaise}:${it.qualityGrade}${it.stockStatus}"
        }
        val raw = "S1|${board.catalogVersion}|${board.shopId}|$safeShopName|${board.epochMinutes}|$itemPart"
        val signed = "$raw|${crc16Hex(raw)}"
        val compressed = "Z1:" + encodeBase85(zlibCompress(signed.toByteArray(Charsets.UTF_8)))

        if (compressed.length <= MAX_SINGLE_QR_CHARS) {
            return listOf(compressed)
        }
        val chunks = compressed.chunked(MAX_SINGLE_QR_CHARS)
        return chunks.mapIndexed { idx, chunk -> "F${idx + 1}/${chunks.size}:$chunk" }
    }

    fun decodeSellerBoard(assembledPayload: String): SellerBoardPayload {
        val unpacked = if (assembledPayload.startsWith("Z1:")) {
            val rawBytes = zlibDecompress(decodeBase85(assembledPayload.removePrefix("Z1:")))
            rawBytes.toString(Charsets.UTF_8)
        } else {
            assembledPayload
        }
        val (body, expectedCrc) = verifyAndStripCrc(unpacked)
        require(crc16Hex(body) == expectedCrc) { "Corrupted Shop QR checksum" }

        val parts = body.split("|")
        require(parts.size == 6 && parts[0] == "S1") { "Invalid Shop QR header" }

        val items = if (parts[5].isBlank()) emptyList() else parts[5].split(",").map { token ->
            val seg = token.split(":")
            val grade = seg[2].substring(0, 1)
            val status = seg[2].substring(1, 2).toInt()
            SellerItemQuote(
                productId = seg[0].toInt(),
                pricePaise = seg[1].toLong(),
                qualityGrade = grade,
                stockStatus = status
            )
        }
        return SellerBoardPayload(
            catalogVersion = parts[1].toInt(),
            shopId = parts[2],
            shopName = parts[3],
            epochMinutes = parts[4].toLong(),
            items = items
        )
    }

    fun encodeBuyerCart(cart: BuyerCartPayload): String {
        val itemPart = cart.items.joinToString(",") { "${it.productId}:${it.qtyMilli}" }
        val raw = "C1|${cart.shopId}|${cart.buyerAlias}|${cart.epochMinutes}|$itemPart"
        return "$raw|${crc16Hex(raw)}"
    }

    fun decodeBuyerCart(qrText: String): BuyerCartPayload {
        val (body, expectedCrc) = verifyAndStripCrc(qrText)
        require(crc16Hex(body) == expectedCrc) { "Corrupted Cart QR checksum" }
        val parts = body.split("|")
        require(parts.size == 5 && parts[0] == "C1") { "Invalid Cart QR header" }

        val items = if (parts[4].isBlank()) emptyList() else parts[4].split(",").map { token ->
            val (idStr, qtyStr) = token.split(":")
            CartLine(productId = idStr.toInt(), qtyMilli = qtyStr.toLong())
        }
        return BuyerCartPayload(
            shopId = parts[1],
            buyerAlias = parts[2],
            epochMinutes = parts[3].toLong(),
            items = items
        )
    }

    fun encodeReceipt(receipt: ReceiptPayload): String {
        val linesPart = receipt.lines.joinToString(",") {
            "${it.productId}:${it.qtyMilli}:${it.unitPricePaise}"
        }
        val raw = "R1|${receipt.shopId}|${receipt.receiptCode}|${receipt.epochMinutes}|${receipt.totalPaise}|$linesPart"
        return "$raw|${crc16Hex(raw)}"
    }

    fun decodeReceipt(qrText: String): ReceiptPayload {
        val (body, expectedCrc) = verifyAndStripCrc(qrText)
        require(crc16Hex(body) == expectedCrc) { "Corrupted Receipt QR checksum" }
        val parts = body.split("|")
        require(parts.size == 6 && parts[0] == "R1") { "Invalid Receipt QR header" }

        val lines = if (parts[5].isBlank()) emptyList() else parts[5].split(",").map { token ->
            val seg = token.split(":")
            ReceiptLine(seg[0].toInt(), seg[1].toLong(), seg[2].toLong())
        }
        return ReceiptPayload(
            shopId = parts[1],
            receiptCode = parts[2],
            epochMinutes = parts[3].toLong(),
            totalPaise = parts[4].toLong(),
            lines = lines
        )
    }

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
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream(input.size)
        val buf = ByteArray(256)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            out.write(buf, 0, count)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun zlibDecompress(input: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(input)
        val out = ByteArrayOutputStream(input.size * 2)
        val buf = ByteArray(256)
        while (!inflater.finished()) {
            val count = inflater.inflate(buf)
            out.write(buf, 0, count)
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun encodeBase85(data: ByteArray): String {
        val sb = StringBuilder((data.size * 5) / 4 + 4)
        var i = 0
        while (i < data.size) {
            val rem = data.size - i
            var value = 0L
            for (j in 0 until 4) {
                val b = if (j < rem) (data[i + j].toInt() and 0xFF) else 0
                value = (value shl 8) or b.toLong()
            }
            val chars = CharArray(5)
            for (k in 4 downTo 0) {
                chars[k] = B85_ALPHABET[(value % 85L).toInt()]
                value /= 85L
            }
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
            val bytes = byteArrayOf(
                ((value shr 24) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte()
            )
            out.write(bytes, 0, if (rem < 5) rem - 1 else 4)
            i += 5
        }
        return out.toByteArray()
    }
}

class MultiFrameQrAssembler {
    private var expectedTotal = 0
    private val collected = mutableMapOf<Int, String>()

    sealed class Result {
        data class Progress(val received: Int, val total: Int) : Result()
        data class Complete(val fullPayload: String) : Result()
    }

    fun feed(rawQr: String): Result {
        val frameRegex = Regex("^F(\\d+)/(\\d+):(.*)$", RegexOption.DOT_MATCHES_ALL)
        val match = frameRegex.matchEntire(rawQr)
        if (match == null) {
            reset()
            return Result.Complete(rawQr)
        }
        val idx = match.groupValues[1].toInt()
        val total = match.groupValues[2].toInt()
        val data = match.groupValues[3]

        if (total != expectedTotal) {
            collected.clear()
            expectedTotal = total
        }
        collected[idx] = data

        return if (collected.size == expectedTotal) {
            val merged = (1..expectedTotal).joinToString("") { collected[it].orEmpty() }
            reset()
            Result.Complete(merged)
        } else {
            Result.Progress(collected.size, expectedTotal)
        }
    }

    fun reset() {
        expectedTotal = 0
        collected.clear()
    }
}
