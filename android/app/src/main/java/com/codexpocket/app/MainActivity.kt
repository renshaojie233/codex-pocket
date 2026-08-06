package com.codexpocket.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.codexpocket.app.ui.CodexPocketApp
import com.codexpocket.app.ui.theme.CodexPocketTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodexPocketTheme {
                CodexPocketApp(mainViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.onAppForeground()
    }

    override fun onStop() {
        mainViewModel.onAppBackground()
        super.onStop()
    }
}
