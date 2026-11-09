package com.voicebubble

import java.io.File

/** What the phone's processor can do. */
object Cpu {
    /** int8 matrix-multiply instructions (ARMv8.6 "i8mm"): much faster maths for Whisper. */
    private val i8mm: Boolean by lazy {
        try {
            File("/proc/cpuinfo").readLines().any { line ->
                line.startsWith("Features", ignoreCase = true) && line.split(Regex("\\s+")).contains("i8mm")
            }
        } catch (e: Exception) { false }
    }

    /** The newer fast-maths instructions (phones from ~2022 with a high-end chip). */
    fun hasI8mm(): Boolean = i8mm
}
