package com.mecheri636.offline.domain

import com.mecheri636.offline.data.db.MasterProductEntity
import com.mecheri636.offline.data.db.SellerExpenseEntity
import com.mecheri636.offline.data.db.SellerOrderEntity
import com.mecheri636.offline.data.db.SellerShopItemEntity
import com.mecheri636.offline.data.db.SellerWasteEntity
import com.mecheri636.offline.qr.QrCodec

enum class LineStatus { OK, PARTIAL_CLAMPED, SOLD_OUT_OR_HIDDEN }

enum class VarianceReason { EXACT, ROUND_OFF, BARGAIN_DISCOUNT, SHORT_FALSE_PAYMENT, FAKE_UPI, UDHAAR_DUE, OVERPAID }

data class CounterPackingLine(
    val productId: Int,
    val nameEn: String,
    val nameTa: String,
    val unit: String,
    val qtyMilli: Long,
    val liveUnitPricePaise: Long,
    val lineTotalPaise: Long,
    val lineCogsPaise: Long,
    val status: LineStatus,
    val requestedQtyMilli: Long = qtyMilli,
    val availableStockMilli: Long = qtyMilli,
    val bulkApplied: Boolean = false,
    val isFreebie: Boolean = false
)

data class CounterPackingSummary(
    val isWrongShop: Boolean,
    val buyerAlias: String,
    val lines: List<CounterPackingLine>,
    val activeBillPaise: Long,
    val totalCogsPaise: Long,
    val unavailableCount: Int,
    val clampedCount: Int = 0
)

data class DailyProfitReport(
    val totalSalesPaise: Long,
    val totalCogsPaise: Long,
    val totalExpensesPaise: Long,
    val totalWasteLossPaise: Long,
    val trueNetProfitPaise: Long,
    val completedOrders: Int,
    val originalBilledPaise: Long = totalSalesPaise,
    val cashPaise: Long = totalSalesPaise,
    val upiPaise: Long = 0L,
    val roundOffPaise: Long = 0L,
    val shortFalsePayPaise: Long = 0L,
    val shortFalsePayCount: Int = 0,
    val udhaarPendingPaise: Long = 0L,
    val pendingPaymentCount: Int = 0
)

object SellerOperationsEngine {
    // Half-up paise rounding prevents silent truncation drift on 125g / 333g / 750g weights.
    fun computeLinePaise(qtyMilli: Long, unitPricePaise: Long): Long {
        if (qtyMilli <= 0L || unitPricePaise <= 0L) return 0L
        return ((qtyMilli * unitPricePaise) + 500L) / 1000L
    }

    // Reverse calculator: "Give me ₹20 beans" at ₹70/kg -> 286 milli (286 g).
    fun rupeesToMilliQty(targetPaise: Long, unitPricePaise: Long): Long {
        if (targetPaise <= 0L || unitPricePaise <= 0L) return 0L
        return ((targetPaise * 1000L) + (unitPricePaise / 2L)) / unitPricePaise
    }

    fun resolveEffectiveUnitPrice(
        qtyMilli: Long,
        basePricePaise: Long,
        bulkMinQtyMilli: Long,
        bulkPricePaise: Long
    ): Pair<Long, Boolean> {
        val useBulk = bulkMinQtyMilli > 0L && bulkPricePaise in 1L until basePricePaise && qtyMilli >= bulkMinQtyMilli
        return if (useBulk) bulkPricePaise to true else basePricePaise to false
    }

