package com.mecheri636.offline.ui.screens

import com.mecheri636.offline.data.db.BuyerCartItemEntity
import com.mecheri636.offline.data.db.BuyerReceiptEntity
import com.mecheri636.offline.data.db.CatalogDisplayItem
import com.mecheri636.offline.domain.SellerOperationsEngine
import java.util.Calendar

const val HOME_MARKET_SHOP_ID = "__HOME__"
const val SAVINGS_FRESHNESS_MAX_MS = 48L * 60L * 60L * 1000L

data class RecipeBundleDef(
    val id: String,
    val nameEn: String,
    val nameTa: String,
    val productIds: List<Int>
)

data class SavedShoppingTemplate(
    val name: String,
    val items: List<Pair<Int, Long>>
)

data class MonthlyExpenseSummary(
    val monthTotalPaise: Long,
    val receiptCount: Int,
    val topShopName: String,
    val topShopPaise: Long
)

object BuyerRecipeAndTemplates {
    val CONTEXTUAL_BUNDLES = listOf(
        RecipeBundleDef("sambar", "Sambar Pack", "சாம்பார் தொகுப்பு", listOf(1, 3, 14, 15, 77, 79)),
        RecipeBundleDef("rasam", "Rasam Pack", "ரசம் தொகுப்பு", listOf(1, 68, 77, 79)),
        RecipeBundleDef("poriyal", "Poriyal Pack", "பொரியல் தொகுப்பு", listOf(7, 35, 16, 73, 61)),
        RecipeBundleDef("keerai", "Keerai Pack", "கீரை தொகுப்பு", listOf(80, 81, 3, 68)),
        RecipeBundleDef("fruits", "Fruit Pack", "பழ தொகுப்பு", listOf(106, 115, 117, 120))
    )

    fun sanitizeBuyerAlias(raw: String): String =
        raw.replace("|", "").replace(":", "").replace(",", "")
            .replace("\n", "").replace("\r", "")
            .filter { it >= ' ' && it != '\u007F' }
            .trim()
            .take(12)
            .ifBlank { "B636" }

    fun computeEffectiveLinePaise(
        item: BuyerCartItemEntity,
        catalogItem: CatalogDisplayItem?
    ): Pair<Long, Boolean> {
        if (catalogItem != null && !catalogItem.isEnabledInShop) return 0L to false
        val basePrice = if (catalogItem != null && catalogItem.pricePaise > 0L) catalogItem.pricePaise else item.snapshotPricePaise
        if (basePrice <= 0L || item.qtyMilli <= 0L) return 0L to false
        val (effUnit, isBulk) = SellerOperationsEngine.resolveEffectiveUnitPrice(
            qtyMilli = item.qtyMilli,
            basePricePaise = basePrice,
            bulkMinQtyMilli = catalogItem?.bulkMinQtyMilli ?: 0L,
            bulkPricePaise = catalogItem?.bulkPricePaise ?: 0L
        )
        return SellerOperationsEngine.computeLinePaise(item.qtyMilli, effUnit) to isBulk
    }

    fun computeUnifiedCartTotalPaise(
        cart: List<BuyerCartItemEntity>,
        catalogMap: Map<Int, CatalogDisplayItem>
    ): Long = cart.sumOf { computeEffectiveLinePaise(it, catalogMap[it.productId]).first }

    fun computeCurrentMonthExpenses(receipts: List<BuyerReceiptEntity>): MonthlyExpenseSummary {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthStart = cal.timeInMillis
        val monthly = receipts.filter { it.createdAt >= monthStart }
        val total = monthly.sumOf { if (it.paidPaise > 0L) it.paidPaise else it.totalPaise }
        val byShop = monthly.groupBy { it.shopName.ifBlank { it.shopId } }
            .mapValues { (_, list) -> list.sumOf { if (it.paidPaise > 0L) it.paidPaise else it.totalPaise } }
        val top = byShop.maxByOrNull { it.value }
        return MonthlyExpenseSummary(
            monthTotalPaise = total,
            receiptCount = monthly.size,
            topShopName = top?.key ?: "-",
            topShopPaise = top?.value ?: 0L
        )
    }

    fun encodeTemplates(templates: List<SavedShoppingTemplate>): String =
        templates.joinToString("\n") { tpl ->
            val cleanName = tpl.name.replace("|", " ").replace("\n", " ").trim().take(24)
            val pairs = tpl.items.joinToString(",") { "${it.first}:${it.second}" }
            "$cleanName|$pairs"
        }

    fun decodeTemplates(raw: String?): List<SavedShoppingTemplate> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            if (parts.size != 2 || parts[0].isBlank()) return@mapNotNull null
            val items = parts[1].split(",").mapNotNull { token ->
                val seg = token.split(":")
                if (seg.size == 2) seg[0].toIntOrNull()?.let { id -> seg[1].toLongOrNull()?.let { q -> id to q } }
                else null
            }
            if (items.isEmpty()) null else SavedShoppingTemplate(parts[0], items)
        }
    }
}
