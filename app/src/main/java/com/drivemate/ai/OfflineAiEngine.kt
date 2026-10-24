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


    /** Report whether Android can load the JNI runtime; this does not test a model. */
    fun verifyNativeLibrary(): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: "(unavailable)"
        val library = File(nativeDir, "liblitertlm_jni.so")
        val details = "Android API ${android.os.Build.VERSION.SDK_INT}, ABI ${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}"
        return try {
            System.loadLibrary("litertlm_jni")
            "Native AI library: OK\n${details}\nFile extracted: ${library.isFile}\nNext: import a compatible .litertlm model."
        } catch (error: UnsatisfiedLinkError) {
            android.util.Log.e("DriveMateAI", "Native library failed to load", error)
            "Native AI library: FAILED\n${details}\nFile extracted: ${library.isFile}\nCause: ${error.message ?: "unknown error"}"
        } catch (error: Throwable) {
            android.util.Log.e("DriveMateAI", "Native library diagnostic error", error)
            "Native AI check failed: ${error.javaClass.simpleName}: ${error.message ?: "unknown error"}"
        }
    }

    fun load(modelFile: File) {
        // Preload JNI to surface any missing native component explicitly.
        System.loadLibrary("litertlm_jni")
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
        return chat.sendMessage(message).toString()
    }

    override fun close() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }
}
