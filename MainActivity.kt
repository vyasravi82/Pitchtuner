package com.example.pitchtuner

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private val sampleRate = 44100
    private val fftSize = 4096

    private lateinit var pitchDetector: PitchDetector
    private lateinit var noteText: TextView
    private lateinit var freqText: TextView
    private lateinit var centsBar: ProgressBar
    private lateinit var centsText: TextView

    private var audioRecord: AudioRecord? = null
    @Volatile private var running = false

    companion object {
        private const val REQ_RECORD_AUDIO = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        noteText = findViewById(R.id.noteText)
        freqText = findViewById(R.id.freqText)
        centsBar = findViewById(R.id.centsBar)
        centsText = findViewById(R.id.centsText)

        pitchDetector = PitchDetector(sampleRate, fftSize)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO
            )
        } else {
            startListening()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            noteText.text = "Mic permission needed"
        }
    }

    private fun startListening() {
        val minBufBytes = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufBytes = max(minBufBytes, fftSize * 2)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufBytes
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            noteText.text = "Mic init failed"
            return
        }

        audioRecord = record
        running = true
        record.startRecording()

        thread(name = "PitchCapture") {
            val frame = ShortArray(fftSize)
            while (running) {
                var read = 0
                while (read < fftSize && running) {
                    val n = record.read(frame, read, fftSize - read)
                    if (n <= 0) break
                    read += n
                }
                if (!running) break
                if (read == fftSize) {
                    val result = pitchDetector.process(frame)
                    runOnUiThread { updateUi(result) }
                }
            }
        }
    }

    private fun updateUi(result: PitchResult?) {
        if (result == null) {
            noteText.text = "—"
            freqText.text = ""
            centsText.text = ""
            centsBar.progress = 50
            return
        }
        noteText.text = "${result.noteName}${result.octave}"
        freqText.text = "%.1f Hz".format(result.frequencyHz)
        val cents = result.centsOff
        centsText.text = if (cents >= 0) "+%.0f cents".format(cents) else "%.0f cents".format(cents)
        // map -50..+50 cents onto 0..100 progress
        val clamped = cents.coerceIn(-50f, 50f)
        centsBar.progress = (50 + clamped).roundToInt()
    }

    private fun max(a: Int, b: Int) = if (a > b) a else b

    override fun onPause() {
        super.onPause()
        stopListening()
    }

    override fun onResume() {
        super.onResume()
        if (audioRecord == null &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        }
    }

    private fun stopListening() {
        running = false
        audioRecord?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        audioRecord = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }
}
