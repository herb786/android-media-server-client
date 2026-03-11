package com.hacaller.hamediaclient

import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun DebianFileBrowser(host: String, user: String, pass: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf("/home/$user") }
    var fileList by remember { mutableStateOf(emptyList<RemoteFile>()) }
    var status by remember { mutableStateOf("Scanning...") }
    var progress by remember { mutableStateOf(0f) }
    var isTransferring by remember { mutableStateOf(false) }
    var transferType by remember { mutableStateOf("") } // "Download" or "Upload"

    fun loadFiles(path: String) {
        scope.launch(Dispatchers.IO) {
            status = "Scanning $path..."
            try {
                fileList = fetchFileList(host, user, pass, path)
                status = "Found ${fileList.size} items"
            } catch (e: Exception) {
                status = "Error: ${e.message}"
                Log.e("DebianFileBrowser", "Error fetching file list", e)
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                isTransferring = true
                transferType = "Uploading"
                try {
                    // Get the real filename from the URI
                    var fileName = "upload_${System.currentTimeMillis()}"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }

                    val inputStream = context.contentResolver.openInputStream(uri)
                    val tempFile = File(context.cacheDir, fileName)
                    inputStream?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    val targetDir = if (fileList.any { it.name == "Downloads" && it.isDirectory }) {
                        "$currentPath/Downloads"
                    } else {
                        currentPath
                    }

                    uploadToDebian(host, user, pass, tempFile, targetDir) { p ->
                        progress = p
                    }
                    status = "Upload successful: $fileName"
                    loadFiles(currentPath)
                    tempFile.delete() // Clean up the temporary file
                } catch (e: Exception) {
                    status = "Upload failed: ${e.message}"
                    Log.e("DebianFileBrowser", "Upload error", e)
                } finally {
                    isTransferring = false
                    progress = 0f
                }
            }
        }
    }

    LaunchedEffect(currentPath) {
        loadFiles(currentPath)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (currentPath != "/" && currentPath != "/home/$user") {
                IconButton(onClick = {
                    val parent = File(currentPath).parent ?: "/"
                    currentPath = parent
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
            Text("Debian Media Server", style = MaterialTheme.typography.headlineMedium)
        }

        Text("Path: $currentPath", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { loadFiles(currentPath) }) {
                Text("Refresh")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                enabled = !isTransferring
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Upload File")
            }
        }

        Text(status, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (isTransferring) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("$transferType: ${(progress * 100).toInt()}%")
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        LazyColumn {
            items(fileList) { file ->
                ListItem(
                    headlineContent = { Text(file.name) },
                    leadingContent = {
                        Icon(
                            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = if (file.isDirectory) "Folder" else "File"
                        )
                    },
                    modifier = Modifier.clickable {
                        if (file.isDirectory) {
                            currentPath = file.fullPath
                        } else if (!isTransferring) {
                            scope.launch(Dispatchers.IO) {
                                isTransferring = true
                                transferType = "Downloading"
                                val target = File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                    file.name
                                )
                                try {
                                    downloadWithProgress(host, user, pass, file.fullPath, target) { p ->
                                        progress = p
                                    }
                                    status = "Downloaded ${file.name}"
                                } catch (e: Exception) {
                                    Log.e("DebianFileBrowser", "Download failed", e)
                                    status = "Download failed"
                                } finally {
                                    isTransferring = false
                                    progress = 0f
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
