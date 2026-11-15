# F-Droid Distribution & Release Sync Guide

Wupp supports three F-Droid distribution paths, all kept in sync from Git tags and `fastlane/metadata/android/en-US/`:

1. **Self-hosted F-Droid Repo (GitHub Pages)** — `.github/workflows/fdroid-repo.yml`
2. **IzzyOnDroid** — signed `fdroid` flavor APKs from GitHub Releases
3. **Official F-Droid (`fdroiddata`)** — pure source builds via `fdroid/com.wupp.app.yml`

---

## 1. Self-Hosted F-Droid Repo Setup (Recommended for Instant Updates)

Every time `Release APK` succeeds (or when you manually trigger **Sync F-Droid Repo** in Actions), `.github/workflows/fdroid-repo.yml` downloads your signed release APKs + `fastlane` metadata, builds an F-Droid repository index (`index-v2.json` / `index-v1.jar`), and publishes it to GitHub Pages:

`https://hanzalaramz.github.io/Wupp/fdroid/repo`

### One-time setup

1. Generate a persistent F-Droid repo signing key and upload its secrets to GitHub (never rotate this key after publishing, or existing F-Droid users will get a signature mismatch):

   **Windows (PowerShell):**
   ```powershell
   .\scripts\setup-fdroid-repo-key.ps1
   ```

   **macOS / Linux:**
   ```bash
   ./scripts/setup-fdroid-repo-key.sh
   ```

2. In GitHub **Settings -> Pages**, set **Source** to **GitHub Actions**.
3. Run the **Sync F-Droid Repo** workflow manually once (`Actions -> Sync F-Droid Repo -> Run workflow`) to index existing releases right away. Future `v*` tag pushes will sync automatically after `Release APK` finishes.

---

## 2. Cutting a Synced Release

Use the release helper so `gradle.properties`, `CHANGELOG.md`, `fastlane/metadata/android/en-US/changelogs/*.txt`, and the Git tag always stay in sync:

```powershell
# Windows
.\scripts\release.ps1 -Version 0.2.1
```

```bash
# macOS / Linux
./scripts/release.sh 0.2.1
```

When you push the commit and tag (`git push origin main && git push origin v0.2.1`):
- `.github/workflows/release.yml` builds `whatsbridge.aar` from source, builds & signs both `github` and `fdroid` flavor APKs, extracts release notes from `CHANGELOG.md`, and publishes the GitHub Release.
- `.github/workflows/fdroid-repo.yml` runs automatically right after and updates your GitHub Pages F-Droid repository.

---

## 3. Flavor Differences (`github` vs `fdroid`)

- **`github` flavor:** Includes `REQUEST_INSTALL_PACKAGES` and the in-app GitHub Releases bottom-sheet updater (`BuildConfig.ENABLE_SELF_UPDATE = true`). Outputs `Wupp-<ver>-arm64-v8a.apk` and `Wupp-<ver>-universal.apk`.
- **`fdroid` flavor:** Strips `REQUEST_INSTALL_PACKAGES`, disables the in-app APK self-updater (`BuildConfig.ENABLE_SELF_UPDATE = false`), and turns off AGP `dependenciesInfo`. Outputs `Wupp-<ver>-fdroid-arm64-v8a.apk` and `Wupp-<ver>-fdroid-universal.apk`.

---

## 4. Submitting to Official F-Droid / IzzyOnDroid

- **IzzyOnDroid:** Open a submission issue pointing to `https://github.com/hanzalaramz/Wupp`, mentioning the `fdroid` flavor APKs (`Wupp-<ver>-fdroid-universal.apk`) and `fastlane/metadata/android/en-US/`.
- **Official F-Droid (`gitlab.com/fdroid/fdroiddata`):** Copy `fdroid/com.wupp.app.yml` into `metadata/com.wupp.app.yml` in a fork of `fdroiddata` after tagging your first flavored release (`v0.2.1+`), verify locally with `fdroid build com.wupp.app`, and open an MR.
