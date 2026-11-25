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
    @ColumnInfo(name = "catalog_version") val catalogVersion: Int = 1
)

@Entity(tableName = "seller_profile")
data class SellerProfileEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "shop_id") val shopId: String,
    @ColumnInfo(name = "shop_name") val shopName: String,
    @ColumnInfo(name = "shop_area") val shopArea: String = "Mecheri",
    @ColumnInfo(name = "pin_hash") val pinHash: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
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
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L
)

@Entity(tableName = "seller_orders", indices = [Index(value = ["created_at"])])
data class SellerOrderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "order_id") val orderId: Long = 0L,
    @ColumnInfo(name = "receipt_code") val receiptCode: String,
    @ColumnInfo(name = "buyer_alias") val buyerAlias: String,
    @ColumnInfo(name = "total_paise") val totalPaise: Long,
    @ColumnInfo(name = "cogs_paise") val cogsPaise: Long,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long
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
    @ColumnInfo(name = "line_total_paise") val lineTotalPaise: Long
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
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long
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
    @ColumnInfo(name = "stock_status") val stockStatus: Int
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
    @ColumnInfo(name = "created_at") val createdAt: Long
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
    val buyerCartQtyMilli: Long = 0L
)
