@file:OptIn(ExperimentalMaterial3Api::class)

package com.daftar.kredi.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CalculatorScreen() {
    var display by remember { mutableStateOf("0") }
    var expr by remember { mutableStateOf("") }
    var acc by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<Char?>(null) }
    var fresh by remember { mutableStateOf(true) }

    fun cur(): Double = display.toDoubleOrNull() ?: 0.0

    fun calc(a: Double, b: Double, o: Char): Double? = when (o) {
        '+' -> a + b
        '−' -> a - b
        '×' -> a * b
        '÷' -> if (b == 0.0) null else a / b
        else -> b
    }

    fun fail() {
        display = "خطأ"
        expr = ""
        acc = null
        op = null
        fresh = true
    }

    fun press(k: String) {
        if (display == "خطأ") {
            display = "0"; expr = ""; acc = null; op = null; fresh = true
        }
        when {
            k.length == 1 && k[0].isDigit() -> {
                if (fresh) {
                    display = k
                    fresh = false
                } else if (display.length < 15) {
                    display = if (display == "0") k else display + k
                }
            }
            k == "." -> {
                if (fresh) {
                    display = "0."
                    fresh = false
                } else if (!display.contains('.')) {
                    display += "."
                }
            }
            k == "C" -> {
                display = "0"; expr = ""; acc = null; op = null; fresh = true
            }
            k == "⌫" -> {
                if (!fresh) {
                    display = if (display.length <= 1 || (display.length == 2 && display.startsWith("-"))) "0"
                    else display.dropLast(1)
                }
            }
            k == "±" -> {
                if (display != "0") {
                    display = if (display.startsWith("-")) display.drop(1) else "-$display"
                }
            }
            k == "%" -> {
                val c = cur()
                val a = acc
                val v = if (a != null && (op == '+' || op == '−')) a * c / 100 else c / 100
                display = numStr(v)
                fresh = true
            }
            k == "=" -> {
                val a = acc
                val o = op
                if (a != null && o != null) {
                    val b = cur()
                    val r = calc(a, b, o)
                    if (r == null) {
                        fail()
                    } else {
                        expr = "${numStr(a)} $o ${numStr(b)} ="
                        display = numStr(r)
                        acc = null
                        op = null
                        fresh = true
                    }
                }
            }
            else -> {
                val c = cur()
                val o = op
                if (o == null) {
                    acc = c
                } else if (!fresh) {
                    val a = acc ?: c
                    val r = calc(a, c, o)
                    if (r == null) {
                        fail()
                        return
                    }
                    acc = r
                    display = numStr(r)
                }
                op = k[0]
                expr = "${numStr(acc ?: c)} $k"
                fresh = true
            }
        }
    }

    val rows = listOf(
        listOf("C", "⌫", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("±", "0", ".", "=")
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CenterAlignedTopAppBar(title = { Text("الآلة الحاسبة") }) }
    ) { pad ->
        // الحاسبة تبقى من اليسار لليمين حتى تكون الأرقام بترتيبها المعتاد
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            expr,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            display,
                            fontSize = if (display.length > 11) 32.sp else 48.sp,
                            maxLines = 1,
                            textAlign = TextAlign.End
                        )
                    }
                }
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        r.forEach { k -> CalcKey(k, Modifier.weight(1f)) { press(k) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    val isOp = label in listOf("÷", "×", "−", "+", "=")
    val isFn = label in listOf("C", "⌫", "%", "±")
    val colors = when {
        isOp -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
        isFn -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        else -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = colors,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 26.sp)
    }
}
