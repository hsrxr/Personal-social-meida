package com.journal.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.journal.app.navigation.NavGraph
import com.journal.app.ui.screen.home.HomeViewModel
import com.journal.app.ui.screen.settings.SettingsViewModel
import com.journal.app.ui.theme.JournalTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val settingsViewModel by viewModels<SettingsViewModel>()
    private val homeViewModel by viewModels<HomeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JournalTheme {
                NavGraph()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        settingsViewModel.onAuthResult(resultCode, data)
        homeViewModel.onAuthResult(resultCode, data)
    }
}
