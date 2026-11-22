package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.BhoomgaadiNavHost
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.theme.BhoomgaadiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val app = application as BhoomgaadiApp
        setContent {
            val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(app.repository))
            val language by authViewModel.language.collectAsStateWithLifecycle()
            BhoomgaadiTheme(language = language) {
                BhoomgaadiNavHost(app = app, authViewModel = authViewModel)
            }
        }
    }
}
