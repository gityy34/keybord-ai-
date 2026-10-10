package dev.raabta.keyboard.tools

import android.content.Context
import com.google.mlkit.common.MlKit
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.*

interface TranslationEngine {
    fun download(done: (Boolean) -> Unit)
    fun translate(text: String, done: (String?) -> Unit)
    fun close()
}

/** ML Kit is initialized only for an explicit translation/model action. Text stays on-device. */
class LocalTranslation(context: Context, source: String, target: String): TranslationEngine {
    private val client: Translator
    init {
        initializeOnce(context)
        client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
    }
    override fun download(done: (Boolean) -> Unit) {
        client.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { done(true) }.addOnFailureListener { done(false) }
    }
    override fun translate(text: String, done: (String?) -> Unit) {
        client.translate(text).addOnSuccessListener { done(it) }.addOnFailureListener { done(null) }
    }
    override fun close() = client.close()
    companion object {
        private var initialized = false
        @Synchronized internal fun initializeOnce(context: Context) {
            // The SDK's explicit initializer rejects a second initialization in one process.
            if (!initialized) {
                MlKit.initialize(context.applicationContext)
                initialized = true
            }
        }
        fun deleteModels(context: Context, done: (Boolean) -> Unit) {
            initializeOnce(context)
            RemoteModelManager.getInstance().deleteDownloadedModel(TranslateRemoteModel.Builder(TranslateLanguage.URDU).build())
                .addOnSuccessListener { done(true) }.addOnFailureListener { done(false) }
        }
    }
}
