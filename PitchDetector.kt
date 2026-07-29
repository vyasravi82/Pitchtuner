package com.example.pitchtuner

import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PitchResult(
    val frequencyHz: Float,
    val noteName: String,
    val octave: Int,
    val centsOff: Float
)

/**
 * Real-time pitch detector built on a chromogram / chroma-spectrogram approach:
 *
 *  1. Window the incoming audio frame (Hann window) to reduce spectral leakage.
 *  2. FFT it to the frequency domain.
 *  3. Fold spectral energy from every FFT bin into 12 pitch-class ("chroma") bins,
 *     based on which semitone that bin's frequency belongs to (a chromogram).
 *  4. The chroma bin with the most energy is the detected pitch class.
 *  5. Within that pitch class, find the strongest FFT bin and refine it with
 *     parabolic interpolation to get a precise fundamental frequency.
 *  6. Convert frequency -> note name / octave / cents-off-from-in-tune.
 *
 * This mirrors the *category* of technique (FFT -> chroma folding -> note pick)
 * used by chroma-based tuner apps; all math here is standard, published DSP
 * (Cooley-Tukey FFT, chroma features per Fujishima 1999 / Müller), written from
 * scratch - no proprietary code or coefficients from any existing app.
 */
class PitchDetector(
    private val sampleRate: Int,
    private val fftSize: Int = 4096
) {
    companion object {
        private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        private const val A4_FREQ = 440.0
        private const val A4_MIDI = 69

        private const val MIN_FREQ_HZ = 55.0   // ~A1, low guitar range
        private const val MAX_FREQ_HZ = 2000.0 // covers fundamentals for voice/most instruments
    }

    private val window = FloatArray(fftSize) { i ->
        // Hann window
        (0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * i / (fftSize - 1))).toFloat()
    }

    /**
     * @param samples PCM16 mono samples, length must equal fftSize.
     * @return detected pitch, or null if the frame is too quiet / no clear pitch.
     */
    fun process(samples: ShortArray): PitchResult? {
        require(samples.size == fftSize) { "Expected $fftSize samples, got ${samples.size}" }

        // 1. Quick silence gate on raw signal (RMS)
        var sumSq = 0.0
        for (s in samples) sumSq += (s * s).toDouble()
        val rms = sqrt(sumSq / samples.size)
        if (rms < 200.0) return null // below noise floor for 16-bit PCM

        // 2. Window + copy into FFT buffers
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            real[i] = samples[i] * window[i]
        }

        // 3. FFT
        FFT.transform(real, imag)

        // Magnitude spectrum (only need first half, real signal is symmetric)
        val half = fftSize / 2
        val magnitude = FloatArray(half)
        for (i in 0 until half) {
            magnitude[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }

        val binHz = sampleRate.toDouble() / fftSize
        val minBin = max(1, (MIN_FREQ_HZ / binHz).toInt())
        val maxBin = min(half - 2, (MAX_FREQ_HZ / binHz).toInt())
        if (minBin >= maxBin) return null

        // 4. Fold spectral energy into 12 chroma (pitch-class) bins -> the chromogram
        val chromaEnergy = DoubleArray(12)
        val bestBinForClass = IntArray(12) { -1 }
        val bestMagForClass = FloatArray(12)

        for (bin in minBin..maxBin) {
            val freq = bin * binHz
            val midi = 69.0 + 12.0 * log2(freq / A4_FREQ)
            val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
            val mag = magnitude[bin]

            chromaEnergy[pitchClass] += mag
            if (mag > bestMagForClass[pitchClass]) {
                bestMagForClass[pitchClass] = mag
                bestBinForClass[pitchClass] = bin
            }
        }

        // 5. Winning pitch class = most energetic chroma bin
        var winningClass = 0
        for (c in 1 until 12) {
            if (chromaEnergy[c] > chromaEnergy[winningClass]) winningClass = c
        }

        val peakBin = bestBinForClass[winningClass]
        if (peakBin < 1 || peakBin >= half - 1) return null

        // Reject if the "winning" peak is too weak relative to loudest bin overall
        // (avoids reporting a note from pure noise).
        var overallMaxMag = 0f
        for (bin in minBin..maxBin) overallMaxMag = max(overallMaxMag, magnitude[bin])
        if (overallMaxMag <= 0f || bestMagForClass[winningClass] < overallMaxMag * 0.5f) return null

        // 6. Parabolic interpolation around the peak bin for sub-bin frequency accuracy
        val alpha = magnitude[peakBin - 1].toDouble()
        val beta = magnitude[peakBin].toDouble()
        val gamma = magnitude[peakBin + 1].toDouble()
        val denom = (alpha - 2 * beta + gamma)
        val p = if (denom != 0.0) 0.5 * (alpha - gamma) / denom else 0.0
        val refinedFreq = (peakBin + p) * binHz

        if (refinedFreq < MIN_FREQ_HZ || refinedFreq > MAX_FREQ_HZ) return null

        // 7. Frequency -> nearest note name / octave / cents off
        val exactMidi = A4_MIDI + 12.0 * log2(refinedFreq / A4_FREQ)
        val nearestMidi = exactMidi.roundToInt()
        val cents = ((exactMidi - nearestMidi) * 100.0).toFloat()
        val noteIndex = ((nearestMidi % 12) + 12) % 12
        val octave = (nearestMidi / 12) - 1

        return PitchResult(
            frequencyHz = refinedFreq.toFloat(),
            noteName = NOTE_NAMES[noteIndex],
            octave = octave,
            centsOff = cents
        )
    }
}
