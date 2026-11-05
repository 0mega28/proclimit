# ProcLimit

A personal (sideloaded) Android app that sets **Developer options → Apps → Background process limit** to **"At most 4 processes"** at every boot.

## Why this exists

Since the Android 17 QPR1 update (September 15, 2026), many Pixels, from the Pixel 6 to the 10a, lag, stutter and freeze under memory pressure, and some apps crash. Users report zRAM filling up while cached apps aren't cleared, with 8 GB phones hit hardest ([Insider Paper](https://insiderpaper.com/pixel-10a-and-9-lag-issues-android-17-qpr1-update/), [Android Authority](https://www.androidauthority.com/android-17-qpr1-pixel-performance-regression-3718939/)). Reports on Reddit:

- [Lags on Google Pixel 8](https://www.reddit.com/r/GooglePixel/comments/1wvnj9c/lags_on_google_pixel_8/)
- [Pixel 8 slowed down](https://www.reddit.com/r/GooglePixel/comments/1wtxm0m/pixel_8_slowed_down/)
- [New update is really slowing my 9a down](https://www.reddit.com/r/GooglePixel/comments/1wt0od2/new_update_is_really_slowing_my_9a_down_anyone/)
- [Is there any chance Google will address…](https://www.reddit.com/r/GooglePixel/comments/1wu9j93/is_there_any_chance_google_will_address/)
- [Pixel 10 Pro animation and unlock stutter](https://www.reddit.com/r/GooglePixel/comments/1x3vyho/comment/pgglmwq/)

**The workaround** (credit to the Redditors who shared it, [here](https://www.reddit.com/r/Pixel8phones/comments/1wrd3an/comment/pdpan9h/) and [here](https://www.reddit.com/r/GooglePixel/comments/1wr1767/comment/pd2l9vb/)): set Developer options → Apps → Background process limit to "At most 4 processes". It keeps memory pressure down, but Android resets it to Standard on every reboot. ProcLimit sets it again for you at each boot.

**The real fix** is in the OS. [GrapheneOS traced the regression](https://www.androidauthority.com/android-17-qpr1-pixel-performance-regression-3718939/) to Google-supplied Pixel components and patched it in its own releases ([discussion](https://news.ycombinator.com/item?id=49937718)); stock Pixels are waiting on Google. As GrapheneOS points out, the process limit has a usability cost: apps you switch back to restart more often. Treat ProcLimit as a stopgap: once your Pixel gets the fix, tap **Reset to Standard** and uninstall it.

## How it works

Android keeps this value only in `ActivityManagerService` memory and forgets it on reboot. ProcLimit sets it again at boot by calling the hidden `IActivityManager.setProcessLimit(4)` through plain reflection on `ActivityManager.getService()`. The call is authorized by `android.permission.SET_PROCESS_LIMIT` (protection level `signature|privileged|development`), which the manifest declares and you grant once with `adb shell pm grant`.

The receiver is direct-boot aware and acts on `LOCKED_BOOT_COMPLETED`, which arrives right after boot, before you unlock. `BOOT_COMPLETED` alone isn't enough: Android delivers it to every listening app in turn, and on a real phone (358 receivers on a Pixel 8) it reached ProcLimit more than 3 minutes after boot. `BOOT_COMPLETED` is still handled as a backup and re-applies the limit.

- Package: `io.github.omega28.proclimit`
- Limit values: `-1` Standard, `0` no background processes, `1`–`4` at most N
- No hidden-API workaround is included. If the reflective call turns out to be blocked, the app shows the exact exception.

## Knowing it worked without opening the app

After each boot, ProcLimit:

- posts a silent notification as soon as the limit is applied, before unlock, so it's waiting when you unlock: *At most 4 background processes · Limit applied at boot · <time>*. It stays in the shade until you dismiss it.
- if the apply failed, posts an alerting notification with the error instead
- shows a toast, *ProcLimit: At most 4 processes*, when `BOOT_COMPLETED` arrives after unlock; on a busy phone that can be a few minutes later

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
2. **Launch once.** Open ProcLimit from the launcher. This takes the app out of the "stopped" state; without it, Android won't deliver the boot broadcasts. The screen should show *Permission not granted* with the grant command. Tap **Allow notifications** and allow.
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
   Pass: `android.intent.action.LOCKED_BOOT_COMPLETED: OK: Set to at most 4 processes` within seconds of boot, later followed by the same line for `BOOT_COMPLETED`. You should also have seen the toast after unlocking, the notification shade should show *At most 4 background processes*, and the app's *Last boot* card shows the same result with the boot time.
9. **Confirm in Developer options** (assumption 3): Settings → System → Developer options → Background process limit shows **"At most 4 processes"**. Check again a few minutes later to make sure nothing reset it.

The protocol passes when steps 4, 5, 7, 8 and 9 all pass.

## If it fails

- **Grant lost after reboot (step 7) or call rejected (step 5):** fall back to Shizuku. Call the same `setProcessLimit(4)` through Shizuku's binder at boot (`com.android.shell` already holds `SET_PROCESS_LIMIT`).
- **Call blocked by hidden-API restrictions (step 5):** the result line will name the hidden method. A workaround (for example a reflection-based exemption) can be added; it was left out on purpose until it's shown to be needed.
- **Boot result OK but Developer options shows Standard later (step 9):** something reset the limit after boot; the app would need to re-apply later (for example via a delayed job).

## Reset

Tap **Reset to Standard** in the app, or uninstall it and reboot. Uninstalling also removes the grant.

## License

[MIT](LICENSE)
