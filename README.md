# TigerTag Creator Studio for Android

An open-source filament tag editor for Android phones with NFC. Read supported TigerTag records, edit filament settings, browse the public catalogue, and write a standard unsigned TigerTag to a compatible blank tag.

The [Windows app](https://github.com/IgooGG/TigerTagCreatorStudioPC) supports the wCopy WBW106-IDIC USB reader.

## Install and use

Download `TigerTag-Creator-Studio-Android.apk` from this repository's Releases page on an Android 7.0 or newer phone. Open the APK and follow Android's prompts to install it. Turn on NFC; browsing and file editing also work without an NFC reader. A first catalogue download and product images need internet access; cached data can be used offline.

Choose **Read tag**, then hold a supported tag against the back of the phone until reading completes. Select a catalogue product or edit the fields, choose **Write tag**, and hold a blank or initialized tag to the phone. The app rejects signed and other nonblank tags and verifies the write by reading it back. Keep the tag in place until it finishes. Interrupted writes may leave incomplete data. Check printing settings against the manufacturer's recommendations.

The app can import and export 80- or 144-byte TigerTag payloads as `.bin`. A scanned signature is checked offline using the tag UID, format and product ID. **Other filament fields are not covered by that signature.** Imported payloads do not contain the UID, so their signatures cannot be verified. Unchanged original payloads retain their signature bytes on export; edits produce an unsigned record. The application cannot create manufacturer signatures.

## Build from source

Install JDK 17 and Android SDK 35. Set `ANDROID_HOME` or configure the SDK in Android Studio, then run:

```sh
./gradlew assembleDebug lintDebug
```

On Windows, use `gradlew.bat`. The APK is at `app/build/outputs/apk/debug/app-debug.apk`. See [RELEASING.md](RELEASING.md) for versioning and signing instructions. Release assets are debug builds; configure a persistent signing key to let users update future versions without uninstalling.

## Protocol and attribution

This is an independent project, not an official TigerTag product. It uses the [TigerTag RFID Guide](https://github.com/TigerTag-Project/TigerTag-RFID-Guide) for protocol and public catalogue data. TigerTag names and marks belong to their respective owners. The application code is available under the [MIT License](LICENSE); third-party data and trademarks have their own terms.
