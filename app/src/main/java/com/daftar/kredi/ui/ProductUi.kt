package com.daftar.kredi.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Size
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.daftar.kredi.data.SavedProduct
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

// ---------------------------------------------------------------
// الصور
// ---------------------------------------------------------------

/** يصغّر الصورة ويصحح اتجاهها ويحفظها داخل التطبيق، ويرجع مسار الملف */
suspend fun importImage(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return@withContext null

        val orientation = ctx.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val deg = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (deg != 0f) {
            val m = Matrix().apply { postRotate(deg) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val scale = 800f / maxOf(bmp.width, bmp.height)
        if (scale < 1f) {
            bmp = Bitmap.createScaledBitmap(
                bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
            )
        }
        val dir = File(ctx.filesDir, "products").apply { mkdirs() }
        val f = File(dir, "p_${System.currentTimeMillis()}.jpg")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        f.absolutePath
    } catch (e: Exception) {
        null
    }
}

@Composable
fun ProductImage(path: String, size: Dp, modifier: Modifier = Modifier) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path.isBlank()) null else withContext(Dispatchers.IO) {
            try {
                val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(path, o)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.ShoppingCart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------
// الكاميرا ومسح الباركود
// ---------------------------------------------------------------

@Composable
fun CameraGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    if (granted) {
        content()
    } else {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("نحتاج إذن الكاميرا لمسح الباركود", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("السماح بالكاميرا") }
            Text(
                "إذا ما ظهر الطلب، فعّل الإذن من إعدادات الجهاز > التطبيقات > دفتر الكريدي",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    torchOn: Boolean = false,
    onCode: (String) -> Unit
) {
    val owner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentTorch by rememberUpdatedState(torchOn)
    val executor = remember { Executors.newSingleThreadExecutor() }
    // نقتصر على أنواع الباركود المستعملة في المحلات لرفع الدقة وتقليل الأخطاء
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_ITF,
                    Barcode.FORMAT_QR_CODE
                )
                .build()
        )
    }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val cameraHolder = remember { arrayOfNulls<Camera>(1) }

    LaunchedEffect(torchOn) {
        cameraHolder[0]?.cameraControl?.enableTorch(torchOn)
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                providerHolder[0]?.unbindAll()
            } catch (e: Exception) {
            }
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { c ->
            val previewView = PreviewView(c)
            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

            // اللمس على الشاشة = تركيز على النقطة
            previewView.setOnTouchListener { v, ev ->
                if (ev.action == MotionEvent.ACTION_UP) {
                    val point = previewView.meteringPointFactory.createPoint(ev.x, ev.y)
                    cameraHolder[0]?.cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(point).build()
                    )
                    v.performClick()
                }
                true
            }

            val future = ProcessCameraProvider.getInstance(c)
            future.addListener({
                try {
                    val provider = future.get()
                    providerHolder[0] = provider

                    val previewSelector = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .build()
                    val preview = Preview.Builder().setResolutionSelector(previewSelector).build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    // دقة تحليل عالية (1080p) لقراءة الباركود الصغير والبعيد بدقة
                    val analysisSelector = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(analysisSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor, BarcodeAnalyzer(scanner) { code -> currentOnCode(code) })

                    provider.unbindAll()
                    val cam = provider.bindToLifecycle(
                        owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                    )
                    cameraHolder[0] = cam
                    cam.cameraControl.enableTorch(currentTorch)
                } catch (e: Exception) {
                }
            }, ContextCompat.getMainExecutor(c))
            previewView
        }
    )
}

@Composable
fun ScannerDialog(onDismiss: () -> Unit, onCode: (String) -> Unit) {
    var done by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                CameraGate {
                    CameraPreview(Modifier.fillMaxSize(), torchOn = torch) { code ->
                        if (!done) {
                            done = true
                            Beeper.success()
                            onCode(code)
                        }
                    }
                }
                Text(
                    "وجّه الكاميرا نحو الباركود",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.TopCenter).padding(32.dp)
                )
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = { torch = !torch }) {
                        Text(if (torch) "إطفاء الإضاءة" else "إضاءة", color = Color.White)
                    }
                    Button(onClick = onDismiss) { Text("إلغاء") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------
// نموذج المنتج: اسم + سعر + باركود + صورة
// ---------------------------------------------------------------

@Composable
fun ProductFormDialog(
    vm: AppViewModel,
    initial: SavedProduct?,
    presetBarcode: String,
    onDismiss: () -> Unit,
    onSave: (SavedProduct) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var price by remember { mutableStateOf(initial?.let { fmtPlain(it.price) } ?: "") }
    var barcode by remember { mutableStateOf(initial?.barcode ?: presetBarcode) }
    var imagePath by remember { mutableStateOf(initial?.imagePath ?: "") }
    var error by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val f = pendingFile
        if (ok && f != null) {
            scope.launch {
                val p = importImage(ctx, Uri.fromFile(f))
                if (p != null) imagePath = p
                f.delete()
            }
        }
    }

    fun startCapture() {
        val f = File(ctx.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
        pendingFile = f
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        takePicture.launch(uri)
    }

    val askCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCapture() else ctx.toast("يلزم إذن الكاميرا لالتقاط الصورة")
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch { importImage(ctx, uri)?.let { imagePath = it } }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "منتج جديد" else "تعديل المنتج") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProductImage(imagePath, 96.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            ) startCapture() else askCamera.launch(Manifest.permission.CAMERA)
                        }) { Text("التقاط صورة") }
                        OutlinedButton(onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) { Text("من المعرض") }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = "" },
                    label = { Text("اسم المنتج") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it; error = "" },
                    label = { Text("السعر") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it; error = "" },
                    label = { Text("الباركود") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { showScanner = true }) { Text("مسح") }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = parseAmount(price)
                if (name.isBlank()) {
                    error = "اكتب اسم المنتج"
                    return@Button
                }
                if (p == null) {
                    error = "اكتب السعر بشكل صحيح"
                    return@Button
                }
                scope.launch {
                    val code = barcode.trim()
                    if (code.isNotEmpty()) {
                        val ex = vm.findByBarcode(code)
                        if (ex != null && ex.id != (initial?.id ?: -1L)) {
                            error = "هذا الباركود مسجّل للمنتج: ${ex.name}"
                            return@launch
                        }
                    }
                    onSave(
                        SavedProduct(
                            id = initial?.id ?: 0,
                            name = name.trim(),
                            price = p,
                            barcode = code,
                            imagePath = imagePath
                        )
                    )
                }
            }) { Text("حفظ") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("حذف", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )

    if (showScanner) {
        ScannerDialog(
            onDismiss = { showScanner = false },
            onCode = { code ->
                barcode = code
                showScanner = false
            }
        )
    }
}
