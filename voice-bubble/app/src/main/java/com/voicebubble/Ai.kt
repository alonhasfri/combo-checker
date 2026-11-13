package com.voicebubble

import android.content.Context
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.MessageCreateParams
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

/**
 * Optional AI features (rewrite, translate, reply suggestions) with your own key:
 * a free Google Gemini key (starts with "AIza", from aistudio.google.com), or an Anthropic key ("sk-ant-").
 * Only the text being rewritten/translated (or the visible chat, for replies) is sent.
 */
object Ai {
    class Failure(message: String) : Exception(message)

    fun key(c: Context) = c.getSharedPreferences("voice", Context.MODE_PRIVATE).getString("aiKey", "")!!.trim()
    fun setKey(c: Context, k: String) = c.getSharedPreferences("voice", Context.MODE_PRIVATE).edit().putString("aiKey", k.trim()).apply()
    fun hasKey(c: Context) = key(c).isNotEmpty()
    fun provider(c: Context) = when {
        key(c).startsWith("sk-ant") -> "Claude"
        key(c).startsWith("sk-or-") -> "OpenRouter"
        key(c).isNotEmpty() -> "Gemini"
        else -> ""
    }

    private fun limitNote(maxChars: Int?) =
        if (maxChars != null && maxChars > 0) "\n\nHard limit: at most $maxChars characters in total. Count them; shorten it if needed." else ""

    /** Names for the length choice, shown in the app. */
    val lengths = listOf("" to "As I said it", "auto" to "Auto (AI decides)", "short" to "Short", "medium" to "Medium", "long" to "Long")
    fun lengthName(v: String) = lengths.firstOrNull { it.first == v }?.second ?: "As I said it"

    private fun lengthNote(c: Context, kind: String): String = when (Prefs(c).aiLength) {
        "short" -> when (kind) {
            "reply" -> "\n\nLength: short. Each reply is a few words, like a quick text."
            "summary" -> "\n\nLength: short. Just 2 bullet points."
            else -> "\n\nLength: SHORT. The result must be shorter than the text: one or two brief sentences, no more."
        }
        "medium" -> when (kind) {
            "reply" -> "\n\nLength: medium. Each reply is one full sentence."
            "summary" -> "\n\nLength: medium. 3 or 4 bullet points."
            else -> "\n\nLength: medium. Keep the result about as long as the text; don't add sentences."
        }
        "long" -> when (kind) {
            "reply" -> "\n\nLength: long. Each reply is 2 or 3 sentences."
            "summary" -> "\n\nLength: long. 5 to 7 bullet points with details."
            else -> "\n\nLength: long. Make the result about twice as long as the text: add natural detail and flair, without changing what they meant."
        }
        // The AI picks: the character's way of talking and what the message is for.
        "auto" -> when (kind) {
            "reply" -> "\n\nLength: you decide what fits each reply: how the person or character would really text, and what the chat needs."
            "summary" -> "\n\nLength: you decide how many bullet points the conversation needs (2 to 7)."
            else -> "\n\nLength: you decide, but stay reasonable for a chat message. A character who rambles can add a little; " +
                "a curt one stays brief; a quick message stays quick. Never more than about twice as long as the text."
        }
        else -> ""
    }

    /**
     * The longest a rewrite may be, from the length setting (null = no cap). The AI is told this number, and
     * anything still longer is cut at the end of a sentence, because AIs love to add more than asked.
     */
    fun lengthCap(c: Context, textLen: Int): Int? = when (Prefs(c).aiLength) {
        "short" -> maxOf(50, (textLen * 0.9).toInt())
        "medium" -> maxOf(80, (textLen * 1.3).toInt())
        "auto" -> maxOf(110, (textLen * 2.2).toInt())
        "long" -> null
        else -> maxOf(90, (textLen * 1.8).toInt())   // "as I said it": about as long as what you said
    }

    /** Roblox hides (####) anything its chat filter doesn't like: the AI writes so it gets through. */
    private fun chatNote(c: Context): String =
        if (runCatching { AppDetector.current(c) == Limit.ROBLOX }.getOrDefault(false))
            "\n\nThis goes into Roblox chat, whose filter hides (####) messages it doesn't like. Keep it clean so it gets " +
                "through: no swear words (not even mild ones), no insults (idiot, stupid, dumb), no violent words (kill, die, " +
                "dead, blood), no digits or numbers, no links, emails, usernames or app names (Discord, Snapchat…), " +
                "no square brackets, \$ signs or odd spellings. Keep the character's voice with clean words."
        else ""

