@file:OptIn(ExperimentalMaterial3Api::class)

package com.daftar.kredi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftar.kredi.data.Txn

@Composable
fun CustomerDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cur by vm.settings.currency.collectAsState()
    val customerFlow = remember(id) { vm.customerFlow(id) }
    val txnsFlow = remember(id) { vm.txnsFlow(id) }
    val customer by customerFlow.collectAsState(initial = null)
    val txns by txnsFlow.collectAsState(initial = emptyList())

    var menu by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var addDebt by remember { mutableStateOf(false) }
    var addPay by remember { mutableStateOf(false) }
    var editTxn by remember { mutableStateOf<Txn?>(null) }
    var deleteTxn by remember { mutableStateOf<Txn?>(null) }

    val c = customer
    val balance = txns.sumOf { if (it.isDebt) it.amount else -it.amount }
    val owes = balance > 0.005
    val credit = balance < -0.005

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(c?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (c != null && c.phone.isNotBlank()) {
                        IconButton(onClick = { ctx.dial(c.phone) }) {
                            Icon(Icons.Default.Call, contentDescription = "اتصال")
                        }
                    }
                    IconButton(onClick = {
                        if (c != null) ctx.shareText(buildStatement(c.name, txns, cur))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة كشف الحساب")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "المزيد")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (c != null && c.phone.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("إرسال كشف عبر واتساب") },
                                    onClick = {
                                        menu = false
                                        ctx.whatsapp(c.phone, buildStatement(c.name, txns, cur))
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("تعديل البيانات") },
                                onClick = { menu = false; showEdit = true }
                            )
                            DropdownMenuItem(
                                text = { Text("حذف الزبون") },
                                onClick = { menu = false; showDelete = true }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { addDebt = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DebtButtonColor, contentColor = Color.White
                        )
                    ) { Text("إضافة دين") }
                    Button(
                        onClick = { addPay = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaidButtonColor, contentColor = Color.White
                        )
                    ) { Text("تسجيل دفعة") }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            when {
                                owes -> "المتبقي عليه"
                                credit -> "له رصيد"
                                else -> "الحساب"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            when {
                                owes -> money(balance, cur)
                                credit -> money(-balance, cur)
                                else -> "مسدّد ✓"
                            },
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (owes) debtColor() else paidColor()
                        )
                        if (c != null && c.phone.isNotBlank()) {
                            Text("الهاتف: ${c.phone}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (c != null && c.note.isNotBlank()) {
                            Text("ملاحظة: ${c.note}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Text("سجل العمليات", style = MaterialTheme.typography.titleMedium)
            }
            if (txns.isEmpty()) {
                item {
                    Text(
                        "لا توجد عمليات بعد",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(txns, key = { it.id }) { t ->
                Card(onClick = { editTxn = t }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (t.isDebt) "دين" else "دفعة",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                fmtDateTime(t.timestamp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (t.note.isNotBlank()) {
                                Text(t.note, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Text(
                            (if (t.isDebt) "+ " else "− ") + money(t.amount, cur),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (t.isDebt) debtColor() else paidColor()
                        )
                    }
                }
            }
        }
    }

    if (addDebt) {
        TxnDialog(
            title = "إضافة دين", initial = null, confirmText = "إضافة",
            onDismiss = { addDebt = false },
            onSave = { a, n -> vm.addTxn(id, true, a, n); addDebt = false }
        )
    }
    if (addPay) {
        TxnDialog(
            title = "تسجيل دفعة", initial = null, confirmText = "تسجيل",
            onDismiss = { addPay = false },
            onSave = { a, n -> vm.addTxn(id, false, a, n); addPay = false }
        )
    }
    editTxn?.let { t ->
        TxnDialog(
            title = if (t.isDebt) "تعديل الدين" else "تعديل الدفعة",
            initial = t, confirmText = "حفظ",
            onDismiss = { editTxn = null },
            onSave = { a, n -> vm.updateTxn(t.copy(amount = a, note = n)); editTxn = null },
            onDelete = { deleteTxn = t; editTxn = null }
        )
    }
    deleteTxn?.let { t ->
        ConfirmDialog(
            title = "حذف العملية",
            text = "هل تريد حذف هذه العملية (${money(t.amount, cur)})؟",
            confirmText = "حذف",
            onConfirm = { vm.deleteTxn(t.id); deleteTxn = null },
            onDismiss = { deleteTxn = null }
        )
    }
    if (showEdit && c != null) {
        CustomerFormDialog(
            initial = c,
            onDismiss = { showEdit = false },
            onSave = { n, p, note ->
                vm.updateCustomer(c.copy(name = n, phone = p, note = note))
                showEdit = false
            }
        )
    }
    if (showDelete && c != null) {
        ConfirmDialog(
            title = "حذف الزبون",
            text = "سيتم حذف «${c.name}» وكل عملياته نهائياً. هل أنت متأكد؟",
            confirmText = "حذف",
            onConfirm = { showDelete = false; vm.deleteCustomer(c.id); onBack() },
            onDismiss = { showDelete = false }
        )
    }
}
