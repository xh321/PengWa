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
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UpdateRepository(application)
    private val mutableState = MutableStateFlow(UpdateState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun check() = execute {
        mutableState.value = UpdateState(UpdateStage.Checking)
        val release = withContext(Dispatchers.IO) { repository.check() }
        mutableState.value = UpdateState(if (release == null) UpdateStage.Current else UpdateStage.Available, release)
    }

    fun download() = execute {
        val release = mutableState.value.release ?: return@execute
        mutableState.value = UpdateState(UpdateStage.Downloading, release)
        val file = withContext(Dispatchers.IO) {
            repository.download(release) { progress -> mutableState.value = mutableState.value.copy(progress = progress) }
        }
        mutableState.value = UpdateState(UpdateStage.Ready, release, file = file)
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
            catch (e: Exception) { fail(if (e is UpdateFailure) e.message.orEmpty() else "连接或文件操作失败，请检查网络及存储空间后重试") }
        }
    }
}
