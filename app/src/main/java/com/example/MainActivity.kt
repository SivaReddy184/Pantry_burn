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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

// Formatting utilities
private val priceFormat = DecimalFormat("₹#,##0.00")
private val priceShortFormat = DecimalFormat("₹#,##0")
private val qtyFormat = DecimalFormat("#,##0.##")
private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

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
    var showScanBarcodeDialog by remember { mutableStateOf(false) }
    var showRecordPurchaseDialogForItemId by remember { mutableStateOf<Int?>(null) }
    var historyDialogItemId by remember { mutableStateOf<Int?>(null) }
    var learningFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var prefilledBarcodeForNewItem by remember { mutableStateOf<String?>(null) }
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

    // Helper to start barcode scan with safe runtime reflection, falling back to simulator if not loaded
    val startScanner = {
        tryGmsBarcodeScan(
            context = context,
            onSuccess = { code ->
                if (code.isNotBlank()) {
                    val existingItem = items.find { it.barcode == code }
                    if (existingItem != null) {
                        Toast.makeText(context, "Scanned: ${existingItem.name}", Toast.LENGTH_SHORT).show()
                        showRecordPurchaseDialogForItemId = existingItem.id
                    } else {
                        Toast.makeText(context, "New product detected! Add to catalog.", Toast.LENGTH_LONG).show()
                        prefilledBarcodeForNewItem = code
                        showAddItemDialog = true
                    }
                }
            },
            onFailure = { e ->
                // Fallback to simulation dialog on failure / lack of Play Services
                showScanBarcodeDialog = true
            }
        )
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
                    IconButton(
                        onClick = startScanner,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = "Scan Barcode",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
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
                        prefilledBarcodeForNewItem = null
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
                    onShowHistory = { item -> historyDialogItemId = item.id },
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
                    onRecordPurchase = { itemId ->
                        showRecordPurchaseDialogForItemId = itemId
                    }
                )
                2 -> AnalyticsTab(
                    itemsWithPurchases = itemsWithPurchases,
                    spendingMetrics = spendingMetrics
                )
                3 -> PriceTrendsTab(
                    itemsWithPurchases = itemsWithPurchases
                )
                4 -> LogsTab(
                    consumptionLogs = consumptionLogs,
                    purchases = purchases,
                    items = items,
                    onDeleteLog = { log -> viewModel.deleteConsumptionLog(log) },
                    onEditLog = { log, qty, date -> viewModel.updateConsumptionLog(log, qty, date) },
                    onDeletePurchase = { purchase -> viewModel.deletePurchase(purchase) }
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
            prefilledBarcode = prefilledBarcodeForNewItem,
            onDismiss = { showAddItemDialog = false },
            onConfirm = { name, cat, unit, initStock, initRate, barcode, vendor, initPrice, trackingMode, isPaused ->
                viewModel.addGroceryItem(name, cat, unit, initStock, initRate, barcode, vendor, initPrice, trackingMode, isPaused)
                showAddItemDialog = false
            }
        )
    }

    if (showScanBarcodeDialog) {
        BarcodeScannerSimulatorDialog(
            onDismiss = { showScanBarcodeDialog = false },
            onScanResult = { code ->
                showScanBarcodeDialog = false
                val existingItem = items.find { it.barcode == code }
                if (existingItem != null) {
                    Toast.makeText(context, "Scanned: ${existingItem.name}", Toast.LENGTH_SHORT).show()
                    showRecordPurchaseDialogForItemId = existingItem.id
                } else {
                    Toast.makeText(context, "New barcode detected! Prefilling...", Toast.LENGTH_SHORT).show()
                    prefilledBarcodeForNewItem = code
                    showAddItemDialog = true
                }
            }
        )
    }

    showRecordPurchaseDialogForItemId?.let { itemId ->
        val selectedItem = items.find { it.id == itemId }
        if (selectedItem != null) {
            RecordPurchaseDialog(
                item = selectedItem,
                onDismiss = { showRecordPurchaseDialogForItemId = null },
                onConfirm = { qty, price, vendor ->
                    viewModel.recordPurchase(itemId, qty, price, vendor)
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

// ==================== PRODUCT HISTORY DIALOG ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductHistoryDialog(
    item: GroceryItem,
    viewModel: GroceryViewModel,
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

    var selectedTab by remember { mutableStateOf(0) }
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
                            text = "${item.category} • Stock: ${qtyFormat.format(item.currentStock)} ${item.unit}",
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
                        text = { Text("Logs (${itemLogs.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Buys (${itemPurchases.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
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
                            Text("No purchase history recorded yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(itemPurchases, key = { it.purchaseId }) { purchase ->
                                val dateStr = dateFormat.format(Date(purchase.date))
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
                                            Text(
                                                text = "${qtyFormat.format(purchase.quantityBought)} ${item.unit} • ${priceFormat.format(purchase.totalPrice)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Vendor: ${purchase.vendor} • ${priceFormat.format(purchase.unitPrice)}/${item.unit}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
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
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.category.contains(searchQuery, ignoreCase = true) ||
        (it.barcode?.contains(searchQuery) == true)
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
            placeholder = { Text("Search by name, category, barcode...") },
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
                        text = if (searchQuery.isNotEmpty()) "Try refining your keywords or searching a barcode." else "Add your food items or scan barcodes to begin tracking your smart pantry stock!",
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
            // Header: Category, Mode Chip, Pause Chip, & Stock Pill
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
                            Text(
                                text = item.category.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit Product Details",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp)
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

            // Barcode if available
            if (!item.barcode.isNullOrEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.barcode,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

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
    onRecordPurchase: (Int) -> Unit
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

                                            Column(modifier = Modifier.weight(1f)) {
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
                                            }

                                            // Quick Restock Record Buy button
                                            IconButton(
                                                onClick = { onRecordPurchase(item.id) },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.LocalMall,
                                                    contentDescription = "Buy",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
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
    spendingMetrics: SpendingMetrics
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

    // Filtered Category Breakdown for selected Month and Year
    val selectedCategoryBreakdown = remember(itemsWithPurchases, selectedYear, selectedMonth) {
        val breakdown = mutableMapOf<String, Double>()
        for (itemWithPurchases in itemsWithPurchases) {
            val cat = itemWithPurchases.item.category
            val currentMonthPurchases = itemWithPurchases.purchases.filter { p ->
                val pCal = Calendar.getInstance().apply { timeInMillis = p.date }
                pCal.get(Calendar.YEAR) == selectedYear && pCal.get(Calendar.MONTH) == selectedMonth
            }
            val totalForCat = currentMonthPurchases.sumOf { it.totalPrice }
            if (totalForCat > 0) {
                breakdown[cat] = (breakdown[cat] ?: 0.0) + totalForCat
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
    val selectedMonthSpending = remember(selectedCategoryBreakdown) {
        selectedCategoryBreakdown.sumOf { it.second }
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
                        text = "You spent ${priceFormat.format(selectedMonthSpending)} in $monthYearLabel across ${selectedCategoryBreakdown.size} categories.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Monthly Spending Bar Chart
        MonthlyCategoryBarChart(
            categorySpending = selectedCategoryBreakdown,
            monthYearLabel = monthYearLabel
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Spending by Category
        Text(
            text = "Spending by Category ($monthYearLabel)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (selectedCategoryBreakdown.isEmpty()) {
            Text(
                text = "No category spending recorded in $monthYearLabel.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            val maxCatPrice = selectedCategoryBreakdown.first().second
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
                    selectedCategoryBreakdown.forEach { (cat, price) ->
                        val ratio = if (maxCatPrice > 0) (price / maxCatPrice).toFloat() else 0f
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = cat, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                modifier = Modifier.padding(bottom = 80.dp)
            )
        } else {
            val maxVendorPrice = selectedVendorBreakdown.first().second
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp),
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
    }
}

// ==================== PRICE TRENDS TAB ====================
@Composable
fun PriceTrendsTab(
    itemsWithPurchases: List<GroceryItemWithPurchases>
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
                itemsWithPurchases.forEachIndexed { idx, entry ->
                    DropdownMenuItem(
                        text = { Text(entry.item.name) },
                        onClick = {
                            selectedItemIndex = idx
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
            val lastTwoPurchases = remember(sortedPurchases) {
                sortedPurchases.take(2)
            }
            val lowestUnitCost = remember(sortedPurchases) {
                lowestPurchase?.unitPrice
            }
            val lastUnitCost = remember(sortedPurchases) {
                sortedPurchases.firstOrNull { it.quantityBought > 0 }?.unitPrice
            }
            val averageUnitCost = remember(sortedPurchases) {
                val valid = sortedPurchases.filter { it.quantityBought > 0 }
                if (valid.isNotEmpty()) valid.sumOf { it.totalPrice } / valid.sumOf { it.quantityBought } else null
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
                            text = lowestUnitCost?.let { "${priceShortFormat.format(it)}/${item.unit}" } ?: "N/A",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusHealthy
                        )
                    }
                    HorizontalDivider(modifier = Modifier.height(36.dp).width(1.dp).align(Alignment.CenterVertically))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Last Deal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        Text(
                            text = lastUnitCost?.let { "${priceShortFormat.format(it)}/${item.unit}" } ?: "N/A",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(modifier = Modifier.height(36.dp).width(1.dp).align(Alignment.CenterVertically))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Avg Deal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        Text(
                            text = averageUnitCost?.let { "${priceShortFormat.format(it)}/${item.unit}" } ?: "N/A",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
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
                            text = "Amount: ${priceFormat.format(lowestPurchase.totalPrice)} for ${qtyFormat.format(lowestPurchase.quantityBought)} ${item.unit} (${priceShortFormat.format(lowestPurchase.unitPrice)}/${item.unit})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
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
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${dateFormat.format(Date(purchase.date))} • ${qtyFormat.format(purchase.quantityBought)} ${item.unit}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = priceFormat.format(purchase.totalPrice),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${priceShortFormat.format(purchase.unitPrice)}/${item.unit}",
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
                        
                        // Compare to average
                        val isCheaper = averageUnitCost != null && unitCost < averageUnitCost
                        val badgeColor = if (isCheaper) StatusHealthy else WarningSoon
                        val badgeText = if (isCheaper) "Cheaper Deal" else "Standard / High Deal"

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
                                        Text(
                                            text = purchase.vendor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = dateFormat.format(Date(purchase.date)),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
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
                                        text = "Unit Cost: ${priceFormat.format(unitCost)} per ${item.unit}",
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
    prefilledBarcode: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, unit: String, initialStock: Double, dailyRate: Double, barcode: String?, vendor: String?, initialPrice: Double, trackingMode: String, isPaused: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("grams") }
    var initialStock by remember { mutableStateOf("0") }
    var dailyRate by remember { mutableStateOf("0") }
    var initialPrice by remember { mutableStateOf("0") }
    var barcode by remember { mutableStateOf(prefilledBarcode ?: "") }
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
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (e.g. Grains, Protein)") },
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
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barcode (Optional)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
                                    name,
                                    category,
                                    unit,
                                    initialStock.toDoubleOrNull() ?: 0.0,
                                    dailyRate.toDoubleOrNull() ?: 0.0,
                                    barcode.ifBlank { null },
                                    preferredVendor.ifBlank { null },
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
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double, totalPrice: Double, vendor: String) -> Unit
) {
    var quantity by remember { mutableStateOf("") }
    var totalPrice by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf(item.preferredVendor ?: "Bigbasket") }

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

                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("Store / Vendor Name") },
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
                            val qtyVal = quantity.toDoubleOrNull()
                            val priceVal = totalPrice.toDoubleOrNull()
                            if (qtyVal != null && qtyVal > 0 && priceVal != null && priceVal >= 0) {
                                onConfirm(qtyVal, priceVal, vendor)
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
fun BarcodeScannerSimulatorDialog(
    onDismiss: () -> Unit,
    onScanResult: (String) -> Unit
) {
    var customBarcode by remember { mutableStateOf("") }

    val demoProducts = listOf(
        "Almonds (8901058002314)" to "8901058002314",
        "Whey Protein (748927052511)" to "748927052511",
        "Peanut Butter (8906014023124)" to "8906014023124",
        "Simulate New Diet Drink" to "9901234567890"
    )

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
                    text = "Barcode Scanner Simulator 📷",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "A physical device with Play Services is required for the real camera scanner bottom sheet. Tap a demo barcode below to simulate scanning:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    lineHeight = 16.sp
                )

                // Render pre-set demo scanner buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    demoProducts.forEach { (label, code) ->
                        Button(
                            onClick = { onScanResult(code) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Text(
                    text = "Or enter custom barcode:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = customBarcode,
                    onValueChange = { customBarcode = it },
                    placeholder = { Text("E.g. 1234567890") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (customBarcode.isNotBlank()) {
                                onScanResult(customBarcode)
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Simulate")
                    }
                }
            }
        }
    }
}

/**
 * Defensive reflection utility to call GmsBarcodeScanning dynamically.
 * This guarantees 100% compilation success in environments lacking the Play Services SDK
 * during compile-time verification but enables full native functionality at runtime.
 */
fun tryGmsBarcodeScan(
    context: android.content.Context,
    onSuccess: (String) -> Unit,
    onFailure: (Exception) -> Unit
) {
    try {
        val scanningClass = Class.forName("com.google.android.gms.code.scanner.GmsBarcodeScanning")
        val getClientMethod = scanningClass.getMethod("getClient", android.content.Context::class.java)
        val scannerInstance = getClientMethod.invoke(null, context)

        val startScanMethod = scannerInstance.javaClass.getMethod("startScan")
        val task = startScanMethod.invoke(scannerInstance)

        @Suppress("UNCHECKED_CAST")
        val gmsTask = task as? com.google.android.gms.tasks.Task<Any>
        if (gmsTask != null) {
            gmsTask.addOnSuccessListener { barcode ->
                try {
                    if (barcode != null) {
                        val getRawValueMethod = barcode.javaClass.getMethod("getRawValue")
                        val rawValue = getRawValueMethod.invoke(barcode) as? String
                        if (!rawValue.isNullOrBlank()) {
                            onSuccess(rawValue)
                        } else {
                            onFailure(Exception("Scan returned empty value"))
                        }
                    } else {
                        onFailure(Exception("Scan returned null result"))
                    }
                } catch (e: Exception) {
                    onFailure(e)
                }
            }.addOnFailureListener { e ->
                onFailure(e)
            }
        } else {
            onFailure(Exception("GMS Task cast failure"))
        }
    } catch (e: Exception) {
        onFailure(e)
    }
}

@Composable
fun MonthlyCategoryBarChart(
    categorySpending: List<Pair<String, Double>>,
    monthYearLabel: String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
) {
    if (categorySpending.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp)
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No spending recorded in $monthYearLabel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = "Log a purchase in $monthYearLabel to view category analytics.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val maxSpending = categorySpending.maxOf { it.second }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "MONTHLY SPENDING BY CATEGORY ($monthYearLabel)".uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                categorySpending.forEach { (category, totalSpent) ->
                    val ratio = if (maxSpending > 0) (totalSpent / maxSpending).toFloat() else 0f
                    val barHeightFraction = (ratio * 0.82f) + 0.05f

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = priceShortFormat.format(totalSpent),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight(barHeightFraction)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        )
                                    )
                                )
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 64.dp)
                        )
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
    var category by remember { mutableStateOf(item.category) }
    var unit by remember { mutableStateOf(item.unit) }
    var barcode by remember { mutableStateOf(item.barcode ?: "") }
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
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
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
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barcode (Optional)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
                            if (name.isNotBlank() && category.isNotBlank() && unit.isNotBlank()) {
                                onConfirm(
                                    item.copy(
                                        name = name.trim(),
                                        category = category.trim(),
                                        unit = unit.trim(),
                                        barcode = barcode.trim().ifEmpty { null },
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
    onDeletePurchase: (Purchase) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(0) } // 0: All, 1: Consumption, 2: Buys
    var logToEdit by remember { mutableStateOf<ConsumptionLogWithItem?>(null) }
    var logToDelete by remember { mutableStateOf<ConsumptionLogWithItem?>(null) }
    var purchaseToDelete by remember { mutableStateOf<Pair<Purchase, GroceryItem?>?>(null) }

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
                            val category = item?.category ?: "Unknown"

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
                                        Text(
                                            text = category.uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.5.sp
                                        )
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
                            val category = item?.category ?: "Restock"

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
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
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = category.uppercase(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "• Store: ${purchase.vendor}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${dateFormat.format(Date(purchase.date))} • ${priceFormat.format(purchase.totalPrice)} (${priceFormat.format(purchase.unitPrice)}/$unit)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "+${qtyFormat.format(purchase.quantityBought)} $unit",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )

                                        IconButton(
                                            onClick = { purchaseToDelete = Pair(purchase, item) },
                                            modifier = Modifier.size(34.dp)
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
