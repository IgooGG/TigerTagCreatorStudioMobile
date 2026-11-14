# Publishing an Android release

1. Update `versionName` and increase `versionCode` in `app/build.gradle`, and update the version shown in `app/src/main/assets/editor.html`. The version code must be a positive integer greater than the previous release's code. Commit and push.
2. On GitHub, create and publish a release from the intended commit with a tag matching `versionName`, for example `0.0.2` or `v0.0.2`.
3. The **Build Android release** workflow checks the tag, builds and lints the app, and uploads `TigerTag-Creator-Studio-Android.apk` to that release. Check Actions if it fails; a failed job can be rerun.

The workflow triggers only when a release is published. Commits, pushes, tags on their own, and draft releases do not start a build.

The workflow installs Android platform 35 and Build Tools 35.0.0 explicitly. It uses `android-actions/setup-android@v4`, whose defaults no longer request the obsolete `tools` SDK package.

## Signing and updates

The workflow builds a debug APK. With no signing secrets it uses a newly generated debug key on each runner, so users will generally have to uninstall a previously installed build (losing app data) to install another. For installable updates, create and securely back up a dedicated signing keystore and configure all four repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: base64 encoding of the complete keystore file, on one line.
- `ANDROID_STORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: signing key alias.
- `ANDROID_KEY_PASSWORD`: key password.

The workflow uses that same key to sign its debug APKs when all four secrets are present. Keep the keystore and passwords private and use the same key for every release. Losing or changing it prevents normal updates of earlier builds. Existing APKs signed with a different local debug key will require an uninstall before installing the first APK signed with the new key. Never commit the keystore or passwords to the repository.
