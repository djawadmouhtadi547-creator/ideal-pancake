package com.daftar.kredi.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM customers")
    fun customers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun customer(id: Long): Flow<Customer?>

    @Insert
    suspend fun insertCustomer(c: Customer): Long

    @Update
    suspend fun updateCustomer(c: Customer)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: Long)

    @Query("SELECT * FROM txns")
    fun allTxns(): Flow<List<Txn>>

    @Query("SELECT * FROM txns WHERE customerId = :id ORDER BY timestamp DESC")
    fun txnsOf(id: Long): Flow<List<Txn>>

    @Insert
    suspend fun insertTxn(t: Txn): Long

    @Update
    suspend fun updateTxn(t: Txn)

    @Query("DELETE FROM txns WHERE id = :id")
    suspend fun deleteTxn(id: Long)

    @Query("SELECT * FROM saved_products ORDER BY name")
    fun products(): Flow<List<SavedProduct>>

    @Insert
    suspend fun insertProduct(p: SavedProduct): Long

    @Update
    suspend fun updateProduct(p: SavedProduct)

    @Query("SELECT * FROM saved_products WHERE barcode = :code AND barcode != '' LIMIT 1")
    suspend fun byBarcode(code: String): SavedProduct?

    @Query("DELETE FROM saved_products WHERE id = :id")
    suspend fun deleteProduct(id: Long)

    // ---- للنسخ الاحتياطي ----
    @Query("SELECT * FROM customers")
    suspend fun customersOnce(): List<Customer>

    @Query("SELECT * FROM txns")
    suspend fun txnsOnce(): List<Txn>

    @Query("SELECT * FROM saved_products")
    suspend fun productsOnce(): List<SavedProduct>

    @Query("DELETE FROM txns")
    suspend fun clearTxns()

    @Query("DELETE FROM customers")
    suspend fun clearCustomers()

    @Query("DELETE FROM saved_products")
    suspend fun clearProducts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(list: List<Customer>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTxns(list: List<Txn>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(list: List<SavedProduct>)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_products ADD COLUMN barcode TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE saved_products ADD COLUMN imagePath TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [Customer::class, Txn::class, SavedProduct::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kredi.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
