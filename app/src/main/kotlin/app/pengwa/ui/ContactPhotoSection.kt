package app.pengwa.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.pengwa.R
import app.pengwa.photo.ContactPhotos
import app.pengwa.photo.PhotoStorage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ContactPhotoSection(contactId: UUID) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember(contactId) { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<File?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(contactId) { photos = withContext(Dispatchers.IO) { ContactPhotos.list(context, contactId) } }

    fun add(uri: Uri, capture: File? = null) {
        loading = true
        failed = false
        scope.launch {
            try {
                photos = withContext(Dispatchers.IO) {
                    ContactPhotos.add(context, contactId, uri)
                    ContactPhotos.list(context, contactId)
                }
            } catch (_: Exception) {
                failed = true
            } finally {
                capture?.delete()
                loading = false
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = pendingPath?.let(::File)
        pendingPath = null
        if (file != null) {
            if (taken) add(PhotoStorage.uriFor(context, file), file) else file.delete()
        }
    }
    fun takePhoto() {
        try {
            val file = PhotoStorage.newCaptureFile(context)
            pendingPath = file.absolutePath
            camera.launch(PhotoStorage.uriFor(context, file))
        } catch (_: Exception) {
            pendingPath?.let(::File)?.delete()
            pendingPath = null
            failed = true
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) takePhoto() else failed = true
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) add(uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.contact_photos, photos.size), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (photos.isEmpty()) R.string.contact_photos_empty else R.string.contact_photos_private),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(enabled = !loading, onClick = {
                if (hasCameraPermission(context)) takePhoto() else permission.launch(Manifest.permission.CAMERA)
            }) { Text(stringResource(R.string.camera_button)) }
            OutlinedButton(enabled = !loading, onClick = {
                try {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (_: ActivityNotFoundException) { failed = true }
            }) { Text(stringResource(R.string.contact_photos_add)) }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(stringResource(R.string.contact_photos_failed), color = MaterialTheme.colorScheme.error)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(photos, key = { _, file -> file.name }) { index, file ->
                Surface(onClick = { selected = file }, shape = MaterialTheme.shapes.medium) {
                    ContactPhoto(file, stringResource(R.string.contact_photo_description, index + 1), Modifier.size(112.dp), 320, ContentScale.Crop)
                }
            }
        }
    }
    selected?.let { file ->
        Dialog(onDismissRequest = { selected = null; confirmRemove = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.contact_photo_view), style = MaterialTheme.typography.titleMedium)
                    ContactPhoto(file, stringResource(R.string.contact_photo_view), Modifier.fillMaxWidth().heightIn(max = 400.dp).aspectRatio(0.85f), 1600, ContentScale.Fit)
                    if (confirmRemove) Text(stringResource(R.string.contact_photo_remove_confirm))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = !loading, onClick = {
                            if (!confirmRemove) confirmRemove = true else {
                                loading = true
                                scope.launch {
                                    val removed = withContext(Dispatchers.IO) { ContactPhotos.remove(context, contactId, file) }
                                    if (removed) { photos = photos.filterNot { it == file }; selected = null } else failed = true
                                    confirmRemove = false
                                    loading = false
                                }
                            }
                        }) { Text(stringResource(if (confirmRemove) R.string.contact_photo_remove_yes else R.string.contact_photo_remove)) }
                        TextButton(onClick = { selected = null; confirmRemove = false }) { Text(stringResource(R.string.close)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactPhoto(file: File, description: String, modifier: Modifier, maxSize: Int, scale: ContentScale) {
    val context = LocalContext.current
    val request = remember(context, file, maxSize) {
        ImageRequest.Builder(context)
            .data(file)
            .size(maxSize, maxSize)
            .scale(Scale.FIT)
            .precision(Precision.INEXACT)
            .build()
    }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsState()
    if (state is AsyncImagePainter.State.Success) Image(painter, description, modifier, contentScale = scale)
    else Box(modifier) {
        Text(stringResource(if (state is AsyncImagePainter.State.Error) R.string.contact_photo_unavailable else R.string.contact_photo_loading))
    }
}
