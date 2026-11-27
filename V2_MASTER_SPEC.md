# 636mecheri v2.0 — Master Technical Specification & Handoff Blueprint

## 1. Baseline & Unbreakable Guardrails
- **App / Package:** `636mecheri` (`com.mecheri636.offline`), Kotlin + Jetpack Compose, API 36 compatible, Termux + GitHub Actions CI/CD.
- **Stable Baseline:** `v1.0.3-offline` (`versionCode = 4`, commit `bc3b063`, `2.85 MB` signed APK, Room DB v2).
- **Target Release:** `v2.0.0-offline` (`versionCode = 5`, Room DB v3). Clean up leftover `v1.0.2` intermediate artifact names in `.github/workflows/build-apk.yml`.
- **Gate 1 (0% Network / 0% Radio):** Strictly preserve ALL `tools:node="remove"` rules in `AndroidManifest.xml` (NO Internet, Wi-Fi, Bluetooth, NFC, USB, Location, Ad ID, VIBRATE). Keep `OfflineTrustVerifier` passing 100% with kernel socket `EACCES` proof. Zero external network libraries.
- **Gate 2 (16 KB ELF Alignment):** Zero new native `.so` binaries. Pure Kotlin + Android SDK only.
- **v1.0.3 Cross-Compatibility:** Never break existing `S2`, `C2`, `R2`, or `Z1:` QR decoders, and never rename existing `AppThemeMode` enum constants.
- **R8 & APK Hygiene:** Keep `minifyEnabled = true` and `shrinkResources = true`. Replace `resources.getIdentifier` with a static `IconMap` (`productId -> R.drawable.ic_prod_XXX`) so R8 never strips the 160 icons. Target compact APK (~3.2–3.5 MB).

---

## 2. Strictly Forbidden / Do NOT Use List (Eliminated Traps)
1. **NO `EncryptedSharedPreferences`** (deprecated, ~400 KB Tink bloat, Xiaomi/Poco Keystore `AEADBadTagException` crash). Keep `v1.0.3` **Salted-Hash `SharedPreferences`** in `AppLockManager`.
2. **NO Room-based live AppLock (`internal_config`)** — causes main-thread startup crashes/unlocked flash and puts lock state in SQLite.
3. **NO Base85 QR encoding** — forces ZXing into dense 8-bit Byte mode. Use RFC 9285 **Base45 (`Z2:`)**.
4. **NO 8-layer cascading filter funnel**, NO dual identical arrow rails, NO stacked 60dp side-rail + vertical sidebar, and NO hard-locked 6-card viewport.
5. **NO Unicode 14–15 emojis** (`🫚`, `🪪`, `🫛`, `🫜`, `🫧`, `🪶` render as `☐` on Android 8–12). Draw 7 Shape icons and Greens visual traits with **Compose Canvas / Vectors**.
6. **NO Tamil syllable tags** on the 29 lookalike greens (`ic_prod_077`–`105`) — syllables collide (`மு`, `அ`, `க`, `பு`) and confuse non-readers.
7. **NO hardcoded default retail prices published automatically** — starter checklists enable items at `price_paise = 0` and exclude `₹0` items from Shop QR until the seller sets today's rate.
8. **NO `batch_id` Bundle Undo** — collides with pre-existing cart items. Use a **5-second in-memory Cart Snapshot Restore** (cleared on any subsequent manual edit).
9. **NO passing `StateFlow` directly as a Compose `remember(...)` key** — always pass the collected `List`.
10. **NO overriding `Auto Lock = 0s (Immediate)`** with the 30-second app-switch grace period.
11. **NO hard-deleting** products, orders, or Udhaar customers — use Soft Delete (`is_archived = 1`) and `VOIDED` order audit status.
12. **NO external TTS IPC** (drop both Tamil & English TTS — use pure `ToneGenerator` beeps + `LocalHapticFeedback` only).
13. **NO Apache POI / iText / OpenPDF**, **NO NFC in core 0%-radio APK**, and **NO importing external `.pdf`/`.xlsx`/`.csv`/`.docx` files**.

---

## 3. Unified Room Database `MIGRATION_2_3` (Single Migration, Safe Defaults)
All added columns MUST have explicit SQL `DEFAULT` values so upgraded `v1.0.3` installs never hit `NOT NULL` crashes.

