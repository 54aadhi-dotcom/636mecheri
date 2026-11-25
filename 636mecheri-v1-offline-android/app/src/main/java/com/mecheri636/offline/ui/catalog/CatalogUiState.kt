package com.mecheri636.offline.ui.catalog

import com.mecheri636.offline.data.db.CatalogDisplayItem

enum class UserRole { SELLER, BUYER }
enum class AppLanguage { TAMIL, ENGLISH, BILINGUAL }
enum class AppThemeMode { SUNLIGHT_LIGHT, OLED_DARK }

enum class ViewMode(val labelEn: String, val labelTa: String) {
    ACCORDION_HEADERS("Headers (▲/▼)", "தலைப்புகள் (▲/▼)"),
    COMPACT_LIST("Compact List", "சுருக்கப் பட்டியல்"),
    GRID_2_COL("2-Col Grid", "2-நிரல் கட்டம்"),
    GRID_3_MINI("3-Col Mini", "3-நிரல் சிறிய ஐகான்")
}

enum class SortOption(val labelEn: String, val labelTa: String) {
    CATALOG_ID_ASC("Default (1→160)", "இயல்பு (1→160)"),
    NAME_EN_ASC("Name A → Z", "பெயர் A → Z"),
    NAME_EN_DESC("Name Z → A", "பெயர் Z → A"),
    NAME_TA_ASC("Tamil அ → ஃ", "தமிழ் அ → ஃ"),
    PRICE_LOW_HIGH("Price Low → High", "விலை குறைவு → அதிகம்"),
    PRICE_HIGH_LOW("Price High → Low", "விலை அதிகம் → குறைவு"),
    GRADE_A_FIRST("Grade A Fresh First", "தரம் A முதலில்"),
    STOCK_IN_FIRST("In Stock First", "இருப்பு உள்ளவை முதலில்"),
    RECENTLY_UPDATED("Recently Updated", "சமீபத்தில் மாற்றியவை"),
    MOST_ORDERED("Most Ordered", "அதிகம் வாங்கியவை")
}

enum class IconDensity(val iconDp: Int, val rowDp: Int) {
    SMALL(28, 56),
    MEDIUM(40, 68)
}

enum class ExpandStyle {
    INLINE_EXPAND,
    ZOOM_PREVIEW_CARD
}

enum class PriceBracket(val label: String, val minPaise: Long, val maxPaise: Long) {
    ALL("All ₹", 0L, Long.MAX_VALUE),
    UNDER_30("< ₹30", 0L, 2999L),
    FROM_30_TO_60("₹30–₹60", 3000L, 6000L),
    FROM_60_TO_100("₹60–₹100", 6001L, 10000L),
    ABOVE_100("> ₹100", 10001L, Long.MAX_VALUE)
}

data class CatalogFilterState(
    val searchQuery: String = "",
    val selectedCategories: Set<String> = emptySet(),
    val selectedGrades: Set<String> = emptySet(),
    val selectedUnits: Set<String> = emptySet(),
    val priceBracket: PriceBracket = PriceBracket.ALL,
    val sellerShowAddedOnly: Boolean = true,
    val buyerHidePersonalHidden: Boolean = true,
    val sortOption: SortOption = SortOption.CATALOG_ID_ASC,
    val viewMode: ViewMode = ViewMode.ACCORDION_HEADERS
)

data class VisualCustomization(
    val language: AppLanguage = AppLanguage.BILINGUAL,
    val themeMode: AppThemeMode = AppThemeMode.SUNLIGHT_LIGHT,
    val iconDensity: IconDensity = IconDensity.SMALL,
    val expandStyle: ExpandStyle = ExpandStyle.INLINE_EXPAND
)

data class CategorySection(
    val code: String,
    val titleEn: String,
    val titleTa: String,
    val items: List<CatalogDisplayItem>
)

object CatalogFilterEngine {
    private val CATEGORY_ORDER = listOf("VEG", "BEAN", "TUBER", "GREEN", "FRUIT")

    fun apply(
        raw: List<CatalogDisplayItem>,
        role: UserRole,
        filter: CatalogFilterState
    ): List<CatalogDisplayItem> {
        val q = filter.searchQuery.trim().lowercase()
        val filtered = raw.filter { item ->
            val roleVisibilityOk = if (role == UserRole.SELLER && filter.sellerShowAddedOnly) {
                item.isEnabledInShop
            } else {
                true
            }
            val queryOk = q.isEmpty() ||
                item.nameEn.lowercase().contains(q) ||
                item.nameTa.lowercase().contains(q) ||
                item.productId.toString() == q
            val catOk = filter.selectedCategories.isEmpty() || item.categoryCode in filter.selectedCategories
            val gradeOk = filter.selectedGrades.isEmpty() || item.qualityGrade in filter.selectedGrades
            val unitOk = filter.selectedUnits.isEmpty() || item.unit in filter.selectedUnits
            val priceOk = filter.priceBracket == PriceBracket.ALL ||
                (item.pricePaise in filter.priceBracket.minPaise..filter.priceBracket.maxPaise)

            roleVisibilityOk && queryOk && catOk && gradeOk && unitOk && priceOk
        }

        return when (filter.sortOption) {
            SortOption.CATALOG_ID_ASC -> filtered.sortedBy { it.productId }
            SortOption.NAME_EN_ASC -> filtered.sortedBy { it.nameEn.lowercase() }
            SortOption.NAME_EN_DESC -> filtered.sortedByDescending { it.nameEn.lowercase() }
            SortOption.NAME_TA_ASC -> filtered.sortedBy { it.nameTa }
            SortOption.PRICE_LOW_HIGH -> filtered.sortedBy { it.pricePaise }
            SortOption.PRICE_HIGH_LOW -> filtered.sortedByDescending { it.pricePaise }
            SortOption.GRADE_A_FIRST -> filtered.sortedWith(compareBy<CatalogDisplayItem> { it.qualityGrade }.thenBy { it.productId })
            SortOption.STOCK_IN_FIRST -> filtered.sortedWith(compareBy<CatalogDisplayItem> {
                when (it.stockStatus) { 1 -> 0; 2 -> 1; else -> 2 }
            }.thenBy { it.productId })
            SortOption.RECENTLY_UPDATED -> filtered.sortedByDescending { it.updatedAt }
            SortOption.MOST_ORDERED -> filtered.sortedByDescending { it.orderCount }
        }
    }

    fun groupByCategory(items: List<CatalogDisplayItem>): List<CategorySection> {
        val grouped = items.groupBy { it.categoryCode }
        return CATEGORY_ORDER.mapNotNull { code ->
            val list = grouped[code]
            if (list.isNullOrEmpty()) null else {
                val first = list.first()
                CategorySection(code, first.categoryEn, first.categoryTa, list)
            }
        }
    }

    fun formatRs(paise: Long): String = "₹${"%.2f".format(paise / 100.0)}"

    fun formatQty(milli: Long, unit: String): String {
        return if (unit == "kg") {
            if (milli < 1000L) "${milli}g" else "${"%.2f".format(milli / 1000.0).trimEnd('0').trimEnd('.')} kg"
        } else {
            "${"%.1f".format(milli / 1000.0).trimEnd('0').trimEnd('.')} $unit"
        }
    }
}
