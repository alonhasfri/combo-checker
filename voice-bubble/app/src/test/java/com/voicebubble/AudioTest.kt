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
        assertEquals(Cmd.Rewrite("Make me sound like ${Royal.NAME}"), Commands.parse("Out of context, make me sound like Shakespeare."))
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

class FuzzyCommandWordTest {
    @Test fun misheardCommandWord() {
        assertEquals(Cmd.Undo, Commands.parse("Hey bubbles, undo", "hey bubble"))
        assertEquals(Cmd.Undo, Commands.parse("A bubble, undo.", "hey bubble"))
        assertEquals(Cmd.Toggle(Feature.EMOJI, true), Commands.parse("Hay Bubble enable emoji", "hey bubble"))
        assertEquals(Cmd.Undo, Commands.parse("Um, hey bobble, undo", "hey bubble"))
        assertEquals(Commands.Split("Nice one", Cmd.Rewrite("Make me sound like Spamton")),
            Commands.split("Nice one. Hey bubbles make me sound like Spamton", "hey bubble"))
    }

    @Test fun notTriggeredByLookalikes() {
        assertNull(Commands.split("I want a double, make it spicy", "hey bubble"))
        assertNull(Commands.parse("Hey buddy, undo", "hey bubble"))
    }
}

class DictionaryFixesTest {
    private val dict = listOf("Gojo", "Spamton", "Sukuna")
    private fun d(s: String) = Words.applyDictionary(s, dict)

    @Test fun splitWrongly() {
        assertEquals("I like Gojo", d("I like Go Joe"))
        assertEquals("Spamton is back", d("Spam tun is back"))
    }

    @Test fun possessive() {
        assertEquals("Gojo's eyes", d("gojo's eyes"))
        assertEquals("Sukuna's domain", d("Sakuna's domain"))
    }

    @Test fun normalWordsStay() {
        assertEquals("We go to school", d("We go to school"))
        assertEquals("Go home", d("Go home"))
    }
}

class RoyalTest {
    @Test fun instant() {
        assertEquals("Pray tell, peasant, art thou coming to the party? We deem 'tis most fun.",
            Royal.apply("Are you coming to the party? I think it is very fun."))
        assertEquals("Good morrow, We thank thee for thy aid.", Royal.apply("Hello, thank you for your help."))
        assertEquals("Nay, We have no gold.", Royal.apply("No, I have no money."))
        assertEquals("Hark, peasant! We require victuals at once.", Royal.apply("I need food right now."))
    }

    @Test fun turningCharacterModeOff() {
        assertEquals(Cmd.Persona(null), Commands.parse("Out of context, make me sound normal."))
        assertEquals(Cmd.Persona(null), Commands.parse("out of context, back to normal"))
        assertEquals(Cmd.Persona(null), Commands.parse("Hey bubble, make me talk normally", "hey bubble"))
        assertEquals(Cmd.Persona(null), Commands.parse("out of context, stop the character"))
    }
}

class RoyalModeTest {
    @Test fun switching() {
        assertEquals(Cmd.RoyalMode(true), Commands.parse("Hey bubble, offline Shakespeare.", "hey bubble"))
        assertEquals(Cmd.RoyalMode(true), Commands.parse("Hey bubble, switch to instant royal", "hey bubble"))
        assertEquals(Cmd.RoyalMode(false), Commands.parse("Hey bubble, AI royal", "hey bubble"))
        assertEquals(Cmd.RoyalMode(false), Commands.parse("hey bubble switch to the better Shakespeare one", "hey bubble"))
        assertEquals(Cmd.RoyalMode(null), Commands.parse("Hey bubble, royal mode.", "hey bubble"))
        assertEquals(Cmd.RoyalMode(null), Commands.parse("hey bubble medieval English", "hey bubble"))
        assertEquals(Cmd.Persona(Royal.NAME), Commands.parse("Hey bubble, talk like a royal from now on", "hey bubble"))
        assertEquals(Cmd.Persona(null), Commands.parse("Hey bubble, stop royal mode", "hey bubble"))
        assertEquals(Cmd.Persona(null), Commands.parse("Hey bubble, back to normal.", "hey bubble"))
    }
}

class LimitTest {
    @Test fun cutsBetweenWordsOrSentences() {
        assertEquals("short" to null, Limit.fit("short", 200))
        val (a, b) = Limit.fit("I love this game. It is so fun to play with my friends every day", 30)
        assertEquals("I love this game.", a)
        assertEquals("It is so fun to play with my friends every day", b)
        val (c, d) = Limit.fit("one two three four five six seven", 20)
        assertEquals("one two three four", c)
        assertEquals("five six seven", d)
    }
}

class LengthCommandTest {
    @Test fun lastingLengths() {
        assertEquals(Cmd.Length("short"), Commands.parse("Out of context, short."))
        assertEquals(Cmd.Length("short"), Commands.parse("out of context short mode"))
        assertEquals(Cmd.Length("short"), Commands.parse("Out of context, keep it short."))
        assertEquals(Cmd.Length("medium"), Commands.parse("out of context, medium length"))
        assertEquals(Cmd.Length("long"), Commands.parse("Out of context, long."))
        assertEquals(Cmd.Length("long"), Commands.parse("out of context, long answers from now on"))
        assertEquals(Cmd.Length(""), Commands.parse("out of context, normal length"))
        assertEquals(Cmd.Length(""), Commands.parse("out of context, stop short mode"))
    }

    @Test fun oneOffRewritesStay() {
        assertEquals(Cmd.Rewrite("make this shorter"), Commands.parse("Out of context, make this shorter"))
        assertEquals(Cmd.Rewrite("make it short"), Commands.parse("Out of context, make it short"))
        assertEquals(Cmd.Persona(null), Commands.parse("Out of context, normal."))
    }

    @Test fun afterText() {
        assertEquals(Commands.Split("I'm heading home now", Cmd.Length("short")),
            Commands.split("I'm heading home now, out of context, short."))
    }
}
