package com.daftar.kredi.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "txns",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("customerId")]
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val isDebt: Boolean,
    val amount: Double,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_products")
data class SavedProduct(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Double,
    val barcode: String = "",
    val imagePath: String = ""
)

/** الزبون مع رصيده الحالي (موجب = عليه دين، سالب = له رصيد) */
data class CustomerSummary(
    val customer: Customer,
    val balance: Double,
    val lastActivity: Long
)

data class InvoiceItem(
    val id: Long,
    val name: String,
    val price: Double,
    val qty: Double,
    val productId: Long = 0,
    val imagePath: String = ""
)
