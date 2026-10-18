package com.example.android_17_contact_picker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsPickerSessionContract
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

private const val CHANNEL = "com.example.android_17_contact_picker/contacts"

private const val METHOD_PICK_CONTACTS = "pick_contacts"

private const val ERROR_CONTACT_PICKER = "contact_picker_error"

private const val PICK_CONTACTS_REQUEST_CODE = 4197

/** Android 17, where the system Contact Picker replaces broad contacts access. */
private const val CONTACT_PICKER_MIN_SDK = 37

private data class PickedContact(
    val id: String,
    val name: String,
    var photo: ByteArray? = null,
)

class MainActivity : FlutterActivity() {
    private var pendingPickResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    METHOD_PICK_CONTACTS -> pickContacts(call, result)
                    else -> result.notImplemented()
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != PICK_CONTACTS_REQUEST_CODE) {
            return
        }

        val result = pendingPickResult ?: return
        pendingPickResult = null

        // The picker hands back a session URI pointing at the selected contacts.
        val sessionUri = data?.data
        if (resultCode != Activity.RESULT_OK || sessionUri == null) {
            result.success(mapOf("wasCancelled" to true, "contacts" to emptyList<Any>()))
            return
        }

        // Reading the session is a content provider query, so keep it off the main thread.
        Thread {
            try {
                val contacts = readPickedContacts(sessionUri)
                runOnUiThread {
                    result.success(mapOf("wasCancelled" to false, "contacts" to contacts))
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    result.error(
                        ERROR_CONTACT_PICKER,
                        "Unable to read the contacts selected in the picker.",
                        exception.message,
                    )
                }
            }
        }.start()
    }

    private fun pickContacts(call: MethodCall, result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < CONTACT_PICKER_MIN_SDK) {
            result.error(
                ERROR_CONTACT_PICKER,
                "The system Contact Picker requires Android 17 or later.",
                null,
            )
            return
        }

        if (pendingPickResult != null) {
            result.error(ERROR_CONTACT_PICKER, "The picker is already open.", null)
            return
        }

        pendingPickResult = result
        try {
            val intent = Intent(ContactsPickerSessionContract.ACTION_PICK_CONTACTS).apply {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                putExtra(
                    ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_SELECTION_LIMIT,
                    call.arguments as Int,
                )
                // Read back only the fields below instead of every data field.
                putExtra(
                    ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_MATCH_ALL_DATA_FIELDS,
                    false,
                )
                putStringArrayListExtra(
                    ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
                    arrayListOf(
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
                    ),
                )
            }
            startActivityForResult(intent, PICK_CONTACTS_REQUEST_CODE)
        } catch (exception: Exception) {
            pendingPickResult = null
            result.error(
                ERROR_CONTACT_PICKER,
                "Unable to open the system Contact Picker.",
                exception.message,
            )
        }
    }

    private fun readPickedContacts(sessionUri: Uri): List<Map<String, Any?>> {
        val projection = arrayOf(
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.CommonDataKinds.Photo.PHOTO,
        )
        val contactsById = linkedMapOf<String, PickedContact>()

        contentResolver.query(sessionUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIndex = cursor.getColumnIndexOrThrow(
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            )
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
            val photoIndex = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Photo.PHOTO,
            )

            // The cursor holds one row per requested data field, so rows are grouped
            // by contact.
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex) ?: continue
                val contact = contactsById.getOrPut(id) {
                    PickedContact(id = id, name = cursor.getString(nameIndex).orEmpty())
                }

                if (cursor.getString(mimeTypeIndex) ==
                    ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE &&
                    !cursor.isNull(photoIndex)
                ) {
                    contact.photo = cursor.getBlob(photoIndex)
                }
            }
        }

        return contactsById.values.map { contact ->
            mapOf("id" to contact.id, "name" to contact.name, "photo" to contact.photo)
        }
    }
}
