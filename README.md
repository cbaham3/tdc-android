# TDC Android App (The Dance Collective)

A native Android app that opens the studio portal at **https://ckbdevs.com/tdc/**.
Every screen is the live web portal, so website updates show up in the app right
away. A new Play Store version is only needed when the app shell itself changes.

**Start with GUIDE.md**, which walks through building and publishing.

## What the app adds on top of the website
- The TDC logo drawing itself in while it loads (same as the iPhone and desktop apps), with the CKB Devs credit
- Stays logged in between launches
- Venmo, Cash App, phone numbers, email and other websites open in their own apps
- Photo picker for profile pictures and fundraiser banners
- Excel/CSV exports save to **Downloads/TDC**, with Open and Share buttons
- "Print / Save PDF" on invoices, statements and reports uses Android printing
- Back button/gesture goes back a page
- Friendly "Can't Connect" screen with Try Again when offline
- Android 10 and newer, phones and tablets; themed (monochrome) icon on Android 13+

## Files you might change
- **Web address it opens:** `START_URL` near the top of `app/src/main/java/com/ckbdevs/tdc/MainActivity.kt`
- **Name under the icon:** `app/src/main/res/values/strings.xml` (currently `TDC`)
- **App ID:** `com.ckbdevs.tdc` in `app/build.gradle`. It can't change after the first Play upload.
- **Version:** typed in when you run the GitHub workflow; the build number is automatic.

## Play Store art
`play-store/icon-512.png` (app icon) and `play-store/feature-graphic-1024x500.png`
(the banner Google requires) are ready to upload.
