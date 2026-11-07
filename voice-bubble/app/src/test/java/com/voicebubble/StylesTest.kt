package com.voicebubble

import com.voicebubble.Styles.Category
import com.voicebubble.Styles.Style
import org.junit.Assert.assertEquals
import org.junit.Test

class StylesTest {
    private val chat = "Hey, are you around tonight? I am thinking we could grab dinner at 7, if that works for you."
    private val mail = "Hi Sarah, thanks so much for your help with the move. It is really appreciated. Talk soon. Best, Daniel"

    @Test fun chatStyles() {
        assertEquals(chat, Styles.apply(chat, Style.FORMAL, Category.PERSONAL))
        assertEquals("Hey, are you around tonight? I'm thinking we could grab dinner at 7 if that works for you",
            Styles.apply(chat, Style.CASUAL, Category.PERSONAL))
        assertEquals("hey are you around tonight? i'm thinking we could grab dinner at 7 if that works for you",
            Styles.apply(chat, Style.VERY_CASUAL, Category.PERSONAL))
        assertEquals("Hey, are you around tonight? I am thinking we could grab dinner at 7, if that works for you!",
            Styles.apply(chat, Style.EXCITED, Category.PERSONAL))
    }

    @Test fun emailLayout() {
        assertEquals("Hi Sarah,\n\nThanks so much for your help with the move. It is really appreciated. Talk soon.\n\nBest,\nDaniel",
            Styles.apply(mail, Style.FORMAL, Category.EMAIL))
        assertEquals("Hi Sarah, thanks so much for your help with the move. It's really appreciated. Talk soon\n\nBest,\nDaniel",
            Styles.apply(mail, Style.CASUAL, Category.EMAIL))
        assertEquals("hi sarah thanks so much for your help with the move it's really appreciated talk soon\n\nbest\nDaniel",
            Styles.apply(mail, Style.VERY_CASUAL, Category.EMAIL))
        assertEquals("Hi Sarah,\n\nThanks so much for your help with the move. It is really appreciated. Talk soon!\n\nBest,\nDaniel",
            Styles.apply(mail, Style.EXCITED, Category.EMAIL))
    }

    @Test fun emailWithoutGreetingOrSignoff() {
        assertEquals("Thanks for the update.", Styles.apply("thanks for the update", Style.FORMAL, Category.EMAIL))
    }

    @Test fun contractionsOnlyMidSentence() {
        assertEquals("Yes, I am. I'm sure it's fine.", Styles.contract("Yes, I am. I am sure it is fine."))
        assertEquals("We don't know what it is.", Styles.contract("We do not know what it is."))
    }

    @Test fun keepsNumbers() {
        assertEquals("it costs 3.50 and 1,000 people came", Styles.veryCasual("It costs 3.50, and 1,000 people came."))
        assertEquals("It costs 1,000", Styles.casual("It costs 1,000."))
    }

    @Test fun categories() {
        assertEquals(Category.PERSONAL, Styles.categoryOf("com.whatsapp"))
        assertEquals(Category.EMAIL, Styles.categoryOf("com.google.android.gm"))
        assertEquals(Category.WORK, Styles.categoryOf("com.Slack"))
        assertEquals(Category.OTHER, Styles.categoryOf("com.android.chrome"))
        assertEquals(Category.OTHER, Styles.categoryOf(null))
    }

    @Test fun shortcutsStayLowercaseInVeryCasual() {
        val s = listOf(Shortcut("btw", "by the way"))
        assertEquals("by the way it works", Words.applyShortcuts("btw it works", s, capitalize = false))
    }
}

class GenZTest {
    @Test fun slang() {
        assertEquals("tbh idk fr", Styles.apply("To be honest, I don't know. For real.", Style.GEN_Z, Category.OTHER))
        assertEquals("omg ngl that was fire", Styles.apply("Oh my god, not gonna lie, that was fire!", Style.GEN_Z, Category.PERSONAL))
        assertEquals("ok brb", Styles.apply("Okay, be right back.", Style.GEN_Z, Category.PERSONAL))
    }
}
