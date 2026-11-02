package com.daftar.kredi.ui

import android.app.Application
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.daftar.kredi.data.AppDatabase
import com.daftar.kredi.data.Customer
import com.daftar.kredi.data.CustomerSummary
import com.daftar.kredi.data.InvoiceItem
import com.daftar.kredi.data.SavedProduct
import com.daftar.kredi.data.SettingsStore
import com.daftar.kredi.data.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.dao()
    val settings = SettingsStore(app)

    val summaries: StateFlow<List<CustomerSummary>> =
        combine(dao.customers(), dao.allTxns()) { customers, txns ->
            val byCustomer = txns.groupBy { it.customerId }
            customers.map { c ->
                val t = byCustomer[c.id].orEmpty()
                CustomerSummary(
                    customer = c,
                    balance = t.sumOf { if (it.isDebt) it.amount else -it.amount },
                    lastActivity = t.maxOfOrNull { it.timestamp } ?: c.createdAt
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allTxns: StateFlow<List<Txn>> =
        dao.allTxns().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedProducts: StateFlow<List<SavedProduct>> =
        dao.products().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun customerFlow(id: Long) = dao.customer(id)
    fun txnsFlow(id: Long) = dao.txnsOf(id)

    // ---------- الزبائن ----------
    fun addCustomer(name: String, phone: String, note: String) {
        viewModelScope.launch { dao.insertCustomer(Customer(name = name, phone = phone, note = note)) }
    }

    fun updateCustomer(c: Customer) {
        viewModelScope.launch { dao.updateCustomer(c) }
    }

    fun deleteCustomer(id: Long) {
        viewModelScope.launch { dao.deleteCustomer(id) }
    }

    // ---------- العمليات ----------
    fun addTxn(customerId: Long, isDebt: Boolean, amount: Double, note: String) {
        viewModelScope.launch {
            dao.insertTxn(Txn(customerId = customerId, isDebt = isDebt, amount = amount, note = note))
        }
    }

    fun updateTxn(t: Txn) {
        viewModelScope.launch { dao.updateTxn(t) }
    }

    fun deleteTxn(id: Long) {
        viewModelScope.launch { dao.deleteTxn(id) }
    }

    // ---------- المنتجات المحفوظة ----------
    suspend fun findByBarcode(code: String): SavedProduct? = dao.byBarcode(code)

    suspend fun insertProduct(p: SavedProduct): Long = dao.insertProduct(p)

    fun addProduct(p: SavedProduct) {
        viewModelScope.launch { dao.insertProduct(p) }
    }

    fun updateProduct(p: SavedProduct) {
        viewModelScope.launch { dao.updateProduct(p) }
    }

    fun saveProduct(name: String, price: Double) {
        viewModelScope.launch { dao.insertProduct(SavedProduct(name = name, price = price)) }
    }

    fun deleteProduct(id: Long) {
        viewModelScope.launch { dao.deleteProduct(id) }
    }

    fun deleteProduct(p: SavedProduct) {
        viewModelScope.launch {
            dao.deleteProduct(p.id)
            if (p.imagePath.isNotBlank()) {
                try {
                    File(p.imagePath).delete()
                } catch (e: Exception) {
                }
            }
        }
    }

    // ---------- الفاتورة الحالية (في الذاكرة) ----------
    val invoice = mutableStateListOf<InvoiceItem>()
    private var nextItemId = 1L

    val invoiceTotal: Double
        get() = invoice.sumOf { it.price * it.qty }

    fun addItem(name: String, price: Double, qty: Double) {
        invoice.add(InvoiceItem(nextItemId++, name, price, qty))
    }

    /** إضافة منتج من القائمة: إذا كان موجوداً في الفاتورة تزيد كميته بواحد */
    fun addProductToInvoice(p: SavedProduct) {
        val i = if (p.id != 0L) invoice.indexOfFirst { it.productId == p.id } else -1
        if (i >= 0) {
            invoice[i] = invoice[i].copy(qty = invoice[i].qty + 1)
        } else {
            invoice.add(InvoiceItem(nextItemId++, p.name, p.price, 1.0, p.id, p.imagePath))
        }
    }

    fun changeQty(id: Long, delta: Double) {
        val i = invoice.indexOfFirst { it.id == id }
        if (i < 0) return
        val q = invoice[i].qty + delta
        if (q <= 0.0) invoice.removeAt(i) else invoice[i] = invoice[i].copy(qty = q)
    }

    fun updateItem(item: InvoiceItem) {
        val i = invoice.indexOfFirst { it.id == item.id }
        if (i >= 0) invoice[i] = item
    }

    fun removeItem(id: Long) {
        invoice.removeAll { it.id == id }
    }

    fun clearInvoice() {
        invoice.clear()
    }

    fun addInvoiceAsDebt(customerId: Long) {
        val total = invoiceTotal
        if (total <= 0.0) return
        val note = "فاتورة: " + invoice.joinToString("، ") { "${it.name}×${fmt(it.qty)}" }
        addTxn(customerId, true, total, note)
        invoice.clear()
    }

    // ---------- النسخ الاحتياطي ----------
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val customers = dao.customersOnce()
        val txns = dao.txnsOnce()
        val products = dao.productsOnce()
        val root = JSONObject()
        root.put("app", "daftar-kredi")
        root.put("version", 1)
        val cArr = JSONArray()
        customers.forEach {
            cArr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone)
                    .put("note", it.note).put("createdAt", it.createdAt)
            )
        }
        val tArr = JSONArray()
        txns.forEach {
            tArr.put(
                JSONObject().put("id", it.id).put("customerId", it.customerId).put("isDebt", it.isDebt)
                    .put("amount", it.amount).put("note", it.note).put("timestamp", it.timestamp)
            )
        }
        val pArr = JSONArray()
        products.forEach {
            var b64 = ""
            if (it.imagePath.isNotBlank()) {
                try {
                    val f = File(it.imagePath)
                    if (f.exists()) b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                } catch (e: Exception) {
                }
            }
            pArr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("price", it.price)
                    .put("barcode", it.barcode).put("imageB64", b64)
            )
        }
        root.put("customers", cArr)
        root.put("txns", tArr)
        root.put("products", pArr)
        root.toString(2)
    }

    suspend fun importJson(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(text)
            val cArr = root.getJSONArray("customers")
            val tArr = root.getJSONArray("txns")
            val pArr = root.optJSONArray("products") ?: JSONArray()
            val customers = (0 until cArr.length()).map {
                val o = cArr.getJSONObject(it)
                Customer(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    phone = o.optString("phone", ""),
                    note = o.optString("note", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            }
            val txns = (0 until tArr.length()).map {
                val o = tArr.getJSONObject(it)
                Txn(
                    id = o.getLong("id"),
                    customerId = o.getLong("customerId"),
                    isDebt = o.getBoolean("isDebt"),
                    amount = o.getDouble("amount"),
                    note = o.optString("note", ""),
                    timestamp = o.getLong("timestamp")
                )
            }
            val products = (0 until pArr.length()).map {
                val o = pArr.getJSONObject(it)
                var path = ""
                val b64 = o.optString("imageB64", "")
                if (b64.isNotEmpty()) {
                    try {
                        val dir = File(getApplication<Application>().filesDir, "products").apply { mkdirs() }
                        val f = File(dir, "imp_${o.getLong("id")}_${System.nanoTime()}.jpg")
                        f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                        path = f.absolutePath
                    } catch (e: Exception) {
                    }
                }
                SavedProduct(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    price = o.getDouble("price"),
                    barcode = o.optString("barcode", ""),
                    imagePath = path
                )
            }
            db.withTransaction {
                dao.clearTxns()
                dao.clearCustomers()
                dao.clearProducts()
                dao.insertCustomers(customers)
                dao.insertTxns(txns)
                dao.insertProducts(products)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
