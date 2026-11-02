@file:OptIn(ExperimentalMaterial3Api::class)

package com.daftar.kredi.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cur by vm.settings.currency.collectAsState()
    val pinOn by vm.settings.pinEnabled.collectAsState()
    val bioOn by vm.settings.biometricEnabled.collectAsState()
    val canBio = remember { canUseBiometric(ctx) }

    var showPin by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var currencyText by remember { mutableStateOf(cur) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = vm.exportJson()
                val ok = withContext(Dispatchers.IO) {
                    try {
                        ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                ctx.toast(if (ok) "تم حفظ النسخة الاحتياطية" else "فشل حفظ النسخة")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    } catch (e: Exception) {
                        null
                    }
                }
                val ok = text != null && vm.importJson(text)
                ctx.toast(if (ok) "تمت استعادة البيانات" else "الملف غير صالح")
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text("الإعدادات") }) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("العملة", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = currencyText,
                        onValueChange = {
                            currencyText = it
                            vm.settings.setCurrency(it.trim())
                        },
                        label = { Text("رمز العملة (مثال: دج، ر.س، \$)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("دج", "ر.س", "د.إ", "ج.م", "د.ك", "ر.ع", "د.أ", "ل.ل", "\$", "€").forEach { c ->
                            FilterChip(
                                selected = cur == c,
                                onClick = { currencyText = c; vm.settings.setCurrency(c) },
                                label = { Text(c) }
                            )
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الحماية", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("قفل التطبيق برمز PIN", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = pinOn,
                            onCheckedChange = { if (it) showPin = true else vm.settings.clearPin() }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الفتح بالبصمة", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = bioOn,
                            enabled = pinOn && canBio,
                            onCheckedChange = { vm.settings.setBiometric(it) }
                        )
                    }
                    if (!canBio) {
                        Text(
                            "البصمة غير متوفرة أو غير مفعّلة في هذا الجهاز",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "يُطلب الرمز عند فتح التطبيق وبعد تركه في الخلفية أكثر من 30 ثانية.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("النسخ الاحتياطي", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "احفظ نسخة من كل الزبائن والديون في ملف، ويمكنك استعادتها لاحقاً أو على جهاز آخر.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            val d = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                            exportLauncher.launch("kredi-backup-$d.json")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("حفظ نسخة احتياطية") }
                    OutlinedButton(
                        onClick = { confirmRestore = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("استعادة من نسخة") }
                }
            }

            Text(
                "دفتر الكريدي • الإصدار 1.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
        }
    }

    if (showPin) {
        PinSetupDialog(
            onDismiss = { showPin = false },
            onSet = { pin -> vm.settings.setPin(pin); showPin = false }
        )
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = "استعادة البيانات",
            text = "ستُستبدل كل البيانات الحالية بمحتوى النسخة الاحتياطية. هل تريد المتابعة؟",
            confirmText = "متابعة",
            onConfirm = {
                confirmRestore = false
                importLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { confirmRestore = false }
        )
    }
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعيين رمز PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("رمز من 4 أرقام") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirm = it },
                    label = { Text("أعد كتابة الرمز") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    pin.length != 4 -> error = "الرمز يجب أن يكون 4 أرقام"
                    pin != confirm -> error = "الرمزان غير متطابقين"
                    else -> onSet(pin)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
