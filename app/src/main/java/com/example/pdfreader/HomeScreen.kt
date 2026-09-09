package com.example.pdfreader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onOpen: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "PDF Reader",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Fast, offline reading and annotation",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(30.dp))

            Button(
                onClick = onOpen,
                modifier = Modifier.widthIn(min = 180.dp)
            ) {
                Text("Open PDF")
            }
        }
    }
}
