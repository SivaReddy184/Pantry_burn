package com.example.data

import kotlinx.coroutines.flow.Flow

class GroceryRepository(private val groceryDao: GroceryDao) {
    val allItems: Flow<List<GroceryItem>> = groceryDao.getAllItems()
    val allPurchases: Flow<List<Purchase>> = groceryDao.getAllPurchases()
    val itemsWithPurchases: Flow<List<GroceryItemWithPurchases>> = groceryDao.getItemsWithPurchases()

    suspend fun getItemById(id: Int): GroceryItem? = groceryDao.getItemById(id)
    suspend fun getItemByBarcode(barcode: String): GroceryItem? = groceryDao.getItemByBarcode(barcode)
    
    suspend fun insertItem(item: GroceryItem): Long = groceryDao.insertItem(item)
    suspend fun updateItem(item: GroceryItem) = groceryDao.updateItem(item)
    suspend fun deleteItem(item: GroceryItem) = groceryDao.deleteItemWithRelations(item)

    suspend fun insertPurchase(purchase: Purchase): Long = groceryDao.insertPurchase(purchase)
    suspend fun deletePurchase(purchase: Purchase) = groceryDao.deletePurchase(purchase)

    fun getPurchasesForItem(itemId: Int): Flow<List<Purchase>> = groceryDao.getPurchasesForItem(itemId)

    val allConsumptionLogsWithItem: Flow<List<ConsumptionLogWithItem>> = groceryDao.getAllConsumptionLogsWithItem()

    suspend fun insertConsumptionLog(log: ConsumptionLog): Long = groceryDao.insertConsumptionLog(log)
    suspend fun updateConsumptionLog(log: ConsumptionLog) = groceryDao.updateConsumptionLog(log)
    suspend fun deleteConsumptionLog(log: ConsumptionLog) = groceryDao.deleteConsumptionLog(log)
    suspend fun getConsumptionLogById(id: Int): ConsumptionLog? = groceryDao.getConsumptionLogById(id)
}
