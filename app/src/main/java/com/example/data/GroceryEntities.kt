package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "grocery_items")
data class GroceryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String,
    val unit: String, // e.g., "g", "scoops", "items", "ml", "pcs"
    val currentStock: Double = 0.0,
    val dailyConsumptionRate: Double = 0.0, // learned or manual rate per day. 0.0 means not consumed or manual tracking
    val barcode: String? = null,
    val lastPurchaseDate: Long? = null, // ms timestamp of last purchase, used for dynamic consumption learning
    val lastPurchaseQty: Double? = null, // quantity of last purchase, used for dynamic consumption learning
    val preferredVendor: String? = null, // Vendor of last purchase or preferred
    val lastAutomatedDeductionDate: Long? = null, // ms timestamp of last automated deduction run
    val trackingMode: String = "AUTOMATIC", // "AUTOMATIC" or "MANUAL"
    val isPaused: Boolean = false // If true, automated deductions are paused for this item
)

@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey(autoGenerate = true) val purchaseId: Int = 0,
    val itemId: Int, // references GroceryItem.id
    val date: Long, // timestamp in ms
    val quantityBought: Double,
    val totalPrice: Double,
    val vendor: String, // e.g., "Bigbasket", "D-Mart Ready", etc.
    val brand: String? = null,
    val rating: Int? = null // 1 to 5 stars
) {
    // Convenience property
    val unitPrice: Double
        get() = if (quantityBought > 0) totalPrice / quantityBought else 0.0
}

@Entity(tableName = "consumption_logs")
data class ConsumptionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemId: Int, // references GroceryItem.id
    val date: Long, // timestamp in ms
    val quantityConsumed: Double,
    val unit: String,
    val isAutomated: Boolean = false
)

data class ConsumptionLogWithItem(
    @androidx.room.Embedded val log: ConsumptionLog,
    @androidx.room.Relation(
        parentColumn = "itemId",
        entityColumn = "id"
    )
    val item: GroceryItem?
)

