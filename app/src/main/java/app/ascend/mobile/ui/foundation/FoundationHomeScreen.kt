@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.ascend.mobile.ui.foundation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ascend.mobile.ui.adaptive.AscendWindowWidthClass

@Composable
fun FoundationHomeScreen(
    windowWidthClass: AscendWindowWidthClass,
    uiState: HomeUiState,
    showDebugEntry: Boolean,
    onStartScan: () -> Unit,
    onToggleDetails: () -> Unit,
    onOpenFoundationInfo: () -> Unit,
    onOpenDebugMenu: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ASCEND",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    TextButton(onClick = onStartScan) {
                        Text("New scan")
                    }
                    if (showDebugEntry) {
                        TextButton(onClick = onOpenDebugMenu) {
                            Text("Debug")
                        }
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        val contentPadding = PaddingValues(
            start = 20.dp,
            top = scaffoldPadding.calculateTopPadding() + 20.dp,
            end = 20.dp,
            bottom = scaffoldPadding.calculateBottomPadding() + 24.dp,
        )

        when (windowWidthClass) {
            AscendWindowWidthClass.Compact -> CompactFoundationContent(
                contentPadding = contentPadding,
                windowWidthClass = windowWidthClass,
                uiState = uiState,
                onToggleDetails = onToggleDetails,
                onOpenFoundationInfo = onOpenFoundationInfo,
            )

            AscendWindowWidthClass.Medium,
            AscendWindowWidthClass.Expanded,
            -> WideFoundationContent(
                contentPadding = contentPadding,
                windowWidthClass = windowWidthClass,
                uiState = uiState,
                onToggleDetails = onToggleDetails,
                onOpenFoundationInfo = onOpenFoundationInfo,
            )
        }
    }
}

@Composable
private fun CompactFoundationContent(
    contentPadding: PaddingValues,
    windowWidthClass: AscendWindowWidthClass,
    uiState: HomeUiState,
    onToggleDetails: () -> Unit,
    onOpenFoundationInfo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FoundationHero()
        FoundationStatusCard(
            windowWidthClass = windowWidthClass,
            uiState = uiState,
            onToggleDetails = onToggleDetails,
            onOpenFoundationInfo = onOpenFoundationInfo,
        )
    }
}

@Composable
private fun WideFoundationContent(
    contentPadding: PaddingValues,
    windowWidthClass: AscendWindowWidthClass,
    uiState: HomeUiState,
    onToggleDetails: () -> Unit,
    onOpenFoundationInfo: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            FoundationHero()
        }
        Spacer(modifier = Modifier.width(24.dp))
        Column(
            modifier = Modifier.weight(1f),
        ) {
            FoundationStatusCard(
                windowWidthClass = windowWidthClass,
                uiState = uiState,
                onToggleDetails = onToggleDetails,
                onOpenFoundationInfo = onOpenFoundationInfo,
            )
        }
    }
}

@Composable
private fun FoundationHero() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "A clean foundation for measurable progress.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "The Phase 0 foundation now hosts the Phase 2 capture lane while local persistence and quality validation remain isolated to WP06.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FoundationStatusCard(
    windowWidthClass: AscendWindowWidthClass,
    uiState: HomeUiState,
    onToggleDetails: () -> Unit,
    onOpenFoundationInfo: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Foundation status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            StatusLine(label = "Window class", value = windowWidthClass.name)
            StatusLine(label = "UI", value = "Jetpack Compose + Material 3")
            StatusLine(label = "Navigation", value = "Navigation 3")
            StatusLine(label = "State", value = "ViewModel + StateFlow")
            StatusLine(label = "Dependency injection", value = "Hilt")

            if (uiState.detailsExpanded) {
                HorizontalDivider()
                Text(
                    text = "This expanded state is owned by the ViewModel, so ordinary rotation and window-size changes do not reset it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onToggleDetails,
            ) {
                Text(if (uiState.detailsExpanded) "Hide state example" else "Show state example")
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenFoundationInfo,
            ) {
                Text("View architecture boundary")
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
