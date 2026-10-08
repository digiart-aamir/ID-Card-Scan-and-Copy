# Android App Release Checklist

Follow these steps to safely build and publish your app to the Play Store.

## 1\. Prepare Your App

* \[x] Enabled `isMinifyEnabled` and `isShrinkResources` in `app/build.gradle.kts` to reduce app size and protect your code.
* \[ ] Make sure your `versionCode` and `versionName` in `app/build.gradle.kts` are correct before generating the build.

  * *Note: Every time you upload a new version to the Play Store, you must increment the `versionCode`.*
* \[ ] Remove any unused logging or debug statements (if applicable).

## 2\. Generate a Keystore

To sign your app, you need a Keystore file. **Keep this file extremely safe! If you lose it, you will not be able to update your app.**

1. In Android Studio, go to **Build > Generate Signed Bundle / APK...**
2. Select **Android App Bundle** (Google prefers AAB files for the Play Store).
3. Under **Key store path**, click **Create new...**
4. Choose a safe path (e.g., inside a secure folder on your PC, *not* in the public repo).
5. Fill in the passwords and alias (e.g., `upload\\\_key`), and remember them!

## 3\. Build the Signed App Bundle (AAB)

1. Proceed with the **Generate Signed Bundle / APK** wizard.
2. Select the **release** build variant.
3. Click **Finish**. Android Studio will generate an `.aab` file in the `app/release` folder.

## 4\. Google Play Console Setup

1. Go to [Google Play Console](https://play.google.com/console/) and create an account (requires a one-time $25 fee if you haven't yet).
2. Click **Create App**.
3. Go to **Store Presence > Main Store Listing** and fill in the details using the `1\\\_PLAYSTORE\\\_METADATA.md` file.
4. Go to **App Content** and fill out the required forms (Privacy Policy, Ads, Content Rating, Target Audience, Data Safety).

   * **Data Safety Note:** Your app uses the Camera. Make sure you declare that you request Camera permissions, but specify if the data leaves the device or stays locally.

## 5\. Upload \& Publish

1. Go to **Testing > Closed Testing** (or **Production** if you want to publish directly).
2. Click **Create new release**.
3. Upload the `.aab` file you generated in Step 3.
4. Add release notes.
5. Review and roll out!

