# ProcLimit

A personal (sideloaded) Android app that sets **Developer options → Apps → Background process limit** to **"At most 4 processes"** at every boot.

Android keeps this value only in `ActivityManagerService` memory and forgets it on reboot. ProcLimit sets it again from a `BOOT_COMPLETED` receiver by calling the hidden `IActivityManager.setProcessLimit(4)` through plain reflection on `ActivityManager.getService()`. The call is authorized by `android.permission.SET_PROCESS_LIMIT` (protection level `signature|privileged|development`), which the manifest declares and you grant once with `adb shell pm grant`.

- Package: `io.github.omega28.proclimit`
- Limit values: `-1` Standard, `0` no background processes, `1`–`4` at most N
- No hidden-API workaround is included. If the reflective call turns out to be blocked, the app shows the exact exception.

## Build

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
2. **Launch once.** Open ProcLimit from the launcher. This takes the app out of the "stopped" state; without it, Android won't deliver `BOOT_COMPLETED`. The screen should show *Permission … NOT granted*.
3. **Grant**
   ```bash
   adb shell pm grant io.github.omega28.proclimit android.permission.SET_PROCESS_LIMIT
   ```
4. **Verify the grant.** `pm grant` exits silently even when it does nothing, so check:
   ```bash
   adb shell dumpsys package io.github.omega28.proclimit | grep SET_PROCESS_LIMIT
   ```
   Pass: a line with `android.permission.SET_PROCESS_LIMIT: granted=true`.
5. **Apply.** Go back to the app; it should now show *GRANTED*. Tap **Apply 4 now**.
   Pass: *Last button result: OK: set At most 4 (4), now At most 4 (4)*, and Developer options → Background process limit shows "At most 4 processes".
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
   Pass: `boot: OK: set At most 4 (4), now At most 4 (4)`. The app's *Last boot apply* line should show the same result with the boot time.
9. **Confirm in Developer options** (assumption 3): Settings → System → Developer options → Background process limit shows **"At most 4 processes"**. Check again a few minutes later to make sure nothing reset it.

The protocol passes when steps 4, 5, 7, 8 and 9 all pass.

## If it fails

- **Grant lost after reboot (step 7) or call rejected (step 5):** fall back to Shizuku. Call the same `setProcessLimit(4)` through Shizuku's binder at boot (`com.android.shell` already holds `SET_PROCESS_LIMIT`).
- **Call blocked by hidden-API restrictions (step 5):** the result line will name the hidden method. A workaround (for example a reflection-based exemption) can be added; it was left out on purpose until it's shown to be needed.
- **Boot result OK but Developer options shows Standard later (step 9):** something reset the limit after `BOOT_COMPLETED`; the app would need to re-apply later (for example via a delayed job).

## Reset

Tap **Reset to Standard** in the app, or uninstall it and reboot. Uninstalling also removes the grant.
