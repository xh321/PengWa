package app.pengwa.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class UpdateStage { Idle, Checking, Current, Available, Downloading, Ready, Failed }
data class UpdateState(
    val stage: UpdateStage = UpdateStage.Idle,
    val release: UpdateRelease? = null,
    val progress: Float = 0f,
    val file: File? = null,
    val error: String? = null,
    val source: UpdateSource = UpdateSource.Auto,
    val activeSource: String? = null,
    val downloadedBytes: Long = 0,
    val bytesPerSecond: Long = 0,
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UpdateRepository(application)
    private val preferences = application.getSharedPreferences("app_updates", 0)
    private val initialSource = UpdateSource.entries.firstOrNull { it.name == preferences.getString("source", null) } ?: UpdateSource.Auto
    private val mutableState = MutableStateFlow(UpdateState(source = initialSource))
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun selectSource(source: UpdateSource) {
        if (job?.isActive == true) return
        preferences.edit().putString("source", source.name).apply()
        mutableState.value = mutableState.value.copy(source = source, activeSource = null)
    }

    fun check() = execute {
        val source = mutableState.value.source
        mutableState.value = UpdateState(UpdateStage.Checking, source = source)
        val release = withContext(Dispatchers.IO) {
            repository.check(source) { label -> mutableState.value = mutableState.value.copy(activeSource = label) }
        }
        mutableState.value = mutableState.value.copy(stage = if (release == null) UpdateStage.Current else UpdateStage.Available, release = release)
    }

    fun download() = execute {
        val snapshot = mutableState.value
        val release = snapshot.release ?: return@execute
        mutableState.value = UpdateState(UpdateStage.Downloading, release, source = snapshot.source)
        var lastTime = System.nanoTime()
        var lastBytes = -1L
        var lastSource = ""
        val file = withContext(Dispatchers.IO) {
            repository.download(release, snapshot.source) { progress ->
                val now = System.nanoTime()
                if (lastBytes < 0 || progress.source != lastSource || progress.bytes < lastBytes) {
                    lastBytes = progress.bytes; lastTime = now; lastSource = progress.source
                    mutableState.value = mutableState.value.copy(bytesPerSecond = 0)
                }
                val elapsed = (now - lastTime) / 1_000_000_000.0
                val speed = if (elapsed >= 0.5) ((progress.bytes - lastBytes) / elapsed).toLong() else mutableState.value.bytesPerSecond
                if (elapsed >= 0.5) { lastBytes = progress.bytes; lastTime = now }
                mutableState.value = mutableState.value.copy(progress = progress.bytes.toFloat() / progress.total,
                    downloadedBytes = progress.bytes, activeSource = progress.source, bytesPerSecond = speed)
            }
        }
        mutableState.value = mutableState.value.copy(stage = UpdateStage.Ready, file = file)
    }

    fun fail(message: String) { mutableState.value = mutableState.value.copy(stage = UpdateStage.Failed, error = message) }

    fun prepareInstall(onReady: (File) -> Unit) = execute {
        val snapshot = mutableState.value
        val file = snapshot.file ?: return@execute
        val release = snapshot.release ?: return@execute
        withContext(Dispatchers.IO) { repository.verifyApk(file, release) }
        onReady(file)
    }

    private fun execute(action: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(if (e is UpdateFailure) e.message.orEmpty() else "连接或文件操作失败，请切换线路或检查存储空间后重试") }
        }
    }
}
