# ProcLimit

A personal (sideloaded) Android app that sets **Developer options → Apps → Background process limit** to **"At most 4 processes"** at every boot.

Android keeps this value only in `ActivityManagerService` memory and forgets it on reboot. ProcLimit sets it again from a `BOOT_COMPLETED` receiver by calling the hidden `IActivityManager.setProcessLimit(4)` through plain reflection on `ActivityManager.getService()`. The call is authorized by `android.permission.SET_PROCESS_LIMIT` (protection level `signature|privileged|development`), which the manifest declares and you grant once with `adb shell pm grant`.

- Package: `io.github.omega28.proclimit`
- Limit values: `-1` Standard, `0` no background processes, `1`–`4` at most N
- No hidden-API workaround is included. If the reflective call turns out to be blocked, the app shows the exact exception.

## Knowing it worked without opening the app

After each boot (once you've unlocked the phone), ProcLimit:

- shows a toast: *ProcLimit: At most 4 processes*
- posts a silent notification that stays in the shade until you dismiss it: *At most 4 background processes · Limit applied at boot · <time>*
- if the apply failed, posts an alerting notification with the error instead

The notifications need the notification permission: tap **Allow notifications** in the app once. Each kind has its own channel ("Applied at boot", "Problems"), so you can silence the success one in system settings and keep failure alerts.

## Download

Grab `proclimit-vX.Y.apk` from [Releases](https://github.com/0mega28/proclimit/releases). Each release is built and signed by GitHub Actions from the tagged commit, and the release notes list the APK and signing-certificate SHA-256. To check that an APK really came from this repo's workflow:

```bash
gh attestation verify proclimit-v1.0.apk -R 0mega28/proclimit
```

Releases are signed with a fixed key, so later versions install over earlier ones and keep the permission grant. A locally built debug APK has a different signature: uninstall it first, then grant again after installing the release.

## Releasing

Push a version tag; the `Build` workflow builds the signed APK, attests it and creates the GitHub release:

```bash
git tag v1.0 && git push origin v1.0
```

Signing uses four repo secrets: `PROCLIMIT_KEYSTORE_BASE64`, `PROCLIMIT_KEYSTORE_PASSWORD`, `PROCLIMIT_KEY_ALIAS`, `PROCLIMIT_KEY_PASSWORD`. Gradle reads them from the matching `PROCLIMIT_*` environment variables, so release builds without them come out unsigned.

## Build locally

Requires JDK 17 and the Android SDK with `platforms;android-37.0` installed and licenses accepted (the Android Gradle Plugin downloads the build-tools it needs). `local.properties` must contain `sdk.dir=...`; on this Mac that's `/opt/homebrew/share/android-commandlinetools`.

```bash
JAVA_HOME=~/.jbang/cache/jdks/17 ./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Device test protocol

Run these from the Mac. From Termux, drop the `adb shell` prefix from the `pm`/`dumpsys` lines or keep it if Termux's adb is connected to the phone.

1. **Install**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. **Launch once.** Open ProcLimit from the launcher. This takes the app out of the "stopped" state; without it, Android won't deliver `BOOT_COMPLETED`. The screen should show *Permission not granted* with the grant command. Tap **Allow notifications** and allow.
3. **Grant**
   ```bash
   adb shell pm grant io.github.omega28.proclimit android.permission.SET_PROCESS_LIMIT
   ```
4. **Verify the grant.** `pm grant` exits silently even when it does nothing, so check:
   ```bash
   adb shell dumpsys package io.github.omega28.proclimit | grep SET_PROCESS_LIMIT
   ```
   Pass: a line with `android.permission.SET_PROCESS_LIMIT: granted=true`.
5. **Apply.** Switch away from the app and back (it refreshes on resume); it should now show *Permission granted*. Tap **Apply 4 now**.
   Pass: the snackbar says *Set to at most 4 processes*, the top card shows *At most 4 processes · Active now*, and Developer options → Background process limit shows "At most 4 processes".
   Fail (assumption 2): a `SecurityException`, `NoSuchMethodException`, or hidden-API error. Note the exact text.
6. **Reboot**
   ```bash
   adb reboot
   ```
7. **Re-verify the grant** (assumption 1) after the phone is unlocked:
   ```bash
   adb shell dumpsys package io.github.omega28.proclimit | grep SET_PROCESS_LIMIT
   ```
   Pass: still `granted=true`.
8. **Confirm the boot apply.** Wait about a minute after unlocking, then check:
   ```bash
   adb logcat -d -s ProcLimit
   ```
   Pass: `boot: OK: Set to at most 4 processes`. You should also have seen the toast after unlocking, the notification shade should show *At most 4 background processes*, and the app's *Last boot* card shows the same result with the boot time.
9. **Confirm in Developer options** (assumption 3): Settings → System → Developer options → Background process limit shows **"At most 4 processes"**. Check again a few minutes later to make sure nothing reset it.

The protocol passes when steps 4, 5, 7, 8 and 9 all pass.

## If it fails

- **Grant lost after reboot (step 7) or call rejected (step 5):** fall back to Shizuku. Call the same `setProcessLimit(4)` through Shizuku's binder at boot (`com.android.shell` already holds `SET_PROCESS_LIMIT`).
- **Call blocked by hidden-API restrictions (step 5):** the result line will name the hidden method. A workaround (for example a reflection-based exemption) can be added; it was left out on purpose until it's shown to be needed.
- **Boot result OK but Developer options shows Standard later (step 9):** something reset the limit after `BOOT_COMPLETED`; the app would need to re-apply later (for example via a delayed job).

## Reset

Tap **Reset to Standard** in the app, or uninstall it and reboot. Uninstalling also removes the grant.
