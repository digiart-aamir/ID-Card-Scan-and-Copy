# 🪪 ID Card Scan & Copy

![Android Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=android&logoColor=white)

**ID Card Scan & Copy** is a robust, privacy-first Android application designed to turn your smartphone into a high-quality, portable document scanner. It allows users to easily capture, crop, and save digital copies of essential documents like ID cards, driver's licenses, and passports directly on their device.

---

## 🎯 Theme & Idea

The core philosophy behind this project is **Privacy and Utility**. Many document scanning apps upload sensitive user data (like government IDs) to cloud servers for processing, posing a massive privacy risk. 

**Our Idea:** Provide a seamless, high-quality scanning and cropping experience that operates **100% locally and offline**. What happens on your device stays on your device.

---

## 🚀 Features

* **High-Quality Scanning:** Capture crisp, readable copies of both the front and back of documents.
* **Smart Cropping:** Intuitive image cropper to adjust borders and get a perfect, professional-looking document.
* **Local Processing:** No cloud servers. No data collection. Total privacy.
* **Save & Share:** Instantly save scanned documents to the local gallery or share them via other apps.
* **Modern UI:** Built entirely with Jetpack Compose for a smooth, reactive user experience.

---

## 🛠️ Tech Stack

This project is built using modern Android development practices and libraries.

| Category | Technology / Library | Description |
| :--- | :--- | :--- |
| **Language** | Kotlin | The primary programming language for modern Android development. |
| **UI Toolkit** | Jetpack Compose | Modern declarative UI framework used for all screens and components. |
| **Material Design** | Material 3 (M3) | Provides the foundational design system, colors, and typography. |
| **Camera** | CameraX | Jetpack library used for consistent, lifecycle-aware camera integration. |
| **Image Loading** | Coil | Fast, lightweight image loading library backed by Kotlin Coroutines. |
| **Image Processing** | Android Image Cropper | Used for selecting and cropping specific bounds of the captured document. |
| **Asynchronous Ops**| Kotlin Coroutines | Used for background tasks like saving images without blocking the UI thread. |

---

## 📱 User Interface & Flow

The application follows a simple, linear flow designed to get the user in and out quickly:

1.  **Splash Screen:** A modern Android 12+ compliant splash screen.
2.  **Home Screen / Camera View:** The user is immediately presented with a camera preview (via CameraX) to capture their document.
3.  **Capture Action:** The user presses the shutter button to take a photo.
4.  **Crop Screen:** The captured image is passed to the cropping activity where the user can adjust the edges.
5.  **Result / Save:** The final cropped image is displayed. The user can save it directly to their device's Media Store (Gallery).

---

## ⚙️ Requirements & Permissions

To function correctly, the app requires the following device features and permissions:

### Hardware Requirements
* **Camera:** A functioning back camera is required to scan documents.

### Software Requirements
* **Minimum SDK:** API 27 (Android 8.1 Oreo)
* **Target SDK:** API 37 (Android 15)

### Android Permissions
| Permission | Reason |
| :--- | :--- |
| `android.permission.CAMERA` | Required to open the CameraX preview and capture document images. |
| `android.permission.WRITE_EXTERNAL_STORAGE` | (On older Android versions) Required to save the cropped images to the gallery. |

---

## 🏗️ Project Structure

The project is organized by feature and utility to ensure maintainability:

```text
app/src/main/
├── java/com/asbots/idcardscancopy/
│   ├── MainActivity.kt        # Entry point and Jetpack Compose navigation
│   └── ui/theme/              # Material 3 Theme definitions (Colors, Type, Theme)
├── playstore_assets/          # Folder containing metadata, privacy policy, and assets for Google Play release
└── build.gradle.kts           # App-level build configuration (dependencies, build types)
```

---

## 📝 How to Build & Run

1. **Clone the repository:**
   ```bash
   git clone <your-repository-url>
   ```
2. **Open in Android Studio:**
   Ensure you are using the latest stable version of Android Studio (Koala or newer recommended).
3. **Sync Gradle:**
   Allow Android Studio to download the required dependencies (Compose, CameraX, Coil, etc.).
4. **Run the App:**
   Connect a physical Android device (recommended for CameraX testing) or start an emulator and click the Run button (`Shift + F10`).

---

## 🔒 Privacy

As outlined in our `playstore_assets/3_PRIVACY_POLICY.md`, this application does not track, collect, or transmit any user data. All camera captures and image processing happen locally on the user's hardware.

---
*Developed with ❤️ using Jetpack Compose.*