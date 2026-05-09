package com.iris.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ModelMissingScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = "Modelo não encontrado",
                color = Color(0xFFFF6E6E),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "O modelo de inteligência ainda não foi instalado neste celular. " +
                    "Para usar Iris, é preciso conectar o celular a um computador uma " +
                    "única vez. Peça ajuda a uma pessoa vidente.",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Para a pessoa vidente:",
                color = Color(0xFFFFD600),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "1. No PC, baixe o modelo:\n" +
                    "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "2. Conecte o celular via USB e rode:\n" +
                    "adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.iris/files/",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFD600))
                    .clickable(onClick = onRetry)
                    .padding(20.dp)
                    .semantics {
                        contentDescription =
                            "Verificar de novo. Toque duas vezes depois de instalar o modelo."
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Verificar novamente",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
