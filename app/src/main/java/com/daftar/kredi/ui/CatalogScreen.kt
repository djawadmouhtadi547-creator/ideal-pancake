@file:OptIn(ExperimentalMaterial3Api::class)

package com.daftar.kredi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daftar.kredi.data.SavedProduct

@Composable
fun CatalogScreen(vm: AppViewModel, onBack: () -> Unit) {
    val cur by vm.settings.currency.collectAsState()
    val products by vm.savedProducts.collectAsState()
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavedProduct?>(null) }
    var deleting by remember { mutableStateOf<SavedProduct?>(null) }

    val q = query.trim()
    val shown = products.filter {
        q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.barcode.contains(q)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("المنتجات (${products.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("منتج جديد") }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("بحث بالاسم أو الباركود") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (products.isEmpty()) "لا توجد منتجات بعد.\nأضف منتجاً أو امسح باركوده من الكاشير."
                            else "لا توجد نتائج",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                items(shown, key = { it.id }) { p ->
                    Card(onClick = { editing = p }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProductImage(p.imagePath, 60.dp)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (p.barcode.isBlank()) "بدون باركود" else p.barcode,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                money(p.price, cur),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        ProductFormDialog(
            vm = vm, initial = null, presetBarcode = "",
            onDismiss = { showAdd = false },
            onSave = { vm.addProduct(it); showAdd = false }
        )
    }

    editing?.let { p ->
        ProductFormDialog(
            vm = vm, initial = p, presetBarcode = "",
            onDismiss = { editing = null },
            onSave = { vm.updateProduct(it); editing = null },
            onDelete = { deleting = p; editing = null }
        )
    }

    deleting?.let { p ->
        ConfirmDialog(
            title = "حذف المنتج",
            text = "هل تريد حذف «${p.name}» من القائمة؟",
            confirmText = "حذف",
            onConfirm = { vm.deleteProduct(p); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}
