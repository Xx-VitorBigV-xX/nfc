package br.edu.puccampinas.nfc

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.IOException
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.cardview.widget.CardView
import java.io.UnsupportedEncodingException
import kotlin.experimental.and

class MainActivity : AppCompatActivity() {
    companion object {
        const val Error_Detected = "No Nfc Tag Detected"
        const val Write_Success = "Text Written Successfully!"
        const val Write_Error = "Error during writing, try again"
    }

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent
    private lateinit var writingTagFilters: Array<IntentFilter>
    private var writeMode: Boolean = false
    private lateinit var myTag: Tag
    private lateinit var context: Context
    private lateinit var edit_menssage: TextView
    private lateinit var nfc_content: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        edit_menssage = findViewById(R.id.Et_message)
        nfc_content = findViewById(R.id.Nfc_contents)
        button = findViewById(R.id.Btn_Activate)
        button.setOnClickListener {
            try {
                if (myTag == null) {
                    Toast.makeText(this, Error_Detected, Toast.LENGTH_LONG).show()
                } else {
                    write("PlainText:" + edit_menssage.text.toString(), myTag)
                    Toast.makeText(this, Write_Success, Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this, Write_Error, Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } catch (e: FormatException) {
                Toast.makeText(this, Write_Error, Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        context = this
        nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        writingTagFilters = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        })
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, writingTagFilters, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }
    private fun readFromIntent(intent: Intent?) {
        intent?.let { intent ->
            val action: String? = intent.action
            if (NfcAdapter.ACTION_TAG_DISCOVERED == action ||
                NfcAdapter.ACTION_TECH_DISCOVERED == action ||
                NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
                val rawMsgs: Array<Parcelable>? = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                var msgs: Array<NdefMessage?>? = null
                rawMsgs?.let {
                    msgs = arrayOfNulls<NdefMessage>(rawMsgs.size)
                    for (i in rawMsgs.indices) {
                        msgs!![i] = rawMsgs[i] as NdefMessage
                    }
                }
                buildTagViews(msgs)
            }
        }
    }


    private fun write(message: String, tag: Tag) {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                val ndefMessage = NdefMessage(NdefRecord.createMime("text/plain", message.toByteArray()))
                ndef.writeNdefMessage(ndefMessage)
                Toast.makeText(this, "Message written successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Toast.makeText(this, "Error writing message, try again", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            } catch (e: FormatException) {
                Toast.makeText(this, "Error writing message, try again", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            } finally {
                try {
                    ndef.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } else {
            val format = NdefFormatable.get(tag)
            if (format != null) {
                try {
                    format.connect()
                    val ndefMessage = NdefMessage(NdefRecord.createMime("text/plain", message.toByteArray()))
                    format.format(ndefMessage)
                    Toast.makeText(this, "Message written successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Toast.makeText(this, "Error writing message, try again", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                } catch (e: FormatException) {
                    Toast.makeText(this, "Error writing message, try again", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                } finally {
                    try {
                        format.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            } else {
                Toast.makeText(this, "Tag is not NDEF formatted", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readFromIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return
        }
    }

    private fun buildTagViews(msgs: Array<NdefMessage?>?) {
        if (msgs == null || msgs.isEmpty()) return
        var text = ""
        val payload = msgs[0]!!.records[0].payload
        val textEncoding = if (payload[0] and 128.toByte() == 0.toByte()) "UTF-8" else "UTF-16"
        val languageCodeLength: Int = payload[0].toInt() and 63
        try {
            text = String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, charset(textEncoding))
        } catch (e: UnsupportedEncodingException) {
            Log.e("unsupportedEncodingException", e.toString())
        }
        nfc_content.text = "NFC CONTENT$text"
    }


}
