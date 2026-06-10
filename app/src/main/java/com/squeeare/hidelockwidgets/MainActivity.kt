package com.squeeare.hidelockwidgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.squeeare.hidelockwidgets.ui.*
import com.squeeare.hidelockwidgets.ui.theme.HideLockWidgetsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HideLockWidgetsTheme {
                val vm: MainViewModel = viewModel()
                val state by vm.state.collectAsState()
                val snack = remember { SnackbarHostState() }
                LaunchedEffect(state.message) {
                    state.message?.let { snack.showSnackbar(it); vm.clearMessage() }
                }
                Scaffold(
                    snackbarHost = { SnackbarHost(snack) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = state.page == AppPage.SETTINGS,
                                onClick = { vm.setPage(AppPage.SETTINGS) },
                                icon = { Icon(Icons.Rounded.Settings, null) },
                                label = { Text("Settings") }
                            )
                            NavigationBarItem(
                                selected = state.page == AppPage.PREVIEW,
                                onClick = { vm.setPage(AppPage.PREVIEW) },
                                icon = { Icon(Icons.Rounded.Preview, null) },
                                label = { Text("Preview") }
                            )
                        }
                    }
                ) { pad ->
                    when (state.page) {
                        AppPage.SETTINGS -> SettingsScreen(state, vm, pad)
                        AppPage.PREVIEW -> PreviewScreen(state, vm, pad)
                    }
                }
            }
        }
    }
}
