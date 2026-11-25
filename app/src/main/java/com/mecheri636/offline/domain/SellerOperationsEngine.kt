package com.mecheri636.offline.domain

import com.mecheri636.offline.data.db.MasterProductEntity
import com.mecheri636.offline.data.db.SellerShopItemEntity
import com.mecheri636.offline.qr.QrCodec

enum class LineStatus { OK, SOLD_OUT_OR_HIDDEN }

data class CounterPackingLine(
    val productId: Int,
    val nameEn: String,
    val nameTa: String,
    val unit: String,
    val qtyMilli: Long,
    val liveUnitPricePaise: Long,
    val lineTotalPaise: Long,
    val lineCogsPaise: Long,
    val status: LineStatus
)

data class CounterPackingSummary(
    val isWrongShop: Boolean,
    val buyerAlias: String,
    val lines: List<CounterPackingLine>,
    val activeBillPaise: Long,
    val totalCogsPaise: Long,
    val unavailableCount: Int
)

data class DailyProfitReport(
    val totalSalesPaise: Long,
    val totalCogsPaise: Long,
    val totalExpensesPaise: Long,
    val totalWasteLossPaise: Long,
    val trueNetProfitPaise: Long,
    val completedOrders: Int
)

object SellerOperationsEngine {

    fun buildPackingChecklist(
        myShopId: String,
        cart: QrCodec.BuyerCartPayload,
        masterMap: Map<Int, MasterProductEntity>,
        sellerItemsMap: Map<Int, SellerShopItemEntity>
    ): CounterPackingSummary {
        if (cart.shopId != myShopId) {
            return CounterPackingSummary(
                isWrongShop = true,
                buyerAlias = cart.buyerAlias,
                lines = emptyList(),
                activeBillPaise = 0L,
                totalCogsPaise = 0L,
                unavailableCount = 0
            )
        }

        val resolvedLines = cart.items.mapNotNull { cartLine ->
            val master = masterMap[cartLine.productId] ?: return@mapNotNull null
            val shopItem = sellerItemsMap[cartLine.productId]
            val available = shopItem != null && shopItem.isEnabled && shopItem.stockStatus > 0

            val unitPrice = if (available) shopItem!!.pricePaise else 0L
            val lineTotal = if (available) (cartLine.qtyMilli * unitPrice) / 1000L else 0L
            val lineCogs = if (available) (cartLine.qtyMilli * shopItem!!.costPricePaise) / 1000L else 0L

            CounterPackingLine(
                productId = master.productId,
                nameEn = master.nameEn,
                nameTa = master.nameTa,
                unit = master.defaultUnit,
                qtyMilli = cartLine.qtyMilli,
                liveUnitPricePaise = unitPrice,
                lineTotalPaise = lineTotal,
                lineCogsPaise = lineCogs,
                status = if (available) LineStatus.OK else LineStatus.SOLD_OUT_OR_HIDDEN
            )
        }

        return CounterPackingSummary(
            isWrongShop = false,
            buyerAlias = cart.buyerAlias,
            lines = resolvedLines,
            activeBillPaise = resolvedLines.sumOf { it.lineTotalPaise },
            totalCogsPaise = resolvedLines.sumOf { it.lineCogsPaise },
            unavailableCount = resolvedLines.count { it.status == LineStatus.SOLD_OUT_OR_HIDDEN }
        )
    }

    fun buildReceiptQr(
        shopId: String,
        receiptCode: String,
        epochMinutes: Long,
        summary: CounterPackingSummary
    ): String {
        val activeLines = summary.lines
            .filter { it.status == LineStatus.OK }
            .map { QrCodec.ReceiptLine(it.productId, it.qtyMilli, it.liveUnitPricePaise) }

        return QrCodec.encodeReceipt(
            QrCodec.ReceiptPayload(
                shopId = shopId,
                receiptCode = receiptCode,
                epochMinutes = epochMinutes,
                totalPaise = summary.activeBillPaise,
                lines = activeLines
            )
        )
    }

    fun computeTrueProfit(
        salesPaise: Long,
        cogsPaise: Long,
        expensesPaise: Long,
        wasteLossPaise: Long,
        completedOrders: Int
    ): DailyProfitReport = DailyProfitReport(
        totalSalesPaise = salesPaise,
        totalCogsPaise = cogsPaise,
        totalExpensesPaise = expensesPaise,
        totalWasteLossPaise = wasteLossPaise,
        trueNetProfitPaise = salesPaise - cogsPaise - expensesPaise - wasteLossPaise,
        completedOrders = completedOrders
    )
}