### A. Table Updates
- **`master_products` additions:**
  - `shape_code` (`TEXT NOT NULL DEFAULT 'ROUND'`)
  - `color_code` (`TEXT NOT NULL DEFAULT 'GREEN'`)
  - `size_rank` (`INTEGER NOT NULL DEFAULT 2`) — 1..3
  - `weight_rank` (`INTEGER NOT NULL DEFAULT 2`) — 1..3
  - `search_keywords` (`TEXT NOT NULL DEFAULT ''`) — Tanglish phonetic aliases only (engine matches `nameTa || nameEn || productId || searchKeywords`)
  - `is_perishable` (`INTEGER NOT NULL DEFAULT 0`) — Evening Mode `-20%` flag
  - `def_bundle_milli` (`INTEGER NOT NULL DEFAULT 250`) — Smart per-item recipe pack qty (e.g., 50g chilli/ginger, 1000g pumpkin, 1000milli=1bn keerai)
  - `unit_step_mode` (`TEXT NOT NULL DEFAULT 'WEIGHT_GRAMS'`):
    - `WEIGHT_GRAMS`: `kg` (exact grams + `100g`/`250g`/`500g`/`1kg`)
    - `HALF_ALLOWED_PC`: TN half-piece items (`#16`, `#17`, `#26`, `#27`, `#73`, `#122`, `#125` -> `min = 500L`, `step = 500L`)
    - `WHOLE_ONLY`: `bn`, `pk`, indivisible `pc` (`#71`, `#105`, `#141` -> `min = 1000L`, `step = 1000L`)
  - `is_archived` (`INTEGER NOT NULL DEFAULT 0`) — Soft Delete support for Custom POS items (`10001+`)
- **`seller_profile` additions:**
  - `strict_stock_mode` (`INTEGER NOT NULL DEFAULT 0`) — `0` allows selling when `stock_qty_milli == 0` unless `stock_status == 0`
  - `shop_type` (`TEXT NOT NULL DEFAULT 'PRODUCE'`) — reserved for `PRODUCE | EATERY | PROVISION | GENERAL`
  - `opening_cash_float_paise` (`INTEGER NOT NULL DEFAULT 0`)
  - `last_order_seq` (`INTEGER NOT NULL DEFAULT 0`) — monotonic receipt sequence (no duplicate `ORD` IDs after void/prune)
  - `last_backup_epoch_ms` (`INTEGER NOT NULL DEFAULT 0`) — powers 7-day offline backup reminder
- **Order & Udhaar Accounting Columns:**
  - `order_status` (`TEXT NOT NULL DEFAULT 'COMPLETED'` — supports atomic `VOIDED` reversal without deleting rows)
  - `round_off_paise` (`INTEGER NOT NULL DEFAULT 0`) — keeps small waivers (`₹103 -> ₹100`) strictly out of Udhaar
  - `utr_last4` (`TEXT NOT NULL DEFAULT ''`) — optional manual UTR check for UPI bills `> ₹500`
  - `customer_id` link on `UDHAAR` / `SPLIT` orders inside a single `@Transaction`

### B. New Tables in `MIGRATION_2_3`
- **`pos_drafts`:** Persistent multi-slot cart rows (`slot_id` 1..3 for Produce, extensible to T1..T10 for Eateries), `product_id`, `custom_label` (for `➕ Misc ₹`), `qty_milli`, `unit_price_paise`, `override_line_total_paise` (`INTEGER DEFAULT NULL` — locks Kooru `₹10/₹20` totals with zero rounding drift; cleared to `NULL` if weight is manually edited).
- **`buyer_profile`:** Stores sanitized buyer alias (stripped of `|`, `:`, `,`, newline; max 12 safe chars) inside Room so `.m636` backups preserve it.
- **`shop_expenses`:** Local daily expense ledger (`expense_id`, `epoch_ms`, `category`, `note`, `amount_paise`, `is_cash`) for true Net Profit & Cash Z-Report reconciliation.

### C. Upgrade Hydration & Namespaced IDs
- **One-Time Metadata Hydrator:** Gated by a persisted `catalog_meta_v3_done` flag; runs batch `UPDATE master_products SET ... WHERE product_id = ?` once on upgraded `v1.0.3` installs without touching `seller_shop_items` prices or stock.
- **Namespaced ID Blocks:** `1–160` Produce (immutable for QR compat), `201–450` Eatery (`v2.1`), `501–900` Provision (`v2.2`), `10001+` Custom Shop POS Items (CRUD + Soft Delete).

