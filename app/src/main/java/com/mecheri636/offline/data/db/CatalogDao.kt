package com.mecheri636.offline.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class StockInwardInput(
    val productId: Int,
    val addedMilli: Long,
    val unitCostPaise: Long,
    val note: String = ""
)

data class WasteLogInput(
    val productId: Int,
    val wastedMilli: Long,
    val lossPaise: Long,
    val reason: String = "Spoiled"
)

data class ShopPriceComparisonRow(
    val shopId: String,
    val shopName: String,
    val shopLandmark: String,
    val lastSyncedAt: Long,
    val boardValidUntil: Long,
    val productId: Int,
    val pricePaise: Long,
    val qualityGrade: String,
    val stockStatus: Int,
    val stockQtyMilli: Long,
    val bulkMinQtyMilli: Long,
    val bulkPricePaise: Long,
    val minOrderMilli: Long,
    val stepQtyMilli: Long,
    val varietyTag: String
)

@Dao
interface CatalogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMasterProducts(items: List<MasterProductEntity>)

    @Query("SELECT * FROM master_products ORDER BY product_id ASC")
    suspend fun getAllMasterProducts(): List<MasterProductEntity>

    @Query("SELECT * FROM seller_profile WHERE id = 1")
    fun observeSellerProfile(): Flow<SellerProfileEntity?>

    @Query("SELECT * FROM seller_profile WHERE id = 1")
    suspend fun getSellerProfileOnce(): SellerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSellerProfile(profile: SellerProfileEntity)

    @Query(
        """
        INSERT OR IGNORE INTO seller_shop_items (
            product_id, is_enabled, price_paise, quality_grade,
            stock_status, stock_qty_milli, low_stock_milli,
            cost_price_paise, order_count, updated_at,
            bulk_min_qty_milli, bulk_price_paise, min_order_milli, step_qty_milli, variety_tag
        )
        SELECT product_id, 0, 0, 'A', 1, 0, 2000, 0, 0, 0, 0, 0, 100, 100, ''
        FROM master_products
        """
    )
    suspend fun initializeAllHiddenForSeller()

    @Query("SELECT * FROM seller_shop_items")
    suspend fun getAllSellerItems(): List<SellerShopItemEntity>

    @Query("SELECT * FROM seller_shop_items WHERE product_id = :productId")
    suspend fun getSellerItemById(productId: Int): SellerShopItemEntity?

    @Query("UPDATE seller_shop_items SET is_enabled = :enabled, updated_at = :now WHERE product_id = :productId")
    suspend fun setSellerItemEnabled(productId: Int, enabled: Boolean, now: Long)

    @Query(
        """
        UPDATE seller_shop_items
        SET is_enabled = :enabled, updated_at = :now
        WHERE product_id IN (SELECT product_id FROM master_products WHERE category_code = :categoryCode)
        """
    )
    suspend fun setCategoryEnabledForSeller(categoryCode: String, enabled: Boolean, now: Long)

    @Query(
        """
        UPDATE seller_shop_items
        SET price_paise = :pricePaise, cost_price_paise = :costPricePaise,
            quality_grade = :grade, stock_status = :stockStatus,
            stock_qty_milli = :stockQtyMilli, updated_at = :now
        WHERE product_id = :productId
        """
    )
    suspend fun updateSellerItemDetails(
        productId: Int,
        pricePaise: Long,
        costPricePaise: Long,
        grade: String,
        stockStatus: Int,
        stockQtyMilli: Long,
        now: Long
    )

    @Query(
        """
        UPDATE seller_shop_items
        SET price_paise = :pricePaise, cost_price_paise = :costPricePaise,
            quality_grade = :grade, stock_status = :stockStatus,
            stock_qty_milli = :stockQtyMilli, bulk_min_qty_milli = :bulkMinQtyMilli,
            bulk_price_paise = :bulkPricePaise, min_order_milli = :minOrderMilli,
            step_qty_milli = :stepQtyMilli, variety_tag = :varietyTag, updated_at = :now
        WHERE product_id = :productId
        """
    )
    suspend fun updateSellerItemFull(
        productId: Int,
        pricePaise: Long,
        costPricePaise: Long,
        grade: String,
        stockStatus: Int,
        stockQtyMilli: Long,
        bulkMinQtyMilli: Long,
        bulkPricePaise: Long,
        minOrderMilli: Long,
        stepQtyMilli: Long,
        varietyTag: String,
        now: Long
    )

    @Query(
        """
        UPDATE seller_shop_items
        SET stock_qty_milli = MAX(0, stock_qty_milli - :soldMilli),
            stock_status = CASE
                WHEN MAX(0, stock_qty_milli - :soldMilli) <= 0 THEN 0
                WHEN MAX(0, stock_qty_milli - :soldMilli) <= low_stock_milli THEN 2
                ELSE 1
            END,
            order_count = order_count + 1,
            updated_at = :now
        WHERE product_id = :productId
        """
    )
    suspend fun deductStockOnSale(productId: Int, soldMilli: Long, now: Long)

    @Query(
        """
        UPDATE seller_shop_items
        SET stock_qty_milli = stock_qty_milli + :addedMilli,
            cost_price_paise = CASE WHEN :unitCostPaise > 0 THEN :unitCostPaise ELSE cost_price_paise END,
            stock_status = CASE
                WHEN (stock_qty_milli + :addedMilli) <= 0 THEN 0
                WHEN (stock_qty_milli + :addedMilli) <= low_stock_milli THEN 2
                ELSE 1
            END,
            updated_at = :now
        WHERE product_id = :productId
        """
    )
    suspend fun addStockInward(productId: Int, addedMilli: Long, unitCostPaise: Long, now: Long)

    @Query(
        """
        UPDATE seller_shop_items
        SET stock_qty_milli = MAX(0, stock_qty_milli - :wastedMilli),
            stock_status = CASE
                WHEN MAX(0, stock_qty_milli - :wastedMilli) <= 0 THEN 0
                WHEN MAX(0, stock_qty_milli - :wastedMilli) <= low_stock_milli THEN 2
                ELSE 1
            END,
            updated_at = :now
        WHERE product_id = :productId
        """
    )
    suspend fun deductStockOnWaste(productId: Int, wastedMilli: Long, now: Long)

    @Query(
        """
        SELECT m.product_id AS productId, m.category_code AS categoryCode,
               m.category_en AS categoryEn, m.category_ta AS categoryTa,
               m.name_en AS nameEn, m.name_ta AS nameTa,
               m.default_unit AS unit, m.icon_res_name AS iconResName,
               s.is_enabled AS isEnabledInShop, s.price_paise AS pricePaise,
               s.cost_price_paise AS costPricePaise,
               s.quality_grade AS qualityGrade, s.stock_status AS stockStatus,
               s.stock_qty_milli AS stockQtyMilli, s.order_count AS orderCount,
               s.updated_at AS updatedAt, 0 AS buyerCartQtyMilli,
               s.bulk_min_qty_milli AS bulkMinQtyMilli, s.bulk_price_paise AS bulkPricePaise,
               s.min_order_milli AS minOrderMilli, s.step_qty_milli AS stepQtyMilli,
               s.variety_tag AS varietyTag
        FROM master_products m
        INNER JOIN seller_shop_items s ON m.product_id = s.product_id
        ORDER BY m.product_id ASC
        """
    )
    fun observeSellerCatalog(): Flow<List<CatalogDisplayItem>>

    @Insert suspend fun insertSellerOrder(order: SellerOrderEntity): Long
    @Insert suspend fun insertSellerOrderItems(items: List<SellerOrderItemEntity>)
    @Insert suspend fun insertStockLedger(entry: SellerStockLedgerEntity)
    @Insert suspend fun insertExpense(expense: SellerExpenseEntity)
    @Insert suspend fun insertWaste(waste: SellerWasteEntity)
    @Insert suspend fun insertAuditLog(log: AuditLogEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDraft(draft: DraftStateEntity)
    @Query("SELECT * FROM draft_state WHERE draft_key = :key") suspend fun getDraft(key: String): DraftStateEntity?
    @Query("DELETE FROM draft_state WHERE draft_key = :key") suspend fun clearDraft(key: String)

    @Query("SELECT COUNT(*) FROM seller_orders WHERE cart_nonce = :nonce AND cart_nonce != ''")
    suspend fun countOrdersByNonce(nonce: String): Int

    @Query("SELECT * FROM seller_orders WHERE order_id = :orderId")
    suspend fun getOrderById(orderId: Long): SellerOrderEntity?

    @Query("SELECT * FROM seller_order_items WHERE order_id = :orderId")
    suspend fun getOrderItems(orderId: Long): List<SellerOrderItemEntity>

    @Query(
        """
        UPDATE seller_orders
        SET collected_paise = :collectedPaise, cash_paise = :cashPaise, upi_paise = :upiPaise,
            variance_paise = :variancePaise, variance_reason = :varianceReason,
            payment_status = :paymentStatus, payment_mode = :paymentMode, utr_note = :utrNote
        WHERE order_id = :orderId
        """
    )
    suspend fun updateOrderSettlement(
        orderId: Long, collectedPaise: Long, cashPaise: Long, upiPaise: Long,
        variancePaise: Long, varianceReason: String, paymentStatus: String,
        paymentMode: String, utrNote: String
    )

    @Transaction
    suspend fun commitSaleAtomically(
        order: SellerOrderEntity,
        items: List<SellerOrderItemEntity>,
        now: Long
    ): Long {
        if (order.cartNonce.isNotEmpty() && countOrdersByNonce(order.cartNonce) > 0) return -1L
        val orderId = insertSellerOrder(order)
        insertSellerOrderItems(items.map { it.copy(orderId = orderId) })
        items.forEach { item ->
            deductStockOnSale(item.productId, item.qtyMilli, now)
            insertStockLedger(
                SellerStockLedgerEntity(
                    productId = item.productId,
                    entryType = if (item.isFreebie) "FREEBIE" else "SALE",
                    deltaMilli = -item.qtyMilli,
                    unitCostPaise = item.unitCogsPaise,
                    note = order.receiptCode,
                    createdAt = now
                )
            )
        }
        return orderId
    }

    @Transaction
    suspend fun batchStockInwardAtomically(entries: List<StockInwardInput>, now: Long) {
        entries.filter { it.addedMilli > 0L }.forEach { e ->
            val current = getSellerItemById(e.productId)
            val oldQty = current?.stockQtyMilli?.coerceAtLeast(0L) ?: 0L
            val oldCost = current?.costPricePaise?.coerceAtLeast(0L) ?: 0L
            val newQty = oldQty + e.addedMilli
            val weightedCost = when {
                e.unitCostPaise <= 0L -> oldCost
                oldQty <= 0L || oldCost <= 0L -> e.unitCostPaise
                else -> ((oldQty * oldCost) + (e.addedMilli * e.unitCostPaise) + (newQty / 2L)) / newQty
            }
            addStockInward(e.productId, e.addedMilli, weightedCost, now)
            insertStockLedger(
                SellerStockLedgerEntity(
                    productId = e.productId, entryType = "INWARD",
                    deltaMilli = e.addedMilli, unitCostPaise = e.unitCostPaise,
                    note = e.note, createdAt = now
                )
            )
        }
    }

    @Transaction
    suspend fun batchWasteLogAtomically(entries: List<WasteLogInput>, now: Long) {
        entries.filter { it.wastedMilli > 0L }.forEach { e ->
            val current = getSellerItemById(e.productId)
            val computedLoss = if (e.lossPaise > 0L) e.lossPaise
            else ((e.wastedMilli * (current?.costPricePaise ?: 0L)) + 500L) / 1000L
            deductStockOnWaste(e.productId, e.wastedMilli, now)
            insertWaste(SellerWasteEntity(productId = e.productId, wastedQtyMilli = e.wastedMilli, lossPaise = computedLoss, reason = e.reason, createdAt = now))
            insertStockLedger(SellerStockLedgerEntity(productId = e.productId, entryType = "WASTE", deltaMilli = -e.wastedMilli, unitCostPaise = current?.costPricePaise ?: 0L, note = e.reason, createdAt = now))
        }
    }

    @Query("SELECT * FROM seller_orders ORDER BY created_at DESC LIMIT 200")
    fun observeRecentOrders(): Flow<List<SellerOrderEntity>>

    @Query("SELECT * FROM seller_orders WHERE created_at BETWEEN :startMs AND :endMs ORDER BY created_at DESC")
    fun observeOrdersBetween(startMs: Long, endMs: Long): Flow<List<SellerOrderEntity>>

    @Query("SELECT * FROM seller_expenses ORDER BY created_at DESC LIMIT 200")
    fun observeRecentExpenses(): Flow<List<SellerExpenseEntity>>

    @Query("SELECT * FROM seller_waste_log ORDER BY created_at DESC LIMIT 200")
    fun observeRecentWaste(): Flow<List<SellerWasteEntity>>

    @Query("SELECT * FROM audit_log ORDER BY created_at DESC LIMIT 200")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT COUNT(*) FROM seller_orders")
    suspend fun getTotalOrderCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCustomerUdhaar(customer: CustomerUdhaarEntity): Long
    @Query("SELECT * FROM customer_udhaar ORDER BY balance_paise DESC, updated_at DESC") fun observeCustomerUdhaar(): Flow<List<CustomerUdhaarEntity>>
    @Insert suspend fun insertCashZReport(report: CashZReportEntity)
    @Query("SELECT * FROM cash_z_reports ORDER BY created_at DESC LIMIT 60") fun observeCashZReports(): Flow<List<CashZReportEntity>>

    @Query("SELECT * FROM buyer_saved_shops ORDER BY is_favorite DESC, last_synced_at DESC")
    fun observeBuyerSavedShops(): Flow<List<BuyerSavedShopEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBuyerSavedShop(shop: BuyerSavedShopEntity)
    @Query("DELETE FROM buyer_shop_prices WHERE shop_id = :shopId") suspend fun clearShopPrices(shopId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertShopPrices(prices: List<BuyerShopPriceEntity>)

    @Transaction
    suspend fun replaceShopCatalogSnapshot(shop: BuyerSavedShopEntity, prices: List<BuyerShopPriceEntity>) {
        upsertBuyerSavedShop(shop); clearShopPrices(shop.shopId); insertShopPrices(prices)
    }

    @Query(
        """
        SELECT m.product_id AS productId, m.category_code AS categoryCode,
               m.category_en AS categoryEn, m.category_ta AS categoryTa,
               m.name_en AS nameEn, m.name_ta AS nameTa,
               m.default_unit AS unit, m.icon_res_name AS iconResName,
               1 AS isEnabledInShop, p.price_paise AS pricePaise, 0 AS costPricePaise,
               p.quality_grade AS qualityGrade, p.stock_status AS stockStatus,
               p.stock_qty_milli AS stockQtyMilli, 0 AS orderCount, 0 AS updatedAt,
               COALESCE(c.qty_milli, 0) AS buyerCartQtyMilli,
               p.bulk_min_qty_milli AS bulkMinQtyMilli, p.bulk_price_paise AS bulkPricePaise,
               p.min_order_milli AS minOrderMilli, p.step_qty_milli AS stepQtyMilli,
               p.variety_tag AS varietyTag
        FROM buyer_shop_prices p
        INNER JOIN master_products m ON m.product_id = p.product_id
        LEFT JOIN buyer_cart_items c ON c.shop_id = p.shop_id AND c.product_id = p.product_id
        WHERE p.shop_id = :shopId
          AND (:hidePersonal = 0 OR m.product_id NOT IN (SELECT product_id FROM buyer_hidden_products))
        ORDER BY m.product_id ASC
        """
    )
    fun observeBuyerShopCatalog(shopId: String, hidePersonal: Boolean): Flow<List<CatalogDisplayItem>>

    @Query(
        """
        SELECT s.shop_id AS shopId, s.shop_name AS shopName, s.shop_landmark AS shopLandmark,
               s.last_synced_at AS lastSyncedAt, s.board_valid_until AS boardValidUntil,
               p.product_id AS productId, p.price_paise AS pricePaise, p.quality_grade AS qualityGrade,
               p.stock_status AS stockStatus, p.stock_qty_milli AS stockQtyMilli,
               p.bulk_min_qty_milli AS bulkMinQtyMilli, p.bulk_price_paise AS bulkPricePaise,
               p.min_order_milli AS minOrderMilli, p.step_qty_milli AS stepQtyMilli,
               p.variety_tag AS varietyTag
        FROM buyer_shop_prices p
        INNER JOIN buyer_saved_shops s ON s.shop_id = p.shop_id
        WHERE p.product_id = :productId
        ORDER BY p.price_paise ASC
        """
    )
    fun observeComparisonForProduct(productId: Int): Flow<List<ShopPriceComparisonRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun hideProductForBuyer(entry: BuyerHiddenProductEntity)
    @Query("DELETE FROM buyer_hidden_products WHERE product_id = :productId") suspend fun unhideProductForBuyer(productId: Int)
    @Query("SELECT product_id FROM buyer_hidden_products") fun observeBuyerHiddenIds(): Flow<List<Int>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCartItem(item: BuyerCartItemEntity)
    @Query("DELETE FROM buyer_cart_items WHERE shop_id = :shopId AND product_id = :productId") suspend fun removeCartItem(shopId: String, productId: Int)
    @Query("DELETE FROM buyer_cart_items WHERE shop_id = :shopId") suspend fun clearShopCart(shopId: String)
    @Query("SELECT * FROM buyer_cart_items WHERE shop_id = :shopId") fun observeCartForShop(shopId: String): Flow<List<BuyerCartItemEntity>>
    @Query("SELECT COUNT(*) FROM buyer_receipts WHERE receipt_code = :code") suspend fun countReceiptsByCode(code: String): Int
    @Insert suspend fun insertBuyerReceipt(receipt: BuyerReceiptEntity)
    @Query("SELECT * FROM buyer_receipts ORDER BY created_at DESC LIMIT 200") fun observeBuyerReceipts(): Flow<List<BuyerReceiptEntity>>
}
