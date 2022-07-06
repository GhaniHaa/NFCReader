package com.example.nfcexample

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.*
import android.nfc.tech.Ndef
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import kotlin.experimental.and

class MainActivity : AppCompatActivity() {

    lateinit var writeTagFilters: Array<IntentFilter>
    var nfcAdapter: NfcAdapter? = null
    var pendingIntent: PendingIntent? = null
    var writeMode = false
    var myTag: Tag? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Toast.makeText(this, "Welcome", Toast.LENGTH_SHORT).show()


        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show()
            finish()
        }

        //For when the activity is launched by the intent-filter for android.nfc.action.NDEF_DISCOVERE
        readFromIntent(intent)
        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            0
        )
        val tagDetected = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT)
        writeTagFilters = arrayOf(tagDetected)
    }

    /******************************************************************************
     * Read From NFC Tag
     ****************************************************************************/
    private fun readFromIntent(intent: Intent) {
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action || NfcAdapter.ACTION_TECH_DISCOVERED == action || NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
            myTag = intent.getParcelableExtra<Parcelable>(NfcAdapter.EXTRA_TAG) as Tag?
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            var msgs = mutableListOf<NdefMessage>()
            if (rawMsgs != null) {
                for (i in rawMsgs.indices) {
                    msgs.add(i, rawMsgs[i] as NdefMessage)
                }
                buildTagViews(msgs.toTypedArray())
            }
            else {
                println("xxx1 ${myTag.toString()}")
            }
        }
    }

    private fun buildTagViews(msgs: Array<NdefMessage>) {
        if (msgs == null || msgs.isEmpty()){
            Toast.makeText(this, "Cannot Read NFC", Toast.LENGTH_SHORT).show()
        }
        else {
            var text = ""
            val payload = msgs[0].records[0].payload
            val textEncoding: Charset = if ((payload[0] and 128.toByte()).toInt() == 0) Charsets.UTF_8 else Charsets.UTF_16 // Get the Text Encoding
            val languageCodeLength: Int = (payload[0] and 51).toInt() // Get the Language Code, e.g. "en"
            try {
                // Get the Text
                text = String(
                    payload,
                    languageCodeLength + 1,
                    payload.size - languageCodeLength - 1,
                    textEncoding
                )
            } catch (e: UnsupportedEncodingException) {
                Log.e("UnsupportedEncoding", e.toString())
            }
            tvResult.text = "Message read from NFC Tag:\n $text"
        }
    }

    /******************************************************************************
     * Write to NFC Tag
     ****************************************************************************/
    @Throws(IOException::class, FormatException::class)
    private fun write(text: String, tag: Tag?) {
        val records = arrayOf(createRecord(text))
        val message = NdefMessage(records)
        // Get an instance of Ndef for the tag.
        val ndef = Ndef.get(tag)
        // Enable I/O
        ndef.connect()
        // Write the message
        ndef.writeNdefMessage(message)
        // Close the connection
        ndef.close()
    }

    @Throws(UnsupportedEncodingException::class)
    private fun createRecord(text: String): NdefRecord {
        val lang = "en"
        val textBytes = text.toByteArray()
        val langBytes = lang.toByteArray(charset("US-ASCII"))
        val langLength = langBytes.size
        val textLength = textBytes.size
        val payload = ByteArray(1 + langLength + textLength)

        // set status byte (see NDEF spec for actual bits)
        payload[0] = langLength.toByte()

        // copy langbytes and textbytes into payload
        System.arraycopy(langBytes, 0, payload, 1, langLength)
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength)
        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
    }

    /**
     * For reading the NFC when the app is already launched
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readFromIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
    }

    public override fun onPause() {
        super.onPause()
        WriteModeOff()
    }

    public override fun onResume() {
        super.onResume()
        WriteModeOn()
    }

    /******************************************************************************
     * Enable Write and foreground dispatch to prevent intent-filter to launch the app again
     ****************************************************************************/
    private fun WriteModeOn() {
        writeMode = true
        nfcAdapter!!.enableForegroundDispatch(this, pendingIntent, writeTagFilters, null)
    }

    /******************************************************************************
     * Disable Write and foreground dispatch to allow intent-filter to launch the app
     ****************************************************************************/
    private fun WriteModeOff() {
        writeMode = false
        nfcAdapter!!.disableForegroundDispatch(this)
    }

    companion object {
        const val ERROR_DETECTED = "No NFC tag detected!"
        const val WRITE_SUCCESS = "Text written to the NFC tag successfully!"
        const val WRITE_ERROR = "Error during writing, is the NFC tag close enough to your device?"
    }
}