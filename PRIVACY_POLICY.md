# Privacy Policy for NutriLog

**Last updated:** November 3, 2026

NutriLog ("we", "our", or "the app") is committed to protecting your privacy. This Privacy Policy explains how our application collects, uses, and safeguards your information when you use NutriLog.

---

### 1. Information We Collect and How We Use It

#### A. Nutrition & Health Logs (Local Storage)
* **What is collected:** Meals logged, food items, macro nutrient estimates (calories, protein, carbs, fats, fiber), daily water intake, and personal target goals.
* **Storage:** All your nutrition logs and targets are stored locally on your device in a secure SQLite database (Room). We do not collect or sell your personal dietary data.

#### B. Camera & Images
* **Camera Permission (`android.permission.CAMERA`):** Used exclusively when you choose to scan a barcode, take a photo of a meal/recipe, or scan a Nutrition Facts label.
* **Processing:** Images are processed in real-time to detect barcodes via Google Play Services ML Kit or analyzed using Google Gemini (via Firebase AI Logic) to extract ingredient and macronutrient information. Images are not stored on external servers or used for advertising.

#### C. Google Drive Cloud Backup & Auto-Sync
* **Storage Access:** When you use Google Drive Backup or Wi-Fi Auto-Sync, your nutrition database is exported directly to your private Google Drive account using Android's Storage Access Framework (SAF) and Google Auto Backup.
* **Privacy:** We cannot view, access, or share your Google Drive files. The sync operates strictly between your device and your personal Google account.

#### D. Network & Wi-Fi State (`android.permission.ACCESS_NETWORK_STATE`, `android.permission.INTERNET`)
* Used to detect whether your device is connected to an active Wi-Fi network to execute automatic Google Drive uploads and prevent unwanted data transfers over cellular networks.

---

### 2. Third-Party Services
NutriLog utilizes reputable third-party services to provide core functionality:
* **Google Play Services (ML Kit):** On-device barcode scanning.
* **Firebase AI Logic & Google Gemini API:** Natural language food estimation and multimodal meal/recipe analysis.
* **OpenFoodFacts:** Free open-source food product database for barcode UPC lookups.

Each of these providers maintains their own privacy standards and compliance with applicable data protection laws.

---

### 3. Data Retention and Deletion
* You retain complete ownership of your data.
* You can delete individual log entries, custom foods, or your entire database directly within the app at any time.
* Uninstalling the application or clearing app storage deletes all locally stored data immediately.

---

### 4. Children’s Privacy
NutriLog does not knowingly collect personally identifiable information from children under the age of 13.

---

### 5. Changes to This Privacy Policy
We may update this Privacy Policy from time to time. Any updates will be reflected in this document with a revised "Last updated" date.

---

### 6. Contact Us
If you have any questions or feedback regarding this Privacy Policy, please contact:
* **Developer:** Mike Jaron
* **Email:** MikeJaron@gmail.com
