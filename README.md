# Immich Provider for Android

Access photos and videos from your Immich server directly from Android's system file picker.

The app adds an **Immich** source to **Open from**. It displays your timeline in the same chronological order as Immich, provides thumbnails, opens original files, and gives you access to your albums.

> This is a community project and is not affiliated with Immich. The Immich logo belongs to the Immich project.

## Download the APK

[**Download the latest version**](https://github.com/SpeedTomy/Immich-Provider/releases/latest/download/immich-provider.apk)

Android 10 or later is required. The first time you install the APK, Android may ask you to allow installations from this source.

## Setup

### 1. Create an Immich API key

In the Immich web interface:

1. Open your account settings.
2. Open the **API Keys** section.
3. Create a dedicated key for Immich Provider.
4. Grant only the read permissions required for media, thumbnails, original files, and albums.
5. Copy the key immediately.

Avoid using an administrator key or a key with permission to modify or delete data.

### 2. Configure the app

1. Install and open **Immich Provider**.
2. Enter the full URL of your server, for example:

   ```text
   https://photos.example.com
   ```

   Or, on a trusted local network:

   ```text
   http://192.168.1.201:2283
   ```

3. Paste the API key, then tap the save button.

The key is encrypted using Android Keystore. The app sends your credentials only to the configured server.

### 3. Select a photo

From an app offering **Attach file**, **Browse**, or **Open from**:

1. Open Android's system file picker.
2. Open the sources panel.
3. Select **Immich** — the server address appears below the logo.
4. Select a recent media item or open the **Albums** folder.

## Features

- timeline sorted by capture date, from newest to oldest;
- Immich visibility, stack, and partner-sharing rules;
- photo and video previews;
- original file download when opening a media item;
- navigation through Immich albums;
- read-only access;
- support for local HTTP servers;
- API key encryption using Android Keystore;
- Immich icon and server address in Android's file picker.

## Known limitations

- The root currently displays the 250 most recent media items. Albums remain fully accessible.
- Immich's compact timeline response does not include original filenames, so Android may display the Immich asset ID as the document name.
- Original files are downloaded to the app's private cache before being opened; streaming is not yet supported.
- HTTP is allowed for local servers. Do not use HTTP over the Internet or on an untrusted network.
- Samsung's **Cloud media app** photo-picker menu uses `CloudMediaProvider`, not `DocumentsProvider`. Android currently reserves this mechanism for apps approved by a device manufacturer/OEM, so a manually installed app cannot add Immich to that menu.

## Verified compatibility

- Android 10 and later (`minSdk 29`);
- Immich 3.1.0;
- Samsung Galaxy S24+ using the Google/Samsung system file picker.

Older Immich versions may work through fallback API routes, but an exact timeline order is not guaranteed.

## Security and privacy

- The API key is stored in `EncryptedSharedPreferences` and protected by Android Keystore.
- Files are exposed as read-only.
- Previews and original files are stored in the app's private cache.
- No telemetry or third-party service is included.
- Local HTTP URLs are intentionally supported. Always prefer HTTPS when available.

## Build from source

Requirements: Android SDK, JDK 17, and an Android 10+ device or emulator.

```sh
git clone https://github.com/SpeedTomy/Immich-Provider.git
cd Immich-Provider
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

- `ImmichDocumentsProvider.kt`: Storage Access Framework integration;
- `ImmichMediaSource.kt`: HTTP requests and Immich API adaptation;
- `DocumentId.kt`: stable identifiers for roots, albums, and media items;
- `ImmichSettings.kt`: server URL and encrypted API key storage;
- `MainActivity.kt`: configuration screen.

The timeline uses `GET /api/timeline/buckets` and `GET /api/timeline/bucket`. Albums use `GET /api/albums` and `GET /api/albums/{id}`. Thumbnails and original files use `/api/assets/{id}/thumbnail` and `/api/assets/{id}/original`.

## Development

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

GitHub releases are built by `.github/workflows/release.yml` and signed with a stable key stored in GitHub Secrets.

## Acknowledgements

[Immich](https://immich.app/) for its excellent photo management platform and API. The vector logo used by this app comes from the `design` directory of the official Immich repository.