---

## 4. Core Architecture & Feature Blueprint (`v2.0`)

### A. QR Codec, Scanner & CameraX Fixes
- **`Z2:` RFC 9285 Base45:** Add optional `Z2:` encoder/decoder (+ whitelist `"Z2:"` in `QrFirewall`) keeping 5.5-bit Alphanumeric QR mode while preserving plain `C2` compatibility for `v1.0.3` sellers.
- **Multi-Frame Scanner (`OfflineQrScannerScreen.kt`):** Wire `MultiFrameQrAssembler` with session-ID safety and `"Frame X of Y"` progress UI. Copy YUV luminance bytes safely respecting `rowStride` and `pixelStride` using a reused buffer.
- **CameraX Teardown Safety:** Inside `DisposableEffect`, ALWAYS call `cameraProvider.unbindAll()` BEFORE `executor.shutdown()`.
- **Scanner Torch (`🔦`):** 1-tap CameraX torch toggle for night stalls and dark shelves (zero new permissions).

### B. 114 Mozhi Muthal / 26 A–Z Rotary Wheel + Adaptive Grid
- **Reusable `MozhiMuthalWheel.kt`:** Used across Seller POS, Seller Catalog, Buyer Browse, and Udhaar Customer Ledger.
  - **Exact 114 Tamil Mozhi Muthal Map (11 Parent Groups):**
    - `உயிர் (12)`: அ, ஆ, இ, ஈ, உ, ஊ, எ, ஏ, ஐ, ஒ, ஓ, ஔ
    - `க (12)`, `ச (12)`, `த (12)`, `ந (12)`, `ப (12)`, `ம (12)`, `ய (12)`, `வ (12)`, `ஞ (4: ஞ, ஞா, ஞெ, ஞொ)`, `ங (2: ங, ஙா)`
    - Dynamic **`பிற`** group shown automatically only if active items start with loanword/Grantha letters (`ராகி`, `லெமன்`, `ஜவ்வரிசி`, etc.).
  - **English Mode:** 26 `A–Z` letters.
  - **Hybrid Scrolling + Clicking + `(↑, ↓, ←, →)` Layout:**
    - Top bar has 1-tap switch **`[🎡 அ/A Wheel]` | `[⭕ வடிவம் Shape]`** (non-reader friendly).
    - **2 Compact Rotary Wheels** (`LazyColumn` + `rememberSnapFlingBehavior`, bounded, active-only items with count badges): Left = `வரிசை` (or Category in EN), Middle = active starting letter (`கொ` -> exact Tamil grapheme matcher, never raw UTF-16 `.first()`).
    - **Right Compact D-Pad (`↑, ↓, ←, →`):** `←`/`→` switches focus across Left Wheel / Right Wheel / Grid; `↑`/`↓` steps cleanly by 1 slot/page. Tapping faded above/below wheel items also steps `↑`/`↓`. Optional hardware **Volume Up / Down** stepping toggle for true wet-hand mode.
    - **Full-Width Adaptive Grid Below:** `LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 120.dp))` with `88.dp` bottom padding (Android Large Font safe) + compact bottom-right floating `[🔼 Prev] [🔽 Next]` stepping by `visibleItemsInfo.size.coerceAtLeast(2)`. All wheel filtering is 100% in-memory on settled scroll.
- **Canvas Icons & Visual Markers:** 7 pure Canvas Shape icons + minimalist Canvas traits on lookalike greens (`ic_prod_077`–`105`, e.g., red stem for Thandu Keerai, white flower for Agathi) + static `IconMap` for R8.

