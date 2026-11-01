package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.data.*
import com.example.ui.theme.*
import com.example.viewmodel.GroceryViewModel
import com.example.viewmodel.SpendingMetrics
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

// Formatting utilities - rounded to 2 decimal places everywhere in the app
private val priceFormat = DecimalFormat("₹#,##0.00")
private val priceShortFormat = DecimalFormat("₹#,##0.00")
private val qtyFormat = DecimalFormat("#,##0.00")
private val percentFormat = DecimalFormat("0.00")
private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

// Unit price formatter: rounded to 2 decimal places everywhere
fun formatUnitPrice(price: Double): String {
    return DecimalFormat("₹#,##0.00").format(price)
}

// Rating color progression: 5 is green, decreasing to yellowish (3) and down to red (1)
fun getRatingColor(rating: Int): Color {
    return when (rating) {
        5 -> Color(0xFF2E7D32) // Pure Green
        4 -> Color(0xFF689F38) // Light / Lime Green
        3 -> Color(0xFFFBC02D) // Yellowish / Amber
        2 -> Color(0xFFF57C00) // Deep Orange
        1 -> Color(0xFFD32F2F) // Red
        else -> Color.Gray
    }
}

fun getRatingLabel(rating: Int): String {
    return when (rating) {
        5 -> "5 ★ Excellent Quality"
        4 -> "4 ★ Good Quality"
        3 -> "3 ★ Average Quality"
        2 -> "2 ★ Below Average"
        1 -> "1 ★ Poor Quality"
        else -> "Unrated"
    }
}

