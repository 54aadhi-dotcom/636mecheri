package com.mecheri636.offline.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        BuyerReceiptEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mecheri636_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
