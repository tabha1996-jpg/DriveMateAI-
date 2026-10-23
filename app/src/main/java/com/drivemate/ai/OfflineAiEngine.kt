package com.drivemate.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File

/** Call methods from a background executor, never from the UI thread. */
class OfflineAiEngine(private val context: Context) : AutoCloseable {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    fun load(modelFile: File) {
        require(modelFile.isFile && modelFile.length() > 0L) { "Model file missing or empty" }
        close()
        val candidate = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )
        )
        try {
            candidate.initialize()
            val chat = candidate.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are DriveMate AI, a helpful offline driving companion. " +
                        "Reply in clear Hinglish or English according to the user. " +
                        "Never claim to know live traffic, charger availability, maps, or current location. " +
                        "Give conservative EV range guidance; encourage safe stops and never distract a driver."
                    )
                )
            )
            engine = candidate
            conversation = chat
        } catch (t: Throwable) {
            runCatching { candidate.close() }
            throw t
        }
    }

    fun reply(message: String): String {
        val chat = checkNotNull(conversation) { "Import a compatible .litertlm model first." }
        return chat.sendMessage(message).text
    }

    override fun close() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }
}
