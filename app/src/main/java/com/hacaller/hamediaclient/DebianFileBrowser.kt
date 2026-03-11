package com.hacaller.hamediaclient

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
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

@Composable
fun DebianFileBrowser(host: String, user: String, pass: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf("/home/$user") }
    var fileList by remember { mutableStateOf(emptyList<RemoteFile>()) }
    var status by remember { mutableStateOf("Scanning...") }
    var downloadProgress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }

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

    LaunchedEffect(currentPath) {
        loadFiles(currentPath)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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

        Button(onClick = { loadFiles(currentPath) }) {
            Text("Refresh List")
        }

        Text(status, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (isDownloading) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Downloading: ${(downloadProgress * 100).toInt()}%")
                LinearProgressIndicator(
                    progress = { downloadProgress },
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
                        } else if (!isDownloading) {
                            scope.launch(Dispatchers.IO) {
                                isDownloading = true
                                val target = File(context.getExternalFilesDir(null), file.name)
                                try {
                                    downloadWithProgress(host, user, pass, file.fullPath, target) { progress ->
                                        downloadProgress = progress
                                    }
                                } catch (e: Exception) {
                                    Log.e("DebianFileBrowser", "Download failed", e)
                                } finally {
                                    isDownloading = false
                                    downloadProgress = 0f
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