@Composable
fun RatingBar(
    rating: Int?,
    maxRating: Int = 5,
    starSize: androidx.compose.ui.unit.Dp = 16.dp,
    interactive: Boolean = false,
    showBadge: Boolean = true,
    onRatingChanged: ((Int) -> Unit)? = null
) {
    if (rating == null && !interactive) return

    val activeColor = rating?.let { getRatingColor(it) } ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        for (i in 1..maxRating) {
            val isFilled = rating != null && i <= rating
            val tint = if (isFilled) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

            if (interactive && onRatingChanged != null) {
                Box(
                    modifier = Modifier
                        .size(starSize + 10.dp)
                        .clickable { onRatingChanged(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFilled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "$i Star",
                        tint = tint,
                        modifier = Modifier.size(starSize)
                    )
                }
            } else {
                Icon(
                    imageVector = if (isFilled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "$i Star",
                    tint = tint,
                    modifier = Modifier.size(starSize)
                )
            }
        }

        if (showBadge && rating != null && rating > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                color = activeColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "$rating/5",
                    color = activeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
fun RatePurchaseDialog(
    purchase: Purchase,
    itemName: String,
    initialBrandRating: Int? = null,
    onDismiss: () -> Unit,
    onConfirm: (rating: Int, brand: String?) -> Unit
) {
    var brand by remember { mutableStateOf(purchase.brand ?: "") }
    var selectedRating by remember { mutableStateOf(purchase.rating ?: initialBrandRating ?: 5) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Rate Brand: $itemName",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Store: ${purchase.vendor} • ${dateFormat.format(Date(purchase.date))}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand Name") },
                    placeholder = { Text("e.g. Amul, Tata, Nestle") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Select Quality Rating (1 - 5)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val activeColor = getRatingColor(selectedRating)
                            for (starIndex in 1..5) {
                                val isSelected = starIndex <= selectedRating
                                val starTint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                IconButton(
                                    onClick = { selectedRating = starIndex },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "$starIndex Star",
                                        tint = starTint,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            color = getRatingColor(selectedRating).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = getRatingLabel(selectedRating),
                                color = getRatingColor(selectedRating),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(selectedRating, brand.trim().ifEmpty { null })
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Rating")
                    }
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    val viewModel: GroceryViewModel = viewModel()
                    MainAppScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: GroceryViewModel, modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(0) }
    
    val items by viewModel.allItems.collectAsStateWithLifecycle()
    val purchases by viewModel.allPurchases.collectAsStateWithLifecycle()
    val itemsWithPurchases by viewModel.itemsWithPurchases.collectAsStateWithLifecycle()
    val spendingMetrics by viewModel.spendingMetrics.collectAsStateWithLifecycle()
    val consumptionLogs by viewModel.allConsumptionLogsWithItem.collectAsStateWithLifecycle()
    val isGlobalAutoPaused by viewModel.isGlobalAutoPaused.collectAsStateWithLifecycle()

    var showAddItemDialog by remember { mutableStateOf(false) }
    var showRecordPurchaseDialogForItemId by remember { mutableStateOf<Int?>(null) }
    var historyDialogItemId by remember { mutableStateOf<Int?>(null) }
    var historyDialogInitialTab by remember { mutableStateOf(0) }
    var learningFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var dismissedAlertItemIds by remember { mutableStateOf(setOf<Int>()) }
    var showDataBackupDialog by remember { mutableStateOf(false) }

    val lowStockAlerts = remember(items) {
        items.filter { item ->
            val hasConsumption = item.dailyConsumptionRate > 0
            if (hasConsumption && item.currentStock > 0.0) {
                val daysLeft = item.currentStock / item.dailyConsumptionRate
                daysLeft <= 3.0
            } else {
                false
            }
        }
    }

    LaunchedEffect(items) {
        val lowStockIds = lowStockAlerts.map { it.id }.toSet()
        dismissedAlertItemIds = dismissedAlertItemIds.intersect(lowStockIds)
    }

    val activeLowStockAlerts = remember(lowStockAlerts, dismissedAlertItemIds) {
        lowStockAlerts.filter { it.id !in dismissedAlertItemIds }
    }

    var showNotificationCenter by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(activeLowStockAlerts) {
        if (activeLowStockAlerts.isNotEmpty()) {
            val itemNames = activeLowStockAlerts.joinToString(", ") { it.name }
            Toast.makeText(
                context,
                "⚠️ Stock Warning: $itemNames will run out in < 3 days!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "PantryBurn Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PANTRYBURN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Notification bell with red badge count
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp)
                    ) {
                        IconButton(
                            onClick = { showNotificationCenter = true }
                        ) {
                            if (activeLowStockAlerts.isNotEmpty()) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsActive,
                                        contentDescription = "Alerts",
                                        tint = AlertOOS,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    // Badge dot
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = (2).dp, y = (-2).dp)
                                            .size(16.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AlertOOS),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = activeLowStockAlerts.size.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Notifications,
                                    contentDescription = "Alerts",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { showDataBackupDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Backup,
                            contentDescription = "Data Protection & Backup",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    // Profile avatar bubble matching StockSense Clean Minimalism theme
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { showDataBackupDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "Profile & Backup",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Kitchen, contentDescription = "Pantry") },
                    label = { Text("Pantry", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.ShoppingCart, contentDescription = "Restock") },
                    label = { Text("Restock", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.Analytics, contentDescription = "Analytics") },
                    label = { Text("Analytics", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Rounded.TrendingUp, contentDescription = "Price Trends") },
                    label = { Text("Price Trends", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Rounded.ReceiptLong, contentDescription = "Logs") },
                    label = { Text("Logs", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        showAddItemDialog = true
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Add Item", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> PantryTab(
                    items = items,
                    lowStockAlerts = activeLowStockAlerts,
                    isGlobalAutoPaused = isGlobalAutoPaused,
                    onToggleGlobalPause = { viewModel.setGlobalAutoPaused(!isGlobalAutoPaused) },
                    onToggleItemPause = { item -> viewModel.toggleItemPause(item) },
                    onSetItemTrackingMode = { item, mode -> viewModel.setItemTrackingMode(item, mode) },
                    onShowHistory = { item ->
                        historyDialogInitialTab = 0
                        historyDialogItemId = item.id
                    },
                    onShowNotificationCenter = { showNotificationCenter = true },
                    onLogEmpty = { itemId ->
                        val item = items.find { it.id == itemId }
                        if (item != null) {
                            val lastDate = item.lastPurchaseDate
                            val lastQty = item.lastPurchaseQty
                            val now = System.currentTimeMillis()
                            
                            viewModel.logItemEmpty(itemId)

                            // Formulate dynamic learning feedback dialog text
                            learningFeedbackMessage = if (lastDate != null && lastQty != null) {
                                val diffDays = (now - lastDate).toDouble() / (1000.0 * 60 * 60 * 24)
                                if (diffDays >= 0.5) {
                                    val newRate = lastQty / diffDays
                                    "🧠 **Dynamic Consumption Learning Triggered!**\n\nYour last purchase of **${qtyFormat.format(lastQty)} ${item.unit}** lasted **${qtyFormat.format(diffDays)} days**.\n\nThe app has recalculated and updated your **Daily Consumption Rate** to **${qtyFormat.format(newRate)} ${item.unit}/day**.\n\nYour future out-of-stock predictions will now automatically adjust to this real-life usage pattern!"
                                } else {
                                    "Logged empty.\n\n*Note: Less than 12 hours have passed since your last purchase of ${item.name}, so consumption rate learning was skipped to avoid highly inflated rates.*"
                                }
                            } else {
                                "Logged empty.\n\n*Pro-tip: Record a purchase history record next time so the app can automatically learn your actual consumption rate when you log it empty!*"
                            }
                        }
                    },
                    onRecordPurchase = { itemId ->
                        showRecordPurchaseDialogForItemId = itemId
                    },
                    onAdjustStock = { itemId, stock ->
                        viewModel.adjustStock(itemId, stock)
                    },
                    onAdjustRate = { itemId, rate ->
                        viewModel.adjustConsumptionRate(itemId, rate)
                    },
                    onDeleteItem = { item ->
                        viewModel.deleteGroceryItem(item)
                    },
                    onRecordConsumption = { itemId, qty ->
                        viewModel.recordConsumption(itemId, qty)
                    },
                    onUpdateItem = { updatedItem ->
                        viewModel.updateGroceryItem(updatedItem)
                    },
                    onDismissAllAlerts = { ids ->
                        dismissedAlertItemIds = dismissedAlertItemIds + ids
                    }
                )
                1 -> RestockCartTab(
                    items = items,
                    purchases = purchases,
                    onRecordPurchase = { itemId ->
                        showRecordPurchaseDialogForItemId = itemId
                    },
                    onShowPriceHistory = { itemId ->
                        historyDialogInitialTab = 1
                        historyDialogItemId = itemId
                    }
                )
                2 -> AnalyticsTab(
                    itemsWithPurchases = itemsWithPurchases,
                    spendingMetrics = spendingMetrics,
                    consumptionLogs = consumptionLogs
                )
                3 -> PriceTrendsTab(
                    itemsWithPurchases = itemsWithPurchases,
                    onGetBrandRating = { brand, itemId -> viewModel.getLatestRatingForBrand(brand, itemId) }
                )
                4 -> LogsTab(
                    consumptionLogs = consumptionLogs,
                    purchases = purchases,
                    items = items,
                    onDeleteLog = { log -> viewModel.deleteConsumptionLog(log) },
                    onEditLog = { log, qty, date -> viewModel.updateConsumptionLog(log, qty, date) },
                    onDeletePurchase = { purchase -> viewModel.deletePurchase(purchase) },
                    onUpdatePurchaseRating = { purchase, rating, brand -> viewModel.updatePurchaseRating(purchase, rating, brand) },
                    onGetBrandRating = { brand, itemId -> viewModel.getLatestRatingForBrand(brand, itemId) }
                )
            }
        }
    }

    // Dialogs
    if (showDataBackupDialog) {
        DataBackupRestoreDialog(
            onDismiss = { showDataBackupDialog = false },
            onExportBackup = {
                scope.launch {
                    val jsonString = viewModel.exportBackupJson()
                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("PantryBurn Backup", jsonString)
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(context, "Full JSON Backup copied to clipboard!", Toast.LENGTH_LONG).show()
                }
            },
            onImportBackup = { jsonString ->
                scope.launch {
                    val success = viewModel.importBackupJson(jsonString)
                    if (success) {
                        Toast.makeText(context, "Data restored successfully!", Toast.LENGTH_LONG).show()
                        showDataBackupDialog = false
                    } else {
                        Toast.makeText(context, "Error: Invalid JSON backup format.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showAddItemDialog) {
        AddItemDialog(
            onDismiss = { showAddItemDialog = false },
            onConfirm = { name, unit, initStock, initRate, vendor, initPrice, trackingMode, isPaused ->
                viewModel.addGroceryItem(
                    name = name,
                    category = "General",
                    unit = unit,
                    initialStock = initStock,
                    initialRate = initRate,
                    barcode = null,
                    preferredVendor = vendor,
                    initialPrice = initPrice,
                    trackingMode = trackingMode,
                    isPaused = isPaused
                )
                showAddItemDialog = false
            }
        )
    }

    showRecordPurchaseDialogForItemId?.let { itemId ->
        val selectedItem = items.find { it.id == itemId }
        if (selectedItem != null) {
            RecordPurchaseDialog(
                item = selectedItem,
                viewModel = viewModel,
                onDismiss = { showRecordPurchaseDialogForItemId = null },
                onConfirm = { qty, price, vendor, brand, rating ->
                    viewModel.recordPurchase(itemId, qty, price, vendor, brand, rating)
                    showRecordPurchaseDialogForItemId = null
                    Toast.makeText(context, "Purchase logged for ${selectedItem.name}!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    learningFeedbackMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { learningFeedbackMessage = null },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
            title = { Text("Pantry Learning Insights", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
            text = { Text(msg, fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = {
                Button(
                    onClick = { learningFeedbackMessage = null }
                ) {
                    Text("Incredible!")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showNotificationCenter) {
        NotificationCenterDialog(
            alerts = lowStockAlerts,
            dismissedIds = dismissedAlertItemIds,
            onDismissAlert = { itemId ->
                dismissedAlertItemIds = dismissedAlertItemIds + itemId
            },
            onRestoreAlert = { itemId ->
                dismissedAlertItemIds = dismissedAlertItemIds - itemId
            },
            onDismiss = { showNotificationCenter = false },
            onRecordPurchase = { itemId ->
                showNotificationCenter = false
                showRecordPurchaseDialogForItemId = itemId
            }
        )
    }

    historyDialogItemId?.let { itemId ->
        val currentHistoryItem = items.find { it.id == itemId }
        if (currentHistoryItem != null) {
            ProductHistoryDialog(
                item = currentHistoryItem,
                viewModel = viewModel,
                initialTab = historyDialogInitialTab,
                onDismiss = { historyDialogItemId = null },
                onRecordPurchase = { pId ->
                    historyDialogItemId = null
                    showRecordPurchaseDialogForItemId = pId
                },
                onRecordConsumption = { cId, qty ->
                    viewModel.recordConsumption(cId, qty)
                }
            )
        }
    }
}

data class PriceFluctuationInfo(
    val prevPurchase: Purchase?,
    val unitPriceDiff: Double?,
    val pctDiff: Double?,
    val isLowestEver: Boolean,
    val isHighestEver: Boolean
)

@Composable
fun ProductPriceFluctuationCard(
    item: GroceryItem,
    purchases: List<Purchase>,
    modifier: Modifier = Modifier
) {
    val validPurchases = remember(purchases) {
        purchases.filter { it.quantityBought > 0 }.sortedBy { it.date }
    }

    if (validPurchases.isEmpty()) {
        return
    }

    val latest = validPurchases.last()
    val prior = if (validPurchases.size >= 2) validPurchases[validPurchases.size - 2] else null
    val minPrice = validPurchases.minOf { it.unitPrice }
    val maxPrice = validPurchases.maxOf { it.unitPrice }
    val lowestPurchase = validPurchases.firstOrNull { kotlin.math.abs(it.unitPrice - minPrice) < 0.0001 }
    val totalQty = validPurchases.sumOf { it.quantityBought }
    val avgPrice = if (totalQty > 0) validPurchases.sumOf { it.totalPrice } / totalQty else latest.unitPrice

    val latestDiff = if (prior != null) latest.unitPrice - prior.unitPrice else null
    val latestPctDiff = if (prior != null && prior.unitPrice > 0) (latestDiff!! / prior.unitPrice) * 100.0 else null

    var selectedIndex by remember(validPurchases) {
        mutableStateOf(validPurchases.size - 1)
    }
    val inspectedPurchase = validPurchases.getOrElse(selectedIndex) { latest }
    val inspectedIndex = validPurchases.indexOf(inspectedPurchase).coerceAtLeast(0)
    val inspectedPrior = if (inspectedIndex > 0) validPurchases[inspectedIndex - 1] else null
    val inspectedDiff = if (inspectedPrior != null) inspectedPurchase.unitPrice - inspectedPrior.unitPrice else null
    val inspectedPct = if (inspectedPrior != null && inspectedPrior.unitPrice > 0) (inspectedDiff!! / inspectedPrior.unitPrice) * 100.0 else null

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title & Fluctuation Trend Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "HISTORICAL PRICE FLUCTUATIONS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (latestDiff != null) {
                    when {
                        latestDiff < -0.001 -> {
                            Surface(
                                color = StatusHealthy.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, StatusHealthy.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "🔻 Price Drop (${percentFormat.format(kotlin.math.abs(latestPctDiff ?: 0.0))}%)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusHealthy,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        latestDiff > 0.001 -> {
                            Surface(
                                color = AlertOOS.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, AlertOOS.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "🔺 Price Hike (+${percentFormat.format(latestPctDiff ?: 0.0)}%)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlertOOS,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        else -> {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "➖ Stable Rate",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "1st Restock",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Key Price Summary Metric Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Lowest Ever (Deal)
                Surface(
                    color = StatusHealthy.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StatusHealthy.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Lowest Deal 🏆",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusHealthy
                        )
                        Text(
                            text = "${formatUnitPrice(minPrice)}/${item.unit}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusHealthy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (lowestPurchase != null) {
                            Text(
                                text = lowestPurchase.vendor,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Latest Restock Rate
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Latest Rate",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${formatUnitPrice(latest.unitPrice)}/${item.unit}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dateFormat.format(Date(latest.date)),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Average Rate
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Average Rate",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${formatUnitPrice(avgPrice)}/${item.unit}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${validPurchases.size} restocks",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart / Fluctuation Visualization
            if (validPurchases.size >= 2) {
                Text(
                    text = "Price Trajectory (Tap point to inspect)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val primaryColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.outlineVariant
                val surfaceColor = MaterialTheme.colorScheme.surface
                val healthyColor = StatusHealthy
                val alertColor = AlertOOS

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(surfaceColor, shape = RoundedCornerShape(12.dp))
                        .border(1.dp, outlineColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(validPurchases) {
                                detectTapGestures { tapOffset ->
                                    val leftPad = 24.dp.toPx()
                                    val rightPad = 24.dp.toPx()
                                    val availW = size.width - leftPad - rightPad
                                    if (availW > 0 && validPurchases.size > 1) {
                                        val step = availW / (validPurchases.size - 1)
                                        val clicked = ((tapOffset.x - leftPad + step / 2f) / step).toInt()
                                            .coerceIn(0, validPurchases.size - 1)
                                        selectedIndex = clicked
                                    }
                                }
                            }
                    ) {
                        val leftPad = 24.dp.toPx()
                        val rightPad = 24.dp.toPx()
                        val topPad = 20.dp.toPx()
                        val bottomPad = 24.dp.toPx()

                        val availW = size.width - leftPad - rightPad
                        val availH = size.height - topPad - bottomPad

                        if (availW <= 0 || availH <= 0) return@Canvas

                        val priceRange = maxPrice - minPrice
                        val points = validPurchases.mapIndexed { idx, p ->
                            val x = leftPad + (idx.toFloat() / (validPurchases.size - 1)) * availW
                            val normY = if (priceRange > 0.0001) {
                                ((p.unitPrice - minPrice) / priceRange).toFloat()
                            } else {
                                0.5f
                            }
                            val y = topPad + (1f - normY) * availH
                            Offset(x, y)
                        }

                        // Reference lines for Min and Max
                        val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        val minY = if (priceRange > 0.0001) topPad + availH else topPad + availH * 0.5f
                        val maxY = if (priceRange > 0.0001) topPad else topPad + availH * 0.5f

                        // Draw Min line
                        drawLine(
                            color = healthyColor.copy(alpha = 0.35f),
                            start = Offset(leftPad, minY),
                            end = Offset(size.width - rightPad, minY),
                            strokeWidth = 1.5f,
                            pathEffect = dashedEffect
                        )

                        // Draw Max line if different
                        if (priceRange > 0.0001) {
                            drawLine(
                                color = alertColor.copy(alpha = 0.3f),
                                start = Offset(leftPad, maxY),
                                end = Offset(size.width - rightPad, maxY),
                                strokeWidth = 1.5f,
                                pathEffect = dashedEffect
                            )
                        }

                        // Gradient fill path under line
                        val fillPath = Path().apply {
                            moveTo(points.first().x, size.height - bottomPad)
                            points.forEach { lineTo(it.x, it.y) }
                            lineTo(points.last().x, size.height - bottomPad)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.28f),
                                    primaryColor.copy(alpha = 0.02f)
                                ),
                                startY = topPad,
                                endY = size.height - bottomPad
                            )
                        )

                        // Stroke line path connecting points
                        val strokePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(
                            path = strokePath,
                            color = primaryColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Draw dots and highlights
                        points.forEachIndexed { idx, pt ->
                            val p = validPurchases[idx]
                            val isLowest = kotlin.math.abs(p.unitPrice - minPrice) < 0.0001
                            val isSelected = idx == selectedIndex

                            if (isSelected) {
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.25f),
                                    radius = 12.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = primaryColor,
                                    radius = 7.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = surfaceColor,
                                    radius = 4.dp.toPx(),
                                    center = pt
                                )
                            } else if (isLowest) {
                                drawCircle(
                                    color = healthyColor.copy(alpha = 0.3f),
                                    radius = 10.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = healthyColor,
                                    radius = 6.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = surfaceColor,
                                    radius = 3.dp.toPx(),
                                    center = pt
                                )
                            } else {
                                drawCircle(
                                    color = primaryColor,
                                    radius = 5.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = surfaceColor,
                                    radius = 2.5.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Horizontal timeline scrubber buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val shortDateFmt = SimpleDateFormat("dd MMM", Locale.getDefault())
                    validPurchases.forEachIndexed { idx, p ->
                        val isSelected = idx == selectedIndex
                        val isLowest = kotlin.math.abs(p.unitPrice - minPrice) < 0.0001
                        val chipBorderColor = when {
                            isSelected -> primaryColor
                            isLowest -> healthyColor.copy(alpha = 0.6f)
                            else -> outlineColor.copy(alpha = 0.6f)
                        }
                        val chipBgColor = when {
                            isSelected -> primaryColor.copy(alpha = 0.15f)
                            isLowest -> healthyColor.copy(alpha = 0.08f)
                            else -> surfaceColor
                        }

                        Surface(
                            onClick = { selectedIndex = idx },
                            shape = RoundedCornerShape(8.dp),
                            color = chipBgColor,
                            border = BorderStroke(1.dp, chipBorderColor),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isLowest) {
                                    Text("🏆", fontSize = 10.sp)
                                }
                                Text(
                                    text = shortDateFmt.format(Date(p.date)),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatUnitPrice(p.unitPrice),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLowest) healthyColor else if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Detail card for the currently inspected restock point
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Restock #${inspectedIndex + 1}: ${dateFormat.format(Date(inspectedPurchase.date))}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (kotlin.math.abs(inspectedPurchase.unitPrice - minPrice) < 0.0001) {
                                    Surface(
                                        color = StatusHealthy.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Best Deal",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusHealthy,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Store: ${inspectedPurchase.vendor}${if (!inspectedPurchase.brand.isNullOrBlank()) " • ${inspectedPurchase.brand}" else ""} • ${qtyFormat.format(inspectedPurchase.quantityBought)} ${item.unit} for ${priceFormat.format(inspectedPurchase.totalPrice)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${formatUnitPrice(inspectedPurchase.unitPrice)}/${item.unit}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            if (inspectedDiff != null) {
                                val isDrop = inspectedDiff < -0.001
                                val isHike = inspectedDiff > 0.001
                                Text(
                                    text = when {
                                        isDrop -> "🔻 -${formatUnitPrice(kotlin.math.abs(inspectedDiff))} (-${percentFormat.format(kotlin.math.abs(inspectedPct ?: 0.0))}%)"
                                        isHike -> "🔺 +${formatUnitPrice(inspectedDiff)} (+${percentFormat.format(inspectedPct ?: 0.0)}%)"
                                        else -> "➖ Same price"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDrop) StatusHealthy else if (isHike) AlertOOS else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Baseline Restock Set",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Recorded at ${formatUnitPrice(latest.unitPrice)}/${item.unit} from ${latest.vendor}. Future restocks will automatically plot price fluctuation curves, percentage changes, and price alerts here!",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== PRODUCT HISTORY DIALOG ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductHistoryDialog(
    item: GroceryItem,
    viewModel: GroceryViewModel,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onRecordPurchase: (Int) -> Unit,
    onRecordConsumption: (Int, Double) -> Unit
) {
    val consumptionLogs by viewModel.allConsumptionLogsWithItem.collectAsStateWithLifecycle()
    val purchases by viewModel.allPurchases.collectAsStateWithLifecycle()

    val itemLogs = remember(consumptionLogs, item.id) {
        consumptionLogs.filter { it.log.itemId == item.id }.sortedByDescending { it.log.date }
    }

    val itemPurchases = remember(purchases, item.id) {
        purchases.filter { it.itemId == item.id }.sortedByDescending { it.date }
    }

    var selectedTab by remember(item.id, initialTab) { mutableStateOf(initialTab) }
    var purchaseToDelete by remember { mutableStateOf<Purchase?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Stock: ${qtyFormat.format(item.currentStock)} ${item.unit}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close Dialog")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode & Pause Quick Controls Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tracking Mode:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = item.trackingMode == "AUTOMATIC",
                                    onClick = { viewModel.setItemTrackingMode(item, "AUTOMATIC") },
                                    label = { Text("Auto (8 PM)", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = item.trackingMode == "MANUAL",
                                    onClick = { viewModel.setItemTrackingMode(item, "MANUAL") },
                                    label = { Text("Manual", fontSize = 11.sp) }
                                )
                            }
                        }

                        if (item.trackingMode == "AUTOMATIC") {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (item.isPaused) "⏸️ Daily Auto Entries: PAUSED" else "▶️ Daily Auto Entries: ACTIVE (8 PM)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (item.isPaused) WarningSoon else StatusHealthy
                                )
                                Switch(
                                    checked = !item.isPaused,
                                    onCheckedChange = { viewModel.toggleItemPause(item) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs for Consumption vs Purchases
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Usage Logs (${itemLogs.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timeline,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("Restock Log (${itemPurchases.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    if (itemLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No consumption history recorded yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(itemLogs, key = { it.log.id }) { logWithItem ->
                                val log = logWithItem.log
                                val dateTimeFormatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(log.date))
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = if (log.isAutomated) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (log.isAutomated) "🤖 AUTO 8 PM" else "👤 MANUAL",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (log.isAutomated) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "${qtyFormat.format(log.quantityConsumed)} ${log.unit}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = dateTimeFormatted,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteConsumptionLog(log) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Log", tint = AlertOOS, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (itemPurchases.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No restock history recorded yet.",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "Log purchases to track price fluctuations, best vendor deals, and unit cost trends over time.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Calculate price fluctuation mapping chronologically
                        val priceFluctuationMap = remember(itemPurchases) {
                            val validAsc = itemPurchases.filter { it.quantityBought > 0 }.sortedBy { it.date }
                            val minP = validAsc.minOfOrNull { it.unitPrice } ?: 0.0
                            val maxP = validAsc.maxOfOrNull { it.unitPrice } ?: 0.0
                            
                            validAsc.mapIndexed { index, p ->
                                val prev = if (index > 0) validAsc[index - 1] else null
                                val diff = if (prev != null) p.unitPrice - prev.unitPrice else null
                                val pct = if (prev != null && prev.unitPrice > 0) (diff!! / prev.unitPrice) * 100.0 else null
                                p.purchaseId to PriceFluctuationInfo(
                                    prevPurchase = prev,
                                    unitPriceDiff = diff,
                                    pctDiff = pct,
                                    isLowestEver = kotlin.math.abs(p.unitPrice - minP) < 0.0001,
                                    isHighestEver = validAsc.size > 1 && kotlin.math.abs(p.unitPrice - maxP) < 0.0001 && minP != maxP
                                )
                            }.toMap()
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Historical Price View & Fluctuation Tracker Component
                            item {
                                ProductPriceFluctuationCard(
                                    item = item,
                                    purchases = itemPurchases,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RESTOCK LOG ENTRIES (${itemPurchases.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "Chronological Fluctuation",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                            }

                            items(itemPurchases, key = { it.purchaseId }) { purchase ->
                                val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(purchase.date))
                                val flucInfo = priceFluctuationMap[purchase.purchaseId]
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "${qtyFormat.format(purchase.quantityBought)} ${item.unit} • ${priceFormat.format(purchase.totalPrice)}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${formatUnitPrice(purchase.unitPrice)}/${item.unit}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            // Price Fluctuation vs Prior Restock Badge
                                            if (flucInfo != null) {
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    if (flucInfo.isLowestEver) {
                                                        Surface(
                                                            color = StatusHealthy.copy(alpha = 0.15f),
                                                            shape = RoundedCornerShape(4.dp),
                                                            border = BorderStroke(1.dp, StatusHealthy.copy(alpha = 0.35f))
                                                        ) {
                                                            Text(
                                                                text = "🏆 Best Price Deal",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = StatusHealthy,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }

                                                    if (flucInfo.unitPriceDiff != null) {
                                                        val isDrop = flucInfo.unitPriceDiff < -0.001
                                                        val isHike = flucInfo.unitPriceDiff > 0.001
                                                        val pctFormatted = percentFormat.format(kotlin.math.abs(flucInfo.pctDiff ?: 0.0))
                                                        Surface(
                                                            color = if (isDrop) StatusHealthy.copy(alpha = 0.12f) else if (isHike) AlertOOS.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = when {
                                                                    isDrop -> "🔻 -${formatUnitPrice(kotlin.math.abs(flucInfo.unitPriceDiff))} (-$pctFormatted%) vs previous"
                                                                    isHike -> "🔺 +${formatUnitPrice(flucInfo.unitPriceDiff)} (+$pctFormatted%) vs previous"
                                                                    else -> "➖ Same price as previous"
                                                                },
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = if (isDrop) StatusHealthy else if (isHike) AlertOOS else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    } else {
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "⭐ Initial Restock",
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Vendor: ${purchase.vendor}${if (!purchase.brand.isNullOrBlank()) " • Brand: ${purchase.brand}" else ""}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                            if (purchase.rating != null) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                RatingBar(rating = purchase.rating, starSize = 12.dp, interactive = false)
                                            }
                                            Text(
                                                text = dateStr,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        }

                                        IconButton(
                                            onClick = { purchaseToDelete = purchase },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Delete Buy Record",
                                                tint = AlertOOS,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    purchaseToDelete?.let { purchase ->
        DeletePurchaseConfirmDialog(
            purchase = purchase,
            itemName = item.name,
            unit = item.unit,
            onDismiss = { purchaseToDelete = null },
            onConfirm = {
                viewModel.deletePurchase(purchase)
                purchaseToDelete = null
            }
        )
    }
}

// ==================== PANTRY TAB ====================
@Composable
fun PantryTab(
    items: List<GroceryItem>,
    lowStockAlerts: List<GroceryItem>,
    isGlobalAutoPaused: Boolean,
    onToggleGlobalPause: () -> Unit,
    onToggleItemPause: (GroceryItem) -> Unit,
    onSetItemTrackingMode: (GroceryItem, String) -> Unit,
    onShowHistory: (GroceryItem) -> Unit,
    onShowNotificationCenter: () -> Unit,
    onLogEmpty: (Int) -> Unit,
    onRecordPurchase: (Int) -> Unit,
    onAdjustStock: (Int, Double) -> Unit,
    onAdjustRate: (Int, Double) -> Unit,
    onDeleteItem: (GroceryItem) -> Unit,
    onRecordConsumption: (Int, Double) -> Unit,
    onUpdateItem: (GroceryItem) -> Unit,
    onDismissAllAlerts: (List<Int>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredItems = items.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            placeholder = { Text("Search food items...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        )

        // Global Auto-Deductions Toggle Bar (8 PM Daily)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isGlobalAutoPaused) WarningSoon.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            ),
            border = BorderStroke(1.dp, if (isGlobalAutoPaused) WarningSoon.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isGlobalAutoPaused) Icons.Rounded.PauseCircle else Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = if (isGlobalAutoPaused) WarningSoon else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isGlobalAutoPaused) "Auto Deductions PAUSED" else "Daily Auto Deductions (8:00 PM)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isGlobalAutoPaused) WarningSoon else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isGlobalAutoPaused) "Automated entries stopped. Only manual logs allowed." else "Automated daily entries execute at 8:00 PM daily.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                Switch(
                    checked = !isGlobalAutoPaused,
                    onCheckedChange = { onToggleGlobalPause() }
                )
            }
        }

        // High-visibility top alert banner for low stock
        if (lowStockAlerts.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onShowNotificationCenter() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = AlertOOS.copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.dp, AlertOOS.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = "Alerts",
                        tint = AlertOOS,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pantry Warnings (${lowStockAlerts.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AlertOOS
                        )
                        val alertSummary = lowStockAlerts.take(2).joinToString(", ") { it.name }
                        val suffix = if (lowStockAlerts.size > 2) " and ${lowStockAlerts.size - 2} more" else ""
                        Text(
                            text = "$alertSummary$suffix running out in < 3 days. Tap to review predicted OOS dates.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = AlertOOS,
                            modifier = Modifier.size(20.dp)
                        )
                        IconButton(
                            onClick = {
                                onDismissAllAlerts(lowStockAlerts.map { it.id })
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss Alerts",
                                tint = AlertOOS,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fastfood,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No matching pantry items" else "Your Pantry is Empty",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Try refining your search keyword." else "Add your food items to begin tracking your smart pantry stock!",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    PantryItemCard(
                        item = item,
                        isGlobalAutoPaused = isGlobalAutoPaused,
                        onLogEmpty = { onLogEmpty(item.id) },
                        onRecordPurchase = { onRecordPurchase(item.id) },
                        onAdjustStock = { stock -> onAdjustStock(item.id, stock) },
                        onAdjustRate = { rate -> onAdjustRate(item.id, rate) },
                        onDeleteItem = { onDeleteItem(item) },
                        onRecordConsumption = { qty -> onRecordConsumption(item.id, qty) },
                        onUpdateItem = onUpdateItem,
                        onToggleItemPause = { onToggleItemPause(item) },
                        onSetItemTrackingMode = { mode -> onSetItemTrackingMode(item, mode) },
                        onShowHistory = { onShowHistory(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantryItemCard(
    item: GroceryItem,
    isGlobalAutoPaused: Boolean,
    onLogEmpty: () -> Unit,
    onRecordPurchase: () -> Unit,
    onAdjustStock: (Double) -> Unit,
    onAdjustRate: (Double) -> Unit,
    onDeleteItem: () -> Unit,
    onRecordConsumption: (Double) -> Unit,
    onUpdateItem: (GroceryItem) -> Unit,
    onToggleItemPause: () -> Unit,
    onSetItemTrackingMode: (String) -> Unit,
    onShowHistory: () -> Unit
) {
    var showAdjustStockDialog by remember { mutableStateOf(false) }
    var showAdjustRateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showConsumeDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Prediction calculations
    val hasConsumption = item.dailyConsumptionRate > 0
    val daysLeft = if (hasConsumption && item.currentStock > 0) {
        item.currentStock / item.dailyConsumptionRate
    } else {
        0.0
    }

    val isOOS = item.currentStock <= 0.0
    val isLow = !isOOS && hasConsumption && daysLeft <= 7.0

    val statusColor = when {
        isOOS -> AlertOOS
        isLow -> WarningSoon
        else -> StatusHealthy
    }

    val statusText = when {
        isOOS -> "OUT OF STOCK ⚠️"
        !hasConsumption -> "No daily consumption rate set"
        daysLeft < 1.0 -> "Runs out TODAY! ⚠️"
        daysLeft < 2.0 -> "Runs out TOMORROW! ⚠️"
        daysLeft <= 7.0 -> "Runs out in ${daysLeft.toInt()} days ⚠️"
        else -> {
            val dateMs = System.currentTimeMillis() + (daysLeft * 24L * 60L * 60L * 1000L).toLong()
            "OOS prediction: ${dateFormat.format(Date(dateMs))} (${daysLeft.toInt()} days)"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { showDeleteConfirmDialog = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: Edit Button, Mode Chip, Pause Chip, & Stock Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        onClick = { showEditDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit Product Details",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "EDIT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Mode & Pause Badge
                    if (item.trackingMode == "AUTOMATIC") {
                        val isAutoPaused = item.isPaused || isGlobalAutoPaused
                        if (isAutoPaused) {
                            Surface(
                                color = WarningSoon.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                onClick = onToggleItemPause
                            ) {
                                Text(
                                    text = "⏸️ Auto 8 PM Paused",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningSoon,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "🤖 Auto 8 PM",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "👤 Manual",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOOS) "OOS" else "${qtyFormat.format(item.currentStock)} ${item.unit}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Item Name
            Text(
                text = item.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar to show stock level (based on consumption rate)
            if (hasConsumption && !isOOS) {
                val maxStockReference = item.dailyConsumptionRate * 30.0 // 30-day capacity estimate
                val fraction = (item.currentStock / maxStockReference).coerceIn(0.0, 1.0).toFloat()
                
                Column {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = statusColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Prediction Text
            Text(
                text = statusText,
                fontSize = 12.sp,
                color = if (isOOS || isLow) statusColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = if (isOOS || isLow) FontWeight.SemiBold else FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Daily consumption info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily burn: ${qtyFormat.format(item.dailyConsumptionRate)} ${item.unit}/day",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (item.preferredVendor != null) {
                    Text(
                        text = "Vendor: ${item.preferredVendor}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons (Rearranged into 2 distinct rows to fit properly on small screens like S24)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: Administrative / Adjustments / History
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // History Button
                    Button(
                        onClick = onShowHistory,
                        modifier = Modifier
                            .weight(1.1f)
                            .height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = "Product History", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("History", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    // Adjust Stock
                    OutlinedButton(
                        onClick = { showAdjustStockDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Stock", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    // Adjust Consumption Rate
                    OutlinedButton(
                        onClick = { showAdjustRateDialog = true },
                        modifier = Modifier
                            .weight(1.1f)
                            .height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Burn Rate", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    // Log Empty button: The core learning trigger
                    Button(
                        onClick = onLogEmpty,
                        enabled = !isOOS,
                        modifier = Modifier
                            .weight(1.1f)
                            .height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(Icons.Rounded.NoFood, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Empty", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Row 2: Daily Operations (Consume & Purchase)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Log Daily Consumption
                    Button(
                        onClick = { showConsumeDialog = true },
                        enabled = !isOOS,
                        modifier = Modifier
                            .weight(1.1f)
                            .height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.Cookie, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Consume", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    // Record Buy
                    Button(
                        onClick = onRecordPurchase,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.AddShoppingCart, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Record Buy", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    // Dialog: Adjust Stock
    if (showAdjustStockDialog) {
        var stockInput by remember { mutableStateOf(item.currentStock.toString()) }
        AlertDialog(
            onDismissRequest = { showAdjustStockDialog = false },
            title = { Text("Adjust stock for ${item.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter current stock in **${item.unit}**:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = stockInput,
                        onValueChange = { stockInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val value = stockInput.toDoubleOrNull()
                        if (value != null && value >= 0) {
                            onAdjustStock(value)
                            showAdjustStockDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustStockDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Adjust Consumption Rate
    if (showAdjustRateDialog) {
        var rateInput by remember { mutableStateOf(item.dailyConsumptionRate.toString()) }
        AlertDialog(
            onDismissRequest = { showAdjustRateDialog = false },
            title = { Text("Update daily burn rate", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("How many **${item.unit}** do you consume per day?", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rateInput,
                        onValueChange = { rateInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val value = rateInput.toDoubleOrNull()
                        if (value != null && value >= 0) {
                            onAdjustRate(value)
                            showAdjustRateDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustRateDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Confirm Delete Item
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete item?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove **${item.name}** and all its historical purchases from your database? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteItem()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertOOS)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Consume Item
    if (showConsumeDialog) {
        ConsumeItemDialog(
            item = item,
            onDismiss = { showConsumeDialog = false },
            onConfirm = { quantity ->
                onRecordConsumption(quantity)
                showConsumeDialog = false
            }
        )
    }

    if (showEditDialog) {
        EditProductDialog(
            item = item,
            onDismiss = { showEditDialog = false },
            onConfirm = { updatedItem ->
                onUpdateItem(updatedItem)
                showEditDialog = false
            }
        )
    }
}

// ==================== RESTOCK CART TAB ====================
@Composable
fun RestockCartTab(
    items: List<GroceryItem>,
    purchases: List<Purchase> = emptyList(),
    onRecordPurchase: (Int) -> Unit,
    onShowPriceHistory: (Int) -> Unit = {}
) {
    // Determine restock items: OOS or runs out in <= 7 days, sorted strictly by OOS urgency (OOS and soonest to run out at top)
    val restockItems = remember(items) {
        items.filter {
            val daysLeft = if (it.dailyConsumptionRate > 0) it.currentStock / it.dailyConsumptionRate else 0.0
            it.currentStock <= 0.0 || (it.dailyConsumptionRate > 0 && daysLeft <= 7.0)
        }.sortedWith(
            compareBy<GroceryItem> {
                if (it.currentStock <= 0.0) 0.0
                else if (it.dailyConsumptionRate <= 0.0) Double.MAX_VALUE
                else it.currentStock / it.dailyConsumptionRate
            }.thenBy { it.currentStock }
             .thenBy { it.name.lowercase() }
        )
    }

    // Group items by vendor (defaulting to "Other Vendors" if null or empty)
    // Vendors sorted by most urgent item first, and items within each vendor strictly sorted by OOS urgency
    val groupedByVendor = remember(restockItems) {
        restockItems.groupBy {
            it.preferredVendor?.trim()?.ifEmpty { null } ?: "Other Vendors"
        }.mapValues { (_, vendorItems) ->
            vendorItems.sortedWith(
                compareBy<GroceryItem> {
                    if (it.currentStock <= 0.0) 0.0
                    else if (it.dailyConsumptionRate <= 0.0) Double.MAX_VALUE
                    else it.currentStock / it.dailyConsumptionRate
                }.thenBy { it.currentStock }
                 .thenBy { it.name.lowercase() }
            )
        }.toList().sortedBy { (_, vendorItems) ->
            vendorItems.minOfOrNull {
                if (it.currentStock <= 0.0) 0.0
                else if (it.dailyConsumptionRate <= 0.0) Double.MAX_VALUE
                else it.currentStock / it.dailyConsumptionRate
            } ?: Double.MAX_VALUE
        }
    }

    // Interactive unchecked set to track shopping checked list visually
    var checkedItems by remember { mutableStateOf(setOf<Int>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "One-Click Restock Cart",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Generate order checklists automatically grouped by store/vendor based on out-of-stock timelines.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (restockItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        tint = StatusHealthy,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Fully Stocked! 🥳",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "None of your tracked grocery items are currently out of stock or running low within the next 7 days.",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 4.dp)
            ) {
                groupedByVendor.forEach { (vendor, vendorItems) ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Vendor Header
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Store,
                                        tint = MaterialTheme.colorScheme.primary,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = vendor.uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                }
                                
                                Divider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )

                                // Group items list
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    vendorItems.forEach { item ->
                                        val isChecked = checkedItems.contains(item.id)
                                        val daysLeft = if (item.dailyConsumptionRate > 0) item.currentStock / item.dailyConsumptionRate else 0.0
                                        val stockText = if (item.currentStock <= 0.0) "Out of Stock" else "${qtyFormat.format(item.currentStock)} ${item.unit} left"
                                        val warningText = if (item.currentStock <= 0.0) "OOS ⚠️" else "lasts ~${daysLeft.toInt()} days ⚠️"

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    checkedItems = if (checked) {
                                                        checkedItems + item.id
                                                    } else {
                                                        checkedItems - item.id
                                                    }
                                                }
                                            )
                                            
                                            Spacer(modifier = Modifier.width(4.dp))

                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { onShowPriceHistory(item.id) }
                                            ) {
                                                Text(
                                                    text = item.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = if (isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$stockText ($warningText)",
                                                    fontSize = 11.sp,
                                                    color = if (isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else AlertOOS
                                                )

                                                // Price history & fluctuation indicator
                                                val itemPurchasesList = remember(purchases, item.id) {
                                                    purchases.filter { it.itemId == item.id && it.quantityBought > 0 }.sortedBy { it.date }
                                                }
                                                if (itemPurchasesList.isNotEmpty()) {
                                                    val lastP = itemPurchasesList.last()
                                                    val prevP = if (itemPurchasesList.size >= 2) itemPurchasesList[itemPurchasesList.size - 2] else null
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "Last: ${formatUnitPrice(lastP.unitPrice)}/${item.unit}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary
                                                        )
                                                        if (prevP != null) {
                                                            val diff = lastP.unitPrice - prevP.unitPrice
                                                            val pct = if (prevP.unitPrice > 0) (diff / prevP.unitPrice) * 100.0 else 0.0
                                                            when {
                                                                diff < -0.001 -> {
                                                                    Surface(
                                                                        color = StatusHealthy.copy(alpha = 0.15f),
                                                                        shape = RoundedCornerShape(4.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = "🔻 -${percentFormat.format(kotlin.math.abs(pct))}%",
                                                                            fontSize = 9.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = StatusHealthy,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                        )
                                                                    }
                                                                }
                                                                diff > 0.001 -> {
                                                                    Surface(
                                                                        color = AlertOOS.copy(alpha = 0.15f),
                                                                        shape = RoundedCornerShape(4.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = "🔺 +${percentFormat.format(pct)}%",
                                                                            fontSize = 9.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = AlertOOS,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // View Price Fluctuations button
                                            IconButton(
                                                onClick = { onShowPriceHistory(item.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Timeline,
                                                    contentDescription = "Price Fluctuations",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            // Quick Restock Record Buy button
                                            IconButton(
                                                onClick = { onRecordPurchase(item.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.LocalMall,
                                                    contentDescription = "Buy",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== MONTH YEAR PICKER DIALOG ====================
@Composable
fun MonthYearPickerDialog(
    initialYear: Int,
    initialMonth: Int, // 0-indexed
    onDismiss: () -> Unit,
    onSelectMonthYear: (year: Int, month: Int) -> Unit
) {
    var pickerYear by remember { mutableStateOf(initialYear) }
    var pickerMonth by remember { mutableStateOf(initialMonth) }

    val months = remember {
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Month & Year",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Year Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { pickerYear -= 1 }) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = "Previous Year",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = pickerYear.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { pickerYear += 1 }) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = "Next Year",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 12 Months Grid
                val chunkedMonths = months.chunked(3)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chunkedMonths.forEachIndexed { rowIndex, rowMonths ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowMonths.forEachIndexed { colIndex, monthLabel ->
                                val monthIndex = rowIndex * 3 + colIndex
                                val isSelected = monthIndex == pickerMonth
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    onClick = { pickerMonth = monthIndex }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = monthLabel,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSelectMonthYear(pickerYear, pickerMonth)
                            onDismiss()
                        }
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

// ==================== ANALYTICS TAB ====================
@Composable
fun AnalyticsTab(
    itemsWithPurchases: List<GroceryItemWithPurchases>,
    spendingMetrics: SpendingMetrics,
    consumptionLogs: List<ConsumptionLogWithItem> = emptyList()
) {
    var selectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) } // 0-indexed
    var showMonthPicker by remember { mutableStateOf(false) }

    val nowCal = Calendar.getInstance()
    val isCurrentMonthAndYear = selectedYear == nowCal.get(Calendar.YEAR) && selectedMonth == nowCal.get(Calendar.MONTH)

    val selectedDate = remember(selectedYear, selectedMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }.time
    }

    val monthYearLabel = remember(selectedDate) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedDate)
    }

    val monthLabelUpper = remember(selectedDate) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedDate).uppercase()
    }

    // Filtered Item Breakdown for selected Month and Year
    val selectedItemBreakdown = remember(itemsWithPurchases, selectedYear, selectedMonth) {
        val breakdown = mutableMapOf<String, Double>()
        for (itemWithPurchases in itemsWithPurchases) {
            val itemName = itemWithPurchases.item.name
            val currentMonthPurchases = itemWithPurchases.purchases.filter { p ->
                val pCal = Calendar.getInstance().apply { timeInMillis = p.date }
                pCal.get(Calendar.YEAR) == selectedYear && pCal.get(Calendar.MONTH) == selectedMonth
            }
            val totalForItem = currentMonthPurchases.sumOf { it.totalPrice }
            if (totalForItem > 0) {
                breakdown[itemName] = (breakdown[itemName] ?: 0.0) + totalForItem
            }
        }
        breakdown.toList().sortedByDescending { it.second }
    }

    // Filtered Vendor Breakdown for selected Month and Year
    val selectedVendorBreakdown = remember(itemsWithPurchases, selectedYear, selectedMonth) {
        val breakdown = mutableMapOf<String, Double>()
        for (itemWithPurchases in itemsWithPurchases) {
            for (p in itemWithPurchases.purchases) {
                val pCal = Calendar.getInstance().apply { timeInMillis = p.date }
                if (pCal.get(Calendar.YEAR) == selectedYear && pCal.get(Calendar.MONTH) == selectedMonth) {
                    val v = p.vendor.trim().ifEmpty { "Other" }
                    breakdown[v] = (breakdown[v] ?: 0.0) + p.totalPrice
                }
            }
        }
        breakdown.toList().sortedByDescending { it.second }
    }

    // Total Spend for Selected Month & Year
    val selectedMonthSpending = remember(selectedItemBreakdown) {
        selectedItemBreakdown.sumOf { it.second }
    }

    // Spending for Previous Month
    val prevMonthSpending = remember(itemsWithPurchases, selectedYear, selectedMonth) {
        val prevCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
            add(Calendar.MONTH, -1)
        }
        val prevYear = prevCal.get(Calendar.YEAR)
        val prevMonth = prevCal.get(Calendar.MONTH)

        itemsWithPurchases.sumOf { itemWithP ->
            itemWithP.purchases.filter { p ->
                val pCal = Calendar.getInstance().apply { timeInMillis = p.date }
                pCal.get(Calendar.YEAR) == prevYear && pCal.get(Calendar.MONTH) == prevMonth
            }.sumOf { it.totalPrice }
        }
    }

    // Calculated Projected Monthly Cost
    val projectedMonthlyCost = remember(itemsWithPurchases) {
        var totalProjected = 0.0
        for (itemWithPurchases in itemsWithPurchases) {
            val item = itemWithPurchases.item
            if (item.dailyConsumptionRate > 0) {
                val lastPurchase = itemWithPurchases.purchases.maxByOrNull { it.date }
                val unitCost = lastPurchase?.let {
                    if (it.quantityBought > 0) it.totalPrice / it.quantityBought else null
                } ?: 0.0
                val monthlyBurnQty = item.dailyConsumptionRate * 30.0
                totalProjected += monthlyBurnQty * unitCost
            }
        }
        totalProjected
    }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            initialYear = selectedYear,
            initialMonth = selectedMonth,
            onDismiss = { showMonthPicker = false },
            onSelectMonthYear = { y, m ->
                selectedYear = y
                selectedMonth = m
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Cost & Smart Analytics",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Financial and consumption metrics by month.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Month & Year Navigation Selector Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, selectedYear)
                            set(Calendar.MONTH, selectedMonth)
                            add(Calendar.MONTH, -1)
                        }
                        selectedYear = cal.get(Calendar.YEAR)
                        selectedMonth = cal.get(Calendar.MONTH)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous Month",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Surface(
                    onClick = { showMonthPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = "Pick Month",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = monthYearLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = "Calendar Picker",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, selectedYear)
                            set(Calendar.MONTH, selectedMonth)
                            add(Calendar.MONTH, 1)
                        }
                        selectedYear = cal.get(Calendar.YEAR)
                        selectedMonth = cal.get(Calendar.MONTH)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Next Month",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        if (!isCurrentMonthAndYear) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        val now = Calendar.getInstance()
                        selectedYear = now.get(Calendar.YEAR)
                        selectedMonth = now.get(Calendar.MONTH)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("📍 Jump to Current Month", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Cost Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TOTAL SPEND",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        letterSpacing = 1.sp
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = monthLabelUpper,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = priceFormat.format(selectedMonthSpending),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "spent",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "PREVIOUS MONTH SPEND",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = priceFormat.format(prevMonthSpending),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ESTIMATED BURN RUN-RATE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = priceFormat.format(projectedMonthlyCost),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Pro-tip Budget Prediction Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    tint = WarningSoon,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Monthly Insight ($monthYearLabel)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "You spent ${priceFormat.format(selectedMonthSpending)} in $monthYearLabel across ${selectedItemBreakdown.size} food items.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Spending by Item
        Text(
            text = "Spending by Item ($monthYearLabel)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (selectedItemBreakdown.isEmpty()) {
            Text(
                text = "No item spending recorded in $monthYearLabel.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            val maxItemPrice = selectedItemBreakdown.first().second
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    selectedItemBreakdown.forEach { (itemName, price) ->
                        val ratio = if (maxItemPrice > 0) (price / maxItemPrice).toFloat() else 0f
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = itemName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = priceFormat.format(price), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // Spending by Vendor
        Text(
            text = "Spending by Store / Vendor ($monthYearLabel)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (selectedVendorBreakdown.isEmpty()) {
            Text(
                text = "No store/vendor spending recorded in $monthYearLabel.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            val maxVendorPrice = selectedVendorBreakdown.first().second
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    selectedVendorBreakdown.forEach { (vendor, price) ->
                        val ratio = if (maxVendorPrice > 0) (price / maxVendorPrice).toFloat() else 0f
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = vendor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = priceFormat.format(price), fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Product Monthly Consumption & Burn Value Table
        ProductMonthlyConsumptionTable(
            itemsWithPurchases = itemsWithPurchases,
            consumptionLogs = consumptionLogs,
            selectedYear = selectedYear,
            selectedMonth = selectedMonth,
            monthYearLabel = monthYearLabel,
            modifier = Modifier.padding(bottom = 80.dp)
        )
    }
}

@Composable
fun ProductMonthlyConsumptionTable(
    itemsWithPurchases: List<GroceryItemWithPurchases>,
    consumptionLogs: List<ConsumptionLogWithItem>,
    selectedYear: Int,
    selectedMonth: Int,
    monthYearLabel: String,
    modifier: Modifier = Modifier
) {
    data class ProductConsumptionRowData(
        val item: GroceryItem,
        val dailyConsumptionRate: Double,
        val unit: String,
        val consumedDaysCount: Int,
        val consumedQtySoFar: Double,
        val unitPrice: Double,
        val dailyBurnRatePrice: Double,
        val priceForConsumed: Double
    )

    val rowsData = remember(itemsWithPurchases, consumptionLogs, selectedYear, selectedMonth) {
        itemsWithPurchases.sortedBy { it.item.name.lowercase() }.map { itemWithP ->
            val item = itemWithP.item
            val latestPurchase = itemWithP.purchases.filter { it.quantityBought > 0 }.maxByOrNull { it.date }
            val unitPrice = latestPurchase?.unitPrice ?: 0.0

            // Filter consumption logs for this specific item in the selected month & year
            val itemLogsInMonth = consumptionLogs.filter { logWithItem ->
                logWithItem.log.itemId == item.id && run {
                    val lCal = Calendar.getInstance().apply { timeInMillis = logWithItem.log.date }
                    lCal.get(Calendar.YEAR) == selectedYear && lCal.get(Calendar.MONTH) == selectedMonth
                }
            }

            // Distinct dates (calendar days of the month) on which this product was actually consumed
            val distinctDays = itemLogsInMonth.map {
                val lCal = Calendar.getInstance().apply { timeInMillis = it.log.date }
                lCal.get(Calendar.DAY_OF_MONTH)
            }.toSet()
            val consumedDaysCount = distinctDays.size

            // Actual quantity consumed strictly on those consumed dates
            val loggedQty = itemLogsInMonth.sumOf { it.log.quantityConsumed }
            val effectiveQty = if (loggedQty > 0.0) {
                loggedQty
            } else if (consumedDaysCount > 0 && item.dailyConsumptionRate > 0.0) {
                consumedDaysCount * item.dailyConsumptionRate
            } else {
                0.0
            }

            val dailyBurnRatePrice = item.dailyConsumptionRate * unitPrice
            val priceForConsumed = effectiveQty * unitPrice

            ProductConsumptionRowData(
                item = item,
                dailyConsumptionRate = item.dailyConsumptionRate,
                unit = item.unit,
                consumedDaysCount = consumedDaysCount,
                consumedQtySoFar = effectiveQty,
                unitPrice = unitPrice,
                dailyBurnRatePrice = dailyBurnRatePrice,
                priceForConsumed = priceForConsumed
            )
        }
    }

    val totalMonthConsumedAmount = remember(rowsData) {
        rowsData.sumOf { it.priceForConsumed }
    }

    val totalDailyBurnRunRate = remember(rowsData) {
        rowsData.sumOf { it.dailyBurnRatePrice }
    }

    val totalProductsConsumed = remember(rowsData) {
        rowsData.count { it.consumedDaysCount > 0 }
    }

    val totalConsumedDaysSum = remember(rowsData) {
        rowsData.sumOf { it.consumedDaysCount }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("analytics_consumption_table")
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Monthly Consumption & Burn Value",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Product-level burn rates and amount consumed in $monthYearLabel",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Highlight Summary Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL CONSUMED VALUE ($monthYearLabel)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = priceFormat.format(totalMonthConsumedAmount),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (totalProductsConsumed > 0) "$totalProductsConsumed of ${rowsData.size} consumed" else "0 consumed this month",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (totalConsumedDaysSum > 0) "$totalConsumedDaysSum total log days" else "${priceFormat.format(totalDailyBurnRunRate)}/day burn",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (rowsData.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No products found. Add items to your pantry to track monthly consumption.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            // Horizontal scroll container for the 4-column table
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Header hint row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${rowsData.size} Products",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Scroll horizontally for details",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Table content with horizontal scrolling
                    val tableHorizontalScrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(tableHorizontalScrollState)
                    ) {
                        // Table Header Row
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("consumption_table_header"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PRODUCT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(150.dp)
                            )
                            Text(
                                text = "DAILY CONSUMPTION",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(140.dp)
                            )
                            Text(
                                text = "SO FAR CONSUMED",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(140.dp)
                            )
                            Text(
                                text = "PRICE CONSUMED",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(150.dp)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                        // Table Body Rows (all users products)
                        rowsData.forEachIndexed { index, row ->
                            val isEven = index % 2 == 0
                            val rowBg = if (isEven) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            }

                            Row(
                                modifier = Modifier
                                    .background(rowBg)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("consumption_row_${row.item.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Column 1: Product
                                Column(modifier = Modifier.width(150.dp)) {
                                    Text(
                                        text = row.item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = row.item.category,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Column 2: Products daily consumption
                                Column(
                                    modifier = Modifier.width(140.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = if (row.dailyConsumptionRate > 0) {
                                            "${qtyFormat.format(row.dailyConsumptionRate)} ${row.unit}/day"
                                        } else {
                                            "0 ${row.unit}/day"
                                        },
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.End
                                    )
                                    if (row.dailyConsumptionRate > 0 && row.unitPrice > 0) {
                                        Text(
                                            text = "${priceShortFormat.format(row.dailyBurnRatePrice)}/day",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }

                                // Column 3: So far consumed in current month
                                Column(
                                    modifier = Modifier.width(140.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "${qtyFormat.format(row.consumedQtySoFar)} ${row.unit}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (row.consumedQtySoFar > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        textAlign = TextAlign.End
                                    )
                                    Text(
                                        text = if (row.consumedDaysCount > 0) {
                                            "${row.consumedDaysCount} day${if (row.consumedDaysCount > 1) "s" else ""} consumed"
                                        } else {
                                            "0 days consumed"
                                        },
                                        fontSize = 10.sp,
                                        color = if (row.consumedDaysCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        textAlign = TextAlign.End
                                    )
                                }

                                // Column 4: Price for so far consumed (products burn rate x consumed day)
                                Column(
                                    modifier = Modifier.width(150.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = priceFormat.format(row.priceForConsumed),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (row.priceForConsumed > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        textAlign = TextAlign.End
                                    )
                                    if (row.consumedDaysCount > 0 && row.dailyBurnRatePrice > 0) {
                                        Text(
                                            text = "${priceShortFormat.format(row.dailyBurnRatePrice)}/d × ${row.consumedDaysCount}d",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            textAlign = TextAlign.End
                                        )
                                    } else if (row.unitPrice > 0) {
                                        Text(
                                            text = "@ ${formatUnitPrice(row.unitPrice)}/${row.unit}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            textAlign = TextAlign.End
                                        )
                                    } else {
                                        Text(
                                            text = "No purchase price",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }

                        // Total Footer Row
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .testTag("consumption_total_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.width(150.dp)) {
                                Text(
                                    text = "TOTAL AMOUNT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$monthYearLabel (${rowsData.size} products)",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }

                            Text(
                                text = "—",
                                modifier = Modifier.width(140.dp),
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )

                            Column(
                                modifier = Modifier.width(140.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = if (totalProductsConsumed > 0) "$totalProductsConsumed items" else "0 items",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    text = if (totalConsumedDaysSum > 0) "$totalConsumedDaysSum log days" else "0 days",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    textAlign = TextAlign.End
                                )
                            }

                            Column(
                                modifier = Modifier.width(150.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = priceFormat.format(totalMonthConsumedAmount),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    text = "Total Consumed",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== PRICE TRENDS TAB ====================
@Composable
fun PriceTrendsTab(
    itemsWithPurchases: List<GroceryItemWithPurchases>,
    onGetBrandRating: ((String, Int) -> Int?)? = null
) {
    if (itemsWithPurchases.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Create catalog items first to track price trends.",
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
        return
    }

    var selectedItemIndex by remember { mutableStateOf(0) }
    val itemWithPurchases = itemsWithPurchases.getOrNull(selectedItemIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp)
    ) {
        Text(
            text = "Price History & Unit Cost Tracker",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Monitor fluctuations in unit prices (e.g. ₹ per gram) over time to secure the best deals.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Dropdown/Selector for Item
        var expandedDropdown by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expandedDropdown = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = itemWithPurchases?.item?.name ?: "Select Item",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            DropdownMenu(
                expanded = expandedDropdown,
                onDismissRequest = { expandedDropdown = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                itemsWithPurchases.forEachIndexed { index, iwp ->
                    DropdownMenuItem(
                        text = { Text(iwp.item.name) },
                        onClick = {
                            selectedItemIndex = index
                            expandedDropdown = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (itemWithPurchases != null) {
            val item = itemWithPurchases.item
            val sortedPurchases = itemWithPurchases.purchases.sortedByDescending { it.date }

            // Cost Calculations
            val lowestPurchase = remember(sortedPurchases) {
                sortedPurchases.filter { it.quantityBought > 0 }.minByOrNull { it.unitPrice }
            }
            val highestPurchase = remember(sortedPurchases) {
                sortedPurchases.filter { it.quantityBought > 0 }.maxByOrNull { it.unitPrice }
            }
            val lastTwoPurchases = remember(sortedPurchases) {
                sortedPurchases.take(2)
            }
            val lowestUnitCost = remember(sortedPurchases) {
                lowestPurchase?.unitPrice
            }
            val lastUnitCost = remember(sortedPurchases) {
                sortedPurchases.firstOrNull { it.quantityBought > 0 }?.unitPrice
            }
            val highestUnitCost = remember(sortedPurchases) {
                highestPurchase?.unitPrice
            }

            // Summary metrics row
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Lowest Deal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        Text(
                            text = lowestUnitCost?.let { "${formatUnitPrice(it)}/${item.unit}" } ?: "N/A",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusHealthy
                        )
                    }
                    HorizontalDivider(modifier = Modifier.height(36.dp).width(1.dp).align(Alignment.CenterVertically))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Last Deal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        Text(
                            text = lastUnitCost?.let { "${formatUnitPrice(it)}/${item.unit}" } ?: "N/A",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(modifier = Modifier.height(36.dp).width(1.dp).align(Alignment.CenterVertically))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Highest Deal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        Text(
                            text = highestUnitCost?.let { "${formatUnitPrice(it)}/${item.unit}" } ?: "N/A",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AlertOOS
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Least Price Vendor Highlight Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = StatusHealthy.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, StatusHealthy.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = StatusHealthy,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "🏆 LEAST PRICE VENDOR",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (lowestPurchase != null) {
                        Text(
                            text = "The least price spent for ${item.name} was on ${dateFormat.format(Date(lowestPurchase.date))} from ${lowestPurchase.vendor}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Amount: ${priceFormat.format(lowestPurchase.totalPrice)} for ${qtyFormat.format(lowestPurchase.quantityBought)} ${item.unit} (${formatUnitPrice(lowestPurchase.unitPrice)}/${item.unit})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        if (!lowestPurchase.brand.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Brand: ${lowestPurchase.brand}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                val lRating = lowestPurchase.rating ?: onGetBrandRating?.invoke(lowestPurchase.brand, item.id)
                                if (lRating != null) {
                                    RatingBar(rating = lRating, starSize = 12.dp, interactive = false)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No purchase history available yet to calculate least price vendor.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Last 2 Purchases History Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Last 2 Purchases History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (lastTwoPurchases.isEmpty()) {
                        Text(
                            text = "No recent purchase history.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            lastTwoPurchases.forEachIndexed { index, purchase ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (index == 0) "Latest Buy" else "Previous Buy",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = purchase.vendor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            if (!purchase.brand.isNullOrBlank()) {
                                                Text(
                                                    text = " • ${purchase.brand}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${dateFormat.format(Date(purchase.date))} • ${qtyFormat.format(purchase.quantityBought)} ${item.unit}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                        val bRating = purchase.rating ?: purchase.brand?.let { onGetBrandRating?.invoke(it, item.id) }
                                        if (bRating != null) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            RatingBar(rating = bRating, starSize = 12.dp, interactive = false)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = priceFormat.format(purchase.totalPrice),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${formatUnitPrice(purchase.unitPrice)}/${item.unit}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "All Purchase History (${sortedPurchases.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (sortedPurchases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No purchase records found. Click 'Record Buy' on home tab to add log.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sortedPurchases.forEach { purchase ->
                        val unitCost = if (purchase.quantityBought > 0) purchase.totalPrice / purchase.quantityBought else 0.0
                        
                        // Compare to lowest / highest deals
                        val isLowest = lowestUnitCost != null && unitCost <= lowestUnitCost + 0.0001
                        val isHighest = highestUnitCost != null && unitCost >= highestUnitCost - 0.0001 && (lowestUnitCost == null || highestUnitCost > lowestUnitCost + 0.0001)
                        val badgeColor = if (isLowest) StatusHealthy else if (isHighest) AlertOOS else WarningSoon
                        val badgeText = if (isLowest) "Lowest Deal" else if (isHighest) "Highest Deal" else "Standard Deal"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = purchase.vendor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (!purchase.brand.isNullOrBlank()) {
                                                Text(
                                                    text = " • ${purchase.brand}",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                        Text(
                                            text = dateFormat.format(Date(purchase.date)),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                        val bRating = purchase.rating ?: purchase.brand?.let { onGetBrandRating?.invoke(it, item.id) }
                                        if (bRating != null) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            RatingBar(rating = bRating, starSize = 12.dp, interactive = false)
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = priceFormat.format(purchase.totalPrice),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${qtyFormat.format(purchase.quantityBought)} ${item.unit}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Unit Cost: ${formatUnitPrice(unitCost)} per ${item.unit}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    Surface(
                                        color = badgeColor.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            color = badgeColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== DIALOGS ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, unit: String, initialStock: Double, dailyRate: Double, vendor: String?, initialPrice: Double, trackingMode: String, isPaused: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("grams") }
    var initialStock by remember { mutableStateOf("0") }
    var dailyRate by remember { mutableStateOf("0") }
    var initialPrice by remember { mutableStateOf("0") }
    var preferredVendor by remember { mutableStateOf("") }
    var trackingMode by remember { mutableStateOf("AUTOMATIC") }
    var isPaused by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Pantry Item",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name (e.g. Oats)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (e.g. grams, scoops, items)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = initialStock,
                    onValueChange = { initialStock = it },
                    label = { Text("Current Stock Level") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = initialPrice,
                    onValueChange = { initialPrice = it },
                    label = { Text("Price Paid for Current Stock") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dailyRate,
                    onValueChange = { dailyRate = it },
                    label = { Text("Daily Burn Rate (0 if unknown)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Tracking Mode Selector
                Text(
                    text = "Tracking Mode",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = trackingMode == "AUTOMATIC",
                        onClick = { trackingMode = "AUTOMATIC" },
                        label = { Text("Automatic (8 PM)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = trackingMode == "MANUAL",
                        onClick = { trackingMode = "MANUAL" },
                        label = { Text("Manual Only") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (trackingMode == "AUTOMATIC") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPaused) "Pause Daily Auto-Log" else "Daily Auto-Log Active",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = isPaused,
                            onCheckedChange = { isPaused = it }
                        )
                    }
                }

                OutlinedTextField(
                    value = preferredVendor,
                    onValueChange = { preferredVendor = it },
                    label = { Text("Default Store/Vendor (e.g. Bigbasket)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(
                                    name.trim(),
                                    unit.trim().ifEmpty { "units" },
                                    initialStock.toDoubleOrNull() ?: 0.0,
                                    dailyRate.toDoubleOrNull() ?: 0.0,
                                    preferredVendor.trim().ifEmpty { null },
                                    initialPrice.toDoubleOrNull() ?: 0.0,
                                    trackingMode,
                                    isPaused
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
fun RecordPurchaseDialog(
    item: GroceryItem,
    viewModel: GroceryViewModel,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double, totalPrice: Double, vendor: String, brand: String?, rating: Int?) -> Unit
) {
    var quantity by remember { mutableStateOf("") }
    var totalPrice by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf(item.preferredVendor ?: "Bigbasket") }
    var brand by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf<Int?>(null) }
    var hasManuallyChangedRating by remember { mutableStateOf(false) }
    var showBrandSuggestions by remember { mutableStateOf(false) }

    val allPurchasesList by viewModel.allPurchases.collectAsStateWithLifecycle()
    val brandSuggestions = remember(item.id, allPurchasesList) {
        viewModel.getBrandSuggestionsForItem(item.id)
    }

    val brandInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(brandInteractionSource) {
        brandInteractionSource.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release ||
                interaction is androidx.compose.foundation.interaction.FocusInteraction.Focus) {
                showBrandSuggestions = true
            }
        }
    }

    val filteredSuggestions = remember(brand, brandSuggestions) {
        if (brand.isBlank()) brandSuggestions
        else brandSuggestions.filter { it.brand.contains(brand.trim(), ignoreCase = true) }
    }

    // When brand changes, check if there's an existing rating for this brand
    val matchedRating = remember(brand) {
        if (brand.isNotBlank()) viewModel.getLatestRatingForBrand(brand, item.id) else null
    }

    // If a previous rating exists for this brand and user hasn't manually overridden yet, preselect it
    LaunchedEffect(matchedRating) {
        if (matchedRating != null && !hasManuallyChangedRating) {
            rating = matchedRating
        }
    }

    val qtyVal = quantity.toDoubleOrNull()
    val priceVal = totalPrice.toDoubleOrNull()
    val unitPriceCalculated = if (qtyVal != null && qtyVal > 0 && priceVal != null && priceVal >= 0) priceVal / qtyVal else null

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Record Buy: ${item.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity Purchased (${item.unit})") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = totalPrice,
                    onValueChange = { totalPrice = it },
                    label = { Text("Total Price Paid (₹)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                if (unitPriceCalculated != null) {
                    val prevPurchasesForThis = remember(allPurchasesList, item.id) {
                        allPurchasesList.filter { it.itemId == item.id && it.quantityBought > 0 }.sortedBy { it.date }
                    }
                    val lastRate = prevPurchasesForThis.lastOrNull()?.unitPrice
                    val lowestRate = prevPurchasesForThis.minOfOrNull { it.unitPrice }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Calculated Rate:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${formatUnitPrice(unitPriceCalculated)} / ${item.unit}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (lastRate != null) {
                                val rateDiff = unitPriceCalculated - lastRate
                                val pct = (rateDiff / lastRate) * 100.0
                                val isBest = lowestRate != null && unitPriceCalculated <= lowestRate
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Last: ${formatUnitPrice(lastRate)}/${item.unit}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                    when {
                                        isBest -> Text("🎉 Best price deal!", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusHealthy)
                                        rateDiff < -0.001 -> Text("🔻 Price drop of ${percentFormat.format(kotlin.math.abs(pct))}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusHealthy)
                                        rateDiff > 0.001 -> Text("🔺 Price hike of +${percentFormat.format(pct)}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AlertOOS)
                                        else -> Text("➖ Same as last restock", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("Store / Vendor Name") },
                    placeholder = { Text("e.g. Bigbasket, D-Mart, Blinkit") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Brand Input with Previous Bought Brands & Ratings
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = {
                            brand = it
                            hasManuallyChangedRating = false
                            showBrandSuggestions = true
                        },
                        label = { Text("Brand Name (Optional)") },
                        placeholder = { Text("Tap to select previous brand or type manual name") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        interactionSource = brandInteractionSource,
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (brand.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            brand = ""
                                            rating = null
                                            hasManuallyChangedRating = false
                                            showBrandSuggestions = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear Brand",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showBrandSuggestions = !showBrandSuggestions },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showBrandSuggestions) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                                        contentDescription = "Toggle previous brands",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Previous bought brands with their ratings dropdown/list
                    AnimatedVisibility(
                        visible = showBrandSuggestions,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Previously Bought Brands (${item.name})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    TextButton(
                                        onClick = { showBrandSuggestions = false },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("Done", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (brandSuggestions.isEmpty()) {
                                    Text(
                                        text = "No previous brands logged for ${item.name} yet. Enter brand name above manually.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else if (filteredSuggestions.isEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "No match for \"$brand\"",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                        TextButton(
                                            onClick = { brand = "" },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text("Show All", fontSize = 11.sp)
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 180.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        filteredSuggestions.forEach { suggestion ->
                                            val isSelected = brand.equals(suggestion.brand, ignoreCase = true)
                                            Surface(
                                                onClick = {
                                                    brand = suggestion.brand
                                                    hasManuallyChangedRating = false
                                                    if (suggestion.rating != null) {
                                                        rating = suggestion.rating
                                                    }
                                                    showBrandSuggestions = false
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(
                                                            text = suggestion.brand,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }

                                                    if (suggestion.rating != null) {
                                                        val ratingColor = getRatingColor(suggestion.rating)
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                                                for (s in 1..5) {
                                                                    Icon(
                                                                        imageVector = if (s <= suggestion.rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                                        contentDescription = null,
                                                                        tint = if (s <= suggestion.rating) ratingColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                                                        modifier = Modifier.size(13.dp)
                                                                    )
                                                                }
                                                            }
                                                            Surface(
                                                                color = ratingColor.copy(alpha = 0.15f),
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "${suggestion.rating}/5",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = ratingColor,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        Text(
                                                            text = "No rating",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Brand Quality Rating Section
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Brand Quality Rating",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (matchedRating != null && brand.isNotBlank()) {
                                Surface(
                                    color = getRatingColor(matchedRating).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "★ Previous: $matchedRating/5",
                                        color = getRatingColor(matchedRating),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Star selector with dynamic colors: 5=green, 4=lime, 3=yellow, 2=orange, 1=red
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val activeColor = rating?.let { getRatingColor(it) } ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            for (starIndex in 1..5) {
                                val isSelected = rating != null && starIndex <= rating!!
                                val starTint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                IconButton(
                                    onClick = {
                                        hasManuallyChangedRating = true
                                        rating = if (rating == starIndex) null else starIndex
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "$starIndex Star",
                                        tint = starTint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (rating != null) getRatingLabel(rating!!) else "Tap stars to rate brand quality (1=Red, 5=Green)",
                            fontSize = 11.sp,
                            fontWeight = if (rating != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = rating?.let { getRatingColor(it) } ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (qtyVal != null && qtyVal > 0 && priceVal != null && priceVal >= 0) {
                                onConfirm(
                                    qtyVal,
                                    priceVal,
                                    vendor,
                                    brand.trim().ifEmpty { null },
                                    rating
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Buy")
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCenterDialog(
    alerts: List<GroceryItem>,
    dismissedIds: Set<Int>,
    onDismissAlert: (Int) -> Unit,
    onRestoreAlert: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRecordPurchase: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val activeCount = alerts.count { it.id !in dismissedIds }
                        Icon(
                            imageVector = if (activeCount > 0) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = if (activeCount > 0) AlertOOS else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pantry Alerts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (alerts.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = StatusHealthy,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "All Stocked Up!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No items are running out within 3 days.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        text = "The following items are running low and predicted to go out of stock within 3 days. Consider restocking soon!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(alerts) { item ->
                            val daysLeft = item.currentStock / item.dailyConsumptionRate
                            val dateMs = System.currentTimeMillis() + (daysLeft * 24L * 60L * 60L * 1000L).toLong()
                            val predictedOosDate = dateFormat.format(Date(dateMs))
                            val isDismissed = item.id in dismissedIds

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDismissed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                ),
                                border = BorderStroke(1.dp, if (isDismissed) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else AlertOOS.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (isDismissed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Estimated OOS Date: $predictedOosDate",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDismissed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else AlertOOS
                                        )
                                        Text(
                                            text = "(${qtyFormat.format(daysLeft)} days of stock left${if (isDismissed) " - Dismissed" else ""})",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (isDismissed) {
                                            IconButton(
                                                onClick = { onRestoreAlert(item.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Notifications,
                                                    contentDescription = "Unmute Alert",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { onDismissAlert(item.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.NotificationsOff,
                                                    contentDescription = "Mute Alert",
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = { onRecordPurchase(item.id) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isDismissed) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f) else AlertOOS,
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Buy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Got It", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductDialog(
    item: GroceryItem,
    onDismiss: () -> Unit,
    onConfirm: (GroceryItem) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var unit by remember { mutableStateOf(item.unit) }
    var preferredVendor by remember { mutableStateOf(item.preferredVendor ?: "") }
    var trackingMode by remember { mutableStateOf(item.trackingMode) }
    var isPaused by remember { mutableStateOf(item.isPaused) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Edit Product Info",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (e.g., grams, pieces)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Tracking Mode Selector
                Text(
                    text = "Tracking Mode",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = trackingMode == "AUTOMATIC",
                        onClick = { trackingMode = "AUTOMATIC" },
                        label = { Text("Automatic (8 PM)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = trackingMode == "MANUAL",
                        onClick = { trackingMode = "MANUAL" },
                        label = { Text("Manual Only") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (trackingMode == "AUTOMATIC") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPaused) "Pause Daily Auto-Log" else "Daily Auto-Log Active",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = isPaused,
                            onCheckedChange = { isPaused = it }
                        )
                    }
                }

                OutlinedTextField(
                    value = preferredVendor,
                    onValueChange = { preferredVendor = it },
                    label = { Text("Preferred Vendor (Optional)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && unit.isNotBlank()) {
                                onConfirm(
                                    item.copy(
                                        name = name.trim(),
                                        unit = unit.trim(),
                                        preferredVendor = preferredVendor.trim().ifEmpty { null },
                                        trackingMode = trackingMode,
                                        isPaused = isPaused
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// ==================== DAILY CONSUMPTION LOGS ====================

@Composable
fun ConsumeItemDialog(
    item: GroceryItem,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var quantityText by remember { mutableStateOf(if (item.dailyConsumptionRate > 0) item.dailyConsumptionRate.toString() else "0") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Log Consumption",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Record how much ${item.name} you consumed today. This will subtract from your current stock of ${qtyFormat.format(item.currentStock)} ${item.unit}.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity Consumed (${item.unit})") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val qty = quantityText.toDoubleOrNull() ?: 0.0
                            if (qty > 0) {
                                onConfirm(qty)
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Log")
                    }
                }
            }
        }
    }
}

@Composable
fun EditConsumptionLogDialog(
    logWithItem: ConsumptionLogWithItem,
    onDismiss: () -> Unit,
    onConfirm: (Double, Long) -> Unit
) {
    val log = logWithItem.log
    val itemName = logWithItem.item?.name ?: "Unknown Product"
    var quantityText by remember { mutableStateOf(log.quantityConsumed.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Edit Consumption Log",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Adjust the consumed quantity of $itemName. Your current stock will automatically update by the difference.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity Consumed (${log.unit})") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val qty = quantityText.toDoubleOrNull() ?: 0.0
                            if (qty >= 0) {
                                onConfirm(qty, log.date)
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConsumptionConfirmDialog(
    logWithItem: ConsumptionLogWithItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val itemName = logWithItem.item?.name ?: "Unknown Product"
    val qtyText = "${qtyFormat.format(logWithItem.log.quantityConsumed)} ${logWithItem.log.unit}"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Consumption Log?") },
        text = { Text("Are you sure you want to delete this log of $qtyText of $itemName? This will restore the stock.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun DeletePurchaseConfirmDialog(
    purchase: Purchase,
    itemName: String,
    unit: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val qtyText = "${qtyFormat.format(purchase.quantityBought)} $unit"
    val priceText = priceFormat.format(purchase.totalPrice)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Buy Log?") },
        text = { Text("Are you sure you want to delete this purchase entry ($qtyText for $priceText from ${purchase.vendor}) for $itemName? This will also reduce the current stock accordingly.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Buy", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

sealed class ActivityLogItem(val timestamp: Long) {
    data class Consumption(val logWithItem: ConsumptionLogWithItem) : ActivityLogItem(logWithItem.log.date)
    data class Buy(val purchase: Purchase, val item: GroceryItem?) : ActivityLogItem(purchase.date)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsTab(
    consumptionLogs: List<ConsumptionLogWithItem>,
    purchases: List<Purchase>,
    items: List<GroceryItem>,
    onDeleteLog: (ConsumptionLog) -> Unit,
    onEditLog: (ConsumptionLog, Double, Long) -> Unit,
    onDeletePurchase: (Purchase) -> Unit,
    onUpdatePurchaseRating: ((Purchase, Int?, String?) -> Unit)? = null,
    onGetBrandRating: ((String, Int) -> Int?)? = null
) {
    var selectedFilter by remember { mutableStateOf(0) } // 0: All, 1: Consumption, 2: Buys
    var logToEdit by remember { mutableStateOf<ConsumptionLogWithItem?>(null) }
    var logToDelete by remember { mutableStateOf<ConsumptionLogWithItem?>(null) }
    var purchaseToDelete by remember { mutableStateOf<Pair<Purchase, GroceryItem?>?>(null) }
    var purchaseToRate by remember { mutableStateOf<Pair<Purchase, GroceryItem?>?>(null) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }
    val itemMap = remember(items) { items.associateBy { it.id } }

    val allActivities = remember(consumptionLogs, purchases, itemMap) {
        val consumptionItems = consumptionLogs.map { ActivityLogItem.Consumption(it) }
        val buyItems = purchases.map { ActivityLogItem.Buy(it, itemMap[it.itemId]) }
        (consumptionItems + buyItems).sortedByDescending { it.timestamp }
    }

    val displayList = remember(selectedFilter, allActivities, consumptionLogs, purchases, itemMap) {
        when (selectedFilter) {
            1 -> consumptionLogs.sortedByDescending { it.log.date }.map { ActivityLogItem.Consumption(it) }
            2 -> purchases.sortedByDescending { it.date }.map { ActivityLogItem.Buy(it, itemMap[it.itemId]) }
            else -> allActivities
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Activity & Tracking Logs",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Full audit trail of daily consumption logs and recorded grocery store purchases.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Filter Tab Row
        PrimaryTabRow(
            selectedTabIndex = selectedFilter,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Tab(
                selected = selectedFilter == 0,
                onClick = { selectedFilter = 0 },
                text = { Text("All (${allActivities.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedFilter == 1,
                onClick = { selectedFilter = 1 },
                text = { Text("Intake (${consumptionLogs.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedFilter == 2,
                onClick = { selectedFilter = 2 },
                text = { Text("Buys (${purchases.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
        }

        if (displayList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = when (selectedFilter) {
                            1 -> "No consumption logs recorded yet."
                            2 -> "No purchase buys recorded yet."
                            else -> "No logs recorded yet."
                        },
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Text(
                        text = when (selectedFilter) {
                            1 -> "Tap 'Consume' on any product in the Pantry tab to log your daily intake."
                            2 -> "Record purchases from the Restock tab (bag button) or product cards to track restocks."
                            else -> "Consumption entries and purchases will appear here in chronological order."
                        },
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(displayList) { activityItem ->
                    when (activityItem) {
                        is ActivityLogItem.Consumption -> {
                            val logWithItem = activityItem.logWithItem
                            val log = logWithItem.log
                            val item = logWithItem.item
                            val itemName = item?.name ?: "Deleted Product"

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = itemName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (log.isAutomated) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "AUTO (8 PM)",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "MANUAL",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = dateFormat.format(Date(log.date)),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "-${qtyFormat.format(log.quantityConsumed)} ${log.unit}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )

                                        IconButton(
                                            onClick = { logToEdit = logWithItem },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = "Edit log",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { logToDelete = logWithItem },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Delete log",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        is ActivityLogItem.Buy -> {
                            val purchase = activityItem.purchase
                            val item = activityItem.item
                            val itemName = item?.name ?: "Grocery Item #${purchase.itemId}"
                            val unit = item?.unit ?: "units"

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Header: Item name + BOUGHT badge on left, Quantity + Action buttons on right
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Text(
                                                text = itemName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "BOUGHT",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "+${qtyFormat.format(purchase.quantityBought)} $unit",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 4.dp)
                                            )

                                            IconButton(
                                                onClick = { purchaseToRate = Pair(purchase, item) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (purchase.rating != null) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                    contentDescription = "Rate purchase brand",
                                                    tint = if (purchase.rating != null) getRatingColor(purchase.rating) else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { purchaseToDelete = Pair(purchase, item) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = "Delete purchase log",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Store line
                                    Text(
                                        text = "Store: ${purchase.vendor}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Date, Total Price, and Unit Rate
                                    Text(
                                        text = "${dateFormat.format(Date(purchase.date))} • ${priceFormat.format(purchase.totalPrice)} (${formatUnitPrice(purchase.unitPrice)}/$unit)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )

                                    // Brand & Star Rating full-width container (when brand or rating is present)
                                    val currentRating = purchase.rating ?: purchase.brand?.let { onGetBrandRating?.invoke(it, purchase.itemId) }
                                    val brandName = purchase.brand?.trim()
                                    if (!brandName.isNullOrEmpty() || currentRating != null) {
                                        val ratingColor = currentRating?.let { getRatingColor(it) } ?: MaterialTheme.colorScheme.primary
                                        Surface(
                                            color = if (currentRating != null) ratingColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(
                                                0.5.dp,
                                                if (currentRating != null) ratingColor.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.weight(1f, fill = false)
                                                ) {
                                                    Text(
                                                        text = "Brand:",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    )
                                                    Text(
                                                        text = if (!brandName.isNullOrEmpty()) brandName else "Generic",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                if (currentRating != null) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        RatingBar(
                                                            rating = currentRating,
                                                            starSize = 12.dp,
                                                            interactive = false,
                                                            showBadge = false
                                                        )
                                                        Text(
                                                            text = "${currentRating}/5 • ${getRatingLabel(currentRating).substringAfter("★ ")}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = ratingColor
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = "Tap ★ to rate",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    logToEdit?.let { log ->
        EditConsumptionLogDialog(
            logWithItem = log,
            onDismiss = { logToEdit = null },
            onConfirm = { newQty, newDate ->
                onEditLog(log.log, newQty, newDate)
                logToEdit = null
            }
        )
    }

    logToDelete?.let { log ->
        DeleteConsumptionConfirmDialog(
            logWithItem = log,
            onDismiss = { logToDelete = null },
            onConfirm = {
                onDeleteLog(log.log)
                logToDelete = null
            }
        )
    }

    purchaseToDelete?.let { (purchase, item) ->
        DeletePurchaseConfirmDialog(
            purchase = purchase,
            itemName = item?.name ?: "Product #${purchase.itemId}",
            unit = item?.unit ?: "units",
            onDismiss = { purchaseToDelete = null },
            onConfirm = {
                onDeletePurchase(purchase)
                purchaseToDelete = null
            }
        )
    }

    purchaseToRate?.let { (purchase, item) ->
        RatePurchaseDialog(
            purchase = purchase,
            itemName = item?.name ?: "Product #${purchase.itemId}",
            onDismiss = { purchaseToRate = null },
            onConfirm = { rating, brand ->
                onUpdatePurchaseRating?.invoke(purchase, rating, brand)
                purchaseToRate = null
            }
        )
    }
}

@Composable
fun DataBackupRestoreDialog(
    onDismiss: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (String) -> Unit
) {
    var isImportMode by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Data Retention & Backup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🔒 Safe Local Storage Active",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your custom inventory items, purchases, and consumption logs are preserved locally on your phone. Non-destructive migrations and Android Auto-Backup protect your data across app updates.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                    }
                }

                if (!isImportMode) {
                    Text(
                        text = "You can also export a full JSON backup to save or transfer your data to another device at any time.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Button(
                        onClick = onExportBackup,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Full JSON Backup")
                    }

                    OutlinedButton(
                        onClick = { isImportMode = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import / Restore Data")
                    }
                } else {
                    Text(
                        text = "Paste a valid PantryBurn JSON backup below to restore your items, purchase logs, and consumption history:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text("Paste JSON backup code here...", fontSize = 12.sp) },
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 10
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { isImportMode = false }) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (importText.isNotBlank()) {
                                    onImportBackup(importText)
                                }
                            },
                            enabled = importText.isNotBlank(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Restore Now")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isImportMode) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
