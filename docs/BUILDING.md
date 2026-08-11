# Building the app

Two routes: let GitHub build it for you, or build locally.

---

## From Windows, with VS Code and GitHub

Allow twenty minutes the first time. No Android development tools are needed on your machine — GitHub does the compiling.

### 1. Install two tools

**Git** — <https://git-scm.com/download/win>. Download, run, accept the defaults.

**Visual Studio Code** — <https://code.visualstudio.com>. Same. Tick "Add to PATH" if offered.

Restart VS Code after installing Git, otherwise it will not detect it.

### 2. Create the repository

1. Create a [GitHub](https://github.com) account if you do not have one.
2. Click **+** at the top right → **New repository**.
3. Name it `party-pair`.
4. Leave it **Public** — that is what makes GitHub Actions free without limits.
5. **Do not add a README, .gitignore or licence** — the project already has them.
6. **Create repository**.

### 3. Open the project

Unzip the project somewhere simple, for instance `C:\Users\YourName\party-pair`.

Check that `README.md`, `settings.gradle.kts` and the `app` folder sit **directly at the root** — if they are inside a subfolder, move them up one level.

In VS Code: **File → Open Folder**, then choose `party-pair`.

### 4. Publish

1. Click **Source Control** in the left sidebar (or `Ctrl+Shift+G`).
2. Click **Initialize Repository**.
3. Type a message such as `First version` in the box at the top.
4. Click **Commit**. Accept if VS Code offers to stage everything first.
5. Click **Publish Branch**.
6. Sign in to GitHub when prompted, and authorise in the browser.

If VS Code asks for a name and email first, open the terminal (`Ctrl+'` or **Terminal → New Terminal**) and run:

```
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

Then go back to step 4.

VS Code cannot attach to an existing empty repository — its Publish button always creates a new one. If you created one already, either delete it, or connect manually:

```
git remote add origin https://github.com/your-name/party-pair.git
git branch -M main
git push -u origin main
```

### 5. Let GitHub build

1. Open your repository on GitHub.
2. Go to **Actions**.
3. A workflow named **APK** is running — orange dot. Allow three to five minutes.
4. Green dot: it is built.

If it turns red, click through to the run: the summary page now prints the compilation errors directly, with the file and line.

### 6. Get the APK

1. Still under **Actions**, open the finished run.
2. At the bottom, under **Artifacts**: `party-pair-apk`.
3. Download the `.zip` and unpack it.

### 7. Install

**Over USB** — plug the phone in, allow file transfer, copy the APK to *Downloads*. On the phone, open *My Files*, go to *Downloads*, tap the APK.

**Without a cable** — email it to yourself or drop it on your cloud, then open it from the phone.

Android will ask you to allow installation from this source, which is normal for an app that did not come from the Play Store.

---

## Publishing an update

After changing anything:

1. **Source Control** in VS Code.
2. A message describing the change.
3. **Commit**, then **Sync Changes**.
4. GitHub rebuilds; fetch the new APK from **Actions**.

Install it over the old one — your speaker settings are preserved.

### Making a downloadable release

For a proper download page you can share, and to make the in-app update check work:

```
git tag v1.19
git push origin v1.19
```

GitHub then creates a **Release** with the APK attached, at `https://github.com/your-name/party-pair/releases`.

---

## Building locally

Open the project in **Android Studio** and hit *Run*: the app installs straight onto a phone connected over USB.

From the command line:

```bash
gradle wrapper          # once, to generate the wrapper
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

The project targets API 35 and runs on Android 8 and later.

## Signing

The repository contains a debug key (`debug.keystore`), committed on purpose. Without a fixed key every build would produce a different signature, and Android would refuse to install an update over the previous one — forcing an uninstall, and losing your settings.

The key holds no secret. For wider distribution, generate your own release key and pass it through GitHub secrets.
