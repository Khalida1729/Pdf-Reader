package com.example.pdfreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdfreader.reader.PdfReaderScreen
import com.example.pdfreader.reader.PdfReaderViewModel
import com.example.pdfreader.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private val vm by viewModels<PdfReaderViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                var saveMessage by remember { mutableStateOf<String?>(null) }

                val openLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        uri?.let(vm::open)
                    }

                val saveLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/pdf")
                    ) { uri ->
                        if (uri != null) {
                            val output = contentResolver.openOutputStream(uri)
                            if (output == null) {
                                saveMessage = "Could not create the output file."
                            } else {
                                vm.saveTo(output) { result ->
                                    runOnUiThread {
                                        saveMessage = result.fold(
                                            onSuccess = { "PDF saved successfully." },
                                            onFailure = {
                                                "Save failed: ${it.message ?: "Unknown error"}"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                LaunchedEffect(Unit) {
                    intent?.data?.let(vm::open)
                }

                if (state.pageCount == 0) {
                    HomeScreen(
                        onOpen = {
                            openLauncher.launch(arrayOf("application/pdf"))
                        }
                    )
                } else {
                    PdfReaderScreen(
                        vm = vm,
                        onSave = {
                            saveLauncher.launch(
                                vm.currentFileName()
                                    .substringBeforeLast('.')
                                    .ifBlank { "document" } + "_annotated.pdf"
                            )
                        },
                        onOpen = {
                            openLauncher.launch(arrayOf("application/pdf"))
                        },
                        onShare = {
                            val uri = vm.currentUri()
                                ?: return@PdfReaderScreen

                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            context.startActivity(
                                Intent.createChooser(send, "Share PDF")
                            )
                        }
                    )
                }

                if (saveMessage != null) {
                    AlertDialog(
                        onDismissRequest = { saveMessage = null },
                        title = { Text("PDF Reader") },
                        text = { Text(saveMessage!!) },
                        confirmButton = {
                            TextButton(
                                onClick = { saveMessage = null }
                            ) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    }
}
