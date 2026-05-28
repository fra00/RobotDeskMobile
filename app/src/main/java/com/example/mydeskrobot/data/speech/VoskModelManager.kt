package com.example.mydeskrobot.data.speech

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Manages Vosk model download, extraction, and caching.
 * The Italian model is ~50MB compressed.
 */
class VoskModelManager(private val context: Context) {

    companion object {
        private const val TAG = "VoskModelManager"
        private const val MODEL_NAME = "vosk-model-small-it-0.22"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        private const val MODEL_DIR_NAME = "vosk-model-it"
    }

    sealed class ModelState {
        data object NotDownloaded : ModelState()
        data class Downloading(val progress: Float) : ModelState()
        data class Extracting(val progress: Float) : ModelState()
        data class Ready(val model: Model) : ModelState()
        data class Error(val message: String) : ModelState()
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val modelDir: File
        get() = File(context.filesDir, MODEL_DIR_NAME)

    private var cachedModel: Model? = null

    /**
     * Checks if the model is already downloaded and ready.
     */
    fun isModelReady(): Boolean {
        return modelDir.exists() && File(modelDir, "am/final.mdl").exists()
    }

    /**
     * Gets the cached model if ready, or null otherwise.
     */
    fun getModelIfReady(): Model? {
        if (cachedModel != null) return cachedModel
        if (!isModelReady()) return null

        return try {
            Model(modelDir.absolutePath).also {
                cachedModel = it
                _state.value = ModelState.Ready(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            _state.value = ModelState.Error("Failed to load model: ${e.message}")
            null
        }
    }

    /**
     * Loads the model synchronously. Call from a background thread.
     */
    suspend fun loadModel(): Model? = withContext(Dispatchers.IO) {
        if (cachedModel != null) return@withContext cachedModel

        if (!isModelReady()) {
            Log.w(TAG, "Model not downloaded yet")
            _state.value = ModelState.NotDownloaded
            return@withContext null
        }

        try {
            Log.d(TAG, "Loading model from ${modelDir.absolutePath}")
            val model = Model(modelDir.absolutePath)
            cachedModel = model
            _state.value = ModelState.Ready(model)
            Log.d(TAG, "Model loaded successfully")
            model
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            _state.value = ModelState.Error("Failed to load model: ${e.message}")
            null
        }
    }

    /**
     * Downloads and extracts the model. Reports progress via [state] flow.
     */
    suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            Log.d(TAG, "Model already downloaded")
            loadModel()
            return@withContext true
        }

        try {
            _state.value = ModelState.Downloading(0f)
            Log.d(TAG, "Starting download from $MODEL_URL")

            val tempZipFile = File(context.cacheDir, "vosk-model.zip")

            val url = URL(MODEL_URL)
            val connection = url.openConnection()
            connection.connect()

            val totalSize = connection.contentLengthLong
            var downloadedSize = 0L

            connection.getInputStream().use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        val progress = if (totalSize > 0) {
                            downloadedSize.toFloat() / totalSize
                        } else {
                            0f
                        }
                        _state.value = ModelState.Downloading(progress)
                    }
                }
            }

            Log.d(TAG, "Download complete, extracting...")
            _state.value = ModelState.Extracting(0f)

            extractZip(tempZipFile)

            tempZipFile.delete()

            Log.d(TAG, "Extraction complete, loading model...")
            loadModel()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model", e)
            _state.value = ModelState.Error("Download failed: ${e.message}")
            false
        }
    }

    private fun extractZip(zipFile: File) {
        val destDir = context.filesDir

        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            var entriesExtracted = 0

            while (entry != null) {
                val newPath = entry.name.replaceFirst(MODEL_NAME, MODEL_DIR_NAME)
                val newFile = File(destDir, newPath)

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }

                entriesExtracted++
                if (entriesExtracted % 10 == 0) {
                    _state.value = ModelState.Extracting(0.5f)
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        _state.value = ModelState.Extracting(1f)
    }

    /**
     * Deletes the downloaded model to free space.
     */
    fun deleteModel() {
        cachedModel = null
        modelDir.deleteRecursively()
        _state.value = ModelState.NotDownloaded
        Log.d(TAG, "Model deleted")
    }

    /**
     * Releases resources. Call when done with STT.
     */
    fun release() {
        cachedModel?.close()
        cachedModel = null
    }
}
