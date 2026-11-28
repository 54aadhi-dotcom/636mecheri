package com.mecheri636.offline.ui.catalog

import android.content.Context

enum class ShopPreset(val labelEn: String, val labelTa: String) {
    SIMPLE_CART("Simple Cart", "எளிய கடை"),
    STANDARD_VEG("Standard Veg", "காய்கறி கடை"),
    FULL_PRO("Full Pro", "முழு வசதி")
}

data class ShopFeatureConfig(
    val preset: ShopPreset = ShopPreset.STANDARD_VEG,
    val walkInPosEnabled: Boolean = true,
    val morningRateSweepEnabled: Boolean = true,
    val eveningModeEnabled: Boolean = true,
    val kooruButtonsEnabled: Boolean = true,
    val tareWeightEnabled: Boolean = true,
    val udhaarLedgerEnabled: Boolean = true,
    val soundFeedbackEnabled: Boolean = true,
    val buyerFlipViewEnabled: Boolean = true,
    val kooruPreset1Rs: Int = 10,
    val kooruPreset2Rs: Int = 20,
    val kooruPreset3Rs: Int = 30,
    val eveningDiscountPercent: Int = 20,
    val tarePreset1Grams: Int = 150,
    val tarePreset2Grams: Int = 200
) {
    companion object {
        fun forPreset(preset: ShopPreset): ShopFeatureConfig = when (preset) {
            ShopPreset.SIMPLE_CART -> ShopFeatureConfig(
                preset = preset, walkInPosEnabled = true, morningRateSweepEnabled = false,
                eveningModeEnabled = false, kooruButtonsEnabled = false, tareWeightEnabled = false,
                udhaarLedgerEnabled = false, soundFeedbackEnabled = true, buyerFlipViewEnabled = false
            )
            ShopPreset.STANDARD_VEG -> ShopFeatureConfig(preset = preset)
            ShopPreset.FULL_PRO -> ShopFeatureConfig(
                preset = preset, walkInPosEnabled = true, morningRateSweepEnabled = true,
                eveningModeEnabled = true, kooruButtonsEnabled = true, tareWeightEnabled = true,
                udhaarLedgerEnabled = true, soundFeedbackEnabled = true, buyerFlipViewEnabled = true
            )
        }
    }
}

class ShopFeatureConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("mecheri636_features", Context.MODE_PRIVATE)

    fun load(): ShopFeatureConfig = ShopFeatureConfig(
        preset = runCatching { ShopPreset.valueOf(prefs.getString("preset", "STANDARD_VEG") ?: "STANDARD_VEG") }.getOrDefault(ShopPreset.STANDARD_VEG),
        walkInPosEnabled = prefs.getBoolean("f_pos", true),
        morningRateSweepEnabled = prefs.getBoolean("f_morning", true),
        eveningModeEnabled = prefs.getBoolean("f_evening", true),
        kooruButtonsEnabled = prefs.getBoolean("f_kooru", true),
        tareWeightEnabled = prefs.getBoolean("f_tare", true),
        udhaarLedgerEnabled = prefs.getBoolean("f_udhaar", true),
        soundFeedbackEnabled = prefs.getBoolean("f_sound", true),
        buyerFlipViewEnabled = prefs.getBoolean("f_flip", true),
        kooruPreset1Rs = prefs.getInt("kooru_1", 10).coerceIn(1, 500),
        kooruPreset2Rs = prefs.getInt("kooru_2", 20).coerceIn(1, 500),
        kooruPreset3Rs = prefs.getInt("kooru_3", 30).coerceIn(1, 500),
        eveningDiscountPercent = prefs.getInt("eve_pct", 20).coerceIn(5, 70),
        tarePreset1Grams = prefs.getInt("tare_1", 150).coerceIn(10, 2000),
        tarePreset2Grams = prefs.getInt("tare_2", 200).coerceIn(10, 2000)
    )

    fun save(cfg: ShopFeatureConfig) {
        prefs.edit()
            .putString("preset", cfg.preset.name)
            .putBoolean("f_pos", cfg.walkInPosEnabled)
            .putBoolean("f_morning", cfg.morningRateSweepEnabled)
            .putBoolean("f_evening", cfg.eveningModeEnabled)
            .putBoolean("f_kooru", cfg.kooruButtonsEnabled)
            .putBoolean("f_tare", cfg.tareWeightEnabled)
            .putBoolean("f_udhaar", cfg.udhaarLedgerEnabled)
            .putBoolean("f_sound", cfg.soundFeedbackEnabled)
            .putBoolean("f_flip", cfg.buyerFlipViewEnabled)
            .putInt("kooru_1", cfg.kooruPreset1Rs.coerceIn(1, 500))
            .putInt("kooru_2", cfg.kooruPreset2Rs.coerceIn(1, 500))
            .putInt("kooru_3", cfg.kooruPreset3Rs.coerceIn(1, 500))
            .putInt("eve_pct", cfg.eveningDiscountPercent.coerceIn(5, 70))
            .putInt("tare_1", cfg.tarePreset1Grams.coerceIn(10, 2000))
            .putInt("tare_2", cfg.tarePreset2Grams.coerceIn(10, 2000))
            .apply()
    }

    fun resetDefaults(): ShopFeatureConfig {
        val def = ShopFeatureConfig.forPreset(ShopPreset.STANDARD_VEG)
        save(def)
        return def
    }
}
