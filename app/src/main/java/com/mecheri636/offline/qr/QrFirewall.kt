package com.mecheri636.offline.qr

import android.content.Intent
import android.net.Uri
import java.util.Locale

data class ParsedUpiPayment(
    val rawUri: String,
    val vpa: String,
    val payeeName: String,
    val amountPaise: Long?,
    val note: String
)

sealed class QrFirewallVerdict {
    data class AllowedMecheri(val payload: String) : QrFirewallVerdict()
    data class AllowedUpi(val upi: ParsedUpiPayment) : QrFirewallVerdict()
    data class Blocked(val reason: String, val preview: String) : QrFirewallVerdict()
}

object QrFirewall {
    private const val MAX_QR_CHARS = 4096
    private val VPA_REGEX = Regex("^[a-zA-Z0-9._-]{2,64}@[a-zA-Z0-9]{2,32}$")
    private val FRAME_PREFIX = Regex("^F\\d{1,2}/\\d{1,2}:.*$", RegexOption.DOT_MATCHES_ALL)
    private val MECHERI_PREFIXES = listOf("Z1:", "S1|", "S2|", "C1|", "C2|", "R1|", "R2|", "X1|", "B1|", "X3|")
    private val BLOCKED_SCHEMES = listOf(
        "http://", "https://", "javascript:", "file://", "content://",
        "intent://", "tel:", "sms:", "smsto:", "mailto:", "market://", "data:", "ftp://"
    )

    fun inspect(rawInput: String): QrFirewallVerdict {
        if (rawInput.isBlank()) return QrFirewallVerdict.Blocked("Empty QR code", "")
        if (rawInput.length > MAX_QR_CHARS) {
            return QrFirewallVerdict.Blocked("QR exceeds safe 4 KB limit", rawInput.take(32))
        }
        val cleaned = rawInput.trim().filter { ch -> ch >= ' ' && ch != '\u007F' }
        val lower = cleaned.lowercase(Locale.US)
        for (scheme in BLOCKED_SCHEMES) {
            if (lower.startsWith(scheme) || lower.contains("javascript:")) {
                return QrFirewallVerdict.Blocked("Blocked unsafe link ($scheme)", cleaned.take(40))
            }
        }
        if (FRAME_PREFIX.matches(cleaned) || MECHERI_PREFIXES.any { cleaned.startsWith(it) }) {
            return QrFirewallVerdict.AllowedMecheri(cleaned)
        }
        if (lower.startsWith("upi://pay?")) {
            val parsed = parseUpiUri(cleaned)
            return if (parsed != null) QrFirewallVerdict.AllowedUpi(parsed)
            else QrFirewallVerdict.Blocked("Invalid or unsafe UPI QR format", cleaned.take(40))
        }
        return QrFirewallVerdict.Blocked("Unknown QR format (only 636mecheri & UPI allowed)", cleaned.take(40))
    }

    fun parseUpiUri(rawUri: String): ParsedUpiPayment? {
        return runCatching {
            val uri = Uri.parse(rawUri)
            if (!uri.scheme.equals("upi", ignoreCase = true)) return null
            if (!uri.host.equals("pay", ignoreCase = true)) return null
            val vpa = uri.getQueryParameter("pa")?.trim().orEmpty()
            if (!VPA_REGEX.matches(vpa)) return null
            val payeeName = sanitizeText(uri.getQueryParameter("pn").orEmpty(), 40).ifBlank { vpa }
            val amtStr = uri.getQueryParameter("am")?.trim()
            val amountPaise = amtStr?.toDoubleOrNull()?.let { amt ->
                if (amt > 0.0 && amt <= 100000.0) Math.round(amt * 100.0) else null
            }
            val note = sanitizeText(uri.getQueryParameter("tn").orEmpty(), 50)
            ParsedUpiPayment(rawUri, vpa, payeeName, amountPaise, note)
        }.getOrNull()
    }

    fun buildDynamicUpiQr(vpa: String, payeeName: String, amountPaise: Long, billNote: String): String {
        val cleanVpa = vpa.trim()
        require(VPA_REGEX.matches(cleanVpa)) { "Invalid UPI ID format (example: name@bank)" }
        val safeName = Uri.encode(sanitizeText(payeeName, 36).ifBlank { "636mecheri Shop" })
        val safeNote = Uri.encode(sanitizeText(billNote, 40))
        val sb = StringBuilder("upi://pay?pa=").append(Uri.encode(cleanVpa))
            .append("&pn=").append(safeName)
            .append("&cu=INR")
        if (amountPaise > 0L) {
            val rupees = String.format(Locale.US, "%.2f", amountPaise / 100.0)
            sb.append("&am=").append(rupees)
        }
        if (safeNote.isNotBlank()) sb.append("&tn=").append(safeNote)
        return sb.toString()
    }

    // 0% network inside 636mecheri: hands URI to Android OS to switch into GPay/PhonePe/Paytm.
    fun buildUpiAppSwitchIntent(upiUri: String): Intent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
        return Intent.createChooser(intent, "Open UPI App (GPay / PhonePe / Paytm)")
    }

    fun sanitizeText(text: String, maxLen: Int): String =
        text.replace("|", " ").replace("~", " ").replace(";", " ")
            .replace("\n", " ").replace("\r", " ")
            .filter { it >= ' ' && it != '\u007F' }
            .trim()
            .take(maxLen)
}