### C. Walk-In Hybrid POS (Modular Files < 350 Lines Each)
Split cleanly across `SellerSpeedPosScreen.kt`, `PosSlotBar.kt`, `PosWeightKeypad.kt`, `PosCheckoutSheet.kt`:
- **Persistent 3 Slots (`pos_drafts`):** Auto-saved every tap across app restarts / GPay checks.
- **Auto-Learned Top 12 Strip:** Defaults to 12 staples on day 1, then ranks by shop's own 30-day sales frequency.
- **1-Tap `➕ இதர (Misc ₹)`:** Fast unlisted item / carry bag entry.
- **Hybrid Weight + Tare + Kooru:** Quick `🔼/🔽` steps (`100g`/`250g`/`500g`/`1kg`) + tap weight for **Numeric Keypad Overlay** (`345g`, `1.645kg`) with optional **Basket Tare (`-150g`/`-200g`)** + Kooru buttons (`₹10/₹20/₹30`) hard-locked via `override_line_total_paise`.
- **Morning Rate Sweep & Evening Mode:**
  - Morning Sweep updates retail `price_paise`, `cost_paise`, and `Out Today` status; `₹0` items never publish to Shop QR.
  - Evening `-20%` on `is_perishable` items applies to `unitPricePaise` **at checkout time** (accurate `DailyProfitReport` & COGS), never mutates base prices, and auto-resets at local midnight (`YYYYMMDD` check).
- **Checkout, Cash Reconciliation, Void & Giant Buyer View:**
  - `Cash Received` input -> giant `"Give Change: ₹XX"` readout; unified `[CASH]`, `[UPI]`, `[UDHAAR]`, and `[SPLIT]` modes; `UDHAAR` Confirm disabled until `customerId` is picked/added; optional 4-digit UTR field for UPI bills `> ₹500`.
  - **Atomic Void Order:** Marks order `VOIDED`, reverses stock and Udhaar inside `@Transaction`.
  - **Giant Buyer Bill Flip View (`👁️`):** Full-screen itemized Tamil list + `44sp` total + UPI QR to tilt toward walk-in buyers.
  - **IST Midnight Date-Range SQL `SUM`:** Local `Calendar.getInstance()` (`00:00:00.000`–`23:59:59.999`), removes `LIMIT 200` cap, adds Opening Float + Expenses + Refunds to Cash Z-Report.
  - **Offline Clock-Drift Guard:** Warn immediately if `System.currentTimeMillis() < lastSavedOrderTimestampMs`.
  - **Selective `FLAG_KEEP_SCREEN_ON`** on POS & active QR screens only.
  - **Pure Offline Audio:** `ToneGenerator` (1 beep add, 2 beeps paid, error buzz; wrapped safely against OEM `RuntimeException`) + `LocalHapticFeedback`.

### D. Buyer Mode Upgrades
- **Pre-Shop Home Market List:** Browse all 160 items before scanning any shop; on `S2` scan, bind prices, keep unavailable items at low opacity (never delete), show Shop QR freshness timestamp, and show multi-shop savings only if comparison snapshot `< 48 hours` old.
- **Contextual Recipe Bundles:** Use per-item `def_bundle_milli` + **5-second Cart Snapshot Undo FAB** (restores exact prior cart; cleared immediately on any manual edit).
- **Async QR & Live SOS Fix:** 500ms debounced QR bitmap generation on `Dispatchers.Default` with remembered `Bitmap` in `BuyerCartAndQrScreen`. Pass collected `List<BuyerCartItemEntity>` into `BatterySosRelayPanel` as `remember` key.
- **Unified Totals & Buyer Value:** Exact half-up paise + `resolveEffectiveUnitPrice` bulk pricing; thin floating Budget Pill; personal offline monthly expense summary from scanned `R2` receipts + reusable saved shopping templates.

### E. Admin Settings Control Panel, AppLock & Themes
- **Role-Based Salted-Hash AppLock (`SharedPreferences`):**
  - **Staff Mode:** Fast billing only (POS, Buyer QR scan, Cash/UPI checkout).
  - **Admin Mode (Owner PIN/Pattern/Passphrase):** Unlocks Settings Control Panel, Price Edits, Custom POS Items (`10001+`) CRUD (Soft Delete), Void Order, Profit/Z-Reports, and BackupVault.
  - **Grace Period Rule:** 30-second grace period when switching to GPay/SMS — **strictly ignored** if user set `Auto Lock = 0s (Immediate)`.
- **Modular Feature Control Panel:** 3 Shop Presets (`Simple Cart` / `Standard Veg` / `Full Pro`) + 8 individual feature switches (hide unused UI for peak speed) + editable Kooru presets / Evening `%` / Tare + 1-tap **Reset Defaults**.
- **AMOLED-Safe Sunlight Theme:** Preserve `AppThemeMode` enum names; background `#F5F5F5`, `#FFFFFF` cards, `#E0E0E0` border, forced dark status bar icons (`WindowInsetsControllerCompat`); retain `OLED_DARK`.