    fun rewrite(c: Context, text: String, instruction: String, maxChars: Int? = null): String {
        // A one-off "make this shorter" wins over the lasting length.
        val ownLength = Regex("(?i)\\b(short|long|length|brief|expand)").containsMatchIn(instruction)
        val cap = listOfNotNull(maxChars, if (ownLength) null else lengthCap(c, text.length)).minOrNull()
        val system = "You rewrite a chat message someone dictated by voice, before they send it. You are NOT chatting with " +
            "them: never answer the message, reply to it or react to it. Your output is the SAME message (same meaning, " +
            "same requests, questions, facts, names, times and places), just rewritten the way the instruction says. " +
            "Every point in the message must still be in your version, and nothing new added (no extra events, stories " +
            "or details). If the instruction is to sound like a person, character or style, say THEIR message the way " +
            "that character would say it: their grammar, typical words, habits and catchphrases woven into what was " +
            "said; a few touches, natural and easy to read, not a quirk on every word, not just catchphrases on their own. " +
            "Reply with only the rewritten message: no quotes, no explanation.\n\nInstruction: $instruction"
        fun user(extra: String) = "The message to rewrite:\n<message>\n$text\n</message>" +
            (if (ownLength) "" else lengthNote(c, "rewrite")) + limitNote(cap) + chatNote(c) + extra +
            "\n\nNow write that same message, rewritten as instructed. Only the rewritten message."
        var out = ask(c, system, user("")).let { clean(it) }
        // The AI said its own thing instead of your message: once more, firmly.
        if (!keepsMeaning(text, out)) {
            val again = runCatching { clean(ask(c, system, user("\n\nImportant: keep every point of the message. Don't " +
                "replace it with the character's usual lines; rewrite what it says."))) }.getOrNull()
            if (again != null && keepsMeaning(text, again)) out = again
        }
        return if (cap != null && out.length > cap) Limit.fit(out, cap).first else out
    }

    private fun clean(s: String) = s.replace("<message>", "").replace("</message>", "").trim().removeSurrounding("\"")

    private val small = setOf("that", "this", "with", "have", "from", "they", "them", "what", "when", "will", "your",
        "just", "like", "about", "there", "their", "were", "been", "would", "could", "should", "into", "then", "than",
        "want", "really", "going", "gonna", "okay", "yeah", "some", "make", "know", "also", "very", "please")

