package com.example.myapplication

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

data class AiResponsePayload(
    val answer: String,
    val thinkingProcess: String? = null
)

data class AiKeyConfig(
    val provider: String = "GEMINI", // "GEMINI" or "OPENROUTER"
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash",
    val label: String = "Primary Key"
)

object GeminiAiClient {
    private const val TAG = "AiEngineClient"

    // Verified Fast Working Default Models
    private const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
    private const val DEFAULT_OPENROUTER_MODEL = "google/gemini-2.0-flash-exp:free"

    private fun decodeSecret(encoded: String): String {
        return try {
            String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
        } catch (e: Exception) {
            ""
        }
    }

    // Built-in working fallback keys (decoded at runtime)
    private val BUILTIN_GEMINI_KEY: String by lazy { decodeSecret("QVEuQWI4Uk42S05MUUpvSnlDYnRkdm5fZlhpTUtFUXdiUUwtUENjNnFXT1lhc2Qxb29EV0E=") }
    private val BUILTIN_OPENROUTER_KEY: String by lazy { decodeSecret("c2stb3ItdjEtOTBkZjlkYzJiM2EzNzhjZDM2N2Y3NTkwZWEwMTFhZTI3YzFlYzk3NzQ2N2MzOWM4YTYwZTAyOGM0ZmVkMzNjZg==") }

    // Dynamic Cloud Pool State (Synced in Real-time from Firestore app_config/ai)
    private var dynamicProvider: String = "GEMINI"
    private var dynamicModel: String = DEFAULT_GEMINI_MODEL
    private var dynamicKeyPool: MutableList<AiKeyConfig> = mutableListOf()
    private var autoFailoverEnabled: Boolean = true

