package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.random.Random

// --- Navigation Routes ---
object Routes {
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val RESULT = "result/{barcode}"
    fun result(barcode: String) = "result/$barcode"
}

// --- ViewModel ---
sealed class ResultUiState {
    object Loading : ResultUiState()
    data class Success(val barcode: String, val randomNumber: Int) : ResultUiState()
}

class ResultViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun loadData(barcode: String) {
        if (hasLoaded) return
        hasLoaded = true
        
        viewModelScope.launch {
            _uiState.value = ResultUiState.Loading
            delay(2000)
            val random = Random.nextInt(100, 1000)
            _uiState.value = ResultUiState.Success(barcode, random)
        }
    }
}

// --- Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainContent()
            }
        }
    }
}

@Composable
fun MainContent() {
    val navController = rememberNavController()
    
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenScanner = { navController.navigate(Routes.SCANNER) }
                    )
                }
                composable(Routes.SCANNER) {
                    QRScannerScreen(
                        onDismiss = { navController.popBackStack() },
                        onBarcodeDetected = { barcode ->
                            navController.navigate(Routes.result(barcode)) {
                                popUpTo(Routes.HOME)
                            }
                        }
                    )
                }
                composable(
                    route = Routes.RESULT,
                    arguments = listOf(navArgument("barcode") { type = NavType.StringType })
                ) { backStackEntry ->
                    val barcode = backStackEntry.arguments?.getString("barcode") ?: ""
                    ResultScreen(
                        barcode = barcode,
                        onBack = { 
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            } 
                        }
                    )
                }
            }
        }
    }
}

// --- Screens ---

@Composable
fun HomeScreen(onOpenScanner: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onOpenScanner()
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            val permission = Manifest.permission.CAMERA
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                onOpenScanner()
            } else {
                permissionLauncher.launch(permission)
            }
        }) {
            Text(text = "Open QR Scanner")
        }
    }
}

@Composable
fun QRScannerScreen(
    onDismiss: () -> Unit,
    onBarcodeDetected: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        QRScannerView(onBarcodeDetected)
        QRScannerOverlay()
        
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .padding(top = 48.dp, start = 16.dp)
                .align(Alignment.TopStart)
        ) {
            Text("Back", color = Color.White)
        }
    }
}

@Composable
fun ResultScreen(barcode: String, onBack: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var randomNumber by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(barcode) {
        flow {
            delay(2000)
            emit(Random.nextInt(100, 1000))
        }.collect { number ->
            randomNumber = number
            isLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Fetching data for: $barcode...")
        } else {
            Text("Scanned Barcode: $barcode", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Random Result:", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = randomNumber.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onBack) {
                Text("Scan Again")
            }
        }
    }
}

// --- Components ---

@OptIn(ExperimentalGetImage::class)
@Composable
fun QRScannerView(onBarcodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    barcode.rawValue?.let { value ->
                                        onBarcodeDetected(value)
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    Log.e("QRScanner", "Use case binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

@Composable
fun QRScannerOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val linePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "linePosition"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val boxSize = width * 0.7f
        val left = (width - boxSize) / 2
        val top = (height - boxSize) / 2
        val right = left + boxSize
        val bottom = top + boxSize

        drawRect(color = Color.Black.copy(alpha = 0.5f), size = size)
        drawRect(color = Color.Transparent, topLeft = Offset(left, top), size = Size(boxSize, boxSize), blendMode = BlendMode.Clear)

        val strokeWidth = 4.dp.toPx()
        val cornerLength = 40.dp.toPx()
        
        drawLine(Color.White, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
        drawLine(Color.White, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)
        drawLine(Color.White, Offset(right, top), Offset(right - cornerLength, top), strokeWidth)
        drawLine(Color.White, Offset(right, top), Offset(right, top + cornerLength), strokeWidth)
        drawLine(Color.White, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth)
        drawLine(Color.White, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth)
        drawLine(Color.White, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth)
        drawLine(Color.White, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth)

        val lineY = top + (boxSize * linePosition)
        drawLine(color = Color.Green, start = Offset(left + 8.dp.toPx(), lineY), end = Offset(right - 8.dp.toPx(), lineY), strokeWidth = 2.dp.toPx())
    }
}
