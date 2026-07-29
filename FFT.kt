package com.example.pitchtuner

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A standard iterative radix-2 Cooley-Tukey FFT.
 * This is textbook DSP (Cooley & Tukey, 1965) - not proprietary to any app.
 *
 * Operates in place on parallel real/imaginary float arrays whose length
 * must be a power of two.
 */
object FFT {

    fun transform(real: FloatArray, imag: FloatArray) {
        val n = real.size
        require(n and (n - 1) == 0) { "FFT size must be a power of two, was $n" }
        if (n <= 1) return

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var tmp = real[i]; real[i] = real[j]; real[j] = tmp
                tmp = imag[i]; imag[i] = imag[j]; imag[j] = tmp
            }
        }

        // Iterative Cooley-Tukey butterflies
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curWr = 1f
                var curWi = 0f
                for (k in 0 until len / 2) {
                    val evenIdx = i + k
                    val oddIdx = i + k + len / 2

                    val tr = real[oddIdx] * curWr - imag[oddIdx] * curWi
                    val ti = real[oddIdx] * curWi + imag[oddIdx] * curWr

                    real[oddIdx] = real[evenIdx] - tr
                    imag[oddIdx] = imag[evenIdx] - ti
                    real[evenIdx] += tr
                    imag[evenIdx] += ti

                    val nextWr = curWr * wr - curWi * wi
                    val nextWi = curWr * wi + curWi * wr
                    curWr = nextWr
                    curWi = nextWi
                }
                i += len
            }
            len = len shl 1
        }
    }
}
