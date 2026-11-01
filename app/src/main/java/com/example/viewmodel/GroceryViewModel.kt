package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class GroceryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: GroceryRepository
    private val prefs = application.getSharedPreferences("pantry_settings", android.content.Context.MODE_PRIVATE)

    private val _isGlobalAutoPaused = MutableStateFlow(prefs.getBoolean("global_auto_paused", false))
    val isGlobalAutoPaused: StateFlow<Boolean> = _isGlobalAutoPaused.asStateFlow()

    fun setGlobalAutoPaused(paused: Boolean) {
        prefs.edit().putBoolean("global_auto_paused", paused).apply()
        _isGlobalAutoPaused.value = paused
        if (!paused) {
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val items = repository.allItems.first()
                items.forEach { item ->
                    repository.updateItem(item.copy(lastAutomatedDeductionDate = now))
                }
                runAutomatedDeductions()
            }
        }
    }

    init {
        val dao = AppDatabase.getDatabase(application).groceryDao()
        repository = GroceryRepository(dao)
        
        // Seed demo data ONLY if database is empty AND app has never been initialized before
        viewModelScope.launch {
            val hasSeeded = prefs.getBoolean("has_seeded_demo_data_v1", false)
            if (!hasSeeded) {
                repository.allItems.first().let { items ->
                    if (items.isEmpty()) {
                        seedDemoData()
                    }
                }
                prefs.edit().putBoolean("has_seeded_demo_data_v1", true).apply()
            }
            runAutomatedDeductions()
        }
    }

    fun runAutomatedDeductions() {
        viewModelScope.launch {
            if (_isGlobalAutoPaused.value) return@launch

            val items = repository.allItems.first()
            val now = System.currentTimeMillis()
            items.forEach { item ->
                // Check if auto-deduction is enabled for this item
                if (!item.isPaused && item.trackingMode == "AUTOMATIC" && item.dailyConsumptionRate > 0 && item.currentStock > 0) {
                    val lastRun = item.lastAutomatedDeductionDate ?: now
                    
                    val deductionTimes = mutableListOf<Long>()
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = lastRun
                        set(Calendar.HOUR_OF_DAY, 20) // Daily automatic entry at 8:00 PM
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (cal.timeInMillis <= lastRun) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    
                    while (cal.timeInMillis <= now) {
                        deductionTimes.add(cal.timeInMillis)
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    
                    if (deductionTimes.isNotEmpty()) {
                        var tempStock = item.currentStock
                        var lastProcessedTime = lastRun
                        
                        for (deductionTime in deductionTimes) {
                            if (tempStock <= 0) break
                            val deduction = item.dailyConsumptionRate.coerceAtMost(tempStock)
                            tempStock -= deduction
                            lastProcessedTime = deductionTime
                            
                            val log = ConsumptionLog(
                                itemId = item.id,
                                date = deductionTime,
                                quantityConsumed = deduction,
                                unit = item.unit,
                                isAutomated = true
                            )
                            repository.insertConsumptionLog(log)
                        }
                        
                        val updatedItem = item.copy(
                            currentStock = tempStock,
                            lastAutomatedDeductionDate = if (tempStock <= 0) now else lastProcessedTime
                        )
                        repository.updateItem(updatedItem)
                    } else if (item.lastAutomatedDeductionDate == null) {
                        repository.updateItem(item.copy(lastAutomatedDeductionDate = now))
                    }
                } else if (item.lastAutomatedDeductionDate == null || item.currentStock <= 0) {
                    repository.updateItem(item.copy(lastAutomatedDeductionDate = now))
                }
            }
        }
    }

    val allItems: StateFlow<List<GroceryItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPurchases: StateFlow<List<Purchase>> = repository.allPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val itemsWithPurchases: StateFlow<List<GroceryItemWithPurchases>> = repository.itemsWithPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allConsumptionLogsWithItem: StateFlow<List<ConsumptionLogWithItem>> = repository.allConsumptionLogsWithItem
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Calculations: Spendings
    val spendingMetrics = itemsWithPurchases.map { list ->
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) // 0-indexed

        var thisMonthTotal = 0.0
        var rolling30DayTotal = 0.0
        
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

        for (itemWithPurchases in list) {
            for (purchase in itemWithPurchases.purchases) {
                // Rolling 30 days
                if (purchase.date >= thirtyDaysAgo) {
                    rolling30DayTotal += purchase.totalPrice
                }

                // Calendar Month
                val pCal = Calendar.getInstance().apply { timeInMillis = purchase.date }
                if (pCal.get(Calendar.YEAR) == currentYear && pCal.get(Calendar.MONTH) == currentMonth) {
                    thisMonthTotal += purchase.totalPrice
                }
            }
        }

        SpendingMetrics(
            thisMonthSpending = thisMonthTotal,
            rolling30DaySpending = rolling30DayTotal
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpendingMetrics())

    // CRUD operations
    fun addGroceryItem(
        name: String, 
        category: String, 
        unit: String, 
        initialStock: Double, 
        initialRate: Double, 
        barcode: String? = null, 
        preferredVendor: String? = null, 
        initialPrice: Double = 0.0,
        trackingMode: String = "AUTOMATIC",
        isPaused: Boolean = false
    ) {
        prefs.edit().putBoolean("has_seeded_demo_data_v1", true).apply()
        viewModelScope.launch {
            val item = GroceryItem(
                name = name.trim(),
                category = category.trim().ifEmpty { "General" },
                unit = unit.trim().ifEmpty { "units" },
                currentStock = initialStock,
                dailyConsumptionRate = initialRate,
                barcode = barcode?.trim()?.ifEmpty { null },
                preferredVendor = preferredVendor?.trim()?.ifEmpty { null },
                lastAutomatedDeductionDate = System.currentTimeMillis(),
                trackingMode = trackingMode,
                isPaused = isPaused
            )
            val itemId = repository.insertItem(item).toInt()
            
            // Immediately insert a purchase record so that it records in the price trends tab
            val purchase = Purchase(
                itemId = itemId,
                date = System.currentTimeMillis(),
                quantityBought = initialStock,
                totalPrice = initialPrice,
                vendor = preferredVendor?.trim()?.ifEmpty { "Other" } ?: "Other"
            )
            repository.insertPurchase(purchase)
        }
    }

    fun toggleItemPause(item: GroceryItem) {
        viewModelScope.launch {
            val willBePaused = !item.isPaused
            val now = System.currentTimeMillis()
            val updated = item.copy(
                isPaused = willBePaused,
                lastAutomatedDeductionDate = if (!willBePaused) now else item.lastAutomatedDeductionDate
            )
            repository.updateItem(updated)
            if (!willBePaused && !_isGlobalAutoPaused.value) {
                runAutomatedDeductions()
            }
        }
    }

    fun setItemTrackingMode(item: GroceryItem, mode: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = item.copy(
                trackingMode = mode,
                lastAutomatedDeductionDate = if (mode == "AUTOMATIC") now else item.lastAutomatedDeductionDate
            )
            repository.updateItem(updated)
            if (mode == "AUTOMATIC" && !updated.isPaused && !_isGlobalAutoPaused.value) {
                runAutomatedDeductions()
            }
        }
    }

    fun updateGroceryItem(item: GroceryItem) {
        viewModelScope.launch {
            val existing = repository.getItemById(item.id)
            val now = System.currentTimeMillis()
            val isNowActiveAuto = item.trackingMode == "AUTOMATIC" && !item.isPaused
            val wasActiveAuto = existing != null && existing.trackingMode == "AUTOMATIC" && !existing.isPaused
            val itemToSave = if (isNowActiveAuto && !wasActiveAuto) {
                item.copy(lastAutomatedDeductionDate = now)
            } else {
                item
            }
            repository.updateItem(itemToSave)
            if (isNowActiveAuto && !_isGlobalAutoPaused.value) {
                runAutomatedDeductions()
            }
        }
    }

    fun deleteGroceryItem(item: GroceryItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun recordPurchase(
        itemId: Int,
        quantity: Double,
        totalPrice: Double,
        vendor: String,
        brand: String? = null,
        rating: Int? = null,
        date: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val item = repository.getItemById(itemId) ?: return@launch
            val trimmedVendor = vendor.trim().ifEmpty { "Other" }
            val trimmedBrand = brand?.trim()?.ifEmpty { null }
            val now = System.currentTimeMillis()
            
            // Insert Purchase
            val purchase = Purchase(
                itemId = itemId,
                date = date,
                quantityBought = quantity,
                totalPrice = totalPrice,
                vendor = trimmedVendor,
                brand = trimmedBrand,
                rating = rating
            )
            repository.insertPurchase(purchase)

            // Update item stock, last purchase date and qty, preferred vendor, and reset lastAutomatedDeductionDate to now
            val updatedItem = item.copy(
                currentStock = item.currentStock + quantity,
                lastPurchaseDate = date,
                lastPurchaseQty = quantity,
                preferredVendor = trimmedVendor,
                lastAutomatedDeductionDate = now
            )
            repository.updateItem(updatedItem)
        }
    }

    fun updatePurchase(purchase: Purchase) {
        viewModelScope.launch {
            repository.updatePurchase(purchase)
        }
    }

    fun updatePurchaseRating(purchase: Purchase, rating: Int?, brand: String? = null) {
        viewModelScope.launch {
            val updated = purchase.copy(
                rating = rating,
                brand = brand?.trim()?.ifEmpty { null } ?: purchase.brand
            )
            repository.updatePurchase(updated)
        }
    }

    fun getLatestRatingForBrand(brandName: String, itemId: Int? = null): Int? {
        val trimmed = brandName.trim()
        if (trimmed.isEmpty()) return null
        val all = allPurchases.value
        if (itemId != null) {
            val forItem = all.firstOrNull { it.itemId == itemId && it.brand?.equals(trimmed, ignoreCase = true) == true && it.rating != null }
            return forItem?.rating
        }
        return all.firstOrNull { it.brand?.equals(trimmed, ignoreCase = true) == true && it.rating != null }?.rating
    }

    fun getKnownBrandsForItem(itemId: Int): List<String> {
        val all = allPurchases.value
        return all.filter { it.itemId == itemId && !it.brand.isNullOrBlank() }
            .mapNotNull { it.brand?.trim() }
            .distinct()
    }

    fun getBrandSuggestionsForItem(itemId: Int): List<BrandSuggestion> {
        val all = allPurchases.value
        val forThisItem = all.filter { it.itemId == itemId && !it.brand.isNullOrBlank() }

        val itemBrandsMap = LinkedHashMap<String, Int?>()
        forThisItem.sortedByDescending { it.date }.forEach { p ->
            val b = p.brand!!.trim()
            val key = b.lowercase()
            if (!itemBrandsMap.containsKey(key)) {
                val r = getLatestRatingForBrand(b, itemId)
                itemBrandsMap[b] = r
            }
        }

        return itemBrandsMap.map { (brandName, rating) ->
            BrandSuggestion(brand = brandName, rating = rating, isForItem = true)
        }
    }

    // Delete purchase log
    fun deletePurchase(purchase: Purchase) {
        viewModelScope.launch {
            val item = repository.getItemById(purchase.itemId)
            if (item != null) {
                val updatedStock = (item.currentStock - purchase.quantityBought).coerceAtLeast(0.0)
                repository.updateItem(item.copy(currentStock = updatedStock))
            }
            repository.deletePurchase(purchase)
        }
    }

    // Dynamic Consumption Learning: Log item as fully consumed (empty)
    fun logItemEmpty(itemId: Int) {
        viewModelScope.launch {
            val item = repository.getItemById(itemId) ?: return@launch
            val now = System.currentTimeMillis()
            
            val lastPurchaseDate = item.lastPurchaseDate
            val lastPurchaseQty = item.lastPurchaseQty

            var newRate = item.dailyConsumptionRate

            if (lastPurchaseDate != null && lastPurchaseQty != null) {
                val diffMs = now - lastPurchaseDate
                val diffDays = diffMs.toDouble() / (1000.0 * 60 * 60 * 24)
                
                // Only learn if at least 0.5 days have elapsed to avoid division by zero or inflated rates
                if (diffDays >= 0.5) {
                    newRate = lastPurchaseQty / diffDays
                }
            }

            // Set currentStock to 0.0, apply the learned rate, and update deduction date
            val updatedItem = item.copy(
                currentStock = 0.0,
                dailyConsumptionRate = if (newRate > 0) newRate else item.dailyConsumptionRate,
                lastAutomatedDeductionDate = now
            )
            repository.updateItem(updatedItem)
        }
    }

    // Manual Stock adjustment
    fun adjustStock(itemId: Int, newStock: Double) {
        viewModelScope.launch {
            val item = repository.getItemById(itemId) ?: return@launch
            val now = System.currentTimeMillis()
            repository.updateItem(item.copy(
                currentStock = newStock,
                lastAutomatedDeductionDate = if (newStock > 0 && item.currentStock <= 0) now else (item.lastAutomatedDeductionDate ?: now)
            ))
        }
    }

    // Manual Rate adjustment
    fun adjustConsumptionRate(itemId: Int, newRate: Double) {
        viewModelScope.launch {
            val item = repository.getItemById(itemId) ?: return@launch
            repository.updateItem(item.copy(dailyConsumptionRate = newRate))
        }
    }

    // Daily consumption log recording
    fun recordConsumption(itemId: Int, quantity: Double, date: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val item = repository.getItemById(itemId) ?: return@launch
            val updatedStock = (item.currentStock - quantity).coerceAtLeast(0.0)
            
            // Insert consumption log
            val log = ConsumptionLog(
                itemId = itemId,
                date = date,
                quantityConsumed = quantity,
                unit = item.unit
            )
            repository.insertConsumptionLog(log)
            
            // Update item stock
            val updatedItem = item.copy(currentStock = updatedStock)
            repository.updateItem(updatedItem)
        }
    }

    // Delete consumption log
    fun deleteConsumptionLog(log: ConsumptionLog) {
        viewModelScope.launch {
            // Restore quantity to item stock
            val item = repository.getItemById(log.itemId)
            if (item != null) {
                val updatedStock = item.currentStock + log.quantityConsumed
                repository.updateItem(item.copy(currentStock = updatedStock))
            }
            repository.deleteConsumptionLog(log)
        }
    }

    // Update consumption log
    fun updateConsumptionLog(log: ConsumptionLog, newQuantity: Double, newDate: Long) {
        viewModelScope.launch {
            val item = repository.getItemById(log.itemId)
            if (item != null) {
                val stockDiff = log.quantityConsumed - newQuantity
                val updatedStock = (item.currentStock + stockDiff).coerceAtLeast(0.0)
                repository.updateItem(item.copy(currentStock = updatedStock))
            }
            val updatedLog = log.copy(quantityConsumed = newQuantity, date = newDate)
            repository.updateConsumptionLog(updatedLog)
        }
    }

    // Search barcode
    suspend fun findItemByBarcode(barcode: String): GroceryItem? {
        return repository.getItemByBarcode(barcode.trim())
    }

    private suspend fun seedDemoData() {
        val itemsToSeed = listOf(
            Triple("Almonds", "Nuts & Seeds", "grams"),
            Triple("Whey Protein", "Supplements", "scoops"),
            Triple("Peanut Butter", "Spreads", "grams"),
            Triple("Rolled Oats", "Grains", "grams"),
            Triple("Chia Seeds", "Nuts & Seeds", "grams"),
            Triple("Greek Yogurt", "Dairy", "items"),
            Triple("Eggs", "Poultry", "items")
        )

        val vendors = listOf("Bigbasket", "D-Mart Ready", "Amazon Fresh", "Local Market")
        val now = System.currentTimeMillis()

        itemsToSeed.forEachIndexed { index, (name, category, unit) ->
            // Create item with initial estimates
            val initialStock = when (unit) {
                "grams" -> 500.0
                "scoops" -> 30.0
                "items" -> 12.0
                else -> 10.0
            }
            val dailyRate = when (name) {
                "Almonds" -> 30.0 // 30g/day -> lasts ~16 days
                "Whey Protein" -> 1.0 // 1 scoop/day -> lasts 30 days
                "Peanut Butter" -> 20.0 // 20g/day
                "Rolled Oats" -> 50.0 // 50g/day
                "Chia Seeds" -> 15.0 // 15g/day
                "Greek Yogurt" -> 1.0 // 1 pack/day
                "Eggs" -> 2.0 // 2 eggs/day
                else -> 1.0
            }

            val vendor = vendors[index % vendors.size]
            val barcode = when (name) {
                "Almonds" -> "8901058002314"
                "Whey Protein" -> "748927052511"
                "Peanut Butter" -> "8906014023124"
                else -> null
            }

            // Let's backdate last purchase to 15 days ago so OOS predictions look dynamic and realistic!
            val lastPurchaseTime = now - (15L * 24 * 60 * 60 * 1000)

            val itemId = repository.insertItem(GroceryItem(
                name = name,
                category = category,
                unit = unit,
                currentStock = initialStock,
                dailyConsumptionRate = dailyRate,
                barcode = barcode,
                lastPurchaseDate = lastPurchaseTime,
                lastPurchaseQty = initialStock * 1.5, // purchased more than current
                preferredVendor = vendor,
                lastAutomatedDeductionDate = lastPurchaseTime
            )).toInt()

            // Record a backdated purchase
            val price = when (name) {
                "Almonds" -> 450.0
                "Whey Protein" -> 2400.0
                "Peanut Butter" -> 200.0
                "Rolled Oats" -> 120.0
                "Chia Seeds" -> 150.0
                "Greek Yogurt" -> 50.0
                "Eggs" -> 80.0
                else -> 100.0
            }

            repository.insertPurchase(Purchase(
                itemId = itemId,
                date = lastPurchaseTime,
                quantityBought = initialStock * 1.5,
                totalPrice = price,
                vendor = vendor
            ))
            
            // Record another older purchase 45 days ago to demonstrate price history tracking!
            val oldPurchaseTime = now - (45L * 24 * 60 * 60 * 1000)
            repository.insertPurchase(Purchase(
                itemId = itemId,
                date = oldPurchaseTime,
                quantityBought = initialStock * 1.5,
                totalPrice = price * 0.95, // older price was slightly cheaper
                vendor = vendor
            ))
        }
    }

    suspend fun exportBackupJson(): String {
        val items = repository.allItems.first()
        val purchases = repository.allPurchases.first()
        val logs = repository.allConsumptionLogsWithItem.first().map { it.log }

        val root = JSONObject()
        root.put("version", 1)
        root.put("exportTimestamp", System.currentTimeMillis())

        val itemsArr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("category", item.category)
            obj.put("unit", item.unit)
            obj.put("currentStock", item.currentStock)
            obj.put("dailyConsumptionRate", item.dailyConsumptionRate)
            obj.put("barcode", item.barcode ?: JSONObject.NULL)
            obj.put("lastPurchaseDate", item.lastPurchaseDate ?: JSONObject.NULL)
            obj.put("lastPurchaseQty", item.lastPurchaseQty ?: JSONObject.NULL)
            obj.put("preferredVendor", item.preferredVendor ?: JSONObject.NULL)
            obj.put("lastAutomatedDeductionDate", item.lastAutomatedDeductionDate ?: JSONObject.NULL)
            obj.put("trackingMode", item.trackingMode)
            obj.put("isPaused", item.isPaused)
            itemsArr.put(obj)
        }
        root.put("items", itemsArr)

        val purchasesArr = JSONArray()
        purchases.forEach { p ->
            val obj = JSONObject()
            obj.put("purchaseId", p.purchaseId)
            obj.put("itemId", p.itemId)
            obj.put("date", p.date)
            obj.put("quantityBought", p.quantityBought)
            obj.put("totalPrice", p.totalPrice)
            obj.put("vendor", p.vendor)
            purchasesArr.put(obj)
        }
        root.put("purchases", purchasesArr)

        val logsArr = JSONArray()
        logs.forEach { l ->
            val obj = JSONObject()
            obj.put("id", l.id)
            obj.put("itemId", l.itemId)
            obj.put("date", l.date)
            obj.put("quantityConsumed", l.quantityConsumed)
            obj.put("unit", l.unit)
            obj.put("isAutomated", l.isAutomated)
            logsArr.put(obj)
        }
        root.put("logs", logsArr)

        return root.toString(2)
    }

    suspend fun importBackupJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            val itemsArr = root.optJSONArray("items") ?: JSONArray()
            val purchasesArr = root.optJSONArray("purchases") ?: JSONArray()
            val logsArr = root.optJSONArray("logs") ?: JSONArray()

            val idMap = mutableMapOf<Int, Int>()

            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.getJSONObject(i)
                val oldId = obj.optInt("id", -1)
                val item = GroceryItem(
                    name = obj.getString("name"),
                    category = obj.getString("category"),
                    unit = obj.getString("unit"),
                    currentStock = obj.getDouble("currentStock"),
                    dailyConsumptionRate = obj.getDouble("dailyConsumptionRate"),
                    barcode = if (obj.isNull("barcode")) null else obj.optString("barcode"),
                    lastPurchaseDate = if (obj.isNull("lastPurchaseDate")) null else obj.optLong("lastPurchaseDate"),
                    lastPurchaseQty = if (obj.isNull("lastPurchaseQty")) null else obj.optDouble("lastPurchaseQty"),
                    preferredVendor = if (obj.isNull("preferredVendor")) null else obj.optString("preferredVendor"),
                    lastAutomatedDeductionDate = if (obj.isNull("lastAutomatedDeductionDate")) null else obj.optLong("lastAutomatedDeductionDate"),
                    trackingMode = obj.optString("trackingMode", "AUTOMATIC"),
                    isPaused = obj.optBoolean("isPaused", false)
                )
                val newId = repository.insertItem(item).toInt()
                if (oldId != -1) {
                    idMap[oldId] = newId
                }
            }

            for (i in 0 until purchasesArr.length()) {
                val obj = purchasesArr.getJSONObject(i)
                val oldItemId = obj.getInt("itemId")
                val newItemId = idMap[oldItemId] ?: oldItemId
                val purchase = Purchase(
                    itemId = newItemId,
                    date = obj.getLong("date"),
                    quantityBought = obj.getDouble("quantityBought"),
                    totalPrice = obj.getDouble("totalPrice"),
                    vendor = obj.getString("vendor")
                )
                repository.insertPurchase(purchase)
            }

            for (i in 0 until logsArr.length()) {
                val obj = logsArr.getJSONObject(i)
                val oldItemId = obj.getInt("itemId")
                val newItemId = idMap[oldItemId] ?: oldItemId
                val log = ConsumptionLog(
                    itemId = newItemId,
                    date = obj.getLong("date"),
                    quantityConsumed = obj.getDouble("quantityConsumed"),
                    unit = obj.getString("unit"),
                    isAutomated = obj.optBoolean("isAutomated", false)
                )
                repository.insertConsumptionLog(log)
            }

            prefs.edit().putBoolean("has_seeded_demo_data_v1", true).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

data class SpendingMetrics(
    val thisMonthSpending: Double = 0.0,
    val rolling30DaySpending: Double = 0.0
)

data class BrandSuggestion(
    val brand: String,
    val rating: Int?,
    val isForItem: Boolean = true
)
