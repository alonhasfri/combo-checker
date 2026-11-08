package com.voicebubble

import com.voicebubble.Commands.Cmd
import com.voicebubble.Commands.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioTest {
    private val rate = 16000
    private fun quiet(sec: Double) = FloatArray((rate * sec).toInt()) { if (it % 2 == 0) 0.001f else -0.001f }
    private fun voice(sec: Double) = FloatArray((rate * sec).toInt()) { (0.3 * sin(2 * PI * 220 * it / rate)).toFloat() }
    private fun join(vararg parts: FloatArray) = parts.reduce { a, b -> a + b }

    @Test fun cutsAtThePauseAfterSpeech() {
        val a = join(quiet(1.0), voice(4.0), quiet(1.0), voice(2.0))
        assertEquals(52 * Audio.FRAME, Audio.findCut(a))
    }

    @Test fun noCutWithoutSpeechOrPause() {
        assertEquals(-1, Audio.findCut(quiet(6.0)))
        assertEquals(-1, Audio.findCut(join(quiet(0.5), voice(5.0))))
    }

    @Test fun trimsSilence() {
        val a = join(quiet(1.0), voice(4.0), quiet(1.0), voice(2.0))
        // 0.2 s kept before, the 1 s pause shortened to 0.4 s, nothing after the last word.
        assertEquals(66 * Audio.FRAME, Audio.trimSilence(a).size)
    }
}

class LearnTest {
    @Test fun spotsAFix() {
        assertEquals("Gojo", Learn.correction("I watched Go Joe fight", "I watched Gojo fight", 0, 22))
        assertEquals("Sukuna", Learn.correction("see you at sukuna", "see you at Sukuna", 0, 17))
    }

    @Test fun ignoresOtherEdits() {
        assertNull(Learn.correction("Hello", "Hello there", 0, 5))
        assertNull(Learn.correction("Hi. I watched Go Joe", "Hey. I watched Go Joe", 4, 20))
        assertNull(Learn.correction("one two three four five", "a totally different sentence here", 0, 23))
    }
}

class CommandWordTest {
    @Test fun customWord() {
        assertEquals(Cmd.Undo, Commands.parse("Hey bubble, undo", "hey bubble"))
        assertEquals(Commands.Split("call me later", Cmd.Toggle(Feature.EMOJI, true)),
            Commands.split("call me later, hey bubble, enable emoji", "hey bubble"))
        assertNull(Commands.parse("Out of context, undo", "hey bubble"))
    }

    @Test fun summariseAndStyledReply() {
        assertEquals(Cmd.Summarize(""), Commands.parse("Out of context, summarise this chat."))
        assertEquals(Cmd.Reply("in gen z"), Commands.parse("out of context, reply in gen z"))
    }
}

class AdaptTest {
    @Test fun spotsNames() {
        assertEquals(listOf("Sukuna", "Gojo"), Adapt.nameCandidates("I talked to Sukuna and Gojo today. Then we left."))
        assertEquals(listOf("Holmes Place"), Adapt.nameCandidates("Talk to Holmes Place now"))
        assertEquals(emptyList<String>(), Adapt.nameCandidates("Hello there. How are you?"))
    }

    @Test fun fixPair() {
        assertEquals("Go Joe" to "Gojo", Learn.correctionPair("I watched Go Joe fight", "I watched Gojo fight", 0, 22))
    }
}

class PersonaTest {
    @Test fun oneTimeAndLasting() {
        assertEquals(Cmd.Rewrite("Make me sound like Shakespeare"), Commands.parse("Out of context, make me sound like Shakespeare."))
        assertEquals(Commands.Split("Could I get a Big Mac", Cmd.Rewrite("Make me sound like Rouxls Kaard")),
            Commands.split("Could I get a Big Mac, out of context, make me sound like Rouxls Kaard."))
        assertEquals(Cmd.Rewrite("Make me sound like a pirate"), Commands.parse("out of context, in the style of a pirate"))
        assertEquals(Cmd.Persona("Rouxls Kaard"), Commands.parse("Out of context, talk like Rouxls Kaard from now on."))
        assertEquals(Cmd.Persona("Gojo"), Commands.parse("out of context, always talk like Gojo"))
        assertEquals(Cmd.Persona(null), Commands.parse("Out of context, talk normally."))
        assertEquals(Cmd.Persona(null), Commands.parse("out of context, stop talking like Shakespeare"))
        assertEquals(Cmd.Rewrite("make this shorter"), Commands.parse("Out of context, make this shorter"))
    }
}

class LooseCommandsTest {
    @Test fun talkLikeAfterText() {
        assertEquals(Commands.Split("easy you're so bad quit the game", Cmd.Rewrite("Make me sound like Rouxls Kaard")),
            Commands.split("easy you're so bad quit the game hey bubble make me talk like Rouxls Kaard", "hey bubble"))
    }

    @Test fun anyInstructionIsARewrite() {
        assertEquals(Commands.Split("I love pizza", Cmd.Rewrite("turn this into a rap")),
            Commands.split("I love pizza, hey bubble, turn this into a rap.", "hey bubble"))
        assertEquals(Cmd.Rewrite("add more emojis"), Commands.parse("out of context, add more emojis"))
        // Still not a command when it isn't an instruction.
        assertNull(Commands.split("That quote was taken out of context by them"))
    }
}
