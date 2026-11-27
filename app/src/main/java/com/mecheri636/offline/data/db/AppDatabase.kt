package com.mecheri636.offline.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MasterProductEntity::class,
        SellerProfileEntity::class,
        SellerShopItemEntity::class,
        SellerOrderEntity::class,
        SellerOrderItemEntity::class,
        SellerStockLedgerEntity::class,
        SellerExpenseEntity::class,
        SellerWasteEntity::class,
        BuyerSavedShopEntity::class,
        BuyerShopPriceEntity::class,
        BuyerHiddenProductEntity::class,
        BuyerCartItemEntity::class,
        BuyerReceiptEntity::class,
        DraftStateEntity::class,
        AuditLogEntity::class,
        CustomerUdhaarEntity::class,
        CashZReportEntity::class,
        PosDraftEntity::class,
        BuyerProfileEntity::class,
        ShopExpenseEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        const val DB_NAME = "mecheri636_offline.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE seller_profile ADD COLUMN upi_vpa TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE seller_profile ADD COLUMN upi_payee_name TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE seller_profile ADD COLUMN upi_static_raw TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE seller_shop_items ADD COLUMN bulk_min_qty_milli INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_shop_items ADD COLUMN bulk_price_paise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_shop_items ADD COLUMN min_order_milli INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE seller_shop_items ADD COLUMN step_qty_milli INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE seller_shop_items ADD COLUMN variety_tag TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE seller_orders ADD COLUMN original_total_paise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_orders ADD COLUMN collected_paise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_orders ADD COLUMN cash_paise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_orders ADD COLUMN upi_paise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_orders ADD COLUMN variance_paise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_orders ADD COLUMN variance_reason TEXT NOT NULL DEFAULT 'EXACT'")
                db.execSQL("ALTER TABLE seller_orders ADD COLUMN payment_status TEXT NOT NULL DEFAULT 'PAID'")
                db.execSQL("ALTER TABLE seller_orders ADD COLUMN payment_mode TEXT NOT NULL DEFAULT 'CASH'")
                db.execSQL("ALTER TABLE seller_orders ADD COLUMN utr_note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE seller_orders ADD COLUMN cart_nonce TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE seller_orders SET original_total_paise = total_paise, collected_paise = total_paise, cash_paise = total_paise")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_seller_orders_payment_status ON seller_orders (payment_status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_seller_orders_cart_nonce ON seller_orders (cart_nonce)")

                db.execSQL("ALTER TABLE seller_order_items ADD COLUMN product_name_snapshot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE seller_order_items ADD COLUMN unit_cogs_paise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_order_items ADD COLUMN is_freebie INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_order_items ADD COLUMN returned_qty_milli INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE buyer_saved_shops ADD COLUMN shop_landmark TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE buyer_saved_shops ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE buyer_saved_shops ADD COLUMN board_valid_until INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE buyer_saved_shops ADD COLUMN upi_vpa TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE buyer_shop_prices ADD COLUMN stock_qty_milli INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE buyer_shop_prices ADD COLUMN bulk_min_qty_milli INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE buyer_shop_prices ADD COLUMN bulk_price_paise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE buyer_shop_prices ADD COLUMN min_order_milli INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE buyer_shop_prices ADD COLUMN step_qty_milli INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE buyer_shop_prices ADD COLUMN variety_tag TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE buyer_receipts ADD COLUMN original_total_paise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE buyer_receipts ADD COLUMN paid_paise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE buyer_receipts ADD COLUMN variance_reason TEXT NOT NULL DEFAULT 'EXACT'")
                db.execSQL("ALTER TABLE buyer_receipts ADD COLUMN payment_mode TEXT NOT NULL DEFAULT 'CASH'")
                db.execSQL("UPDATE buyer_receipts SET original_total_paise = total_paise, paid_paise = total_paise")

                db.execSQL("CREATE TABLE IF NOT EXISTS `draft_state` (`draft_key` TEXT NOT NULL, `payload_json` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`draft_key`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `audit_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `action_type` TEXT NOT NULL, `ref_id` TEXT NOT NULL, `summary` TEXT NOT NULL, `delta_paise` INTEGER NOT NULL, `actor_mode` TEXT NOT NULL, `created_at` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_created_at ON audit_log (created_at)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `customer_udhaar` (`customer_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `alias` TEXT NOT NULL, `area_note` TEXT NOT NULL, `balance_paise` INTEGER NOT NULL, `risk_tag` TEXT NOT NULL, `updated_at` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_udhaar_alias ON customer_udhaar (alias)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `cash_z_reports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day_key` TEXT NOT NULL, `opening_float_paise` INTEGER NOT NULL, `expected_cash_paise` INTEGER NOT NULL, `counted_cash_paise` INTEGER NOT NULL, `variance_paise` INTEGER NOT NULL, `note` TEXT NOT NULL, `created_at` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_z_reports_day_key ON cash_z_reports (day_key)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE master_products ADD COLUMN shape_code TEXT NOT NULL DEFAULT 'ROUND'")
                db.execSQL("ALTER TABLE master_products ADD COLUMN color_code TEXT NOT NULL DEFAULT 'GREEN'")
                db.execSQL("ALTER TABLE master_products ADD COLUMN size_rank INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE master_products ADD COLUMN weight_rank INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE master_products ADD COLUMN search_keywords TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE master_products ADD COLUMN is_perishable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE master_products ADD COLUMN def_bundle_milli INTEGER NOT NULL DEFAULT 250")
                db.execSQL("ALTER TABLE master_products ADD COLUMN unit_step_mode TEXT NOT NULL DEFAULT 'WEIGHT_GRAMS'")
                db.execSQL("ALTER TABLE master_products ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE seller_profile ADD COLUMN strict_stock_mode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_profile ADD COLUMN shop_type TEXT NOT NULL DEFAULT 'PRODUCE'")
                db.execSQL("ALTER TABLE seller_profile ADD COLUMN opening_cash_float_paise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_profile ADD COLUMN last_order_seq INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_profile ADD COLUMN last_backup_epoch_ms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE seller_profile SET last_order_seq = (SELECT COUNT(*) FROM seller_orders)")

                db.execSQL("ALTER TABLE seller_orders ADD COLUMN order_status TEXT NOT NULL DEFAULT 'COMPLETED'")
                db.execSQL("ALTER TABLE seller_orders ADD COLUMN round_off_paise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seller_orders ADD COLUMN utr_last4 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE seller_orders ADD COLUMN customer_id INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE seller_orders SET order_status = 'VOIDED' WHERE payment_status = 'VOID'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_seller_orders_order_status ON seller_orders (order_status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_seller_orders_customer_id ON seller_orders (customer_id)")

                db.execSQL("ALTER TABLE customer_udhaar ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pos_drafts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `slot_id` TEXT NOT NULL,
                        `product_id` INTEGER NOT NULL,
                        `custom_label` TEXT NOT NULL,
                        `qty_milli` INTEGER NOT NULL,
                        `unit_price_paise` INTEGER NOT NULL,
                        `override_line_total_paise` INTEGER,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pos_drafts_slot_id ON pos_drafts (slot_id)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `buyer_profile` (
                        `id` INTEGER NOT NULL,
                        `buyer_alias` TEXT NOT NULL,
                        `monthly_budget_paise` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shop_expenses` (
                        `expense_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `epoch_ms` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `amount_paise` INTEGER NOT NULL,
                        `is_cash` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shop_expenses_epoch_ms ON shop_expenses (epoch_ms)")
                db.execSQL(
                    """
                    INSERT INTO `shop_expenses` (`epoch_ms`, `category`, `note`, `amount_paise`, `is_cash`)
                    SELECT `created_at`, `expense_type`, `note`, `amount_paise`, 1 FROM `seller_expenses`
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun checkpointAndClose() {
            synchronized(this) {
                INSTANCE?.let { db ->
                    runCatching {
                        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
                    }
                    db.close()
                }
                INSTANCE = null
            }
        }
    }
}
