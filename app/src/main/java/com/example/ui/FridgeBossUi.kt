package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Ingredient
import kotlinx.coroutines.launch
import com.example.data.model.Recipe
import com.example.ui.theme.ExpirySoon
import com.example.ui.theme.ExpiryUrgent
import com.example.ui.theme.ExpirySafe

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeBossApp(viewModel: FridgeViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("fridge") }

    // Observers
    val ingredients by viewModel.ingredientsInFridge.collectAsStateWithLifecycle()
    val consumedList by viewModel.consumedIngredients.collectAsStateWithLifecycle()
    val wastedList by viewModel.wastedIngredients.collectAsStateWithLifecycle()
    val savedRecipesList by viewModel.savedRecipes.collectAsStateWithLifecycle()
    val stats by viewModel.statsState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiRecipeState.collectAsStateWithLifecycle()
    val inAppAlert by viewModel.inAppNotification.collectAsStateWithLifecycle()
    val isOcrScanning by viewModel.isOcrScanning.collectAsStateWithLifecycle()

    // Form Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var showCameraScanner by remember { mutableStateOf(false) }
    var isSpeedDialExpanded by remember { mutableStateOf(false) }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val contentResolver = context.contentResolver
                val bitmap = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    }
                } catch (t: Throwable) {
                    Log.e("PhotoPicker", "Failed to load image", t)
                    null
                }
                if (bitmap != null) {
                    viewModel.processCapturedReceiptBitmap(bitmap)
                }
            }
        }
    }

    // Theme references
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerBg = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(com.example.ui.theme.HighDensityPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "FridgeBoss",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.5).sp,
                        color = com.example.ui.theme.HighDensityText
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.addSampleIngredients() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(com.example.ui.theme.HighDensitySecondary.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Cargar Datos",
                            tint = com.example.ui.theme.HighDensityPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.resetAllData() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(com.example.ui.theme.ExpiryUrgentBg)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Resetear Todo",
                            tint = com.example.ui.theme.ExpiryUrgent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Elegant M3 Style Navigation Dock with top border
            Column {
                Divider(color = com.example.ui.theme.HighDensityBorder, thickness = 1.dp)
                NavigationBar(
                    containerColor = com.example.ui.theme.HighDensitySurfaceVariant,
                    tonalElevation = 0.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = currentTab == "fridge",
                        onClick = { currentTab = "fridge" },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Nevera") },
                        label = { Text("Nevera", fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.example.ui.theme.HighDensityPrimary,
                            selectedTextColor = com.example.ui.theme.HighDensityPrimary,
                            indicatorColor = com.example.ui.theme.HighDensitySecondary,
                            unselectedTextColor = Color(0xFF49454F),
                            unselectedIconColor = Color(0xFF49454F)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == "alerts",
                        onClick = { currentTab = "alerts" },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = "Alertas") },
                        label = { Text("Alertas") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.example.ui.theme.HighDensityPrimary,
                            selectedTextColor = com.example.ui.theme.HighDensityPrimary,
                            indicatorColor = com.example.ui.theme.HighDensitySecondary,
                            unselectedTextColor = Color(0xFF49454F),
                            unselectedIconColor = Color(0xFF49454F)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == "chef",
                        onClick = { currentTab = "chef" },
                        icon = { Icon(Icons.Default.Star, contentDescription = "Chef IA") },
                        label = { Text("Chef IA") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.example.ui.theme.HighDensityPrimary,
                            selectedTextColor = com.example.ui.theme.HighDensityPrimary,
                            indicatorColor = com.example.ui.theme.HighDensitySecondary,
                            unselectedTextColor = Color(0xFF49454F),
                            unselectedIconColor = Color(0xFF49454F)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == "stats",
                        onClick = { currentTab = "stats" },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Score") },
                        label = { Text("Rescate") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.example.ui.theme.HighDensityPrimary,
                            selectedTextColor = com.example.ui.theme.HighDensityPrimary,
                            indicatorColor = com.example.ui.theme.HighDensitySecondary,
                            unselectedTextColor = Color(0xFF49454F),
                            unselectedIconColor = Color(0xFF49454F)
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == "fridge") {
                val rotationAngle by animateFloatAsState(
                    targetValue = if (isSpeedDialExpanded) 135f else 0f,
                    label = "rotation"
                )

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Speed Dial Mini Actions with gorgeous fan animation
                    AnimatedVisibility(
                        visible = isSpeedDialExpanded,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom) + slideInVertically(initialOffsetY = { it / 2 }),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom) + slideOutVertically(targetOffsetY = { it / 2 })
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Botón 3: Subir Recibo
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clickable {
                                        isSpeedDialExpanded = false
                                        photoPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.HighDensitySurfaceVariant),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "Subir Recibo",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = com.example.ui.theme.HighDensityText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                SmallFloatingActionButton(
                                    onClick = {
                                        isSpeedDialExpanded = false
                                        photoPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    containerColor = com.example.ui.theme.HighDensitySecondary,
                                    contentColor = com.example.ui.theme.HighDensityPrimary,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Subir Recibo",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Botón 2: Escanear Recibo
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clickable {
                                        isSpeedDialExpanded = false
                                        showCameraScanner = true
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.HighDensitySurfaceVariant),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "Escanear Recibo",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = com.example.ui.theme.HighDensityText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                SmallFloatingActionButton(
                                    onClick = {
                                        isSpeedDialExpanded = false
                                        showCameraScanner = true
                                    },
                                    containerColor = com.example.ui.theme.HighDensitySecondary,
                                    contentColor = com.example.ui.theme.HighDensityPrimary,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Escanear Recibo",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Botón 1: Crear Nuevo
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clickable {
                                        isSpeedDialExpanded = false
                                        showAddDialog = true
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.HighDensitySurfaceVariant),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "Crear Nuevo",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = com.example.ui.theme.HighDensityText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                SmallFloatingActionButton(
                                    onClick = {
                                        isSpeedDialExpanded = false
                                        showAddDialog = true
                                    },
                                    containerColor = com.example.ui.theme.HighDensitySecondary,
                                    contentColor = com.example.ui.theme.HighDensityPrimary,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Crear Nuevo",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Main Expandable FAB
                    FloatingActionButton(
                        onClick = { isSpeedDialExpanded = !isSpeedDialExpanded },
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir comida o escanear ticket",
                            modifier = Modifier
                                .size(28.dp)
                                .rotate(rotationAngle)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(containerBg)
        ) {
            // Screen switching based on Tab state
            Crossfade(targetState = currentTab, label = "TabTransition") { tab ->
                when (tab) {
                    "fridge" -> FridgeScreen(
                        ingredients = ingredients,
                        isOcrScanning = isOcrScanning,
                        onConsume = { viewModel.markAsConsumed(it) },
                        onWaste = { viewModel.markAsWasted(it) },
                        onDelete = { viewModel.deleteIngredient(it) },
                        viewModel = viewModel
                    )
                    "alerts" -> AlertsScreen(
                        ingredients = ingredients,
                        viewModel = viewModel,
                        onTriggerMockAlert = {
                            viewModel.triggerInAppNotification("🚨 Alerta Simulada: ¡El queso y el pescado están a punto de vencer hoy!")
                        }
                    )
                    "chef" -> ChefScreen(
                        ingredients = ingredients,
                        aiState = aiState,
                        savedRecipes = savedRecipesList,
                        onGenerateRecipe = { viewModel.generateAiRecipeForExpiringFoods() },
                        onGenerateOffline = { viewModel.generateOfflineRecipeFallbackInstance() },
                        onSaveRecipe = { title, ingreds, instructions ->
                            viewModel.saveGeneratedRecipe(title, ingreds, instructions)
                        },
                        onDeleteSavedRecipe = { viewModel.deleteSavedRecipe(it) },
                        onConsumeActive = { viewModel.consumeActiveRecipeIngredients() }
                    )
                    "stats" -> StatsScreen(
                        stats = stats,
                        consumedList = consumedList,
                        wastedList = wastedList,
                        savedRecipesList = savedRecipesList,
                        onDeleteSavedRecipe = { viewModel.deleteSavedRecipe(it) }
                    )
                }
            }

            if (showCameraScanner) {
                CameraXViewfinder(
                    onDismiss = { showCameraScanner = false },
                    onImageCaptured = { bitmap ->
                        showCameraScanner = false
                        viewModel.processCapturedReceiptBitmap(bitmap)
                    }
                )
            }

            // Interactive in-app push notification simulator bar
            inAppAlert?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, primaryColor, RoundedCornerShape(12.dp))
                        .clickable { viewModel.clearNotification() }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notificación",
                            tint = primaryColor,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Móvil Notificación Simulada",
                                color = primaryColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                message,
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearNotification() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.LightGray)
                        }
                    }
                }
            }
        }
    }

    // Modal dialog to add a new food ingredient safely
    if (showAddDialog) {
        val categories = listOf("Verduras", "Lácteos", "Carnes", "Frutas", "Otros")
        var name by remember { mutableStateOf("") }
        var quantity by remember { mutableStateOf("1") }
        var unit by remember { mutableStateOf("unidades") }
        var category by remember { mutableStateOf("Verduras") }
        var expiryDays by remember { mutableStateOf(4f) }
        var price by remember { mutableStateOf("2.50") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Box {
                    IconButton(
                        onClick = { showAddDialog = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar")
                    }

                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Añadir a la Nevera",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(bottom = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Outlined Filled inputs
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nombre del alimento") },
                            placeholder = { Text("Ej. Salmón, Brócoli...") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            singleLine = true
                        )

                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            OutlinedTextField(
                                value = quantity,
                                onValueChange = { quantity = it },
                                label = { Text("Cantidad") },
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = unit,
                                onValueChange = { unit = it },
                                label = { Text("Unidad (g, L, un...)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        // Category Dropdown / Choice selector styled nicely
                        Text("Categoría", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), fontSize = 14.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = category == cat
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) primaryColor else Color.LightGray.copy(alpha = 0.2f))
                                        .clickable { category = cat }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Expiry days slider to eliminate typos
                        Text(
                            text = "Caduca en: ${expiryDays.toInt()} días",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 14.sp,
                            color = if (expiryDays <= 2) ExpiryUrgent else MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = expiryDays,
                            onValueChange = { expiryDays = it },
                            valueRange = -1f..15f,
                            steps = 17,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("Precio Estimado (€)") },
                            placeholder = { Text("2.50") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    viewModel.addIngredient(
                                        name = name,
                                        quantity = quantity.toDoubleOrNull() ?: 1.0,
                                        unit = unit,
                                        category = category,
                                        expiryDays = expiryDays.toInt(),
                                        price = price.toDoubleOrNull() ?: 1.0
                                    )
                                    showAddDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Text("Guardar Alimento", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

fun getCategoryEmoji(category: String, name: String): String {
    val lower = name.lowercase()
    return when {
        lower.contains("leche") || lower.contains("milk") || lower.contains("queso") || lower.contains("cheese") || lower.contains("yogur") -> "🥛"
        lower.contains("carne") || lower.contains("beef") || lower.contains("pollo") || lower.contains("chicken") || lower.contains("steak") || lower.contains("ternera") -> "🥩"
        lower.contains("pescado") || lower.contains("salmon") || lower.contains("fish") -> "🐟"
        lower.contains("manzana") || lower.contains("apple") || lower.contains("platano") || lower.contains("banana") || lower.contains("fruta") -> "🍎"
        lower.contains("brocoli") || lower.contains("verdura") || lower.contains("salad") || lower.contains("lechuga") || lower.contains("zanahoria") -> "🥦"
        else -> when (category.lowercase()) {
            "lacteos", "lácteos" -> "🥛"
            "carnes" -> "🥩"
            "frutas" -> "🍎"
            "verduras" -> "🥦"
            else -> "🥫"
        }
    }
}

@Composable
fun StreakHeroBanner(stats: Stats) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.HighDensitySecondary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RACHA CERO DESPERDICIO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF21005D).copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${stats.currentStreak} Días",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF21005D)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${String.format("%.2f", stats.moneySaved)} € salvados este mes",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = Color(0xFF21005D).copy(alpha = 0.8f)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp)
            ) {
                CircularProgressIndicator(
                    progress = { 0.75f },
                    modifier = Modifier.size(64.dp),
                    color = com.example.ui.theme.HighDensityPrimary,
                    strokeWidth = 6.dp,
                    trackColor = com.example.ui.theme.HighDensityTertiary,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFF21005D),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// -------------------- SCREEN 1: NEVERA & INVENTARIO --------------------

@Composable
fun FridgeScreen(
    ingredients: List<Ingredient>,
    isOcrScanning: Boolean,
    onConsume: (Ingredient) -> Unit,
    onWaste: (Ingredient) -> Unit,
    onDelete: (Ingredient) -> Unit,
    viewModel: FridgeViewModel
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val stats by viewModel.statsState.collectAsStateWithLifecycle()

    // Animated Scan Radar Line if active
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseTranslateY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseRadar"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Streak Game Banner (Material You high density header)
        StreakHeroBanner(stats = stats)

        // Pulse camera OCR simulation state
        if (isOcrScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                // Green laser scanning visual overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = (100 * pulseTranslateY).dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color.Green, Color.Transparent)
                            )
                        )
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📸 Escaneando Ticket de Compra...", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("FridgeBoss OCR leyendo precios, cantidades y fechas...", color = Color.LightGray, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Alimentos en Despensa (${ingredients.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (ingredients.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Empty",
                        tint = Color.LightGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "¡Tu nevera está vacía!",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Presiona '+' o escanea un ticket.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ingredients) { ingredient ->
                    IngredientItemCard(
                        ingredient = ingredient,
                        onConsume = { onConsume(ingredient) },
                        onWaste = { onWaste(ingredient) },
                        onDelete = { onDelete(ingredient) },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun IngredientItemCard(
    ingredient: Ingredient,
    onConsume: () -> Unit,
    onWaste: () -> Unit,
    onDelete: () -> Unit,
    viewModel: FridgeViewModel
) {
    val expiryDetails = viewModel.getExpiryLabelAndColor(ingredient.expiryTimestamp)
    val colorHex = expiryDetails.second

    val alertColor = when (colorHex) {
        "RED" -> ExpiryUrgent
        "ORANGE" -> ExpirySoon
        else -> ExpirySafe
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Category Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(com.example.ui.theme.HighDensityPrimary.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        ingredient.category.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.HighDensityPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${getCategoryEmoji(ingredient.category, ingredient.name)}  ${ingredient.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = com.example.ui.theme.HighDensityText
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    "${ingredient.quantity} ${ingredient.unit} | ${String.format("%.2f", ingredient.purchasePrice)} €",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Expiry Indicator Tag
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(alertColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        expiryDetails.first,
                        color = alertColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Rescue Action Buttons (Quick UI tags)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onConsume,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = ExpirySafe.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Consumir Alimento",
                        tint = ExpirySafe
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onWaste,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = ExpiryUrgent.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Desechar Alimento",
                        tint = ExpiryUrgent
                    )
                }
            }
        }
    }
}

// -------------------- SCREEN 2: ALERTAS DE CADUCIDAD --------------------

@Composable
fun AlertsScreen(
    ingredients: List<Ingredient>,
    viewModel: FridgeViewModel,
    onTriggerMockAlert: () -> Unit
) {
    val expiringSoonItems = ingredients.filter { viewModel.getDaysUntilExpiry(it.expiryTimestamp) <= 3 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Alertas de Desperdicio en Tiempo Real",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "FridgeBoss escanea en segundo plano los alimentos que van a vencer en 3 días o menos para avisarte.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Button(
                    onClick = onTriggerMockAlert,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = "Simulate")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Probar Alerta Dinámica (Simular Notificación)", fontSize = 12.sp)
                }
            }
        }

        Text(
            "Alimentos por caducar pronto (${expiringSoonItems.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.onBackground
        )

        if (expiringSoonItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Todo fresco",
                        tint = ExpirySafe,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "¡Alerta Limpia! Cero Alimentos por Caducar",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text("¡Tu nevera está perfectamente organizada!", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            val chunkedItems = expiringSoonItems.chunked(2)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(chunkedItems) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { ingredient ->
                            val daysLeft = viewModel.getDaysUntilExpiry(ingredient.expiryTimestamp)
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = getCategoryEmoji(ingredient.category, ingredient.name),
                                            fontSize = 24.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (daysLeft <= 0) com.example.ui.theme.ExpiryUrgentBg 
                                                    else Color.White
                                                )
                                                .border(
                                                    1.dp, 
                                                    if (daysLeft <= 0) com.example.ui.theme.ExpiryUrgent 
                                                    else com.example.ui.theme.HighDensityBorder, 
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (daysLeft <= 0) "HOY" else "${daysLeft}d rest",
                                                color = if (daysLeft <= 0) com.example.ui.theme.ExpiryUpcomingText else com.example.ui.theme.ExpiryUrgent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = ingredient.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = com.example.ui.theme.HighDensityText
                                    )
                                    
                                    Spacer(modifier = Modifier.height(2.dp))
                                    
                                    Text(
                                        text = "Cant: ${ingredient.quantity} ${ingredient.unit}",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (rowItems.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// -------------------- SCREEN 3: MOTOR RECEPTAS IA --------------------

@Composable
fun RecipeIllustration(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Recetario Inteligente: $title",
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun ChefScreen(
    ingredients: List<Ingredient>,
    aiState: AiRecipeState,
    savedRecipes: List<Recipe>,
    onGenerateRecipe: () -> Unit,
    onGenerateOffline: () -> Unit,
    onSaveRecipe: (String, String, String) -> Unit,
    onDeleteSavedRecipe: (Recipe) -> Unit,
    onConsumeActive: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var isSavingDone by remember { mutableStateOf(false) }

    val criticalIngredients = remember(ingredients) {
        ingredients.filter { ingredient ->
            val diff = ingredient.expiryTimestamp - System.currentTimeMillis()
            val days = (diff / (1000 * 60 * 60 * 24)).toInt()
            val daysUntilExpiry = if (days < 0 && Math.abs(diff) < (1000 * 60 * 60 * 2)) 0 else days
            daysUntilExpiry <= 3
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Expiry warning friend message banner
        if (criticalIngredients.isNotEmpty()) {
            val firstCritical = criticalIngredients.first()
            val extraCount = criticalIngredients.size - 1
            val alertMessage = if (extraCount > 0) {
                "¡Oye! Veo que tu '${firstCritical.name}' (y otros $extraCount productos) se van a vencer pronto. ¿Cocinamos algo?"
            } else {
                "¡Oye! Veo que tu '${firstCritical.name}' se va a vencer pronto. ¿Cocinamos algo?"
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alerta de Vencimiento",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "¡Alerta Desperdicio Cero!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = alertMessage,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.HighDensityBorder),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.HighDensitySurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Generador de Recetas con IA",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = com.example.ui.theme.HighDensityText
                )
                Text(
                    "Nuestro chef inteligente analiza únicamente los ingredientes que están por caducar en tu inventario para sugerirte comidas ricas sin que compres nada extra.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Render Action Button based on fridge empty checks
                if (ingredients.isEmpty()) {
                    Text(
                        "⚠️ Agrega alimentos a tu nevera antes de pedirle la receta al Chef.",
                        color = ExpiryUrgent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onGenerateRecipe,
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Generar Receta IA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = onGenerateOffline,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Chef Local (Offline)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Display AI State
        when (aiState) {
            is AiRecipeState.Idle -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Chef",
                            tint = Color.LightGray,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Chef FridgeBoss esperando órdenes",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "Presiona un botón arriba para cocinar con IA.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            is AiRecipeState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = primaryColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("El chef está creando la comida desperdicio cero perfecta...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Consultando Gemini API en vivo...", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            is AiRecipeState.Success -> {
                isSavingDone = false
                val text = aiState.recipeMarkdown
                val title = text.lines().firstOrNull { it.contains("**Título") }?.replace("**Título de la Receta**", "")?.replace("**", "")?.trim()
                    ?: text.lines().firstOrNull { it.isNotBlank() }?.replace("**", "") ?: "Calentao Colombiano Inteligente"

                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.HighDensityTertiary),
                    colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.HighDensitySurfaceVariant)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Header inside the recipe card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.5f))
                                .border(width = 0.dp, color = Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = com.example.ui.theme.HighDensityPrimary,
                                    modifier = Modifier.size(20.dp).padding(end = 4.dp)
                                )
                                Column {
                                    Text(
                                        text = "RECOMENDADO AHORA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = com.example.ui.theme.HighDensityPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = com.example.ui.theme.HighDensityText
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    onSaveRecipe(title, "Ingredientes de Nevera", text)
                                    isSavingDone = true
                                },
                                enabled = !isSavingDone
                            ) {
                                Icon(
                                    imageVector = if (isSavingDone) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Guardar",
                                    tint = ExpiryUrgent
                                )
                            }
                        }

                        // Divider between header and instructions
                        Divider(color = com.example.ui.theme.HighDensityTertiary, thickness = 1.dp)

                        // Main Content with image, tags, and markdown text
                        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                            // High fidelity recipe image illustration
                            RecipeIllustration(title = title)

                            Spacer(modifier = Modifier.height(10.dp))

                            // Dynamic Quick Bullet tags
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Receta Chef IA", "Sabor Casero", "Eco-Salva").forEach { tag ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(Color.White)
                                            .border(1.dp, com.example.ui.theme.HighDensityTertiary, RoundedCornerShape(100.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(com.example.ui.theme.HighDensityPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(tag, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.HighDensityText)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                item {
                                    Text(
                                        text = text,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = com.example.ui.theme.HighDensityText
                                    )
                                }
                            }

                            // ¡Manos a la obra! button
                            Button(
                                onClick = onConsumeActive,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("¡Manos a la obra!", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            is AiRecipeState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Aviso de API Key de Gemini",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = aiState.message,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        if (aiState.isKeyMissing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.05f))
                                    .padding(12.dp)
                             ) {
                                Text(
                                    text = "💡 COPIADO RÁPIDO PAR EL SECRETS PANEL:\nIntroduce el secreto GEMINI_API_KEY en la barra lateral izquierda de Google AI Studio y el chef funcionará 100% en vivo.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onGenerateOffline,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Probar con Chef Local (Generador Offline)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// -------------------- SCREEN 4: RESCATES & GAMIFICATION --------------------

@Composable
fun StatsScreen(
    stats: Stats,
    consumedList: List<Ingredient>,
    wastedList: List<Ingredient>,
    savedRecipesList: List<Recipe>,
    onDeleteSavedRecipe: (Recipe) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Streak Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Fuego",
                        tint = Color.Yellow,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        "Racha Cero Desperdicio",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Text(
                        "${stats.currentStreak} Días Consecutivos",
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 28.sp
                    )
                    Text(
                        "Rescatando alimentos y protegiendo tu bolsillo.",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Live stats numerical indicators grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        title = "Ahorrado (€)",
                        value = "${String.format("%.2f", stats.moneySaved)} €",
                        description = "Dinero rescatado del desperdicio",
                        color = ExpirySafe,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "CO₂ Evitado",
                        value = "${String.format("%.1f", stats.carbonSaved)} kg",
                        description = "Huella de carbono compensada",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        title = "% Rescate",
                        value = "${stats.rescueRate} %",
                        description = "Éxito de consumo saludable",
                        color = ExpirySoon,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Comida Perdida",
                        value = "${String.format("%.2f", stats.moneyWasted)} €",
                        description = "Valor arrojado a la basura",
                        color = ExpiryUrgent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Achievements / Unlocked Badges Section
        item {
            Text(
                "Tus Insignias FridgeBoss",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    BadgeRow(
                        title = "Héroe del Brócoli",
                        desc = "Salvaste 2 o más verduras de vencer.",
                        isUnlocked = consumedList.filter { it.category == "Verduras" }.size >= 2
                    )
                    Divider(modifier = Modifier.padding(vertical = 10.dp))
                    BadgeRow(
                        title = "Cero Desperdicio Real",
                        desc = "Mantén una tasa de rescate superior al 80%.",
                        isUnlocked = stats.rescueRate >= 80 && (consumedList.isNotEmpty() || wastedList.isNotEmpty())
                    )
                    Divider(modifier = Modifier.padding(vertical = 10.dp))
                    BadgeRow(
                        title = "Eco Inversor",
                        desc = "Rescata más de 10 € en comida en un solo mes.",
                        isUnlocked = stats.moneySaved >= 10.0
                    )
                }
            }
        }

        // Saved Offline Recipes
        item {
            Text(
                "Tus Recetas Guardadas (${savedRecipesList.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (savedRecipesList.isEmpty()) {
            item {
                Text(
                    "No has guardado recetas aún. ¡Presiona el corazón en la pestaña Chef IA!",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            }
        } else {
            items(savedRecipesList) { recipe ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                recipe.title,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDeleteSavedRecipe(recipe) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ExpiryUrgent)
                            }
                        }
                        Text(
                            recipe.instructions,
                            fontSize = 12.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    description: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, fontSize = 10.sp, color = Color.Gray, lineHeight = 12.sp)
        }
    }
}

@Composable
fun BadgeRow(
    title: String,
    desc: String,
    isUnlocked: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isUnlocked) Color.Yellow.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isUnlocked) Icons.Default.Star else Icons.Default.Lock,
                contentDescription = null,
                tint = if (isUnlocked) Color(0xFFEAB308) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else Color.Gray,
                fontSize = 14.sp
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = Color.Gray
            )
            if (isUnlocked) {
                Text(
                    "¡DESBLOQUEADO! 🎉",
                    fontSize = 10.sp,
                    color = ExpirySafe,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// -------------------- CAMERAX & REAL MACHINE CAMERA FLOW --------------------

fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        val planeProxy = image.planes[0]
        val buffer = planeProxy.buffer
        buffer.rewind() // Ensure the buffer is fully read from the starting position
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (t: Throwable) {
        Log.e("imageProxy", "Conversión de imagen fallida con error/excepción", t)
        null
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraXViewfinder(
    onDismiss: () -> Unit,
    onImageCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Escáner Inteligente CameraX",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = com.example.ui.theme.HighDensityText
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (permissionState.status.isGranted) {
                    // Preview view configuration using process camera provider
                    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
                    val previewView = remember { PreviewView(context) }
                    val imageCapture = remember { ImageCapture.Builder().build() }
                    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                    var cameraProviderRef: ProcessCameraProvider? by remember { mutableStateOf(null) }

                    LaunchedEffect(lifecycleOwner) {
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                cameraProviderRef = cameraProvider
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                            } catch (t: Throwable) {
                                Log.e("CameraX", "Binding failed o no soportado en emulador", t)
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }

                    DisposableEffect(Unit) {
                        onDispose {
                            try {
                                cameraExecutor.shutdown()
                            } catch (t: Throwable) {
                                Log.e("CameraX", "Executor shutdown failed", t)
                            }
                            try {
                                cameraProviderRef?.unbindAll()
                            } catch (t: Throwable) {
                                Log.e("CameraX", "Unbind failed at onDispose", t)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Target framing guide overlay representing receipt focus guides
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .border(2.dp, com.example.ui.theme.HighDensityTertiary, RoundedCornerShape(12.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Capture button
                    Button(
                        onClick = {
                            try {
                                imageCapture.takePicture(
                                    cameraExecutor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val bitmap = imageProxyToBitmap(image)
                                            image.close()
                                            if (bitmap != null) {
                                                ContextCompat.getMainExecutor(context).execute {
                                                    onImageCaptured(bitmap)
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e("CameraX", "Error capturing ticket logo", exception)
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("CameraX", "takePicture call failed", e)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.HighDensityPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Capture")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Capturar Recibo", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val conf = Bitmap.Config.ARGB_8888
                            val simulatedBitmap = Bitmap.createBitmap(400, 400, conf)
                            onImageCaptured(simulatedBitmap)
                        }
                    ) {
                        Text(
                            text = "Simular con Ticket de Prueba (Para Emuladores)",
                            color = com.example.ui.theme.HighDensityPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Explain and request permission
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(com.example.ui.theme.ExpiryUrgentBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = com.example.ui.theme.ExpiryUrgent,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Acceso a la Cámara Requerido",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = com.example.ui.theme.HighDensityText,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "El escáner de FridgeBoss necesita permisos de la cámara para registrar compras, tickets de súper o alimentos en un solo toque.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { permissionState.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.HighDensityPrimary)
                        ) {
                            Text("Permitir Acceso", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
