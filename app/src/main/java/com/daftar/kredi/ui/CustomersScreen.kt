@file:OptIn(ExperimentalMaterial3Api::class)

package com.daftar.kredi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftar.kredi.data.CustomerSummary

@Composable
fun CustomersScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val list by vm.summaries.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }

    val totalDebt = list.sumOf { if (it.balance > 0.005) it.balance else 0.0 }
    val debtors = list.count { it.balance > 0.005 }

    val shown = remember(list, query, sort) {
        val q = query.trim()
        val f = if (q.isEmpty()) list
        else list.filter { it.customer.name.contains(q, ignoreCase = true) || it.customer.phone.contains(q) }
        when (sort) {
            1 -> f.sortedByDescending { it.balance }
            2 -> f.sortedByDescending { it.lastActivity }
            else -> f.sortedBy { it.customer.name.lowercase() }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text("دفتر الكريدي") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("زبون جديد") }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("إجمالي الديون", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            money(totalDebt, cur),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = debtColor()
                        )
                        Text("عدد المدينين: $debtors من ${list.size}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("ابحث بالاسم أو رقم الهاتف") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("الاسم", "أكبر دين", "آخر عملية").forEachIndexed { i, label ->
                        FilterChip(
                            selected = sort == i,
                            onClick = { sort = i },
                            label = { Text(label) }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (list.isEmpty()) "لا يوجد زبائن بعد.\nاضغط على «زبون جديد» للبدء."
                            else "لا توجد نتائج للبحث",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                items(shown, key = { it.customer.id }) { s ->
                    CustomerRow(s, cur) { onOpen(s.customer.id) }
                }
            }
        }
    }

    if (showAdd) {
        CustomerFormDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { n, p, note ->
                vm.addCustomer(n, p, note)
                showAdd = false
            }
        )
    }
}

@Composable
private fun CustomerRow(s: CustomerSummary, cur: String, onClick: () -> Unit) {
    val owes = s.balance > 0.005
    val credit = s.balance < -0.005
    val color = if (owes) debtColor() else paidColor()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.customer.name.trim().take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    s.customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (s.customer.phone.isNotBlank()) {
                    Text(
                        s.customer.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // الدين يظهر بجانب الاسم داخل مربع ملوّن واضح
            Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp)) {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when {
                            owes -> "عليه"
                            credit -> "له"
                            else -> "الحساب"
                        },
                        fontSize = 12.sp,
                        color = color
                    )
                    Text(
                        when {
                            owes -> money(s.balance, cur)
                            credit -> money(-s.balance, cur)
                            else -> "مسدّد ✓"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}