    fun buildPackingChecklist(
        myShopId: String,
        cart: QrCodec.BuyerCartPayload,
        masterMap: Map<Int, MasterProductEntity>,
        sellerItemsMap: Map<Int, SellerShopItemEntity>
    ): CounterPackingSummary {
        if (cart.shopId != myShopId) {
            return CounterPackingSummary(
                isWrongShop = true, buyerAlias = cart.buyerAlias,
                lines = emptyList(), activeBillPaise = 0L, totalCogsPaise = 0L, unavailableCount = 0
            )
        }
        val resolvedLines = cart.items.mapNotNull { cartLine ->
            val master = masterMap[cartLine.productId] ?: return@mapNotNull null
            val shopItem = sellerItemsMap[cartLine.productId]
            val enabled = shopItem != null && shopItem.isEnabled && shopItem.stockStatus > 0
            val stockCap = if (shopItem != null && shopItem.stockQtyMilli > 0L) shopItem.stockQtyMilli else 0L
            val clampedQty = when {
                !enabled -> 0L
                stockCap <= 0L -> 0L
                cartLine.qtyMilli > stockCap -> stockCap
                else -> cartLine.qtyMilli
            }
            val status = when {
                !enabled || clampedQty <= 0L -> LineStatus.SOLD_OUT_OR_HIDDEN
                clampedQty < cartLine.qtyMilli -> LineStatus.PARTIAL_CLAMPED
                else -> LineStatus.OK
            }
            val (unitPrice, bulkApplied) = if (shopItem != null && status != LineStatus.SOLD_OUT_OR_HIDDEN) {
                resolveEffectiveUnitPrice(clampedQty, shopItem.pricePaise, shopItem.bulkMinQtyMilli, shopItem.bulkPricePaise)
            } else 0L to false
            val lineTotal = computeLinePaise(clampedQty, unitPrice)
            val lineCogs = if (shopItem != null && status != LineStatus.SOLD_OUT_OR_HIDDEN) {
                computeLinePaise(clampedQty, shopItem.costPricePaise)
            } else 0L
            CounterPackingLine(
                productId = master.productId, nameEn = master.nameEn, nameTa = master.nameTa,
                unit = master.defaultUnit, qtyMilli = clampedQty, liveUnitPricePaise = unitPrice,
                lineTotalPaise = lineTotal, lineCogsPaise = lineCogs, status = status,
                requestedQtyMilli = cartLine.qtyMilli, availableStockMilli = stockCap,
                bulkApplied = bulkApplied
            )
        }
        return CounterPackingSummary(
            isWrongShop = false, buyerAlias = cart.buyerAlias, lines = resolvedLines,
            activeBillPaise = resolvedLines.sumOf { it.lineTotalPaise },
            totalCogsPaise = resolvedLines.sumOf { it.lineCogsPaise },
            unavailableCount = resolvedLines.count { it.status == LineStatus.SOLD_OUT_OR_HIDDEN },
            clampedCount = resolvedLines.count { it.status == LineStatus.PARTIAL_CLAMPED }
        )
    }

    fun buildReceiptQr(
        shopId: String, receiptCode: String, epochMinutes: Long, summary: CounterPackingSummary
    ): String {
        val activeLines = summary.lines
            .filter { it.status != LineStatus.SOLD_OUT_OR_HIDDEN && it.qtyMilli > 0L }
            .map { QrCodec.ReceiptLine(it.productId, it.qtyMilli, it.liveUnitPricePaise) }
        return QrCodec.encodeReceipt(
            QrCodec.ReceiptPayload(
                shopId = shopId, receiptCode = receiptCode,
                epochMinutes = epochMinutes, totalPaise = summary.activeBillPaise, lines = activeLines
            )
        )
    }

    fun computeTrueProfit(
        salesPaise: Long, cogsPaise: Long, expensesPaise: Long, wasteLossPaise: Long, completedOrders: Int
    ): DailyProfitReport = DailyProfitReport(
        totalSalesPaise = salesPaise, totalCogsPaise = cogsPaise,
        totalExpensesPaise = expensesPaise, totalWasteLossPaise = wasteLossPaise,
        trueNetProfitPaise = salesPaise - cogsPaise - expensesPaise - wasteLossPaise,
        completedOrders = completedOrders
    )

    fun computeFullReport(
        orders: List<SellerOrderEntity>,
        expenses: List<SellerExpenseEntity>,
        waste: List<SellerWasteEntity>
    ): DailyProfitReport {
        val active = orders.filter { it.paymentStatus != "VOID" }
        val collected = active.sumOf { it.collectedPaise }
        val original = active.sumOf { if (it.originalTotalPaise > 0L) it.originalTotalPaise else it.totalPaise }
        val cogs = active.sumOf { it.cogsPaise }
        val exp = expenses.sumOf { it.amountPaise }
        val wasteLoss = waste.sumOf { it.lossPaise }
        val shortOrders = active.filter { it.varianceReason == "SHORT_FALSE_PAYMENT" || it.varianceReason == "FAKE_UPI" }
        val roundOff = active.filter { it.varianceReason == "ROUND_OFF" || it.varianceReason == "BARGAIN_DISCOUNT" }.sumOf { it.variancePaise.coerceAtLeast(0L) }
        val udhaar = active.filter { it.varianceReason == "UDHAAR_DUE" }.sumOf { it.variancePaise.coerceAtLeast(0L) }
        return DailyProfitReport(
            totalSalesPaise = collected, totalCogsPaise = cogs,
            totalExpensesPaise = exp, totalWasteLossPaise = wasteLoss,
            trueNetProfitPaise = collected - cogs - exp - wasteLoss,
            completedOrders = active.size, originalBilledPaise = original,
            cashPaise = active.sumOf { it.cashPaise }, upiPaise = active.sumOf { it.upiPaise },
            roundOffPaise = roundOff,
            shortFalsePayPaise = shortOrders.sumOf { it.variancePaise.coerceAtLeast(0L) },
            shortFalsePayCount = shortOrders.size,
            udhaarPendingPaise = udhaar,
            pendingPaymentCount = active.count { it.paymentStatus == "PENDING_PAYMENT" || it.paymentStatus == "UPI_PENDING" }
        )
    }
}
