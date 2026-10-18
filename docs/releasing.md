# Beluga Android: Release Engineering & Publishing Guide

This guide walks through the complete release engineering lifecycle for Beluga Android: generating signing keys, configuring GitHub Actions CI/CD secrets, version bumping, release tagging, and binary checksum verification.

---

## 1. Generating a Release Signing Keystore

To produce signed production APKs (`.apk`) and Android App Bundles (`.aab`), create a dedicated Java KeyStore using `keytool`:

```bash
keytool -genkeypair -v \
  -keystore beluga-release.jks \
  -alias beluga-release-key \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype JKS
```

> [!CAUTION]
> Back up your keystore file securely and record the passwords in a secure password manager. If you lose this keystore, you will not be able to push updates to installed apps or app store listings.

---

## 2. Base64-Encoding Keystore for CI/CD

GitHub Actions injects the release keystore as an environment secret. Convert the binary keystore into a Base85/Base64 string:

### Windows (PowerShell)
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("beluga-release.jks")) | Set-Content -NoNewline -Path "keystore_base64.txt"
```

### Linux & macOS
```bash
base64 -w 0 beluga-release.jks > keystore_base64.txt
# On macOS (BSD base64):
base64 -i beluga-release.jks -o keystore_base64.txt
```

---

## 3. Configuring GitHub Actions Secrets

In your GitHub repository, navigate to **Settings > Secrets and variables > Actions** and add the following repository secrets:

| Secret Name | Value Description |
| :--- | :--- |
| `KEYSTORE_BASE64` | The complete one-line contents of `keystore_base64.txt`. |
| `KEYSTORE_PASSWORD` | The password chosen during `keytool` keystore creation. |
| `KEY_ALIAS` | The key alias (e.g. `beluga-release-key`). |
| `KEY_PASSWORD` | The password for the specific key entry in the keystore. |

---

## 4. Local Release Build Verification (Optional)

You can verify release builds locally prior to tagging:

### Linux / macOS
```bash
export KEYSTORE_FILE="/path/to/beluga-release.jks"
export KEYSTORE_PASSWORD="your-keystore-password"
export KEY_ALIAS="beluga-release-key"
export KEY_PASSWORD="your-key-password"

./gradlew assembleRelease bundleRelease
```

### Windows (PowerShell)
```powershell
$env:KEYSTORE_FILE = "C:\path\to\beluga-release.jks"
$env:KEYSTORE_PASSWORD = "your-keystore-password"
$env:KEY_ALIAS = "beluga-release-key"
$env:KEY_PASSWORD = "your-key-password"

.\gradlew.bat assembleRelease bundleRelease
```

The resulting signed artifacts will be placed in:
- `app/build/outputs/apk/release/beluga-release.apk`
- `app/build/outputs/bundle/release/beluga-release.aab`

---

## 5. Version Bumping

Prior to creating a release tag, increment the version in [`app/build.gradle.kts`](../app/build.gradle.kts):

```kotlin
defaultConfig {
    applicationId = "com.beluga.e2ee"
    minSdk = 26
    targetSdk = 35
    versionCode = 2          // Monotonically increasing integer
    versionName = "2.1.0"    // Semantic version string
}
```

Commit the version change:
```bash
git add app/build.gradle.kts
git commit -m "chore(release): bump version to 2.1.0"
git push origin main
```

---

## 6. Tagging & Triggering Automated Release

When you push a Git tag matching `v*` to `main`, GitHub Actions automatically triggers the release publishing pipeline:

```bash
# Create annotated git tag
git tag -a v2.0.0 -m "Release v2.0.0: Initial production release of Beluga Android"

# Push tag to GitHub
git push origin v2.0.0
```

### Automated GitHub Actions Workflow Steps:
1. Validates code compilation, executes `./gradlew testDebugUnitTest`, and runs `./gradlew lintDebug`.
2. Decodes `KEYSTORE_BASE64` to `/tmp/release.jks`.
3. Runs `./gradlew assembleRelease bundleRelease`.
4. Computes cryptographic SHA-256 hashes and saves them to `SHA256SUMS.txt`.
5. Publishes a new GitHub Release with release notes and attaches `beluga-release.apk`, `beluga-release.aab`, and `SHA256SUMS.txt`.
6. Securely wipes `/tmp/release.jks` in a cleanup step.

---

## 7. Verifying Release Binary Integrity

End users can verify the integrity of their downloaded release artifacts against the published `SHA256SUMS.txt`:

### Linux / macOS
```bash
sha256sum --check SHA256SUMS.txt
```

### Windows (PowerShell)
```powershell
Get-FileHash -Algorithm SHA256 beluga-release.apk, beluga-release.aab | Format-Table -AutoSize
```
Confirm the computed hash matches the value in `SHA256SUMS.txt`.
