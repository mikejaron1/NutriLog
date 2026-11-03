package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.MainScreen
import com.example.ui.NutriViewModel
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {
    private val viewModel: NutriViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration before UI or Firebase operations
        configureAppCheck(this, intent)

        // STEP 2: Edge to edge support
        enableEdgeToEdge()

        // STEP 3: Compose Content
        setContent {
            MainScreen(viewModel = viewModel)
        }
    }
}
