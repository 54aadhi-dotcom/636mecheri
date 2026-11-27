package com.mecheri636.offline.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "master_products",
    indices = [
        Index(value = ["category_code"]),
        Index(value = ["name_en"]),
        Index(value = ["name_ta"])
    ]
)
data class MasterProductEntity(
    @PrimaryKey
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "category_code") val categoryCode: String,
    @ColumnInfo(name = "category_en") val categoryEn: String,
    @ColumnInfo(name = "category_ta") val categoryTa: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "name_ta") val nameTa: String,
    @ColumnInfo(name = "default_unit") val defaultUnit: String,
    @ColumnInfo(name = "icon_res_name") val iconResName: String,
    @ColumnInfo(name = "catalog_version") val catalogVersion: Int = 1,
    @ColumnInfo(name = "shape_code") val shapeCode: String = "ROUND",
    @ColumnInfo(name = "color_code") val colorCode: String = "GREEN",
    @ColumnInfo(name = "size_rank") val sizeRank: Int = 2,
    @ColumnInfo(name = "weight_rank") val weightRank: Int = 2,
    @ColumnInfo(name = "search_keywords") val searchKeywords: String = "",
    @ColumnInfo(name = "is_perishable") val isPerishable: Boolean = false,
    @ColumnInfo(name = "def_bundle_milli") val defBundleMilli: Long = 250L,
    @ColumnInfo(name = "unit_step_mode") val unitStepMode: String = "WEIGHT_GRAMS",
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false
)

@Entity(tableName = "seller_profile")
data class SellerProfileEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "shop_id") val shopId: String,
    @ColumnInfo(name = "shop_name") val shopName: String,
    @ColumnInfo(name = "shop_area") val shopArea: String = "Mecheri",
    @ColumnInfo(name = "pin_hash") val pinHash: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "upi_vpa") val upiVpa: String = "",
    @ColumnInfo(name = "upi_payee_name") val upiPayeeName: String = "",
    @ColumnInfo(name = "upi_static_raw") val upiStaticRaw: String = "",
    @ColumnInfo(name = "strict_stock_mode") val strictStockMode: Boolean = false,
    @ColumnInfo(name = "shop_type") val shopType: String = "PRODUCE",
    @ColumnInfo(name = "opening_cash_float_paise") val openingCashFloatPaise: Long = 0L,
    @ColumnInfo(name = "last_order_seq") val lastOrderSeq: Int = 0,
    @ColumnInfo(name = "last_backup_epoch_ms") val lastBackupEpochMs: Long = 0L
)

@Entity(
    tableName = "seller_shop_items",
    foreignKeys = [
        ForeignKey(
            entity = MasterProductEntity::class,
            parentColumns = ["product_id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["is_enabled"]),
        Index(value = ["price_paise"]),
        Index(value = ["quality_grade"]),
        Index(value = ["stock_status"]),
        Index(value = ["updated_at"]),
        Index(value = ["order_count"])
    ]
)
data class SellerShopItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = false,
    @ColumnInfo(name = "price_paise") val pricePaise: Long = 0L,
    @ColumnInfo(name = "quality_grade") val qualityGrade: String = "A",
    @ColumnInfo(name = "stock_status") val stockStatus: Int = 1,
    @ColumnInfo(name = "stock_qty_milli") val stockQtyMilli: Long = 0L,
    @ColumnInfo(name = "low_stock_milli") val lowStockMilli: Long = 2000L,
    @ColumnInfo(name = "cost_price_paise") val costPricePaise: Long = 0L,
    @ColumnInfo(name = "order_count") val orderCount: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    @ColumnInfo(name = "bulk_min_qty_milli") val bulkMinQtyMilli: Long = 0L,
    @ColumnInfo(name = "bulk_price_paise") val bulkPricePaise: Long = 0L,
    @ColumnInfo(name = "min_order_milli") val minOrderMilli: Long = 100L,
    @ColumnInfo(name = "step_qty_milli") val stepQtyMilli: Long = 100L,
    @ColumnInfo(name = "variety_tag") val varietyTag: String = ""
)

@Entity(
    tableName = "seller_orders",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["payment_status"]),
        Index(value = ["cart_nonce"]),
        Index(value = ["order_status"]),
        Index(value = ["customer_id"])
    ]
)
data class SellerOrderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "order_id") val orderId: Long = 0L,
    @ColumnInfo(name = "receipt_code") val receiptCode: String,
    @ColumnInfo(name = "buyer_alias") val buyerAlias: String,
    @ColumnInfo(name = "total_paise") val totalPaise: Long,
    @ColumnInfo(name = "cogs_paise") val cogsPaise: Long,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "original_total_paise") val originalTotalPaise: Long = totalPaise,
    @ColumnInfo(name = "collected_paise") val collectedPaise: Long = totalPaise,
    @ColumnInfo(name = "cash_paise") val cashPaise: Long = totalPaise,
    @ColumnInfo(name = "upi_paise") val upiPaise: Long = 0L,
    @ColumnInfo(name = "variance_paise") val variancePaise: Long = 0L,
    @ColumnInfo(name = "variance_reason") val varianceReason: String = "EXACT",
    @ColumnInfo(name = "payment_status") val paymentStatus: String = "PAID",
    @ColumnInfo(name = "payment_mode") val paymentMode: String = "CASH",
    @ColumnInfo(name = "utr_note") val utrNote: String = "",
    @ColumnInfo(name = "cart_nonce") val cartNonce: String = "",
    @ColumnInfo(name = "order_status") val orderStatus: String = "COMPLETED",
    @ColumnInfo(name = "round_off_paise") val roundOffPaise: Long = 0L,
    @ColumnInfo(name = "utr_last4") val utrLast4: String = "",
    @ColumnInfo(name = "customer_id") val customerId: Long = 0L
)

