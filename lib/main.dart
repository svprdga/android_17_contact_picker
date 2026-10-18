import 'package:flutter/material.dart';

import 'contacts_source.dart';

void main() {
  runApp(const ContactPickerApp());
}

class ContactPickerApp extends StatelessWidget {
  const ContactPickerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Contact Picker',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
      ),
      home: const ContactsScreen(),
    );
  }
}

class ContactsScreen extends StatefulWidget {
  const ContactsScreen({super.key});

  @override
  State<ContactsScreen> createState() => _ContactsScreenState();
}

class _ContactsScreenState extends State<ContactsScreen> {
  static const _loadLabel = 'Load contacts';
  static const _loadIcon = Icon(Icons.person_search);

  final ContactsSource _contactsSource = const ContactsSource();

  List<AppContact> _contacts = const [];
  bool _isLoading = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Contacts')),
      body: _buildBody(),
      floatingActionButton: _contacts.isEmpty
          ? null
          : FloatingActionButton.extended(
              onPressed: _isLoading ? null : _loadContacts,
              icon: _loadIcon,
              label: const Text(_loadLabel),
            ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
    );
  }

  Widget _buildBody() {
    if (_isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_contacts.isEmpty) {
      return Center(
        child: FilledButton.icon(
          onPressed: _loadContacts,
          icon: _loadIcon,
          label: const Text(_loadLabel),
        ),
      );
    }

    return ListView.builder(
      itemCount: _contacts.length,
      itemBuilder: (context, index) {
        final contact = _contacts[index];
        final photo = contact.photo;

        return ListTile(
          leading: CircleAvatar(
            backgroundImage: photo != null ? MemoryImage(photo) : null,
            child: photo == null ? const Icon(Icons.person) : null,
          ),
          title: Text(contact.name),
        );
      },
    );
  }

  Future<void> _loadContacts() async {
    setState(() => _isLoading = true);

    var contacts = const <AppContact>[];
    String? message;
    try {
      contacts = await _contactsSource.loadContacts();
      if (contacts.isEmpty) {
        message = 'No contacts to show.';
      }
    } on ContactsPermissionDeniedException {
      message = 'The contacts permission is required to read your contacts.';
    } catch (error) {
      message = 'Could not read your contacts: $error';
    }

    if (!mounted) return;

    setState(() {
      _contacts = contacts;
      _isLoading = false;
    });

    if (message != null) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    }
  }
}
