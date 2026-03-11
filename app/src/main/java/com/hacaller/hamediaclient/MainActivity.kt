package com.hacaller.hamediaclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hacaller.hamediaclient.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var isConnected by remember { mutableStateOf(false) }
                var host by remember { mutableStateOf("") }
                var user by remember { mutableStateOf("") }
                var pass by remember { mutableStateOf("") }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (!isConnected) {
                            HomeScreen(onConnect = { h, u, p ->
                                host = h
                                user = u
                                pass = p
                                isConnected = true
                            })
                        } else {
                            DebianFileBrowser(host = host, user = user, pass = pass)
                        }
                    }
                }
            }
        }

    }

    /**
     * A native method that is implemented by the 'hamediaclient' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'hamediaclient' library on application startup.
        init {
            System.loadLibrary("hamediaclient")
        }
    }
}
