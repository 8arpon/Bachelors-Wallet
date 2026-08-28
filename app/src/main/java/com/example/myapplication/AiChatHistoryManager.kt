package com.example.myapplication

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Conversation",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var messages: MutableList<ChatMessage> = mutableListOf()
)

object AiChatHistoryManager {
    private const val PREF_NAME = "ai_chat_sessions_prefs"
    private const val KEY_SESSIONS = "saved_chat_sessions"
    private const val MAX_SESSIONS = 50 // Store up to 50 conversations (like Gemini/ChatGPT)
    private const val MAX_DAYS_IN_MILLIS = 30L * 24 * 60 * 60 * 1000L // 30 Days Retention

    val defaultWelcomeMessage = ChatMessage(
        sender = "ai",
        message = "👋 Assalamu Alaikum! I am your personal **Bachelors AI Financial Agent** 🤖\n\nI have real-time access to your transactions, expenses, debts, and budgets. Ask me anything in Bangla, English, or Banglish!"
    )

    /**
     * Retrieves all saved conversation sessions, pruned to max 50 and last 30 days
     */
    fun getAllSessions(context: Context): List<ChatSession> {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
            val type = object : TypeToken<List<ChatSession>>() {}.type
            val list: List<ChatSession> = Gson().fromJson(json, type) ?: return emptyList()

            val cutoff = System.currentTimeMillis() - MAX_DAYS_IN_MILLIS
            list.filter { it.updatedAt >= cutoff }.sortedByDescending { it.updatedAt }.take(MAX_SESSIONS)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Saves or updates a conversation session
     */
    fun saveSession(context: Context, session: ChatSession) {
        try {
            val sessions = getAllSessions(context).toMutableList()
            val index = sessions.indexOfFirst { it.id == session.id }

            // Auto-generate meaningful session title from first user query if still default
            if (session.title == "New Conversation" || session.title.isBlank()) {
                val firstUserMsg = session.messages.firstOrNull { it.sender == "user" }
                if (firstUserMsg != null) {
                    val clean = firstUserMsg.message.trim().take(40)
                    session.title = if (clean.length >= 40) "$clean..." else clean
                }
            }

            session.updatedAt = System.currentTimeMillis()

            if (index != -1) {
                sessions[index] = session
            } else {
                sessions.add(0, session)
            }

            val cutoff = System.currentTimeMillis() - MAX_DAYS_IN_MILLIS
            val pruned = sessions.filter { it.updatedAt >= cutoff }.take(MAX_SESSIONS)

            val json = Gson().toJson(pruned)
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SESSIONS, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Deletes a specific conversation session
     */
    fun deleteSession(context: Context, sessionId: String) {
        try {
            val sessions = getAllSessions(context).filter { it.id != sessionId }
            val json = Gson().toJson(sessions)
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SESSIONS, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Creates a fresh new session with the standard greeting
     */
    fun createNewSession(): ChatSession {
        return ChatSession(
            messages = mutableListOf(defaultWelcomeMessage)
        )
    }

    /**
     * Gets the most recent session, or creates a new one
     */
    fun getOrCreateLatestSession(context: Context): ChatSession {
        val all = getAllSessions(context)
        return all.firstOrNull() ?: createNewSession().also { saveSession(context, it) }
    }
}
