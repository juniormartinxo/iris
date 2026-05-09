package com.iris.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.iris.audio.TtsManager

private val LINES = listOf(
    "Bem-vindo ao Iris. Iris é seu olho digital.",
    "A câmera fica nas costas do celular. Segure normalmente, com a tela voltada para o seu rosto.",
    "Para descrever o que está à sua frente, mantenha o celular vertical, com a parte de baixo apontando para o chão.",
    "Para ler um texto sobre uma mesa, deite o celular paralelo ao papel, com a tela voltada para cima.",
    "Existem três modos. O modo é falado em voz alta sempre que você troca.",
    "Para começar, toque duas vezes em qualquer lugar da tela.",
)

@Composable
fun TutorialOverlay(tts: TtsManager, onFinish: () -> Unit) {
    var lineIndex by remember { mutableStateOf(0) }

    LaunchedEffect(lineIndex) {
        if (lineIndex < LINES.size) {
            tts.speak(LINES[lineIndex])
            lineIndex++
        } else {
            onFinish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE000000))
            .clickable { onFinish() }
            .semantics {
                contentDescription =
                    "Tutorial em andamento. Toque duas vezes para pular."
            }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Iris",
                color = Color(0xFFFFD600),
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Tutorial",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Toque para pular",
                color = Color(0xFFCCCCCC),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