### F. Multi-Size PDF Printing & Strict Export-Only / App-Import Storage
- **Multi-Size Local Printing (0 Permissions):** Android `PdfDocument` + `PrintManager` using `StaticLayout` (proper Tamil glyph shaping) for **58mm**, **80mm**, **A5**, **A4**, and printable QR Posters/Cards.
- **Lane 1 — One-Way Export-Only (`ACTION_CREATE_DOCUMENT` + `EXTRA_LOCAL_ONLY`):** `.pdf` and UTF-8 BOM (`\uFEFF`) `.csv` in `v2.0` (pure-Kotlin `.xlsx` & `.docx` in `v2.1`). Zero import parsers for external office/CSV/PDF files.
- **Lane 2 — App-Created Import-Only (`.m636` / `.m636acc`):**
  - Export: `PRAGMA wal_checkpoint(TRUNCATE)` -> Magic Header + Schema Version + AES-256-GCM authenticated payload -> update `last_backup_epoch_ms`.
  - Restore: Decrypt to temp file -> verify Magic Header, GCM Auth Tag, SQLite header `user_version` (bytes `60..63` <= current DB version), and `PRAGMA integrity_check` -> delete live `-wal` and `-shm` -> atomic swap -> safe relaunch dialog (no abrupt `Process.killProcess()` failure on Android 14–16).

---

## 5. Reserved Phased Roadmap (Post-`v2.0`)
- **`v2.1` (Eateries + Accounting/Budget Suite):** `assets/packs/eatery_v1.tsv` (`201–450`), Dine-In/Parcel + Fee, 6–10 Table/Token slots, Portion sizes (`Half/Full/Set`), Meal-Time switcher, Daily Token counter, Pure-Kotlin `.xlsx`/`.docx` exporter, offline Accounting & Budget Planner (`.m636acc`), Two-Sided Udhaar Sync QR (`U1:`).
- **`v2.2` (Maligai / Provision Stores):** `assets/packs/provision_v1.tsv` (`501–900`), ZXing 1D Barcode Scanner (`EAN-13`/`EAN-8`/`UPC-A`/`CODE-128`), Scan-to-Learn `product_barcodes` with **Multi-MRP per barcode**, Low-Stock Reorder list, Batch Expiry (`MM/YY`), offline `.m636pack` catalog sharing.

---

## 6. Live Build Checkpoints & New-Chat Handoff
- [ ] **Step 1:** Unified `MIGRATION_2_3` (`master_products`, `seller_profile`, `pos_drafts`, `buyer_profile`, `shop_expenses`, one-time upgrade `UPDATE`) + `Z2:` Base45 + CameraX `unbindAll()`/YUV/Torch fix.
- [ ] **Step 2:** `MozhiMuthalWheel` (114 Tamil + `பிற`, 26 EN, `(↑, ↓, ←, →)`, Volume keys) + `GridCells.Adaptive(120.dp)` + Canvas Shape/Greens + static `IconMap`.
- [ ] **Step 3:** Modular Walk-In POS (`pos_drafts`, Auto Top 12, `Misc ₹`, Numeric+Tare Keypad, Kooru Lock, Evening `-20%`, Void Order, Giant Buyer Bill, IST Z-Report, `ToneGenerator`).
- [ ] **Step 4:** Buyer Mode (Home List, `< 48h` Guard, Smart Bundles + 5s Snapshot Undo, 500ms Async QR, live `List` SOS fix, Monthly Expense tracker).
- [ ] **Step 5:** Admin Settings Control Panel + Staff/Owner AppLock (`0s` override) + Multi-Size `PdfDocument`/UTF-8 CSV Export + BackupVault `TRUNCATE`/`user_version`/`-wal` guard + AMOLED Sunlight Theme + `build-apk.yml` cleanup + `v2.0.0-offline` tag.

### New-Chat Handoff Prompt
Paste `V2_MASTER_SPEC.md` + `git log -1 --oneline && git status -s`, then ask:
> "Follow V2_MASTER_SPEC.md strictly (especially Section 2 Do NOT Use). Inspect relevant files read-only first, then execute the next unchecked [ ] step without breaking v1.0.3 compatibility or Gate 1/Gate 2."
