package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GroceryDao {
    @Query("SELECT * FROM grocery_items ORDER BY name ASC")
    fun getAllItems(): Flow<List<GroceryItem>>

    @Query("SELECT * FROM grocery_items WHERE id = :id")
    suspend fun getItemById(id: Int): GroceryItem?

    @Query("SELECT * FROM grocery_items WHERE barcode = :barcode")
    suspend fun getItemByBarcode(barcode: String): GroceryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: GroceryItem): Long

    @Update
    suspend fun updateItem(item: GroceryItem)

    @Delete
    suspend fun deleteItem(item: GroceryItem)

    @Query("DELETE FROM purchases WHERE itemId = :itemId")
    suspend fun deletePurchasesByItemId(itemId: Int)

    @Query("DELETE FROM consumption_logs WHERE itemId = :itemId")
    suspend fun deleteConsumptionLogsByItemId(itemId: Int)

    @Transaction
    suspend fun deleteItemWithRelations(item: GroceryItem) {
        deletePurchasesByItemId(item.id)
        deleteConsumptionLogsByItemId(item.id)
        deleteItem(item)
    }

    @Query("SELECT * FROM purchases ORDER BY date DESC")
    fun getAllPurchases(): Flow<List<Purchase>>

    @Query("SELECT * FROM purchases WHERE itemId = :itemId ORDER BY date DESC")
    fun getPurchasesForItem(itemId: Int): Flow<List<Purchase>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: Purchase): Long

    @Delete
    suspend fun deletePurchase(purchase: Purchase)
    
    @Transaction
    @Query("SELECT * FROM grocery_items ORDER BY name ASC")
    fun getItemsWithPurchases(): Flow<List<GroceryItemWithPurchases>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumptionLog(log: ConsumptionLog): Long

    @Update
    suspend fun updateConsumptionLog(log: ConsumptionLog)

    @Delete
    suspend fun deleteConsumptionLog(log: ConsumptionLog)

    @Transaction
    @Query("SELECT * FROM consumption_logs ORDER BY date DESC")
    fun getAllConsumptionLogsWithItem(): Flow<List<ConsumptionLogWithItem>>

    @Query("SELECT * FROM consumption_logs WHERE id = :id")
    suspend fun getConsumptionLogById(id: Int): ConsumptionLog?
}

data class GroceryItemWithPurchases(
    @Embedded val item: GroceryItem,
    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val purchases: List<Purchase>
)
