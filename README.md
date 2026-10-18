# android_17_contact_picker

Example app for the article on Google Play's contacts requirement for Android 17+.

A single screen listing the name and photo of the device contacts, reading them through two
different paths:

- **Android 17 (API 37) and later**: the system Contact Picker, launched over a method channel
  from [`MainActivity.kt`](android/app/src/main/kotlin/com/example/android_17_contact_picker/MainActivity.kt).
  The app never holds the contacts permission — `READ_CONTACTS` is declared with
  `android:maxSdkVersion="36"`.
- **Android 16 and earlier, and iOS**: the [flutter_contacts](https://pub.dev/packages/flutter_contacts)
  plugin, after requesting the contacts permission.

The branching lives in [`lib/contacts_source.dart`](lib/contacts_source.dart), which picks the path
from the Android SDK level reported by
[device_info_plus](https://pub.dev/packages/device_info_plus).

## Running it

```sh
flutter pub get
flutter run
```

The project compiles against the Android 17 SDK (`compileSdk = 37`), so install the
*Android 17.0* platform from the SDK Manager first. To see the system Contact Picker you need an
**API 37** emulator or device; anything older takes the permission path.
