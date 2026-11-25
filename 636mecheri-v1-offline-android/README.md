# 636mecheri — 100% Offline Local Shop Android App (0% Network)

**Package ID:** `com.mecheri636.offline` | **Play Store Title:** `636mecheri - Offline Shop` | **License:** Free & Open-Source (₹0, Zero Ads)

## Key Highlights
- **0% Network Hard-Lock:** `INTERNET`, `WIFI`, `NETWORK_STATE`, `BLUETOOTH`, `LOCATION`, `USB`, and `AD_ID` are permanently stripped via `tools:node="remove"` and enforced by an automated `aapt2` security gate on every GitHub Actions build.
- **Equal Users (No Admin Backdoor):** Every installer (including the developer) gets the exact same two modes (**Seller Mode** & **Buyer Mode**) with instant 1-tap switching (`Seller ↔ Buyer`).
- **160 Fixed Mecheri Vegetables, Greens (Keerai) & Fruits:** Bundled offline with **160 custom Android Vector Icons** (`ic_prod_001.xml` .. `ic_prod_160.xml`). All 160 items start **hidden** when a Seller creates their shop—tap `+ Add` only on items you sell.
- **High-Visual Offline UI:** Collapsible category headers (`▲ / ▼`) with quick jump keys, small vector icons (`28dp`) that expand on touch (`96dp / 112dp`), 4 view modes, 10 sort options, multi-facet filters, and Sunlight Light / OLED Dark themes.
- **In-App 100% Offline Trust Verifier:** Live `PackageManager` permission check + live Android Kernel socket block test + APK SHA-256 checksum.

## Build Real `.apk` on GitHub Actions (No PC Needed)
1. Create a new GitHub repository named `636mecheri`.
2. Unzip `636mecheri-v1-offline-android.zip` and push all files to `main` (via Termux `git push` or GitHub mobile web).
3. Open **Actions** tab → click **Build & Verify 636mecheri 100% Offline APK** → download **`636mecheri-v1-offline.apk`** + SHA-256 checksum once the automated `aapt2` zero-network gate turns green!

## Google Play Store Data Safety Declaration
- **Does your app collect or share any user data?** → **No**
- **Contains Ads?** → **No**
- **Price:** **Free (₹0)**
- **Payments inside app?** → **No** (Cash / outside UPI only)