@Entity(
    tableName = "seller_order_items",
    foreignKeys = [
        ForeignKey(
            entity = SellerOrderEntity::class,
            parentColumns = ["order_id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["order_id"]), Index(value = ["product_id"])]
)
data class SellerOrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "order_id") val orderId: Long,
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "qty_milli") val qtyMilli: Long,
    @ColumnInfo(name = "unit_price_paise") val unitPricePaise: Long,
    @ColumnInfo(name = "line_total_paise") val lineTotalPaise: Long,
    @ColumnInfo(name = "product_name_snapshot") val productNameSnapshot: String = "",
    @ColumnInfo(name = "unit_cogs_paise") val unitCogsPaise: Long = 0L,
    @ColumnInfo(name = "is_freebie") val isFreebie: Boolean = false,
    @ColumnInfo(name = "returned_qty_milli") val returnedQtyMilli: Long = 0L
)

@Entity(tableName = "seller_stock_ledger", indices = [Index(value = ["product_id"]), Index(value = ["created_at"])])
data class SellerStockLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "entry_type") val entryType: String,
    @ColumnInfo(name = "delta_milli") val deltaMilli: Long,
    @ColumnInfo(name = "unit_cost_paise") val unitCostPaise: Long,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "seller_expenses", indices = [Index(value = ["created_at"])])
data class SellerExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "expense_type") val expenseType: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "seller_waste_log", indices = [Index(value = ["product_id"]), Index(value = ["created_at"])])
data class SellerWasteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "wasted_qty_milli") val wastedQtyMilli: Long,
    @ColumnInfo(name = "loss_paise") val lossPaise: Long,
    @ColumnInfo(name = "reason") val reason: String = "Spoiled",
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "buyer_saved_shops")
data class BuyerSavedShopEntity(
    @PrimaryKey
    @ColumnInfo(name = "shop_id") val shopId: String,
    @ColumnInfo(name = "shop_name") val shopName: String,
    @ColumnInfo(name = "seller_catalog_ver") val sellerCatalogVer: Int,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long,
    @ColumnInfo(name = "shop_landmark") val shopLandmark: String = "",
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "board_valid_until") val boardValidUntil: Long = 0L,
    @ColumnInfo(name = "upi_vpa") val upiVpa: String = ""
)

@Entity(
    tableName = "buyer_shop_prices",
    primaryKeys = ["shop_id", "product_id"],
    foreignKeys = [
        ForeignKey(
            entity = BuyerSavedShopEntity::class,
            parentColumns = ["shop_id"],
            childColumns = ["shop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["shop_id"]), Index(value = ["product_id"]), Index(value = ["price_paise"])]
)
data class BuyerShopPriceEntity(
    @ColumnInfo(name = "shop_id") val shopId: String,
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "price_paise") val pricePaise: Long,
    @ColumnInfo(name = "quality_grade") val qualityGrade: String,
    @ColumnInfo(name = "stock_status") val stockStatus: Int,
    @ColumnInfo(name = "stock_qty_milli") val stockQtyMilli: Long = 0L,
    @ColumnInfo(name = "bulk_min_qty_milli") val bulkMinQtyMilli: Long = 0L,
    @ColumnInfo(name = "bulk_price_paise") val bulkPricePaise: Long = 0L,
    @ColumnInfo(name = "min_order_milli") val minOrderMilli: Long = 100L,
    @ColumnInfo(name = "step_qty_milli") val stepQtyMilli: Long = 100L,
    @ColumnInfo(name = "variety_tag") val varietyTag: String = ""
)

@Entity(tableName = "buyer_hidden_products")
data class BuyerHiddenProductEntity(
    @PrimaryKey
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "hidden_at") val hiddenAt: Long
)

@Entity(
    tableName = "buyer_cart_items",
    primaryKeys = ["shop_id", "product_id"],
    indices = [Index(value = ["shop_id"])]
)
data class BuyerCartItemEntity(
    @ColumnInfo(name = "shop_id") val shopId: String,
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "qty_milli") val qtyMilli: Long,
    @ColumnInfo(name = "snapshot_price_paise") val snapshotPricePaise: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long
)

