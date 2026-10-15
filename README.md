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
- A paired Bluetooth A2DP audio device.
- To build: Android SDK Platform 37, JDK 17, and the included Gradle wrapper.

The app uses the connected A2DP output reported by Android. With multiple
connected A2DP devices it selects Android's active output, when that can be
identified. It stops rather than changing an arbitrary device. The available
codec list comes from that device's current `BluetoothCodecStatus`; it is
refreshed each time the picker opens. A headset may offer only one codec.

## Build and install

From this directory:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed by your local Android debug key. No release signing key,
signed APK, or device diagnostic is included in this repository. To distribute a
release build, configure and protect your own signing key outside the repo.

## Set up each headset

1. Pair and connect the headphones in Android Bluetooth settings.
2. Add **A2DP codec** to Quick Settings. Long-press it to open the picker.
3. Tap **Allow** if Android asks for Bluetooth access.
4. Tap **Set up** if the picker asks for device association, and approve the
   system prompt. Association is per headset. Repeat this step for another
   headset when the picker asks.

On some builds, Companion Device Manager may not discover an already-paired
headset. In that case, the developer setup via `adb` is:

```sh
adb shell pm grant dev.sebastiano.a2dpcodec android.permission.BLUETOOTH_CONNECT
adb shell cmd companiondevice associate 0 dev.sebastiano.a2dpcodec AA:BB:CC:DD:EE:FF
```

Replace the example address with the headset's Bluetooth address from the
phone's Bluetooth diagnostics, such as `adb shell dumpsys bluetooth_manager`.
Use the headset address, not the phone's. `0` is the Android user ID for a
typical single-user phone. Check `adb shell cmd companiondevice help` if your
device's command syntax differs.

The association and permission are tied to this package. Repeat setup after
uninstalling the app or changing its application ID.

## Use

- The tile icon is a short codec glyph; its subtitle gives the codec name.
- Tap to request an alternate selectable codec, wait for Android to report it,
  then request the original codec again. The tile reports whether both steps
  were confirmed.
- Long-press to open the codec picker. It names the active headset and shows
  only the codecs Android currently reports as selectable for that headset.
  Select one and tap **Use codec**.
- With Bluetooth off or no A2DP device connected, a refreshed tile is disabled.

The tile reflects the state when Android starts or refreshes its service.
SystemUI may temporarily show a cached icon after a connection changes; the
unavailable state has not yet been visually verified during a disconnect.

## Compatibility and privacy

The tile controls classic Bluetooth A2DP. An LE Audio-only connection has no
A2DP codec for this app to show or change.

Codec status and preference calls are hidden Android APIs. A platform update
or a different vendor build may block them even with a companion association.
The only user-facing platform permission it requests is `BLUETOOTH_CONNECT`.
The app does not request network access or transmit data. Its Px8-specific
prototype was validated on a Pixel 11 Pro running Android 17. This generic
package builds successfully but has not yet been run on a device.

## License

Apache License 2.0. See [LICENSE](LICENSE).
