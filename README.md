# A2DP Codec Tile

<img src="docs/icon.svg" alt="A2DP Codec Tile icon" width="96" height="96">

See and change the Bluetooth audio codec your headphones are using, right from
Android Quick Settings.

The tile shows the current codec. **Tap** it to briefly switch to another
available codec and back, which can help renegotiate a glitchy connection.
**Long-press** it to choose a codec yourself. The picker shows only codecs
Android reports as available for the connected headphones.

## Get started

1. Download the APK from the [latest release](https://github.com/rock3r/a2dp-codec-tile/releases/latest) and install it on your phone.
2. Connect your Bluetooth headphones, then open **A2DP Codec Tile**. Allow
   Bluetooth access and approve the device association if Android asks.
3. Add **A2DP codec** to Quick Settings. Its icon and subtitle show the codec
   currently in use.
4. Long-press the tile to pick a codec, or tap it to renegotiate the current
   one. You can also open the picker from the app icon.

Setup may need to be repeated for each pair of headphones. Changing codecs
briefly interrupts audio. Android may choose a different codec again after a
reconnection.

If you used the earlier Px8 prototype, remove its Quick Settings tile and add
the **A2DP codec** tile from this release. Android treats them as separate apps.

## Compatibility

- **Android 17 (API 37)** is the supported target for this release.
- The headphones must be connected through classic Bluetooth **A2DP**. An
  LE Audio-only connection has no A2DP codec for this app to control.
- Codec choices vary by headset and phone. If Android reports only one
  selectable codec, there is nothing else to switch to.
- This is a developer utility built on Android APIs that may behave differently
  on other phone builds. A codec reset is not a guaranteed fix for stuttering.

The app does not request internet access or send your data anywhere. The tile
disables itself when no A2DP output is connected.

If setup fails, or you want to build from source, see the
[technical guide](docs/TECHNICAL.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