    /**
     * Real-time listener on Cloud Firestore 'app_config/ai'
     */
    fun initConfig() {
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("app_config").document("ai").addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    val rawProvider = snap.getString("provider") ?: "GEMINI"
                    dynamicProvider = rawProvider
                    val rawModel = snap.getString("model")
                    dynamicModel = if (!rawModel.isNullOrBlank() && !rawModel.contains("nemotron")) {
                        rawModel
                    } else {
                        if (dynamicProvider == "GEMINI") DEFAULT_GEMINI_MODEL else DEFAULT_OPENROUTER_MODEL
                    }
                    autoFailoverEnabled = snap.getBoolean("autoFailover") ?: true

                    val pool = mutableListOf<AiKeyConfig>()

                    // 1. Check structured keyPool array if present
                    @Suppress("UNCHECKED_CAST")
                    val rawPool = snap.get("keyPool") as? List<Map<String, Any>>
                    if (rawPool != null && rawPool.isNotEmpty()) {
                        for (item in rawPool) {
                            val prov = item["provider"] as? String ?: dynamicProvider
                            val key = item["apiKey"] as? String ?: ""
                            val mod = item["model"] as? String ?: dynamicModel
                            val lbl = item["label"] as? String ?: "Pool Key"
                            if (key.isNotBlank()) {
                                pool.add(AiKeyConfig(prov, key.trim(), mod.trim(), lbl))
                            }
                        }
                    }

                    // 2. Parse main apiKey
                    val rawSingleKey = snap.getString("apiKey")
                    if (!rawSingleKey.isNullOrBlank()) {
                        val splitKeys = rawSingleKey.split(",", "\n", ";").map { it.trim() }.filter { it.isNotBlank() }
                        for ((idx, k) in splitKeys.withIndex()) {
                            if (pool.none { it.apiKey == k }) {
                                val detectedProvider = if (k.startsWith("sk-or-")) "OPENROUTER" else "GEMINI"
                                val defaultMod = if (detectedProvider == "OPENROUTER") DEFAULT_OPENROUTER_MODEL else DEFAULT_GEMINI_MODEL
                                pool.add(AiKeyConfig(detectedProvider, k, dynamicModel.ifBlank { defaultMod }, "Key #${idx + 1}"))
                            }
                        }
                    }

                    // Ensure defaults are present in failover queue
                    if (pool.none { it.apiKey == BUILTIN_GEMINI_KEY }) {
                        pool.add(0, AiKeyConfig("GEMINI", BUILTIN_GEMINI_KEY, DEFAULT_GEMINI_MODEL, "Primary Google Gemini Direct"))
                    }
                    if (pool.none { it.apiKey == BUILTIN_OPENROUTER_KEY }) {
                        pool.add(AiKeyConfig("OPENROUTER", BUILTIN_OPENROUTER_KEY, DEFAULT_OPENROUTER_MODEL, "Failover OpenRouter"))
                    }

                    dynamicKeyPool = pool
                    Log.d(TAG, "AI Engine Pool updated: ${pool.size} keys active, autoFailover=$autoFailoverEnabled")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to listen to AI config: ${e.localizedMessage}")
        }
    }

    /**
     * Unified AI Response Generation returning clean answer and optional thinking process
     */
    suspend fun generateResponse(
        context: Context,
        systemInstruction: String,
        conversationHistory: List<ChatMessage>,
        userMessage: String
    ): AiResponsePayload = withContext(Dispatchers.IO) {
        val keysToTry = getEffectiveKeyQueue(context)

        if (keysToTry.isEmpty()) {
            return@withContext AiResponsePayload("⚠️ AI Engine is initializing. Please try again in a moment.")
        }

        var lastError: String? = null

        // Try keys sequentially with smart auto-failover
        for ((index, config) in keysToTry.withIndex()) {
            val key = config.apiKey
            val isOpRouter = config.provider.equals("OPENROUTER", ignoreCase = true) || key.startsWith("sk-or-")
            val model = if (config.model.contains("nemotron")) {
                if (isOpRouter) DEFAULT_OPENROUTER_MODEL else DEFAULT_GEMINI_MODEL
            } else config.model

            Log.d(TAG, "Attempting AI request with Key #${index + 1} (${config.label}, ${config.provider}, model=$model)...")

            val result = if (isOpRouter) {
                callOpenRouter(key, model, systemInstruction, conversationHistory, userMessage)
            } else {
                callGemini(key, model, systemInstruction, conversationHistory, userMessage)
            }

            when (result) {
                is AiResult.Success -> {
                    Log.d(TAG, "AI Request Succeeded with Key #${index + 1}!")
                    val (cleanAnswer, thinking) = splitThinkingAndAnswer(result.rawText)
                    return@withContext AiResponsePayload(cleanAnswer, thinking)
                }
                is AiResult.Failure -> {
                    lastError = result.errorMessage
                    Log.w(TAG, "Key #${index + 1} (${config.label}) failed: ${result.errorMessage}. Trying next backup endpoint...")
                }
            }
        }

        return@withContext AiResponsePayload("⚠️ All AI endpoints are temporarily busy.\nDetails: ${lastError ?: "Rate Limit"}\nPlease retry in a few seconds.")
    }

    private sealed class AiResult {
        data class Success(val rawText: String) : AiResult()
        data class Failure(val errorMessage: String, val isRateLimit: Boolean) : AiResult()
    }

    private val BACKUP_FREE_MODELS = listOf(
        "google/gemini-2.0-flash-exp:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "deepseek/deepseek-chat:free",
        "qwen/qwen-2.5-72b-instruct:free",
        "mistralai/mistral-7b-instruct:free"
    )

    /**
     * Google Gemini Direct REST API Caller (Fastest & Most Reliable)
     */
    private fun callGemini(
        apiKey: String,
        model: String,
        systemInstruction: String,
        conversationHistory: List<ChatMessage>,
        userMessage: String
    ): AiResult {
        val modelsToTry = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.0-flash-lite")

        for (m in modelsToTry) {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=$apiKey"

                val requestBody = JsonObject()

                val systemObj = JsonObject()
                val systemParts = JsonArray()
                val systemPart = JsonObject()
                systemPart.addProperty("text", systemInstruction)
                systemParts.add(systemPart)
                systemObj.add("parts", systemParts)
                requestBody.add("systemInstruction", systemObj)

                val contentsArray = JsonArray()
                val recentTurns = conversationHistory.takeLast(4)
                for (msg in recentTurns) {
                    if (msg.message.isBlank() || msg.message.startsWith("👋") || msg.message.startsWith("🔄")) continue
                    val turnObj = JsonObject()
                    turnObj.addProperty("role", if (msg.sender == "user") "user" else "model")
                    val parts = JsonArray()
                    val part = JsonObject()
                    part.addProperty("text", msg.message)
                    parts.add(part)
                    turnObj.add("parts", parts)
                    contentsArray.add(turnObj)
                }

                val currentTurn = JsonObject()
                currentTurn.addProperty("role", "user")
                val currentParts = JsonArray()
                val currentPart = JsonObject()
                currentPart.addProperty("text", userMessage)
                currentParts.add(currentPart)
                currentTurn.add("parts", currentParts)
                contentsArray.add(currentTurn)

                requestBody.add("contents", contentsArray)

                val genConfig = JsonObject()
                genConfig.addProperty("temperature", 0.4)
                genConfig.addProperty("maxOutputTokens", 4096)
                val thinkingConfig = JsonObject()
                thinkingConfig.addProperty("thinkingBudget", 0)
                genConfig.add("thinkingConfig", thinkingConfig)
                requestBody.add("generationConfig", genConfig)

                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.doInput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 16000

                val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
                writer.write(requestBody.toString())
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                    val responseStr = reader.readText()
                    reader.close()

                    val jsonResponse = Gson().fromJson(responseStr, JsonObject::class.java)
                    val candidates = jsonResponse.getAsJsonArray("candidates")
                    if (candidates != null && candidates.size() > 0) {
                        val firstCandidate = candidates.get(0).asJsonObject
                        val content = firstCandidate.getAsJsonObject("content")
                        val parts = content.getAsJsonArray("parts")
                        if (parts != null && parts.size() > 0) {
                            val rawText = parts.get(0).asJsonObject.get("text").asString.trim()
                            return AiResult.Success(rawText)
                        }
                    }
                } else {
                    val errorStream = conn.errorStream
                    val errorText = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream, "UTF-8")).readText()
                    } else "HTTP Error $responseCode"
                    Log.w(TAG, "Gemini $m returned $responseCode: $errorText. Trying next model...")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini request exception for $m: ${e.localizedMessage}")
            }
        }

        return AiResult.Failure("Google Gemini endpoints failed", false)
    }

    /**
     * OpenRouter API Caller (Failover)
     */
    private fun callOpenRouter(
        apiKey: String,
        model: String,
        systemInstruction: String,
        conversationHistory: List<ChatMessage>,
        userMessage: String
    ): AiResult {
        val modelsToTry = mutableListOf<String>()
        val primary = if (model.isNotBlank() && !model.contains("gemini-2.0-flash-exp") && !model.contains("nemotron")) model else DEFAULT_OPENROUTER_MODEL
        modelsToTry.add(primary)
        for (m in BACKUP_FREE_MODELS) {
            if (!modelsToTry.contains(m)) modelsToTry.add(m)
        }

        var lastError = "No response"
        var lastRateLimit = false

        for (m in modelsToTry) {
            try {
                val endpoint = "https://openrouter.ai/api/v1/chat/completions"
                val requestBody = JsonObject()
                requestBody.addProperty("model", m)

                val messagesArray = JsonArray()

                val sysObj = JsonObject()
                sysObj.addProperty("role", "system")
                sysObj.addProperty("content", systemInstruction)
                messagesArray.add(sysObj)

                // Multi-turn history (last 4 turns)
                val recentTurns = conversationHistory.takeLast(4)
                for (msg in recentTurns) {
                    if (msg.message.isBlank() || msg.message.startsWith("👋") || msg.message.startsWith("🔄")) continue
                    val turnObj = JsonObject()
                    turnObj.addProperty("role", if (msg.sender == "user") "user" else "assistant")
                    turnObj.addProperty("content", msg.message)
                    messagesArray.add(turnObj)
                }

                val currentObj = JsonObject()
                currentObj.addProperty("role", "user")
                currentObj.addProperty("content", userMessage)
                messagesArray.add(currentObj)

                requestBody.add("messages", messagesArray)
                requestBody.addProperty("temperature", 0.4)
                requestBody.addProperty("max_tokens", 3000)

                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                conn.setRequestProperty("HTTP-Referer", "https://bachelorswallet.app")
                conn.setRequestProperty("X-Title", "Bachelors Wallet")
                conn.doOutput = true
                conn.doInput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 16000

                val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
                writer.write(requestBody.toString())
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                    val responseStr = reader.readText()
                    reader.close()

                    val jsonResponse = Gson().fromJson(responseStr, JsonObject::class.java)
                    val choices = jsonResponse.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val messageObj = choices.get(0).asJsonObject.getAsJsonObject("message")
                        if (messageObj != null && messageObj.has("content")) {
                            val content = messageObj.get("content").asString.trim()
                            if (content.isNotBlank()) {
                                return AiResult.Success(content)
                            }
                        }
                    }
                } else {
                    val errorStream = conn.errorStream
                    val errorText = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream, "UTF-8")).readText()
                    } else "HTTP Error $responseCode"
                    val isRateLimit = responseCode == 429 || responseCode == 402 || responseCode == 503
                    lastError = "OpenRouter ($m) Error ($responseCode): $errorText"
                    lastRateLimit = isRateLimit
                }
            } catch (e: Exception) {
                lastError = "OpenRouter exception ($m): ${e.localizedMessage}"
            }
        }

        return AiResult.Failure(lastError, lastRateLimit)
    }

    /**
     * Splits thinking process and final clean answer
     */
    private fun splitThinkingAndAnswer(raw: String): Pair<String, String?> {
        var text = raw.trim()
        var thinking: String? = null

        // 1. Check for XML think/thought tags
        val thinkMatch = Regex("<think>([\\s\\S]*?)</think>", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("<thought>([\\s\\S]*?)</thought>", RegexOption.IGNORE_CASE).find(text)

        if (thinkMatch != null) {
            thinking = thinkMatch.groupValues[1].trim()
            text = text.replace(thinkMatch.value, "").trim()
        }

        // 2. Check for "Here's a thinking process:" markdown preambles
        if (text.startsWith("Here's a thinking process", ignoreCase = true) || text.startsWith("Thinking process", ignoreCase = true)) {
            val splitIndex = text.indexOf("\n\n")
            if (splitIndex != -1) {
                val candidateThinking = text.substring(0, splitIndex).trim()
                val candidateAnswer = text.substring(splitIndex + 2).trim()

                if (candidateAnswer.isNotBlank()) {
                    if (thinking == null) thinking = candidateThinking
                    text = candidateAnswer
                }
            }
        }

        // 3. Fallback: If text still starts with numbered analysis lines, strip to clean answer
        val lines = text.lines()
        val firstRealLineIndex = lines.indexOfFirst { line ->
            val l = line.trim()
            l.isNotBlank() &&
            !l.startsWith("Here's a thinking", ignoreCase = true) &&
            !l.startsWith("Thinking process", ignoreCase = true) &&
            !l.startsWith("Analyze User Input", ignoreCase = true) &&
            !l.startsWith("1. Analyze", ignoreCase = true) &&
            !l.startsWith("2. Determine", ignoreCase = true) &&
            !l.startsWith("3. Formulate", ignoreCase = true) &&
            !l.startsWith("4. Synthesize", ignoreCase = true) &&
            !l.startsWith("4. Draft Response", ignoreCase = true)
        }

        if (firstRealLineIndex > 0) {
            if (thinking == null) {
                thinking = lines.subList(0, firstRealLineIndex).joinToString("\n").trim()
            }
            text = lines.subList(firstRealLineIndex, lines.size).joinToString("\n").trim()
        }

        return Pair(text.ifBlank { raw }, thinking)
    }

    /**
     * Builds effective list of keys to try in order
     */
    private suspend fun getEffectiveKeyQueue(context: Context): List<AiKeyConfig> {
        val list = mutableListOf<AiKeyConfig>()

        // 1. Cloud Key Pool
        if (dynamicKeyPool.isNotEmpty()) {
            for (k in dynamicKeyPool) {
                if (list.none { it.apiKey == k.apiKey }) {
                    list.add(k)
                }
            }
        }

        // 2. Guaranteed Default Direct Gemini Key & OpenRouter
        if (list.none { it.apiKey == BUILTIN_GEMINI_KEY }) {
            list.add(0, AiKeyConfig("GEMINI", BUILTIN_GEMINI_KEY, DEFAULT_GEMINI_MODEL, "Primary Gemini Direct"))
        }
        if (list.none { it.apiKey == BUILTIN_OPENROUTER_KEY }) {
            list.add(AiKeyConfig("OPENROUTER", BUILTIN_OPENROUTER_KEY, DEFAULT_OPENROUTER_MODEL, "Failover OpenRouter"))
        }

        return list
    }
}
