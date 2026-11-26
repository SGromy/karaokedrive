package com.karaokedrive.recognition.shazam

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** FFT réelle radix-2 sur 2048 points, suffisante pour l'empreinte (≈ 1 500 FFT par extrait de 8 s). */
internal class Fft(private val size: Int = 2048) {

    private val levels = Integer.numberOfTrailingZeros(size)
    private val cosTable = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2 * PI * it / size) }
    private val bitReversed = IntArray(size) { Integer.reverse(it) ushr (32 - levels) }

    private val re = DoubleArray(size)
    private val im = DoubleArray(size)

    init {
        require(size > 1 && size and (size - 1) == 0) { "taille de FFT non puissance de 2 : $size" }
    }

    /** Puissance |X[k]|² des [size]/2 + 1 premières fréquences d'un signal réel. */
    fun power(input: DoubleArray, out: DoubleArray) {
        for (i in 0 until size) {
            re[bitReversed[i]] = input[i]
            im[bitReversed[i]] = 0.0
        }
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                for (k in 0 until half) {
                    val a = start + k
                    val b = a + half
                    val c = cosTable[k * step]
                    val s = -sinTable[k * step]
                    val tr = re[b] * c - im[b] * s
                    val ti = re[b] * s + im[b] * c
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                start += half * 2
            }
            half *= 2
        }
        for (k in 0..size / 2) out[k] = re[k] * re[k] + im[k] * im[k]
    }
}
