@file:OptIn(ExperimentalMaterial3Api::class)

package com.daftar.kredi.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftar.kredi.data.InvoiceItem
import com.daftar.kredi.data.SavedProduct
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CashierScreen(vm: AppViewModel, onCatalog: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cur by vm.settings.currency.collectAsState()
    val customers by vm.summaries.collectAsState()
    val catalog by vm.savedProducts.collectAsState()

    var cameraOn by remember { mutableStateOf(true) }
    var torchOn by remember { mutableStateOf(false) }
    var newBarcode by remember { mutableStateOf<String?>(null) }
    var showManual by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<InvoiceItem?>(null) }
    var pickCustomer by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var manualCode by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf("") }
    var lastCode by remember { mutableStateOf("") }
    var lastTime by remember { mutableLongStateOf(0L) }

    val dialogOpen = newBarcode != null || showManual || showPicker ||
        editing != null || pickCustomer || confirmClear

    LaunchedEffect(banner) {
        if (banner.isNotEmpty()) {
            delay(1800)
            banner = ""
        }
    }

    fun handleCode(raw: String, force: Boolean) {
        val code = raw.trim()
        if (code.isEmpty() || dialogOpen) return
        val now = SystemClock.elapsedRealtime()
        // نفس الباركود خلال ثانيتين = تجاهل (حتى ما يتكرر بالغلط)
        if (!force && code == lastCode && now - lastTime < 2000) return
        lastCode = code
        lastTime = now
        scope.launch {
            val p = vm.findByBarcode(code)
            if (p != null) {
                vm.addProductToInvoice(p)
                Beeper.success()
                banner = "✓ ${p.name}"
            } else {
                Beeper.newProduct()
                newBarcode = code
            }
        }
    }

    val total = vm.invoiceTotal

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("الكاشير") },
                actions = { TextButton(onClick = onCatalog) { Text("المنتجات") } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (cameraOn) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    CameraGate {
                        CameraPreview(Modifier.fillMaxSize(), torchOn = torchOn) { code ->
                            handleCode(code, false)
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        banner.isNotEmpty() -> banner
                        cameraOn -> "وجّه الكاميرا نحو الباركود"
                        else -> "الكاميرا متوقفة"
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (banner.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                    color = if (banner.isNotEmpty()) paidColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (cameraOn) {
                    TextButton(onClick = { torchOn = !torchOn }) {
                        Text(if (torchOn) "إطفاء الضوء" else "إضاءة")
                    }
                }
                TextButton(onClick = { cameraOn = !cameraOn; torchOn = false }) {
                    Text(if (cameraOn) "إيقاف" else "تشغيل")
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    label = { Text("باركود (كتابة أو جهاز ماسح)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        handleCode(manualCode, true)
                        manualCode = ""
                    }),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    handleCode(manualCode, true)
                    manualCode = ""
                }) { Text("إضافة") }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showManual = true }, modifier = Modifier.weight(1f)) {
                    Text("إضافة يدوي")
                }
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
                    Text("من المنتجات")
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vm.invoice.isEmpty()) {
                    item {
                        Text(
                            "الفاتورة فارغة.\nامسح باركود منتج للبدء.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                items(vm.invoice, key = { it.id }) { item ->
                    Card(onClick = { editing = item }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ProductImage(item.imagePath, 52.dp)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${money(item.price, cur)} للواحد",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StepButton("−") { vm.changeQty(item.id, -1.0) }
                                    Text(
                                        fmt(item.qty),
                                        Modifier.padding(horizontal = 10.dp),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    StepButton("+") { vm.changeQty(item.id, 1.0) }
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        money(item.price * item.qty, cur),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("الإجمالي (${vm.invoice.size} منتج)", style = MaterialTheme.typography.bodyLarge)
                            TextButton(
                                onClick = { if (vm.invoice.isNotEmpty()) confirmClear = true },
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("مسح الفاتورة") }
                        }
                        Text(
                            money(total, cur),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { pickCustomer = true },
                            enabled = vm.invoice.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DebtButtonColor, contentColor = Color.White
                            )
                        ) { Text("دين على زبون") }
                        Button(
                            onClick = {
                                vm.clearInvoice()
                                ctx.toast("تم البيع نقداً ✓")
                            },
                            enabled = vm.invoice.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PaidButtonColor, contentColor = Color.White
                            )
                        ) { Text("دفع نقدي") }
                    }
                }
            }
        }
    }

    // منتج جديد غير مسجّل: صورة + اسم + سعر، ثم يُضاف للفاتورة تلقائياً
    newBarcode?.let { code ->
        ProductFormDialog(
            vm = vm,
            initial = null,
            presetBarcode = code,
            onDismiss = { newBarcode = null },
            onSave = { p ->
                scope.launch {
                    val id = vm.insertProduct(p)
                    vm.addProductToInvoice(p.copy(id = id))
                    Beeper.success()
                    banner = "✓ ${p.name}"
                    newBarcode = null
                }
            }
        )
    }

    if (showManual) {
        ManualItemDialog(
            onDismiss = { showManual = false },
            onAdd = { n, p, q ->
                vm.addItem(n, p, q)
                showManual = false
            }
        )
    }

    if (showPicker) {
        CatalogPickerDialog(
            products = catalog,
            cur = cur,
            onDismiss = { showPicker = false },
            onPick = { p ->
                vm.addProductToInvoice(p)
                Beeper.success()
                banner = "✓ ${p.name}"
            }
        )
    }

    editing?.let { item ->
        ItemDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { vm.updateItem(it); editing = null },
            onDelete = { vm.removeItem(item.id); editing = null }
        )
    }

    if (pickCustomer) {
        CustomerPickerDialog(
            customers = customers,
            onDismiss = { pickCustomer = false },
            onPick = { id ->
                vm.addInvoiceAsDebt(id)
                pickCustomer = false
                ctx.toast("تمت إضافة الفاتورة كدين على الزبون")
            }
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "مسح الفاتورة",
            text = "سيتم مسح كل منتجات الفاتورة الحالية. متأكد؟",
            confirmText = "مسح",
            onConfirm = { vm.clearInvoice(); confirmClear = false },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        contentPadding = PaddingValues(0.dp)
    ) { Text(label, fontSize = 22.sp) }
}

@Composable
private fun ManualItemDialog(onDismiss: () -> Unit, onAdd: (String, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة يدوي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("اسم المنتج") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price, onValueChange = { price = it },
                    label = { Text("السعر") }, singleLine = true, isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it },
                    label = { Text("الكمية") }, singleLine = true, isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = parseAmount(price)
                val q = parseAmount(qty)
                if (name.isBlank() || p == null || q == null) error = true
                else onAdd(name.trim(), p, q)
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun CatalogPickerDialog(
    products: List<SavedProduct>,
    cur: String,
    onDismiss: () -> Unit,
    onPick: (SavedProduct) -> Unit
) {
    var q by remember { mutableStateOf("") }
    val key = q.trim()
    val shown = products.filter {
        key.isEmpty() || it.name.contains(key, ignoreCase = true) || it.barcode.contains(key)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر من المنتجات") },
        text = {
            Column {
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    label = { Text("بحث بالاسم أو الباركود") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (products.isEmpty()) {
                    Text("لا توجد منتجات محفوظة بعد. امسح باركود منتج جديد وسيُحفظ تلقائياً.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(shown, key = { it.id }) { p ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(p) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ProductImage(p.imagePath, 44.dp)
                                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text(money(p.price, cur), style = MaterialTheme.typography.bodyLarge)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } }
    )
}
