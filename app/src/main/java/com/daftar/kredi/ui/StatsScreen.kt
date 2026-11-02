@file:OptIn(ExperimentalMaterial3Api::class)

package com.daftar.kredi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

private fun pickerStart(utcMillis: Long): Long =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun pickerEnd(utcMillis: Long): Long =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

@Composable
fun StatsScreen(vm: AppViewModel) {
    val txns by vm.allTxns.collectAsState()
    val summaries by vm.summaries.collectAsState()
    val cur by vm.settings.currency.collectAsState()

    var period by remember { mutableIntStateOf(0) }
    var customFrom by remember { mutableLongStateOf(dayStart(6)) }
    var customTo by remember { mutableLongStateOf(endOfToday()) }
    var showPicker by remember { mutableStateOf(false) }

    val (from, to) = when (period) {
        0 -> dayStart(6) to endOfToday()
        1 -> dayStart(29) to endOfToday()
        else -> customFrom to customTo
    }

    val inRange = txns.filter { it.timestamp in from..to }
    val debts = inRange.filter { it.isDebt }
    val pays = inRange.filter { !it.isDebt }
    val perCustomer = debts.groupBy { it.customerId }.mapValues { e -> e.value.sumOf { it.amount } }
    val names = summaries.associate { it.customer.id to it.customer.name }
    val top = perCustomer.maxByOrNull { it.value }
    val low = if (perCustomer.size >= 2) perCustomer.minByOrNull { it.value } else null
    val topDebtors = summaries.filter { it.balance > 0.005 }.sortedByDescending { it.balance }.take(5)

    val dayFmt = SimpleDateFormat("d/M", Locale.US)
    val chart = dayBuckets(from, to).map { (s, e) ->
        dayFmt.format(Date(s)) to debts.filter { it.timestamp >= s && it.timestamp < e }.sumOf { it.amount }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text("الإحصائيات") }) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = period == 0, onClick = { period = 0 }, label = { Text("أسبوعي") })
                FilterChip(selected = period == 1, onClick = { period = 1 }, label = { Text("شهري") })
                FilterChip(selected = period == 2, onClick = { showPicker = true }, label = { Text("مخصص") })
            }
            Text(
                "من ${fmtDate(from)} إلى ${fmtDate(to)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("ديون جديدة", money(debts.sumOf { it.amount }, cur), debtColor(), Modifier.weight(1f))
                BigStat("مسدَّد", money(pays.sumOf { it.amount }, cur), paidColor(), Modifier.weight(1f))
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("أكثر زبون أخذ دين", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (top != null) "${names[top.key] ?: "—"}  •  ${money(top.value, cur)}" else "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = debtColor()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("أقل زبون أخذ دين", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (low != null) "${names[low.key] ?: "—"}  •  ${money(low.value, cur)}" else "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = paidColor()
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الديون الجديدة يوماً بيوم", style = MaterialTheme.typography.titleMedium)
                    BarChart(chart)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أكبر 5 مدينين حالياً", style = MaterialTheme.typography.titleMedium)
                    if (topDebtors.isEmpty()) {
                        Text("لا يوجد مدينون 🎉", style = MaterialTheme.typography.bodyLarge)
                    }
                    topDebtors.forEachIndexed { i, s ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. ${s.customer.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(money(s.balance, cur), color = debtColor(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis
                    if (s != null && e != null) {
                        customFrom = pickerStart(s)
                        customTo = pickerEnd(e)
                        period = 2
                    }
                    showPicker = false
                }) { Text("تأكيد") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("إلغاء") } }
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(500.dp))
        }
    }
}

@Composable
private fun BigStat(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun BarChart(data: List<Pair<String, Double>>) {
    val max = (data.maxOfOrNull { it.second } ?: 0.0).takeIf { it > 0 } ?: 1.0
    val barColor = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().height(190.dp), verticalAlignment = Alignment.Bottom) {
        data.forEachIndexed { i, (label, v) ->
            val frac = (v / max).toFloat().coerceIn(0.02f, 1f)
            val showLabel = data.size <= 8 || i % 5 == 0
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1.01f - frac))
                Box(
                    Modifier
                        .weight(frac)
                        .fillMaxWidth(0.6f)
                        .background(barColor, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                )
                Text(
                    if (showLabel) label else " ",
                    fontSize = 11.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
