package com.example.mobile.ui.pix

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobile.ui.components.ErrorText
import com.example.mobile.ui.components.PixScaffold
import com.example.mobile.ui.components.ScreenColumn
import com.example.mobile.ui.theme.RuRubyRed
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun PixQrScanScreen(
    accountId: Long,
    startInPasteMode: Boolean = false,
    onQrVerified: (VerifiedQrTransfer) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: PixQrScanViewModel = viewModel(
        key = "pix-qr-scan-$accountId-$startInPasteMode",
        factory = PixQrScanViewModel.factory(accountId, startInPasteMode)
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(state.isPasteMode, hasCameraPermission) {
        if (!state.isPasteMode && !hasCameraPermission && !permissionRequested) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.verifiedTransfer) {
        val verified = state.verifiedTransfer ?: return@LaunchedEffect
        viewModel.consumeNavigation()
        onQrVerified(verified)
    }

    if (state.isPasteMode) {
        PixPasteCodeContent(
            pastedCode = state.pastedCode,
            isLookingUp = state.isLookingUp,
            error = state.error,
            onCodeChange = viewModel::onPastedCodeChange,
            onSubmit = viewModel::submitPastedCode,
            onSwitchToCamera = { viewModel.togglePasteMode(false) },
            onBack = onBack
        )
    } else {
        PixCameraScanContent(
            hasCameraPermission = hasCameraPermission,
            isLookingUp = state.isLookingUp,
            error = state.error,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            },
            onQrDetected = viewModel::onCameraQrDetected,
            onRetryScan = viewModel::clearError,
            onSwitchToPaste = { viewModel.togglePasteMode(true) },
            onBack = onBack
        )
    }
}

@Composable
private fun PixCameraScanContent(
    hasCameraPermission: Boolean,
    isLookingUp: Boolean,
    error: String?,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onQrDetected: (String) -> Unit,
    onRetryScan: () -> Unit,
    onSwitchToPaste: () -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var torchEnabled by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(torchEnabled, boundCamera) {
        val cam = boundCamera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(torchEnabled)
        }
    }

    if (!hasCameraPermission) {
        PixScaffold(title = "", onBack = onBack, useCloseIcon = true) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Permissão de câmera necessária",
                    color = colors.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Para escanear QR Codes Pix com a câmera, autorize o acesso à câmera ou use o Pix Copia e Cola.",
                    color = colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Permitir câmera", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSwitchToPaste,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = colors.onBackground)
                    Spacer(Modifier.width(8.dp))
                    Text("Colar código Pix", color = colors.onBackground, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text("Abrir configurações do app", color = colors.onSurfaceVariant)
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        QrCameraPreview(
            onQrDetected = onQrDetected,
            onCameraBound = { camera ->
                boundCamera = camera
                hasFlash = camera.cameraInfo.hasFlashUnit()
            },
            modifier = Modifier.fillMaxSize()
        )

        ScannerViewfinderOverlay(modifier = Modifier.fillMaxSize())

        // Top controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
            }

            Text(
                text = "Ler QR code",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            if (hasFlash) {
                IconButton(
                    onClick = { torchEnabled = !torchEnabled },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Alternar flash",
                        tint = if (torchEnabled) RuRubyRed else Color.White
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        // Bottom prompt and paste fallback
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Aponte a câmera para o QR Code Pix",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onSwitchToPaste,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Colar código Pix", fontWeight = FontWeight.SemiBold)
            }
        }

        // Lookup loading overlay
        if (isLookingUp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(color = colors.primary)
                        Text(
                            text = "Validando QR Code Pix...",
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // Error overlay when scanned QR is invalid or belongs to the user
        if (error != null && !isLookingUp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Não foi possível ler este QR Code",
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = error,
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRetryScan,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Escanear novamente", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                onRetryScan()
                                onSwitchToPaste()
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Colar código Pix", color = colors.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerViewfinderOverlay(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        drawRect(color = Color.Black.copy(alpha = 0.56f))

        val boxSize = size.minDimension * 0.66f
        val left = (size.width - boxSize) / 2f
        val top = (size.height - boxSize) / 2f
        val radius = 24.dp.toPx()

        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Clear
        )

        val cornerLen = 30.dp.toPx()
        val stroke = 4.dp.toPx()
        val right = left + boxSize
        val bottom = top + boxSize

        val corners = listOf(
            // Top-left
            Offset(left, top + cornerLen) to Offset(left, top),
            Offset(left, top) to Offset(left + cornerLen, top),
            // Top-right
            Offset(right - cornerLen, top) to Offset(right, top),
            Offset(right, top) to Offset(right, top + cornerLen),
            // Bottom-left
            Offset(left, bottom - cornerLen) to Offset(left, bottom),
            Offset(left, bottom) to Offset(left + cornerLen, bottom),
            // Bottom-right
            Offset(right - cornerLen, bottom) to Offset(right, bottom),
            Offset(right, bottom) to Offset(right, bottom - cornerLen)
        )

        corners.forEach { (start, end) ->
            drawLine(
                color = RuRubyRed,
                start = start,
                end = end,
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrCameraPreview(
    onQrDetected: (String) -> Unit,
    onCameraBound: (Camera) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val listener = Runnable {
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                provider = cameraProvider

                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val inputImage = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )
                    barcodeScanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                            if (!value.isNullOrBlank()) {
                                onQrDetected(value)
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                onCameraBound(camera)
            }
        }

        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            barcodeScanner.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun PixPasteCodeContent(
    pastedCode: String,
    isLookingUp: Boolean,
    error: String?,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSwitchToCamera: () -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    PixScaffold(title = "", onBack = onBack, useCloseIcon = true) { padding ->
        ScreenColumn(padding) {
            Text(
                text = "Pix Copia e Cola",
                color = colors.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Cole abaixo o código Pix que você recebeu para conferir o destinatário e o valor antes de confirmar.",
                color = colors.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            OutlinedTextField(
                value = pastedCode,
                onValueChange = onCodeChange,
                label = { Text("Código Pix") },
                placeholder = { Text("000201...", color = colors.onSurfaceVariant) },
                enabled = !isLookingUp,
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outline,
                    focusedLabelColor = colors.primary,
                    unfocusedLabelColor = colors.onSurfaceVariant,
                    cursorColor = colors.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (clip.isNotBlank()) {
                        onCodeChange(clip)
                    }
                },
                enabled = !isLookingUp,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = colors.primary)
                Spacer(Modifier.width(8.dp))
                Text("Colar da área de transferência", color = colors.primary, fontWeight = FontWeight.SemiBold)
            }

            ErrorText(error)

            Button(
                onClick = onSubmit,
                enabled = !isLookingUp && pastedCode.isNotBlank(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLookingUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Continuar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            OutlinedButton(
                onClick = onSwitchToCamera,
                enabled = !isLookingUp,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = colors.onBackground)
                Spacer(Modifier.width(8.dp))
                Text("Ler com a câmera", color = colors.onBackground, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
