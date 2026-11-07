package com.voicebubble

import android.content.Context
import com.voicebubble.Commands.Feature

/** On/off state of everything that can be switched by voice ("out of context, enable …"). */
object Features {
    private fun sp(c: Context) = c.getSharedPreferences("voice", Context.MODE_PRIVATE)

    fun isOn(c: Context, f: Feature): Boolean {
        val p = Prefs(c)
        return when (f) {
            Feature.COMMANDS, Feature.EMOJI, Feature.FILLER, Feature.NUMBERS -> sp(c).getBoolean("feat_${f.name}", true)
            Feature.CAPS -> p.capsLock
            Feature.ONLY_TYPING -> p.onlyWithKeyboard
            Feature.EXTRA -> p.extraAccuracy
            Feature.SOUNDS -> p.sound != "off"
            Feature.VIBRATION -> p.haptics
            Feature.PACK_JJK -> Packs.isOn(c, Packs.jjk)
            Feature.PACK_TOBY -> Packs.isOn(c, Packs.toby)
            Feature.PACK_GENZ -> Packs.isOn(c, Packs.genz)
        }
    }

    fun set(c: Context, f: Feature, on: Boolean) {
        val p = Prefs(c)
        when (f) {
            Feature.COMMANDS, Feature.EMOJI, Feature.FILLER, Feature.NUMBERS -> sp(c).edit().putBoolean("feat_${f.name}", on).apply()
            Feature.CAPS -> p.capsLock = on
            Feature.ONLY_TYPING -> p.onlyWithKeyboard = on
            Feature.EXTRA -> p.extraAccuracy = on
            Feature.SOUNDS -> p.sound = if (on) (if (p.sound == "off") "chime" else p.sound) else "off"
            Feature.VIBRATION -> p.haptics = on
            Feature.PACK_JJK -> Packs.setOn(c, Packs.jjk, on)
            Feature.PACK_TOBY -> Packs.setOn(c, Packs.toby, on)
            Feature.PACK_GENZ -> Packs.setOn(c, Packs.genz, on)
        }
    }
}
