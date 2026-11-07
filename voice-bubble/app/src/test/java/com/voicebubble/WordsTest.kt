package com.voicebubble

import org.junit.Assert.assertEquals
import org.junit.Test

class WordsTest {
    private val dict = listOf("Comboed", "Elad", "Hasfari", "iPhone", "Holmes Place", "Claude")
    private val shorts = listOf(
        Shortcut("btw", "by the way"),
        Shortcut("my email", "me@example.com"),
        Shortcut("omw", "On my way!"),
    )

    private fun d(s: String) = Words.applyDictionary(s, dict, shorts)
    private fun s(s: String) = Words.applyShortcuts(s, shorts)

    @Test fun fixesCloseSpellings() {
        assertEquals("I opened Comboed today.", d("I opened Komboed today."))
        assertEquals("I opened Comboed today.", d("I opened combo ed today."))
        assertEquals("Ask Hasfari.", d("Ask Hasfary."))
        assertEquals("Meet at Holmes Place.", d("Meet at Holms Place."))
        assertEquals("My new iPhone.", d("My new iphone."))
    }

    @Test fun leavesOtherWordsAlone() {
        assertEquals("We combined them.", d("We combined them."))
        assertEquals("Look at the cloud.", d("Look at the cloud."))
        assertEquals("A lad and a ladder.", d("A lad and a ladder."))
        assertEquals("Elad is here.", d("Elad is here."))
    }

    @Test fun expandsShortcuts() {
        assertEquals("Oh, by the way, it works.", s("Oh, BTW, it works."))
        assertEquals("By the way, it works.", s("BTW, it works."))
        assertEquals("Oh by the way it works", s("Oh B.T.W. it works"))
        assertEquals("Send it to me@example.com please.", s("Send it to my email please."))
        assertEquals("On my way!", s("OMW"))
        assertEquals("The btwx stays.", s("The btwx stays."))
    }

    @Test fun shortcutsAreNotCorrectedByDictionary() {
        val withBtw = listOf("BTWS")
        assertEquals("BTW", Words.applyDictionary("BTW", withBtw, shorts))
    }

    @Test fun parsesExportAndClaudeFormat() {
        val p = Words.parse("""
            Here you go:
            ```
            word: Comboed
            word: Elad
            short: btw => by the way
            short: addr => Line 1\nLine 2
            ```
        """.trimIndent())
        assertEquals(listOf("Comboed", "Elad"), p.words)
        assertEquals(listOf(Shortcut("btw", "by the way"), Shortcut("addr", "Line 1\nLine 2")), p.shorts)
    }

    @Test fun parsesPlainLists() {
        val p = Words.parse("Comboed\n- Elad\nbtw → by the way\nomw -> on my way")
        assertEquals(listOf("Comboed", "Elad"), p.words)
        assertEquals(listOf(Shortcut("btw", "by the way"), Shortcut("omw", "on my way")), p.shorts)
    }
}

class PacksTest {
    @Test fun packNamesFixOnlySameSounding() {
        val strict = Packs.jjk.names + Packs.toby.names
        fun d(s: String) = Words.applyDictionary(s, emptyList(), emptyList(), strict)
        assertEquals("Talk to Sukuna.", d("Talk to sukuna."))
        assertEquals("Gojo vs Sukuna", d("gojo vs Sukuna"))
        assertEquals("I like Spamton", d("I like Spamtun"))
        assertEquals("I left it undone.", d("I left it undone."))
        assertEquals("Chris is here.", d("Chris is here."))
        assertEquals("We combined them.", d("We combined them."))
    }

    @Test fun userWordsStillWinOverPacks() {
        assertEquals("uro", Words.applyDictionary("uro", listOf("uro"), emptyList(), listOf("Uro")))
    }

    @Test fun audioWindow() {
        assertEquals(512, audioCtxFor(16000 * 3))
        assertEquals(1128, audioCtxFor(16000 * 20))
        assertEquals(1500, audioCtxFor(16000 * 40))
    }
}
