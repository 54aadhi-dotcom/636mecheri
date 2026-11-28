package com.mecheri636.offline.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

enum class ExportPaperSize(val label: String, val widthPt: Int, val heightPt: Int, val textSp: Float) {
    THERMAL_58MM("58mm Thermal", 164, 520, 8.5f),
    THERMAL_80MM("80mm Thermal", 227, 600, 9.5f),
    A5("A5 Sheet", 420, 595, 11f),
    A4("A4 Sheet", 595, 842, 12f)
}

// Lane 1: One-Way Export-Only (.pdf & UTF-8 BOM .csv) using pure Android SDK (0 external libs, 0 permissions).
object OfflineExportEngine {
    fun createLocalOnlySaveIntent(mimeType: String, suggestedFileName: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, suggestedFileName)
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }

    fun buildTextPdfBytes(title: String, bodyText: String, paper: ExportPaperSize): ByteArray {
        val doc = PdfDocument()
        val margin = if (paper.widthPt <= 230) 8f else 28f
        val contentWidth = (paper.widthPt - (margin * 2)).toInt().coerceAtLeast(80)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = paper.textSp + 2f; isFakeBoldText = true
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = paper.textSp
        }
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).build()
        val bodyLayout = StaticLayout.Builder.obtain(bodyText, 0, bodyText.length, bodyPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true).build()
        val neededH = (margin * 2 + titleLayout.height + 10 + bodyLayout.height).toInt()
        val pageH = maxOf(paper.heightPt, neededH)
        val page = doc.startPage(PdfDocument.PageInfo.Builder(paper.widthPt, pageH, 1).create())
        try {
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            canvas.save(); canvas.translate(margin, margin); titleLayout.draw(canvas); canvas.restore()
            canvas.save(); canvas.translate(margin, margin + titleLayout.height + 8f); bodyLayout.draw(canvas); canvas.restore()
            doc.finishPage(page)
            return ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
        } finally { doc.close() }
    }

    fun buildQrPosterPdfBytes(shopTitle: String, subtitle: String, qrBitmap: Bitmap, paper: ExportPaperSize = ExportPaperSize.A4): ByteArray {
        val doc = PdfDocument()
        val margin = 32f
        val contentW = (paper.widthPt - margin * 2).toInt().coerceAtLeast(100)
        val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f; isFakeBoldText = true }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 11f }
        val headLayout = StaticLayout.Builder.obtain(shopTitle, 0, shopTitle.length, headPaint, contentW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).build()
        val subLayout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length, subPaint, contentW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).build()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(paper.widthPt, paper.heightPt, 1).create())
        try {
            val c = page.canvas; c.drawColor(Color.WHITE)
            c.save(); c.translate(margin, margin); headLayout.draw(c); c.restore()
            val qrSize = minOf(contentW, (paper.heightPt * 0.48f).toInt())
            val qrLeft = ((paper.widthPt - qrSize) / 2)
            val qrTop = (margin + headLayout.height + 18f).toInt()
            c.drawBitmap(qrBitmap, null, Rect(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize), null)
            c.save(); c.translate(margin, (qrTop + qrSize + 18).toFloat()); subLayout.draw(c); c.restore()
            doc.finishPage(page)
            return ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
        } finally { doc.close() }
    }

    fun printPdfLocally(context: Context, jobName: String, pdfBytes: ByteArray): Result<Unit> = runCatching {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>, destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?, callback: WriteResultCallback
            ) {
                runCatching { FileOutputStream(destination.fileDescriptor).use { it.write(pdfBytes); it.flush() } }
                    .onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                    .onFailure { callback.onWriteFailed(it.message) }
            }
        }
        pm.print(jobName, adapter, null)
    }

    fun buildUtf8BomCsvBytes(headers: List<String>, rows: List<List<String>>): ByteArray {
        fun esc(cell: String): String {
            val clean = cell.replace("\r", " ").replace("\n", " ")
            return if (clean.contains(',') || clean.contains('"')) "\"${clean.replace("\"", "\"\"")}\"" else clean
        }
        val sb = StringBuilder("\uFEFF")
        sb.append(headers.joinToString(",") { esc(it) }).append("\r\n")
        rows.forEach { row -> sb.append(row.joinToString(",") { esc(it) }).append("\r\n") }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun writeBytesToSafUri(context: Context, uri: Uri, bytes: ByteArray): Result<Long> = runCatching {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Unable to open output file")
        out.use { it.write(bytes); it.flush() }
        bytes.size.toLong()
    }
}
