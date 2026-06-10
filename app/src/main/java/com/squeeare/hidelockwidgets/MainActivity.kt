package com.squeeare.hidelockwidgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
                    state.message?.let {
                        snack.showSnackbar(it)
                        vm.clearMessage()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snack) },
                    bottomBar = { StudioNavBar(state.page, vm::setPage) },
                    containerColor = MaterialTheme.colorScheme.background
                ) { pad ->
                    AnimatedContent(targetState = state.page, label = "page") { page ->
                        when (page) {
                            AppPage.HOME -> HomeScreen(state, vm, pad)
                            AppPage.PREVIEW -> PreviewScreen(state, vm, pad)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioNavBar(page: AppPage, onPage: (AppPage) -> Unit) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioNavItem(
                selected = page == AppPage.HOME,
                icon = { Icon(Icons.Rounded.Home, null) },
                label = "Главная",
                onClick = { onPage(AppPage.HOME) }
            )
            StudioNavItem(
                selected = page == AppPage.PREVIEW,
                icon = { Icon(Icons.Rounded.Visibility, null) },
                label = "Превью",
                onClick = { onPage(AppPage.PREVIEW) }
            )
        }
    }
}

@Composable
private fun StudioNavItem(
    selected: Boolean,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) colors.secondaryContainer else colors.surface,
            contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
        ),
        modifier = Modifier
            .height(62.dp)
            .width(156.dp)
            .clip(RoundedCornerShape(30.dp))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(3.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