@Entity(tableName = "buyer_receipts", indices = [Index(value = ["shop_id"]), Index(value = ["created_at"])])
data class BuyerReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "shop_id") val shopId: String,
    @ColumnInfo(name = "shop_name") val shopName: String,
    @ColumnInfo(name = "receipt_code") val receiptCode: String,
    @ColumnInfo(name = "total_paise") val totalPaise: Long,
    @ColumnInfo(name = "items_summary") val itemsSummary: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "original_total_paise") val originalTotalPaise: Long = totalPaise,
    @ColumnInfo(name = "paid_paise") val paidPaise: Long = totalPaise,
    @ColumnInfo(name = "variance_reason") val varianceReason: String = "EXACT",
    @ColumnInfo(name = "payment_mode") val paymentMode: String = "CASH"
)

@Entity(tableName = "draft_state")
data class DraftStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "draft_key") val draftKey: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "audit_log", indices = [Index(value = ["created_at"])])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "action_type") val actionType: String,
    @ColumnInfo(name = "ref_id") val refId: String,
    @ColumnInfo(name = "summary") val summary: String,
    @ColumnInfo(name = "delta_paise") val deltaPaise: Long = 0L,
    @ColumnInfo(name = "actor_mode") val actorMode: String = "OWNER",
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "customer_udhaar", indices = [Index(value = ["alias"])])
data class CustomerUdhaarEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "customer_id") val customerId: Long = 0L,
    @ColumnInfo(name = "alias") val alias: String,
    @ColumnInfo(name = "area_note") val areaNote: String = "",
    @ColumnInfo(name = "balance_paise") val balancePaise: Long = 0L,
    @ColumnInfo(name = "risk_tag") val riskTag: String = "NORMAL",
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false
)

@Entity(tableName = "cash_z_reports", indices = [Index(value = ["day_key"])])
data class CashZReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "opening_float_paise") val openingFloatPaise: Long,
    @ColumnInfo(name = "expected_cash_paise") val expectedCashPaise: Long,
    @ColumnInfo(name = "counted_cash_paise") val countedCashPaise: Long,
    @ColumnInfo(name = "variance_paise") val variancePaise: Long,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "pos_drafts", indices = [Index(value = ["slot_id"])])
data class PosDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "slot_id") val slotId: String,
    @ColumnInfo(name = "product_id") val productId: Int,
    @ColumnInfo(name = "custom_label") val customLabel: String = "",
    @ColumnInfo(name = "qty_milli") val qtyMilli: Long,
    @ColumnInfo(name = "unit_price_paise") val unitPricePaise: Long,
    @ColumnInfo(name = "override_line_total_paise") val overrideLineTotalPaise: Long? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "buyer_profile")
data class BuyerProfileEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "buyer_alias") val buyerAlias: String = "B636",
    @ColumnInfo(name = "monthly_budget_paise") val monthlyBudgetPaise: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L
)

@Entity(tableName = "shop_expenses", indices = [Index(value = ["epoch_ms"])])
data class ShopExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "expense_id") val expenseId: Long = 0L,
    @ColumnInfo(name = "epoch_ms") val epochMs: Long,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "is_cash") val isCash: Boolean = true
)

data class CatalogDisplayItem(
    val productId: Int,
    val categoryCode: String,
    val categoryEn: String,
    val categoryTa: String,
    val nameEn: String,
    val nameTa: String,
    val unit: String,
    val iconResName: String,
    val isEnabledInShop: Boolean,
    val pricePaise: Long,
    val costPricePaise: Long,
    val qualityGrade: String,
    val stockStatus: Int,
    val stockQtyMilli: Long,
    val orderCount: Int,
    val updatedAt: Long,
    val buyerCartQtyMilli: Long = 0L,
    val bulkMinQtyMilli: Long = 0L,
    val bulkPricePaise: Long = 0L,
    val minOrderMilli: Long = 100L,
    val stepQtyMilli: Long = 100L,
    val varietyTag: String = "",
    val shapeCode: String = "ROUND",
    val colorCode: String = "GREEN",
    val sizeRank: Int = 2,
    val weightRank: Int = 2,
    val searchKeywords: String = "",
    val isPerishable: Boolean = false,
    val defBundleMilli: Long = 250L,
    val unitStepMode: String = "WEIGHT_GRAMS",
    val isArchived: Boolean = false
)
