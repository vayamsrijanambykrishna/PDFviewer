package `in`.krishna.pdfviewer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.krishna.pdfviewer.model.CarouselConfig
import `in`.krishna.pdfviewer.model.PdfViewerDefaults
import `in`.krishna.pdfviewer.model.rememberPdfViewerState
import `in`.krishna.pdfviewer.ui.PdfViewer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainPdfScreen()
            }
        }
    }
}

@Composable
fun MainPdfScreen() {
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    val viewerState = rememberPdfViewerState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        selectedPdfUri = uri
    }

    // Phone ka system back button dabane par bhi viewer close hokar picker aayega
    BackHandler(enabled = selectedPdfUri != null) {
        selectedPdfUri = null
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            selectedPdfUri?.let { uri ->
                PdfViewer(
                    uri = uri,
                    onBackClick = { selectedPdfUri = null },
                    state = viewerState,
                    colors = PdfViewerDefaults.colors(),
                    carouselConfig = CarouselConfig(
                        enabled = true,
                        height = 140.dp,
                        cardWidth = 85.dp,
                        cardHeight = 115.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            } ?: run {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "PDF Viewer Testing",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Button(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                        Text("फ़ोन से PDF फ़ाइल चुनें")
                    }
                }
            }
        }
    }
}
