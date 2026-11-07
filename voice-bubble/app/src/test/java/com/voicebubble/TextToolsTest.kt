package com.voicebubble

import com.voicebubble.Commands.Cmd
import com.voicebubble.Commands.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextToolsTest {
    @Test fun voiceCommands() {
        assertEquals("Hello.\nHow are you?", TextTools.voiceCommands("Hello. New line. How are you?"))
        assertEquals("How are you?", TextTools.voiceCommands("How are you question mark"))
        assertEquals("I love it!", TextTools.voiceCommands("I love it, exclamation mark."))
        assertEquals("Buy milk, eggs, and bread", TextTools.voiceCommands("Buy milk comma eggs comma and bread"))
        assertEquals("Meet at six.", TextTools.voiceCommands("Meet at five. Scratch that. Meet at six."))
        assertEquals("I love you", TextTools.voiceCommands("I hate you scrap that I love you"))
        assertEquals("Hello there. I love you", TextTools.voiceCommands("Hello there. I hate you, no, scratch that! I love you"))
        assertEquals("", TextTools.voiceCommands("Scrap that."))
        assertEquals("That's it.", TextTools.voiceCommands("That's it period"))
        assertEquals("Version 3.5 is on google.com at 3:00", TextTools.voiceCommands("Version 3.5 is on google.com at 3:00"))
    }

    @Test fun emoji() {
        assertEquals("That was funny 💀", TextTools.emoji("That was funny skull emoji"))
        assertEquals("I love you ❤️.", TextTools.emoji("I love you heart emoji."))
        assertEquals("🔥", TextTools.emoji("Fire emoji"))
        assertEquals("banana emoji", TextTools.emoji("banana emoji"))
    }

    @Test fun fillers() {
        assertEquals("I think, we should go.", TextTools.removeFillers("Um, I think, uh, we should go."))
        assertEquals("I think the plan works", TextTools.removeFillers("I I think the the plan works"))
        assertEquals("Bye bye", TextTools.removeFillers("Bye bye"))
        assertEquals("Take the umbrella", TextTools.removeFillers("Take the umbrella"))
    }

    @Test fun numbers() {
        assertEquals("I need 25%", TextTools.numbersAndDates("I need twenty five percent"))
        assertEquals("I have two cats", TextTools.numbersAndDates("I have two cats"))
        assertEquals("It costs $50", TextTools.numbersAndDates("It costs fifty dollars"))
        assertEquals("See you at 3 pm", TextTools.numbersAndDates("See you at three pm"))
        assertEquals("On March 3", TextTools.numbersAndDates("On march third"))
        assertEquals("250 people came", TextTools.numbersAndDates("two hundred and fifty people came"))
        assertEquals("Someone came", TextTools.numbersAndDates("Someone came"))
    }
}

class CommandsTest {
    @Test fun toggles() {
        assertEquals(Cmd.Toggle(Feature.NUMBERS, true), Commands.parse("Out of context, enable numbers and dates."))
        assertEquals(Cmd.Toggle(Feature.FILLER, false), Commands.parse("out of context disable filler remover"))
        assertEquals(Cmd.Toggle(Feature.PACK_JJK, true), Commands.parse("Out of context, enable the JJK pack."))
        assertEquals(Cmd.Toggle(Feature.CAPS, true), Commands.parse("Out of context, turn on caps lock."))
    }

    @Test fun rewriteTranslateReplyUndo() {
        assertEquals(Cmd.Rewrite("make this more polite"), Commands.parse("Out of context, rewrite, make this more polite."))
        assertEquals(Cmd.Rewrite("make this shorter"), Commands.parse("Out of context, make this shorter"))
        assertEquals(Cmd.Translate("spanish"), Commands.parse("Out of context, translate to Spanish."))
        assertEquals(Cmd.Translate(null), Commands.parse("out of context stop translating"))
        assertEquals(Cmd.Reply("saying I'll be late"), Commands.parse("Out of context, reply saying I'll be late."))
        assertEquals(Cmd.Undo, Commands.parse("Out of contacts, undo."))
    }

    @Test fun commandAfterText() {
        assertEquals(Commands.Split("Could I get a Big Mac", Cmd.Rewrite("make this longer, give me five fries")),
            Commands.split("Could I get a Big Mac, out of context, make this longer, give me five fries."))
        assertEquals(Commands.Split("See you soon", Cmd.Toggle(Feature.EMOJI, true)),
            Commands.split("See you soon. Out of context, enable emoji."))
        assertEquals(Commands.Split("", Cmd.Undo), Commands.split("out of context, undo"))
        assertNull(Commands.split("That quote was out of context."))
        assertNull(Commands.split("They took it out of context and ran with it"))
    }

    @Test fun scratchOnItsOwnIsUndo() {
        assert(Commands.isScratch("Scratch that!"))
        assert(Commands.isScratch("No, scrap that."))
        assert(!Commands.isScratch("Scratch that itch"))
        assertEquals(Cmd.Undo, Commands.parse("Out of context, scrap that."))
    }

    @Test fun normalDictationIsNotACommand() {
        assertNull(Commands.parse("I was out of context yesterday"))
        assertNull(Commands.parse("Hello there"))
        assertNull(Commands.feature("updates"))
    }
}
