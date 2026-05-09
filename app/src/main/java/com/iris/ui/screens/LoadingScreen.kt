package com.iris.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics {
                contentDescription =
                    "Carregando modelo Iris. Aguarde de quinze a trinta segundos."
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator(color = Color(0xFFFFD600))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Carregando Iris...",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Aguarde de 15 a 30 segundos.",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
