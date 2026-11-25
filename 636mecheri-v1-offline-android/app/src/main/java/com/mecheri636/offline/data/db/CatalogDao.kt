package com.mecheri636.offline.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMasterProducts(items: List<MasterProductEntity>)

    @Query("SELECT * FROM master_products ORDER BY product_id ASC")
    suspend fun getAllMasterProducts(): List<MasterProductEntity>

    @Query("SELECT * FROM seller_profile WHERE id = 1")
    fun observeSellerProfile(): Flow<SellerProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSellerProfile(profile: SellerProfileEntity)

    @Query(
        """
        INSERT OR IGNORE INTO seller_shop_items (
            product_id, is_enabled, price_paise, quality_grade,
            stock_status, stock_qty_milli, low_stock_milli,
            cost_price_paise, order_count, updated_at
        )
        SELECT product_id, 0, 0, 'A', 1, 0, 2000, 0, 0, 0
        FROM master_products
        """
    )
    suspend fun initializeAllHiddenForSeller()

    @Query("SELECT * FROM seller_shop_items")
    suspend fun getAllSellerItems(): List<SellerShopItemEntity>

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
        SET stock_qty_milli = MAX(0, stock_qty_milli - :soldMilli),
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
            stock_status = 1,
            updated_at = :now
        WHERE product_id = :productId
        """
    )
    suspend fun addStockInward(productId: Int, addedMilli: Long, unitCostPaise: Long, now: Long)

    @Query(
        """
        UPDATE seller_shop_items
        SET stock_qty_milli = MAX(0, stock_qty_milli - :wastedMilli),
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
               s.updated_at AS updatedAt, 0 AS buyerCartQtyMilli
        FROM master_products m
        INNER JOIN seller_shop_items s ON m.product_id = s.product_id
        ORDER BY m.product_id ASC
        """
    )
    fun observeSellerCatalog(): Flow<List<CatalogDisplayItem>>

    // Orders, Stock, Expenses & Waste
    @Insert
    suspend fun insertSellerOrder(order: SellerOrderEntity): Long

    @Insert
    suspend fun insertSellerOrderItems(items: List<SellerOrderItemEntity>)

    @Insert
    suspend fun insertStockLedger(entry: SellerStockLedgerEntity)

    @Insert
    suspend fun insertExpense(expense: SellerExpenseEntity)

    @Insert
    suspend fun insertWaste(waste: SellerWasteEntity)

    @Query("SELECT * FROM seller_orders ORDER BY created_at DESC LIMIT 100")
    fun observeRecentOrders(): Flow<List<SellerOrderEntity>>

    @Query("SELECT * FROM seller_expenses ORDER BY created_at DESC LIMIT 100")
    fun observeRecentExpenses(): Flow<List<SellerExpenseEntity>>

    @Query("SELECT * FROM seller_waste_log ORDER BY created_at DESC LIMIT 100")
    fun observeRecentWaste(): Flow<List<SellerWasteEntity>>

    @Query("SELECT COUNT(*) FROM seller_orders")
    suspend fun getTotalOrderCount(): Int

    // Buyer Saved Shops & Prices
    @Query("SELECT * FROM buyer_saved_shops ORDER BY last_synced_at DESC")
    fun observeBuyerSavedShops(): Flow<List<BuyerSavedShopEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBuyerSavedShop(shop: BuyerSavedShopEntity)

    @Query("DELETE FROM buyer_shop_prices WHERE shop_id = :shopId")
    suspend fun clearShopPrices(shopId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShopPrices(prices: List<BuyerShopPriceEntity>)

    @Transaction
    suspend fun replaceShopCatalogSnapshot(
        shop: BuyerSavedShopEntity,
        prices: List<BuyerShopPriceEntity>
    ) {
        upsertBuyerSavedShop(shop)
        clearShopPrices(shop.shopId)
        insertShopPrices(prices)
    }

    @Query(
        """
        SELECT m.product_id AS productId, m.category_code AS categoryCode,
               m.category_en AS categoryEn, m.category_ta AS categoryTa,
               m.name_en AS nameEn, m.name_ta AS nameTa,
               m.default_unit AS unit, m.icon_res_name AS iconResName,
               1 AS isEnabledInShop, p.price_paise AS pricePaise,
               0 AS costPricePaise,
               p.quality_grade AS qualityGrade, p.stock_status AS stockStatus,
               0 AS stockQtyMilli, 0 AS orderCount, 0 AS updatedAt,
               COALESCE(c.qty_milli, 0) AS buyerCartQtyMilli
        FROM buyer_shop_prices p
        INNER JOIN master_products m ON m.product_id = p.product_id
        LEFT JOIN buyer_cart_items c ON c.shop_id = p.shop_id AND c.product_id = p.product_id
        WHERE p.shop_id = :shopId
          AND (:hidePersonal = 0 OR m.product_id NOT IN (SELECT product_id FROM buyer_hidden_products))
        ORDER BY m.product_id ASC
        """
    )
    fun observeBuyerShopCatalog(shopId: String, hidePersonal: Boolean): Flow<List<CatalogDisplayItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideProductForBuyer(entry: BuyerHiddenProductEntity)

    @Query("DELETE FROM buyer_hidden_products WHERE product_id = :productId")
    suspend fun unhideProductForBuyer(productId: Int)

    @Query("SELECT product_id FROM buyer_hidden_products")
    fun observeBuyerHiddenIds(): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCartItem(item: BuyerCartItemEntity)

    @Query("DELETE FROM buyer_cart_items WHERE shop_id = :shopId AND product_id = :productId")
    suspend fun removeCartItem(shopId: String, productId: Int)

    @Query("DELETE FROM buyer_cart_items WHERE shop_id = :shopId")
    suspend fun clearShopCart(shopId: String)

    @Query("SELECT * FROM buyer_cart_items WHERE shop_id = :shopId")
    fun observeCartForShop(shopId: String): Flow<List<BuyerCartItemEntity>>

    @Insert
    suspend fun insertBuyerReceipt(receipt: BuyerReceiptEntity)

    @Query("SELECT * FROM buyer_receipts ORDER BY created_at DESC LIMIT 100")
    fun observeBuyerReceipts(): Flow<List<BuyerReceiptEntity>>
}
