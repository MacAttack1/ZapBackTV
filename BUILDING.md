# Building and releasing ZapBackTV

## The signing key (read this first)

Android only installs an update over an app signed with **the same key**. ZapBackTV's key is in the
`signing/` folder (`lumen-tv.jks` + `keystore.properties`). Every build uses it automatically: on your
PC, on your phone, and on GitHub (from repository secrets).

- **Back up the `signing/` folder** somewhere safe (a USB drive, a password manager). If it's lost,
  every device has to uninstall once to move to a new key.
- It's excluded from git (`.gitignore`), so it never ends up on GitHub. Don't remove those lines.
- The first time you install a build signed with this key over an older copy built another way,
  Android says "App not installed". Uninstall the old copy once (re-add your playlist afterwards);
  from then on everything updates normally.

## Build on a PC

### Windows

1. Install **Android Studio**: https://developer.android.com/studio. Open it once and let it finish
   downloading the Android SDK (the defaults are fine). You don't need to create a project.
2. Double-click **`build-apk.bat`** in the project folder.
   The first build downloads Gradle and the libraries, so it takes a few minutes.
3. The APK appears in **`dist\ZapBackTV-<version>.apk`**.

**Java version:** the build tool runs on **Java 17 to 23**. `build-apk.bat` finds a suitable one by
itself (Android Studio's built-in runtime, `JAVA_HOME`, or an installed JDK 17/21). If you only have a
newer Java (24, 25…), the build fails with nothing but the version number, e.g. `25.0.4`. Install
Java 21 alongside it (`winget install EclipseAdoptium.Temurin.21.JDK`) and run the script again.

Options (from a Command Prompt in the folder):

```bat
build-apk.bat                 :: release APK, signed with your key
build-apk.bat debug           :: debug APK
build-apk.bat release install :: build, then install on a device connected over adb
```

To install on a TV over Wi-Fi: turn on Developer options and **Network debugging** on the TV, then
`adb connect <tv-ip>` (adb is in `%LOCALAPPDATA%\Android\Sdk\platform-tools`) and run
`build-apk.bat release install`.

**Prefer Android Studio's buttons?** File › Open › choose this folder, wait for the sync, then
Build › Build App Bundle(s) / APK(s) › Build APK(s).

### Linux / macOS

Install Android Studio (or the SDK command-line tools and a JDK 17+), then:

```bash
./build-apk.sh            # release
./build-apk.sh debug
```

## Build on an Android phone

Open the project folder in **Code on the Go** and build as before. Keep the `signing/` folder inside
the project so phone builds use the same key and can update a PC- or GitHub-built copy (and the other
way round).

## Publish updates through GitHub

The app checks `https://github.com/<repo>/releases` for a newer version. The repository is set in
`gradle.properties`:

```properties
lumen.githubRepo=MacAttack1/ZapBackTV
```

The repository must be **public** so the app can read releases without a password.

### One-time setup (Windows, PowerShell)

Install **Git** (https://git-scm.com) and the **GitHub CLI** (https://cli.github.com), then in the
project folder:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\github-setup.ps1
```

It signs you in to GitHub, creates the repository if needed, stores the signing key as encrypted
repository secrets (`LUMEN_KEYSTORE_B64`, `LUMEN_KEYSTORE_PASSWORD`) and pushes the code.

<details><summary>Doing it by hand instead</summary>

1. Create an empty public repository named like `lumen.githubRepo`.
2. Repository › Settings › Secrets and variables › Actions › New repository secret:
   - `LUMEN_KEYSTORE_B64`: the key file as Base64. In PowerShell:
     `[Convert]::ToBase64String([IO.File]::ReadAllBytes("signing\lumen-tv.jks")) | Set-Clipboard`
   - `LUMEN_KEYSTORE_PASSWORD`: the `storePassword` from `signing\keystore.properties`.
3. `git init -b main`, `git remote add origin https://github.com/<repo>.git`, commit, `git push -u origin main`.
</details>

### Releasing a new version

1. Add a section at the top of **CHANGELOG.md**. This text is exactly what the app shows in its
   update dialog, so write it for the person watching:

   ```markdown
   ## 2.1.1

   - Fixed the guide skipping a day around midnight
   - Faster startup on big playlists
   ```

2. Run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -Version 2.1.1
   ```

   It updates `version.properties`, commits, tags `v2.1.1` and pushes. GitHub Actions builds the
   signed APK and publishes the release (about 5 minutes; watch the **Actions** tab).
   Add `-Local` to build on your PC and upload the release yourself instead.

3. Installed copies offer the update the next time they open (they check a few times a day at most),
   or right away with **Settings › Updates › Check for updates**. Users see the notes and choose
   **Update now**, **Skip this version** or **Later**.

### How versions work

- `version.properties` holds the only version number (`versionName=2.1.0`).
- The Android version code is derived from it (2.1.0 → 20100), so it always goes up.
- A release is offered when its tag (`v2.1.1`) is higher than the installed version and it has an
  `.apk` attached. The app checks that the APK is ZapBackTV and newer before installing it.
