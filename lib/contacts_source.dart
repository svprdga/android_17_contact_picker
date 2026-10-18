import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/services.dart';
import 'package:flutter_contacts/flutter_contacts.dart';

/// A device contact reduced to what this app displays.
class AppContact {
  final String id;
  final String name;
  final Uint8List? photo;

  const AppContact({required this.id, required this.name, this.photo});
}

/// Thrown when the user denies the contacts permission.
class ContactsPermissionDeniedException implements Exception {}

/// Reads device contacts through whichever path the platform allows.
class ContactsSource {
  static const _channel = MethodChannel(
    'com.example.android_17_contact_picker/contacts',
  );

  static const _methodPickContacts = 'pick_contacts';

  static const _maxSelectedContacts = 100;

  /// Android 17, where the system Contact Picker replaces broad contacts access.
  static const _systemPickerMinAndroidSdk = 37;

  const ContactsSource();

  Future<List<AppContact>> loadContacts() async {
    if (await _isSystemPickerAvailable()) {
      return await _pickContactsWithSystemPicker();
    }

    return await _readContactsWithPermission();
  }

  Future<bool> _isSystemPickerAvailable() async {
    if (!Platform.isAndroid) return false;

    final androidInfo = await DeviceInfoPlugin().androidInfo;
    return androidInfo.version.sdkInt >= _systemPickerMinAndroidSdk;
  }

  /// Opens the system Contact Picker. Returns an empty list if the user leaves
  /// without selecting anything.
  Future<List<AppContact>> _pickContactsWithSystemPicker() async {
    final response = await _channel.invokeMapMethod<String, Object?>(
      _methodPickContacts,
      _maxSelectedContacts,
    );

    if (response == null || response['wasCancelled'] == true) {
      return const [];
    }

    final contacts = <AppContact>[];
    for (final rawContact in response['contacts'] as List<Object?>) {
      final contact = rawContact as Map<Object?, Object?>;
      final name = contact['name'] as String;
      if (name.isEmpty) continue;

      contacts.add(
        AppContact(
          id: contact['id'] as String,
          name: name,
          photo: contact['photo'] as Uint8List?,
        ),
      );
    }

    return contacts;
  }

  /// Reads every contact with the contacts permission, which Google Play only
  /// accepts below Android 17.
  Future<List<AppContact>> _readContactsWithPermission() async {
    final status = await FlutterContacts.permissions.request(
      PermissionType.read,
    );
    // iOS can grant access to a subset of the contacts only.
    if (status != PermissionStatus.granted &&
        status != PermissionStatus.limited) {
      throw ContactsPermissionDeniedException();
    }

    final deviceContacts = await FlutterContacts.getAll(
      properties: {ContactProperty.photoThumbnail},
    );

    final contacts = <AppContact>[];
    for (final deviceContact in deviceContacts) {
      final id = deviceContact.id;
      final name = deviceContact.displayName;
      if (id == null || name == null || name.isEmpty) continue;

      contacts.add(
        AppContact(id: id, name: name, photo: deviceContact.photo?.thumbnail),
      );
    }

    return contacts;
  }
}
