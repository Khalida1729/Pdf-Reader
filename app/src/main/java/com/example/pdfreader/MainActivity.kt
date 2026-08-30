package com.example.pdfreader
import com.example.pdfreader.ui.theme.AppTheme
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdfreader.reader.*


class MainActivity : ComponentActivity() {
    private val vm by viewModels<PdfReaderViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val state by vm.state.collectAsStateWithLifecycle()
               // var saveMode by remember { mutableStateOf(false) }
                val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::open) }
                val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
                    uri?.let { target ->
                        contentResolver.openOutputStream(target)?.let { output ->
                            vm.saveTo(output) { }
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    val uri = intent?.data
                    if (uri != null) vm.open(uri)
                }
                val context = LocalContext.current
                if (state.pageCount == 0) {
                    HomeScreen(onOpen = { openLauncher.launch(arrayOf("application/pdf")) })
                } else {
                    PdfReaderScreen(
                        vm = vm,
                        onSave = { saveLauncher.launch(vm.currentFileName().substringBeforeLast('.') + "_annotated.pdf") },
                        onOpen = { openLauncher.launch(arrayOf("application/pdf")) },
                        onShare = {
                            val uri = vm.currentUri() ?: return@PdfReaderScreen
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Share PDF"))
                        }
                    )
                }
            }
        }
    }
}