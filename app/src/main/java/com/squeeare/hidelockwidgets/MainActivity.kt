package com.squeeare.hidelockwidgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.squeeare.hidelockwidgets.ui.AppPage
import com.squeeare.hidelockwidgets.ui.MainViewModel
import com.squeeare.hidelockwidgets.ui.PreviewScreen
import com.squeeare.hidelockwidgets.ui.SettingsScreen
import com.squeeare.hidelockwidgets.ui.theme.HideLockWidgetsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HideLockWidgetsTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.uiState.collectAsState()

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(state.infoMessage) {
                    state.infoMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearMessage()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = state.currentPage == AppPage.SETTINGS,
                                onClick = { viewModel.setPage(AppPage.SETTINGS) },
                                icon = {
                                    Icon(Icons.Rounded.Settings, contentDescription = null)
                                },
                                label = { Text("Settings") }
                            )

                            NavigationBarItem(
                                selected = state.currentPage == AppPage.PREVIEW,
                                onClick = { viewModel.setPage(AppPage.PREVIEW) },
                                icon = {
                                    Icon(Icons.Rounded.Preview, contentDescription = null)
                                },
                                label = { Text("Preview") }
                            )
                        }
                    }
                ) { _ ->
                    when (state.currentPage) {
                        AppPage.SETTINGS -> {
                            SettingsScreen(
                                state = state,
                                onDepthEnabledChanged = viewModel::setDepthEnabled,
                                onPickBackground = viewModel::importBackground,
                                onPickForeground = viewModel::importForeground,
                                onWidgetCountChanged = viewModel::setPreviewWidgetCount,
                                onSave = viewModel::saveConfig,
                                onOpenPreview = { viewModel.setPage(AppPage.PREVIEW) }
                            )
                        }

                        AppPage.PREVIEW -> {
                            PreviewScreen(
                                state = state,
                                onSetActiveLayer = viewModel::setActiveLayer,
                                onMove = viewModel::nudgeActiveLayer,
                                onScale = viewModel::scaleActiveLayer,
                                onResetActiveLayer = viewModel::resetActiveLayer,
                                onSave = viewModel::saveConfig
                            )
                        }
                    }
                }
            }
        }
    }
}