    /**
     * Rough check that a rewrite still says what you said: most of your meaningful words (or their first letters,
     * since characters bend words: "textedeth") are still in it.
     */
    fun keepsMeaning(original: String, rewrite: String): Boolean {
        val words = original.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 && it !in small }.distinct()
        if (words.size < 2) return true
        val out = rewrite.lowercase()
        val kept = words.count { w -> out.contains(w.take(4)) || (w.all { it.isDigit() } && out.contains(w)) }
        return kept >= words.size * 0.4
    }

    fun translate(c: Context, text: String, language: String, maxChars: Int? = null): String = cleanTranslation(ask(c,
        "You translate text someone dictated by voice; it gets sent as a chat message. Keep the tone, names and emoji. " +
            "Reply with only the translated message itself: no label or heading (never \"Message in $language:\" or " +
            "\"Translation:\"), no quotes, no explanation.",
        "Translate the text between the tags into $language.\n<text>\n$text\n</text>" + limitNote(maxChars) + chatNote(c)), language)

    /** Removes labels some AIs add anyway ("Message in Hebrew:", "Translation:", "**Hebrew:**", <text> tags). */
    fun cleanTranslation(out: String, language: String): String {
        var t = out.replace("<text>", "").replace("</text>", "").replace("**", "").trim()
        val lang = Regex.escape(language)
        val label = Regex("(?i)^(?:here(?:'s| is)[^:\n]{0,60}|(?:the )?(?:translated )?(?:translation|message|text)[^:\n]{0,40}|" +
            "[^:\n]{0,30}\\b$lang\\b[^:\n]{0,20}|$lang|הודעה[^:\n]{0,30}|תרגום[^:\n]{0,30})\\s*[:：]\\s*")
        repeat(2) {
            // A label on its own line, or in front of the text.
            val lines = t.lines()
            val first = lines[0].trim()
            if (lines.size > 1 && first.length <= 60 && (first.endsWith(":") ||
                    Regex("(?i)^(?:here(?:'s| is)\\b.*|(?:the )?(?:translation|translated)\\b.*|.*\\b(?:message|text|translation) (?:in|into) $lang\\b.*|$lang\\.?)$").matches(first)))
                t = lines.drop(1).joinToString("\n").trim()
            t = label.replaceFirst(t, "").trim()
            // "Message in Hebrew שלום" with no colon.
            t = Regex("(?i)^(?:the )?(?:message|translation|text) (?:in|into) $lang\\b\\s*[-–—]?\\s*").replaceFirst(t, "").trim()
        }
        return t.trim().removeSurrounding("\"").removeSurrounding("“", "”").trim()
    }

    /**
     * Fixes speech-recognition mistakes ("their" for "there", a misheard name) without rewording anything.
     * [words]: names and words the person uses, so they're spelled right.
     */
    fun fixMistakes(c: Context, text: String, words: String): String {
        val out = ask(c,
            "You correct speech-to-text output. The text was dictated by voice and may contain words the recogniser " +
                "misheard: a wrong word that sounds like the right one, a split or merged word, a misspelled name, wrong " +
                "punctuation. Fix only those, using the context to tell what was really said. Do not reword, shorten, " +
                "add anything, answer it, or change the style, slang or swearing. If nothing is wrong, return it unchanged. " +
                "Reply with only the corrected text.",
            (if (words.isNotBlank()) "Names and words this person uses: $words\n\n" else "") + "<text>\n$text\n</text>")
            .replace("<text>", "").replace("</text>", "").trim().removeSurrounding("\"")
        // A real fix changes a few words; anything very different means the AI went off and did something else.
        val ratio = out.length.toFloat() / text.length.coerceAtLeast(1)
        return if (out.isBlank() || ratio < 0.7f || ratio > 1.4f) text else out
    }

    /** Designs a bubble skin from a description: a small drawing as JSON (see [SkinArt]). */
    fun designSkin(c: Context, description: String): String = ask(c, SkinArt.AI_FORMAT, "Design a bubble skin: $description", longAnswer = true)

    /** Three short replies the user could send next in the chat on screen. */
    fun replies(c: Context, chat: String, instruction: String): List<String> {
        val out = ask(c,
            "You help someone answer a chat on their phone. You see the text visible on their screen " +
                "(it may include buttons and other noise; focus on the conversation). Suggest exactly 3 different " +
                "short replies they could send next, written as they would text. Their request may say what to reply, " +
                "how to say it (a style like gen z slang, formal or flirty, or a character to sound like), or both: follow it. " +
                "One reply per line, no numbering, no quotes.",
            (if (instruction.isNotBlank()) "My request: $instruction\n\n" else "") + "Screen:\n$chat" + lengthNote(c, "reply") +
                chatNote(c) + (if (runCatching { AppDetector.current(c) == Limit.ROBLOX }.getOrDefault(false))
                    "\nEach reply at most ${Limit.ROBLOX_CHARS} characters." else ""))
        return out.lines().map { it.trim().removePrefix("-").removePrefix("•").trim().trim('"') }
            .filter { it.isNotEmpty() }.take(3)
    }

    /** A short summary of the conversation on screen. */
    fun summary(c: Context, chat: String, instruction: String): String = ask(c,
        "You summarise what's on someone's phone screen, usually a chat. Ignore buttons and other noise. " +
            "Give 2 to 5 short bullet points (start each with •) covering what was said and anything they need to " +
            "answer or do. Plain text only.",
        (if (instruction.isNotBlank()) "My request: $instruction\n\n" else "") + "Screen:\n$chat" + lengthNote(c, "summary"))

    /** Blocking call: run it off the main thread. Throws [Failure] with a message you can show. */
    private fun ask(c: Context, system: String, user: String, longAnswer: Boolean = false): String {
        val k = key(c)
        if (k.isEmpty()) throw Failure("Add an AI key first (Settings → AI)")
        val text = when {
            k.startsWith("sk-ant") -> claude(k, system, user, Prefs(c).aiFast)
            k.startsWith("sk-or-") -> openRouter(k, system, user, longAnswer)
            else -> gemini(k, system, user)
        }
        return text.trim().ifEmpty { throw Failure("The AI sent back nothing. Try again") }
    }

    private fun claude(key: String, system: String, user: String, fast: Boolean): String {
        val client = AnthropicOkHttpClient.builder().apiKey(key).timeout(Duration.ofSeconds(60)).build()
        try {
            val builder = MessageCreateParams.builder()
                .model(if (fast) "claude-haiku-5-5" else "claude-opus-5-5")
                .maxTokens(4000L)
                .system(system)
                .addUserMessage(user)
                // Short edits don't need deep thinking: low effort keeps it quick and cheap.
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
            // If Claude Opus declines, the server retries on a fallback model by itself (not offered for Haiku).
            if (!fast) builder.addBeta(AnthropicBeta.of("server-side-fallback-2026-07-01"))
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            val params = builder.build()
            val msg = client.beta().messages().create(params)
            if (msg.stopReason().map { it.toString() }.orElse("") == "refusal") throw Failure("Claude declined this one")
            return msg.content().mapNotNull { b -> b.text().orElse(null)?.text() }.joinToString("")
        } catch (e: Failure) {
            throw e
        } catch (e: UnauthorizedException) {
            throw Failure("That Claude key doesn't work. Check it in Settings → AI")
        } catch (e: RateLimitException) {
            throw Failure("Too many requests right now. Wait a moment and try again")
        } catch (e: AnthropicServiceException) {
            throw Failure("Claude error ${e.statusCode()}")
        } catch (e: Exception) {
            throw Failure("No connection to Claude. Try again")
        } finally {
            runCatching { client.close() }
        }
    }

    /**
     * OpenRouter (the free sk-or- key Comboed uses). Free models come and go and get busy, so a few
     * quick text models are tried in order. "Thinking" is switched off: it only adds waiting time
     * for rewriting or translating a sentence.
     */
    private val openRouterModels = listOf(
        "google/gemma-4-26b-a4b-it:free",      // fast (small active part), good at creative rewrites
        "nvidia/nemotron-3.5-lightning:free",  // built for speed
        "openrouter/free",                     // OpenRouter picks any free model
    )

    private fun openRouter(key: String, system: String, user: String, longAnswer: Boolean = false): String {
        val maxTokens = if (longAnswer) 3000 else (user.length * 2 + 300).coerceAtMost(1500)
        // Free models are often slow only because they're busy: ask two at once and take the first answer.
        val racers = openRouterModels.take(2)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(racers.size)
        val done = java.util.concurrent.ExecutorCompletionService<String?>(pool)
        val authError = java.util.concurrent.atomic.AtomicReference<Failure?>(null)
        racers.forEach { m ->
            done.submit(java.util.concurrent.Callable<String?> {
                try { openRouterOnce(key, m, system, user, maxTokens) } catch (e: Failure) { authError.set(e); null }
            })
        }
        try {
            repeat(racers.size) {
                val r = done.take().get()
                if (!r.isNullOrBlank()) return r
                authError.get()?.let { throw it }
            }
        } finally {
            pool.shutdownNow()
        }
        // Both busy or gone: let OpenRouter pick any free model.
        return openRouterOnce(key, openRouterModels.last(), system, user, maxTokens)
            ?: throw Failure("OpenRouter's free models are busy. Try again in a moment")
    }

    /** One request to one model. Null when that model is busy or unavailable; throws for a bad key. */
    private fun openRouterOnce(key: String, model: String, system: String, user: String, maxTokens: Int): String? {
        for (noThinking in listOf(true, false)) {
            try {
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", maxTokens)
                    .put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", user)))
                if (noThinking) body.put("reasoning", JSONObject().put("enabled", false))
                val conn = URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10000
                conn.readTimeout = 40000
                conn.doOutput = true
                conn.setRequestProperty("content-type", "application/json")
                conn.setRequestProperty("authorization", "Bearer $key")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
                if (code == 401 || code == 403) throw Failure("That OpenRouter key doesn't work. Check it in Settings → AI")
                // Some models insist on thinking: try once more without switching it off.
                if (code == 400 && noThinking) continue
                if (code !in 200..299) return null
                return JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
                    .takeIf { it.isNotBlank() }
            } catch (e: Failure) {
                throw e
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    private fun gemini(key: String, system: String, user: String): String {
        try {
            val body = JSONObject()
                .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", user)))))
                // No "thinking" first: it adds seconds and isn't needed to rewrite or translate a sentence.
                .put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
            // Flash-Lite: Google's quickest model, plenty for rewriting and translating.
            val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            if (code == 429) throw Failure("Gemini's free limit is reached. Wait a minute and try again")
            if (code == 400 || code == 403) throw Failure("That Gemini key doesn't work. Check it in Settings → AI")
            if (code !in 200..299) throw Failure("Gemini error $code")
            val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            return sb.toString()
        } catch (e: Failure) {
            throw e
        } catch (e: Exception) {
            throw Failure("No connection to Gemini. Try again")
        }
    }
}
