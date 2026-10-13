# A2DP Codec Tile

An Android Quick Settings tile that shows the codec used by the connected
Bluetooth A2DP audio output. Tap the tile to request a brief switch to another
available codec and back; long-press it to choose from the codecs reported as
selectable for that connection. The picker uses Compose Material 3 and follows
the phone's light/dark theme. When refreshed with no A2DP connection, the tile
becomes unavailable.

This is an experimental developer utility. It uses hidden Android A2DP codec
methods through reflection and requires a Companion Device Manager association
for the audio device on the Pixel build used for testing. The app does not
disconnect Bluetooth. A codec change interrupts playback briefly and is not a
proven fix for audio stutter.

## Requirements

- Android 17 (API 37). This is the only platform version currently targeted.
- A paired Bluetooth A2DP audio device and an `adb` connection for initial setup.
- To build: Android SDK Platform 37, JDK 17, and the included Gradle wrapper.

The app uses the single A2DP output reported by Android. It does not target a
particular headphone brand. If Android reports more than one connected A2DP
output, the app stops rather than changing an arbitrary device.

## Build and install

From this directory:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed by your local Android debug key. No release signing key,
signed APK, or device diagnostic is included in this repository. To distribute a
release build, configure and protect your own signing key outside the repo.

## One-time device setup

1. Pair and connect the headphones in Android Bluetooth settings.
2. Grant the app's Bluetooth runtime permission:

   ```sh
   adb shell pm grant dev.sebastiano.a2dpcodec android.permission.BLUETOOTH_CONNECT
   ```

3. Find the paired headphones' Bluetooth address from the phone's Bluetooth
   diagnostics (for example, inspect `adb shell dumpsys bluetooth_manager`
   around the device name). Use **that device's address**, not the phone's.
4. Associate the app with that device. Replace the example address with the
   actual one; `0` is the Android user ID for a typical single-user phone:

   ```sh
   adb shell cmd companiondevice associate 0 dev.sebastiano.a2dpcodec AA:BB:CC:DD:EE:FF
   ```

5. Expand Quick Settings, tap **Edit**, and add **A2DP codec**.

The association and permission are tied to this package. Repeat setup after
uninstalling the app or changing its application ID. If your device's
`companiondevice` shell command differs, check
`adb shell cmd companiondevice help` for its syntax.

## Use

- The tile icon is a short codec glyph; its subtitle gives the codec name.
- Tap to request an alternate selectable codec, wait for Android to report it,
  then request the original codec again. The tile reports whether both steps
  were confirmed.
- Long-press to open the codec picker. Only codecs Android reports as selectable
  are shown. Select one and tap **Use codec**.
- With Bluetooth off or no A2DP device connected, a refreshed tile is disabled.

The tile reflects the state when Android starts or refreshes its service.
SystemUI may temporarily show a cached icon after a connection changes; the
unavailable state has not yet been visually verified during a disconnect.

## Compatibility and privacy

Codec status and preference calls are hidden Android APIs. A platform update
or a different vendor build may block them even with a companion association.
The only user-facing platform permission it requests is `BLUETOOTH_CONNECT`.
The app does not request network access or transmit data. Its Px8-specific
prototype was validated on a Pixel 11 Pro running Android 17. This generic
package builds successfully but has not yet been run on a device.

## License

Apache License 2.0. See [LICENSE](LICENSE).
