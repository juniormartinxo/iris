package com.iris.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MainScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(previewView) {
        val pv = previewView ?: return@LaunchedEffect
        runCatching { viewModel.cameraManager.bind(pv) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { viewModel.trigger() }
            .semantics {
                contentDescription =
                    "Toque duas vezes para descrever a cena no modo atual."
            }
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { },
            factory = { ctx ->
                PreviewView(ctx).apply {
                    keepScreenOn = true
                    previewView = this
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            ModeBanner(state)
            Spacer(modifier = Modifier.weight(1f))
            DescriptionStrip(state)
            ModeBar(
                currentMode = state.mode,
                onSelect = { mode ->
                    viewModel.selectMode(mode)
                    viewModel.trigger()
                },
                onRepeat = { viewModel.repeat() },
            )
        }
    }
}

@Composable
private fun ModeBanner(state: AppState) {
    val label = when (state.mode) {
        AppMode.CONTINUOUS -> "Modo Contínuo"
        AppMode.QUESTION -> "Modo Pergunta"
        AppMode.READING -> "Modo Leitura"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { contentDescription = "Modo atual: $label" }
        )
        Spacer(modifier = Modifier.weight(1f))
        state.modelVariant?.let {
            Text(
                text = it,
                color = Color(0xFFFFD600),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DescriptionStrip(state: AppState) {
    if (state.lastDescription.isBlank()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(16.dp)
    ) {
        Text(
            text = state.lastDescription,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ModeBar(
    currentMode: AppMode,
    onSelect: (AppMode) -> Unit,
    onRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111111))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ModeButton(
            selected = currentMode == AppMode.CONTINUOUS,
            label = "Contínuo",
            description = "Modo Contínuo: descreve a cena à frente. Toque duas vezes para usar agora.",
            icon = Icons.Filled.Visibility,
            onClick = { onSelect(AppMode.CONTINUOUS) },
        )
        ModeButton(
            selected = currentMode == AppMode.QUESTION,
            label = "Pergunta",
            description = "Modo Pergunta: faz uma pergunta por voz sobre o que está vendo. Toque duas vezes.",
            icon = Icons.Filled.Mic,
            onClick = { onSelect(AppMode.QUESTION) },
        )
        ModeButton(
            selected = currentMode == AppMode.READING,
            label = "Leitura",
            description = "Modo Leitura: lê em voz alta o texto da imagem. Toque duas vezes.",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            onClick = { onSelect(AppMode.READING) },
        )
        ModeButton(
            selected = false,
            label = "Repetir",
            description = "Repetir a última descrição em voz alta. Toque duas vezes.",
            icon = Icons.Filled.Replay,
            onClick = onRepeat,
        )
    }
}

@Composable
private fun ModeButton(
    selected: Boolean,
    label: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val bg = if (selected) Color(0xFFFFD600) else Color(0xFF222222)
    val fg = if (selected) Color.Black else Color.White
    Column(
        modifier = Modifier
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}
