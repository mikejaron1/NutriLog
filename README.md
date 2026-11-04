# NutriLog 🥗

A minimalist, high-speed nutrition, macronutrient, and water tracking application. Built as both an **offline-first Progressive Web App (PWA)** deployed instantly via GitHub Pages and a **native Android app** built with Jetpack Compose.

---

## 📱 How to Get NutriLog on Your Phone (PWA via GitHub Pages)

NutriLog is designed to be installed directly to your phone's home screen without going through the Google Play Store or Apple App Store.

### 1. Enable GitHub Pages (One-Time Setup)

1. Open your repository on GitHub: [`https://github.com/mikejaron1/NutriLog`](https://github.com/mikejaron1/NutriLog).
2. Click **Settings** (top tabs) $\rightarrow$ select **Pages** in the left sidebar.
3. Under **Build and deployment**:
   * **Source**: Select **GitHub Actions**.
4. Go to the **Actions** tab $\rightarrow$ select the **Deploy PWA to GitHub Pages** workflow $\rightarrow$ click **Run workflow** (or simply push any commit to `main`).
5. Your live app URL will be available at:
   > **`https://mikejaron1.github.io/NutriLog/`**

---

### 2. Install to Your Phone's Home Screen

Open **`https://mikejaron1.github.io/NutriLog/`** in your mobile browser:

* **Android (Chrome)**:
  * Tap the in-app **Install** button or tap the browser menu (**⋮**) in the top right.
  * Select **Install app** or **Add to Home screen**.
* **iPhone (Safari)**:
  * Tap the **Share** button (the square with an upward arrow at the bottom).
  * Scroll down and tap **Add to Home Screen**, then tap **Add**.

The **NutriLog** icon will appear on your phone's home screen. When launched, it runs full-screen like a native app, works completely offline, and manages its own background updates.

---

### 3. Instant Background Updates (~30 Seconds)

* Every time you push a change to the `main` branch, the GitHub Actions workflow automatically updates GitHub Pages in **~25–30 seconds**.
* When you open or bring NutriLog to the foreground on your phone, the Service Worker (`sw.js`) checks for new files.
* If a new version is detected, it downloads the assets in the background and displays an immediate **"New version available! [Update now]"** banner. Tapping it switches to the new version in under a second.

---

## ⚡ Key Features

* **Daily Food Diary**:
  * Categorized meals: **Breakfast**, **Lunch**, **Dinner**, and **Snacks**.
  * Visual progress rings for **Calories**, **Protein**, **Carbs**, and **Fat**.
  * Dynamic calorie remaining calculations against your daily target.

* **Water Tracker in Ounces (OZ)**:
  * Quick-tap logging buttons for **+8 oz**, **+16 oz**, and **-8 oz**.
  * Custom fluid ounce input.
  * Daily water target in ounces with progress bar.

* **Dynamic Macro-Calorie Verification**:
  * Real-time calculation: `(Protein × 4) + (Carbs × 4) + (Fat × 9) = Target Calories`.
  * If the entered macros do not equal your calorie target, an automatic warning banner appears with a helpful reminder:
    > ⚠️ **Macro mismatch**: Protein (4 kcal/g) + Fat (9 kcal/g) + Carbs (4 kcal/g) ≠ Total Calories.

* **Interactive History & Charts**:
  * View 7-day, 14-day, or 30-day nutrition trends.
  * Switch between calorie bar charts and line charts.
  * Average daily calories, protein, and water consumption KPIs.

* **Food Library & Barcode Scanner**:
  * Built-in staple foods database.
  * Custom food item creation (calories, protein, carbs, fat, fiber).
  * Barcode lookup via OpenFoodFacts API.

* **Data Privacy & Backup**:
  * 100% device-local storage.
  * One-tap JSON export (`Export Backup`) and import (`Import Backup`) to save or transfer your logs anytime.

---

## 🛠️ Repository Architecture

```text
NutriLog/
├── .github/workflows/
│   └── deploy-pwa.yml          # GitHub Pages automated deployment workflow
├── pwa/                         # Progressive Web App assets (served by GitHub Pages)
│   ├── index.html               # Main application interface
│   ├── style.css                # Responsive Material Design 3 stylesheet
│   ├── app.js                   # Application state, charts, and service worker handling
│   ├── manifest.webmanifest     # Standalone PWA installation manifest
│   ├── sw.js                    # Offline caching and update worker
│   └── icons/                   # App launcher icons (192x192, 512x512)
├── docs/                        # Mirror of PWA files for branch-based Pages hosting
├── app/                         # Native Android project (Jetpack Compose & Kotlin)
├── build.gradle.kts             # Root Gradle build configuration
└── settings.gradle.kts          # Gradle settings
```

---

## 📄 License
This project is open source and available under the MIT License.
