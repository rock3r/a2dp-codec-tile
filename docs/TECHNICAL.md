# Technical guide

## How it works

`CodecAccess` opens the Bluetooth A2DP profile for a short operation. With one
connected A2DP output it uses that device. With more than one, it asks Android
for the active output and stops if it cannot identify one. The picker reads
`BluetoothCodecStatus.getCodecsSelectableCapabilities()` when opened; it does
not keep a hardcoded headset-specific codec list.

The tile requests a temporary selectable codec, waits for Android to report
the switch, then requests the original codec and checks again. Long-press and
the launcher icon open a Compose Material 3 picker. Its theme follows the
phone's light or dark setting. The tile refreshes when Quick Settings starts
listening and while it is listening to Bluetooth connection broadcasts.

Android's codec status and preference methods are hidden APIs used through
reflection. `BLUETOOTH_CONNECT` permission and, on the tested Pixel build, a
Companion Device Manager association with each headset are required. A
platform update or vendor build can block these calls. The app stops if it
cannot read the active device or current codec.

The core code was validated on a Pixel 11 Pro running Android 17 with Bowers &
Wilkins Px8 S2 and Pixel Buds Pro 2. On the Pixel Buds, Android reported AAC,
Opus, and SBC as selectable, and a switch to Opus was confirmed. The generic
release package has a different application ID from the earlier Px8 prototype,
so Android treats it as a separate app and Quick Settings tile.

## Build from source

You need JDK 17 and Android SDK Platform 37. From the repository root:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK uses your local Android debug key. There are no signing keys or
APK builds in the repository. To update an installed release, sign with the
same private release key; Android will reject a different signer.

## Manual device association

The app starts Android's device association flow when codec access requires
it. Some builds may not discover an already paired headset. For development,
an ADB fallback on the tested build was:

```sh
adb shell pm grant dev.sebastiano.a2dpcodec android.permission.BLUETOOTH_CONNECT
adb shell cmd companiondevice associate 0 dev.sebastiano.a2dpcodec AA:BB:CC:DD:EE:FF
```

Replace the example address with the **headset's** Bluetooth address from the
phone's Bluetooth diagnostics, for example `adb shell dumpsys bluetooth_manager`.
`0` is the Android user ID on a typical single-user phone. Check
`adb shell cmd companiondevice help` if your build's syntax differs. This
association belongs to the app's package; reinstalling or changing the
application ID can require setup again.

## Release builds

Build the unsigned release APK with `./gradlew :app:assembleRelease`. Sign it
with Android SDK Build Tools `apksigner` and a private release keystore kept
outside Git. Verify the output with `apksigner verify --verbose --print-certs`
and publish a SHA-256 checksum alongside the APK. The release key and its
password must be backed up securely before relying on the release for future
updates.
