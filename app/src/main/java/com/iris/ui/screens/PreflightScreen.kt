package com.iris.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun PreflightScreen(
    ttsAvailable: Boolean,
    sttAvailable: Boolean,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.Top) {
            Text(
                text = "Configuração inicial",
                color = Color(0xFFFFD600),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (!ttsAvailable) {
                ActionRow(
                    title = "Voz em português offline",
                    desc = "Não está instalada. Sem ela, Iris não consegue falar com você.",
                    actionLabel = "Abrir configurações de voz",
                    semanticDesc =
                        "Abrir configurações de síntese de voz para baixar pacote português offline.",
                    onClick = {
                        runCatching {
                            context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                        }.recoverCatching {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!sttAvailable) {
                ActionRow(
                    title = "Reconhecimento de voz offline",
                    desc = "Não está instalado. Sem ele, o Modo Pergunta não funciona.",
                    actionLabel = "Abrir configurações de voz",
                    semanticDesc =
                        "Abrir configurações de reconhecimento de voz para baixar pacote português offline.",
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                        }
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF333333))
                    .clickable(onClick = onSkip)
                    .padding(20.dp)
                    .semantics {
                        contentDescription = "Continuar mesmo assim. Toque duas vezes."
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Continuar mesmo assim",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    desc: String,
    actionLabel: String,
    semanticDesc: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        Text(text = title, color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = desc, color = Color(0xFFCCCCCC), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFFD600))
                .clickable(onClick = onClick)
                .padding(12.dp)
                .semantics { contentDescription = semanticDesc },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = actionLabel,
                color = Color.Black,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
