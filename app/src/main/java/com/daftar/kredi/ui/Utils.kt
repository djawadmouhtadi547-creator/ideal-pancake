package com.daftar.kredi.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.daftar.kredi.data.Txn
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun fmt(v: Double): String =
    DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)).format(v)

fun money(v: Double, currency: String): String =
    if (currency.isBlank()) fmt(v) else "${fmt(v)} $currency"

/** رقم قابل للتعديل داخل حقل نصي (بدون فواصل آلاف) */
fun fmtPlain(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** تحويل الأرقام العربية/الفارسية إلى لاتينية */
fun normalizeDigits(s: String): String {
    val sb = StringBuilder()
    for (ch in s) {
        sb.append(
            when (ch) {
                in '٠'..'٩' -> '0' + (ch - '٠')
                in '۰'..'۹' -> '0' + (ch - '۰')
                '٫', ',' -> '.'
                else -> ch
            }
        )
    }
    return sb.toString()
}

/** يرجع الرقم إن كان صالحاً وأكبر من صفر، وإلا null */
fun parseAmount(s: String): Double? =
    normalizeDigits(s.trim()).toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

/** تنسيق ناتج الحاسبة */
fun numStr(v: Double): String {
    if (!v.isFinite()) return "خطأ"
    return BigDecimal(v).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

fun fmtDateTime(ts: Long): String =
    SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US).format(Date(ts))

fun fmtDate(ts: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(ts))

/** بداية اليوم قبل [daysAgo] يوم (0 = اليوم، -1 = بداية الغد) */
fun dayStart(daysAgo: Int): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    c.add(Calendar.DAY_OF_YEAR, -daysAgo)
    return c.timeInMillis
}

fun endOfToday(): Long = dayStart(-1) - 1

/** يقسم المدى إلى أيام (آخر 31 يوم كحد أقصى) */
fun dayBuckets(from: Long, to: Long): List<Pair<Long, Long>> {
    val res = mutableListOf<Pair<Long, Long>>()
    val c = Calendar.getInstance()
    c.timeInMillis = from
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    while (c.timeInMillis <= to && res.size < 800) {
        val s = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
        res.add(s to c.timeInMillis)
    }
    return res.takeLast(31)
}

fun Context.findActivity(): FragmentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

private fun Context.safeStart(i: Intent) {
    try {
        startActivity(i)
    } catch (e: ActivityNotFoundException) {
        toast("لا يوجد تطبيق مناسب")
    }
}

fun Context.dial(phone: String) =
    safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))

/** واتساب يحتاج الرقم بالصيغة الدولية (مثال: 213550123456) */
fun Context.whatsapp(phone: String, text: String = "") {
    var digits = phone.filter { it.isDigit() }
    if (digits.startsWith("00")) digits = digits.drop(2)
    val url = "https://wa.me/$digits" + if (text.isNotEmpty()) "?text=" + Uri.encode(text) else ""
    safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

fun Context.shareText(text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    safeStart(Intent.createChooser(i, "مشاركة"))
}

fun buildStatement(name: String, txns: List<Txn>, currency: String): String {
    val sb = StringBuilder()
    sb.appendLine("كشف حساب: $name")
    sb.appendLine("بتاريخ: ${fmtDate(System.currentTimeMillis())}")
    sb.appendLine("────────────")
    var bal = 0.0
    txns.sortedBy { it.timestamp }.forEach { t ->
        bal += if (t.isDebt) t.amount else -t.amount
        val kind = if (t.isDebt) "دين" else "دفعة"
        sb.append("${fmtDate(t.timestamp)} | $kind ${money(t.amount, currency)}")
        if (t.note.isNotBlank()) sb.append(" (${t.note})")
        sb.appendLine()
    }
    sb.appendLine("────────────")
    sb.append("المتبقي: ${money(bal, currency)}")
    return sb.toString()
}
