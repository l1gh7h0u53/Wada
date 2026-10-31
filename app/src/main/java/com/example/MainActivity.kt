package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.ClothingRepository
import com.example.ui.screens.MainScreen
import com.example.viewmodel.WardrobeViewModel
import com.example.viewmodel.WardrobeViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Room local database and repository layers
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ClothingRepository(database.clothingDao(), applicationContext)
        val factory = WardrobeViewModelFactory(repository)

        setContent {
            // Retrieve viewModel with the custom factory
            val viewModel: WardrobeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = factory
            )

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
