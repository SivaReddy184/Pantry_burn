# 🛒 PantryBurn

> **Smart Pantry Stock & Grocery Burn-Rate Tracker for Android**

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Room Database](https://img.shields.io/badge/Storage-Room%20(SQLite)-009688?style=flat-square&logo=sqlite&logoColor=white)
![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)
![Offline First](https://img.shields.io/badge/Privacy-100%25%20Offline%20First-success?style=flat-square)

**PantryBurn** is an intelligent Android grocery and pantry management app built to eliminate food waste and track household spending. By modeling daily consumption rates ("burn rates"), PantryBurn automatically calculates remaining shelf life, projects exact out-of-stock dates, and generates smart shopping carts before essentials run out.

---

## ✨ Features

### 1. 📦 Pantry Stock & Real-Time Burn Rate Tracking
* **Live Inventory Dashboard:** Track current quantity, packaging size, and unit measurements (`kg`, `g`, `L`, `ml`, `pcs`, `packs`).
* **Burn Rate Estimation:** Calculates daily consumption (`qty/day`) based on real usage logs.
* **Depletion Countdown:** Projects how many days of stock remain and provides visual status badges:
  * 🟢 **Healthy Stock** (> 7 days remaining)
  * 🟡 **Low Stock** (3–7 days remaining)
  * 🔴 **Critical / Out of Stock** (< 3 days remaining)
* **Quick Consume:** Log daily usage with one tap or specify custom amounts.

### 2. 🛒 Smart Restock Cart
* **Auto-Generated Shopping List:** Suggests restocking for items approaching depletion based on their burn rate.
* **One-Tap Check-Off:** Mark items as bought in store and record exact purchase quantity, total cost, and calculated unit price.
* **Instant Stock Sync:** Marking an item as purchased immediately updates current pantry inventory and logs the transaction.

### 3. 📊 Cost & Smart Analytics
* **Monthly Spending Breakdown:** Real-time visibility into your grocery expenditure and daily average cost.
* **Monthly Product Consumption & Burn Value Table:**
  * Shows every product tracked in your pantry.
  * Columns:
    1. **Product Name** & Category
    2. **Daily Consumption Rate** (e.g. `20 g/day`)
    3. **Month-to-Date Consumed Quantity** (calculated strictly from days consumed in the current month)
    4. **Month-to-Date Consumed Value** (`Burn Rate × Consumed Days × Unit Price`)
  * Accurate financial tracking based on actual consumption dates.

### 4. 📈 Price Trends & Deal Tracking
* **Historical Price Insights:** Monitors unit price variations across every purchase over time.
* **Best & Worst Deal Detection:** Displays **Lowest Deal**, **Highest Deal**, and **Last Purchase Price** for every item.
* **Price Fluctuation %:** Shows whether prices increased or dropped compared to previous purchases.
* **Clean 2-Decimal Precision:** Formats all monetary values and unit rates neatly to 2 decimal places.

### 5. 📜 Chronological Activity Timeline
* **Complete Audit Trail:** Unified chronological feed of all grocery actions (consumptions and restocks).
* **Smart Filter Tabs:** Easily toggle between **All Activities**, **Consumptions Only**, and **Restocks Only**.
* **Log Management:** Delete or undo accidental logs with automatic inventory adjustments.

### 6. 🔒 100% Offline & Private
* **Zero Cloud Dependency:** All pantry items, prices, and history are stored locally on your device in a high-performance **Room (SQLite)** database.
* **Zero Trackers / Zero Ads:** No analytics, no accounts, and no data leaves your phone.

---

## 📲 Download & Installation

The latest installable APK is automatically built and released on every update:

1. Go to the [**Releases**](../../releases) page of this repository.
2. Under the latest release **Assets**, download **`PantryBurn.apk`**.
3. On your Android phone, tap the downloaded file to install.
   *(If prompted by Android, enable **"Allow from this source"** or **"Install unknown apps"** in Settings).*

---

## 🛠️ Architecture & Tech Stack

* **Language:** [Kotlin](https://kotlinlang.org/)
* **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material Design 3 (M3)](https://m3.material.io/)
* **Architecture:** MVVM (Model-View-ViewModel) + Clean Architecture
* **State Management:** Kotlin Coroutines & `StateFlow` / `collectAsStateWithLifecycle`
* **Local Persistence:** [Android Room Database](https://developer.android.com/training/data-storage/room) with KSP
* **Tooling & Build:** Gradle Kotlin DSL (`build.gradle.kts`)
* **CI/CD:** GitHub Actions automated build, test verification, and automated GitHub Releases

---

## 💻 Building from Source

### Prerequisites
* **Android Studio** (Koala, Ladybug, Meerkat, or newer)
* **JDK 17**
* **Android SDK** API 34+

### Clone & Build
```bash
# 1. Clone the repository
git clone https://github.com/<your-username>/<repo-name>.git
cd <repo-name>

# 2. Build Debug APK using Gradle
./gradlew :app:assembleDebug

# 3. Output APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

### Running in Android Studio
1. Open Android Studio and select **Open** ➔ select this project directory.
2. Let Gradle sync and download dependencies.
3. Select your device or emulator in the toolbar and click **Run ▶** (`Shift + F10`).

---

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).
