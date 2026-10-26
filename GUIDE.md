# Getting TDC on Google Play: step by step

You build the app the same way as the iPhone app: on GitHub's computers, with
nothing to install on your PC. Then you upload the file to Google Play Console.

---------------------------------------------------------------------------
## 1. Put the project on GitHub (5 min)
1. github.com → **New repository** → name it `tdc-android` → **Private** → Create.
2. Unzip `TDC-Android.zip`. Open the `TDC` folder.
3. On the new repo page click **uploading an existing file**, then drag
   **everything inside** the `TDC` folder (including the `.github` folder, the
   `gradle` folder, `gradlew` and `gradlew.bat`) into the page → **Commit changes**.
   - Windows hides nothing here, but double-check that `.github` uploaded:
     the repo should show `.github/workflows/android-build.yml`.

## 2. Your upload key (already made for you)
`TDC-Android-Upload-Key.zip` holds your **upload key** (`tdc-upload.jks`) and its
password. Google uses it to know new versions really come from you.
- **Keep the zip somewhere safe** (OneDrive/Google Drive *and* a USB stick). Don't put it in the GitHub repo.
- If it's ever lost, Google can reset it (Play Console → Test and release → App integrity → request upload key reset), but that takes a few days.

## 3. Add four secrets to the repo (5 min)
Repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**:

| Name | Value |
|---|---|
| `TDC_KEYSTORE_B64` | the whole contents of `tdc-upload.jks.base64.txt` (open it in Notepad, Ctrl+A, Ctrl+C) |
| `TDC_KEYSTORE_PASSWORD` | the password in `PASSWORD.txt` |
| `TDC_KEY_ALIAS` | `tdc-upload` |
| `TDC_KEY_PASSWORD` | the same password again |

## 4. Build it (about 5–8 min)
Repo → **Actions** → **Build Android app** → **Run workflow** → version `1.0.0` → Run.
When it turns green, open the run and scroll to **Artifacts**:
- **TDC-Play-Store-1.0.0** → the `.aab` file for Google Play
- **TDC-Phone-Test-1.0.0** → an `.apk` you can install on an Android phone right now

If it turns red, open the failed step and send me the error lines; I'll fix them.

## 5. Try it on an Android phone (optional, 2 min)
Copy the `.apk` to the phone (email it to yourself, or Google Drive) → tap it →
allow "Install unknown apps" for that app when asked → Install.
Check: the logo animation, logging in, an Excel export (Roster → Export), a photo upload,
Print on an invoice, the back gesture, and airplane mode (Can't Connect screen).

## 6. Google Play developer account (one time)
1. Go to **play.google.com/console** → sign up → **$25 one-time fee**.
2. Choose **Personal** (you) or **Organization** (needs a free D-U-N-S number for the business).
3. Google verifies your ID (and phone). This can take a few days.

> **Heads-up for new Personal accounts:** before the app can go public, Google
> requires a **closed test with at least 12 testers who stay opted in for 14 days
> straight**. Studio parents and staff with Android phones are perfect testers.
> Organization accounts skip this.

## 7. Create the app in Play Console
**Create app** → App name `The Dance Collective` → Language English (US) → **App** → **Free** → accept → Create.

Then work through **Dashboard → Set up your app**:
- **Privacy policy:** `https://ckbdevs.com/tdc/privacy/` (this page is in the website update; read it over and adjust anything)
- **App access:** "All or some functionality is restricted" → add a **demo parent login** (email + password) so Google's reviewer can get in
- **Ads:** No ads
- **Content rating:** fill in the questionnaire (no violence etc.), category "Utility, Productivity, Communication or Other"
- **Target audience:** pick **13–15, 16–17 and 18+**. (If younger students will log in themselves, add the under-13 groups too. That adds Google's Families policy review.)
- **Data safety:**
  - Collects: Personal info (name, email, phone, address), Photos, Financial info (purchase history), App activity (none)
  - Shared with third parties: **No**. Encrypted in transit: **Yes**. Users can request deletion: **Yes**
  - Account deletion link: `https://ckbdevs.com/tdc/privacy/#delete`
- **Government app:** No. **Financial features:** none. **Health:** No. **News:** No.

**Store listing** (Grow → Store presence → Main store listing):
- Short description (80 chars): `Schedules, events, fundraisers & billing for The Dance Collective families.`
- Full description: what parents, students and instructors can do (classes, events, fundraiser orders and share links, billing and online payments, profile photos).
- App icon: `play-store/icon-512.png` · Feature graphic: `play-store/feature-graphic-1024x500.png`
- Phone screenshots: at least 2 (take them on the test phone: Upcoming, Fundraisers, Billing…)

## 8. Upload and test
**Test and release → Testing → Closed testing → Create track** (or use "Alpha"):
1. **Testers:** create an email list with your testers' Gmail addresses; copy the opt-in link and send it to them.
2. **Create release** → Google offers **Play App Signing**: accept (recommended).
3. Upload the `.aab` → release name `1.0.0` → Save → **Review release** → **Start rollout**.
4. Testers open the opt-in link, then install from the Play Store.

## 9. Go public
After the 14 days (personal accounts), the Dashboard shows **Apply for production**.
Answer the short questionnaire about your test, then create a **Production** release
with the same (or a newer) `.aab`. Review usually takes a few days.

## Later updates
Only needed when the app shell changes (the website updates by itself):
run the workflow again with a higher version (e.g. `1.0.1`) and upload the new `.aab`
to the same track. The build number goes up automatically.
