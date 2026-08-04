package com.codexpocket.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codexpocket.app.ui.CodexPocketApp
import com.codexpocket.app.ui.theme.CodexPocketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodexPocketTheme {
                CodexPocketApp(viewModel<MainViewModel>())
            }
        }
    }
}
