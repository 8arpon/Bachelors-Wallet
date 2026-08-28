package com.example.myapplication

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class EmojiGroup(val name: String, val emojis: List<String>)

val INBUILT_EMOJI_GROUPS = listOf(
    EmojiGroup("Food & Dining", listOf("🍔", "☕", "🍕", "🍜", "🥪", "🍱", "🍲", "🍎", "🥤", "🍦", "🥐", "🍩", "🍗", "🥑", "🥘", "🍫")),
    EmojiGroup("Transport", listOf("🚌", "🚗", "🚲", "🛵", "🚕", "🚆", "✈️", "⛽", "🚶", "🛴", "🛳️")),
    EmojiGroup("Shopping", listOf("🛍️", "👕", "👟", "💄", "🎁", "🎮", "🎬", "📱", "💻", "🕶️", "💍", "🎧")),
    EmojiGroup("Home & Bills", listOf("🧾", "💡", "💧", "📶", "🏠", "🔑", "🛡️", "🔧", "🛋️", "🧹", "⚡")),
    EmojiGroup("Health & Fitness", listOf("💊", "🏥", "🏋️", "🩺", "🏃", "🧘", "🍏", "🦷", "🩹")),
    EmojiGroup("Education & Work", listOf("📚", "🎓", "💼", "💰", "✏️", "🖊️", "🏢", "📊", "💻")),
    EmojiGroup("Fun & Others", listOf("🍿", "🎨", "🐾", "🎵", "🏖️", "⭐", "💸", "🏷️", "🎉", "✈️"))
)

val ALL_INBUILT_EMOJIS = INBUILT_EMOJI_GROUPS.flatMap { it.emojis }.distinct()

/**
 * 🌟 High-Performance Category Creation Dialog with Inbuilt Emoji Picker
 */
@Composable
fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onCategoryCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val customCategories by DataManager.getCategoriesFlow(context).collectAsState(initial = emptyList())
    var categoryName by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf<String?>("🏷️") }
    var selectedGroupIndex by remember { mutableIntStateOf(0) }

    val isDark = ThemeState.isDark.value
    val cardColor = ThemeState.cardBackground.value
    val primaryColor = ThemeState.primaryAccent.value
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Category",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Preview Tag
                val displayName = categoryName.trim().ifEmpty { "Category Name" }
                val fullPreview = if (selectedEmoji != null) "$selectedEmoji $displayName" else displayName

                Surface(
                    color = primaryColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Preview: ", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        Text(
                            text = fullPreview,
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Name Input Field
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { if (it.length <= 25) categoryName = it },
                    label = { Text("Category Title") },
                    placeholder = { Text("e.g. Gym, Coffee, Rent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = primaryColor,
                        focusedLabelColor = primaryColor
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Emoji Picker Title & "No Emoji" Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose Icon / Emoji",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )

                    // No Emoji Button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedEmoji == null) primaryColor else Color.Gray.copy(alpha = 0.12f),
                        modifier = Modifier.clickable { selectedEmoji = null }
                    ) {
                        Text(
                            text = "No Emoji",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedEmoji == null) Color.White else textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Emoji Group Selector Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(INBUILT_EMOJI_GROUPS.indices.toList()) { index ->
                        val group = INBUILT_EMOJI_GROUPS[index]
                        val isSelected = selectedGroupIndex == index
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, primaryColor) else BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f)),
                            modifier = Modifier.clickable { selectedGroupIndex = index }
                        ) {
                            Text(
                                text = group.name,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) primaryColor else Color.Gray,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Emoji Grid
                val currentGroupEmojis = INBUILT_EMOJI_GROUPS.getOrNull(selectedGroupIndex)?.emojis ?: ALL_INBUILT_EMOJIS
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F7))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(currentGroupEmojis) { emoji ->
                        val isSelected = selectedEmoji == emoji
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.25f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, primaryColor) else null,
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { selectedEmoji = emoji }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 18.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val cleanName = categoryName.trim()
                            if (cleanName.isBlank()) {
                                Toast.makeText(context, "Please enter category title", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val allExisting = (DataManager.DEFAULT_CATEGORIES + customCategories.map { it.name })
                            if (DataManager.isDuplicateCategoryName(allExisting, cleanName)) {
                                Toast.makeText(context, "Category '$cleanName' already exists!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val finalCategoryString = if (selectedEmoji != null) {
                                "$selectedEmoji $cleanName"
                            } else {
                                cleanName
                            }

                            DataManager.addCategory(context, finalCategoryString)
                            onCategoryCreated(finalCategoryString)
                            onDismiss()
                            Toast.makeText(context, "Category '$finalCategoryString' created!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1.3f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text("Save Category", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * 🌟 Category Management Bottom Sheet for Settings Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val customCategories by DataManager.getCategoriesFlow(context).collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }
    var disabledCategories by remember { mutableStateOf(DataManager.getDisabledCategories(context)) }
    var showEmojis by remember { mutableStateOf(DataManager.isShowEmojisEnabled(context)) }

    val isDark = ThemeState.isDark.value
    val cardColor = ThemeState.cardBackground.value
    val primaryColor = ThemeState.primaryAccent.value
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)

    val allCategories = remember(customCategories) {
        (DataManager.DEFAULT_CATEGORIES + customCategories.map { it.name }).distinct()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cardColor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
        ) {
            // Responsive Clean Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Category Manager",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Toggle active categories for Home & Budget",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }

                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Emoji Toggle Setting
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text("🏷️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Show Category Emojis", fontWeight = FontWeight.Bold, color = textColor, fontSize = 14.sp)
                            Text("Display emoji prefixes on categories", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = showEmojis,
                        onCheckedChange = { checked ->
                            showEmojis = checked
                            DataManager.setShowEmojisEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                // Section: Custom categories with delete option
                if (customCategories.isNotEmpty()) {
                    item {
                        Text(
                            text = "CUSTOM CATEGORIES (${customCategories.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    items(customCategories, key = { "custom_${it.name}" }) { cat ->
                        val isEnabled = !disabledCategories.contains(cat.name)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (showEmojis) cat.name else DataManager.stripEmoji(cat.name),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isEnabled) textColor else Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { categoryToDelete = cat.name },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFFF3B30).copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { checked ->
                                            DataManager.setCategoryEnabled(context, cat.name, checked)
                                            disabledCategories = DataManager.getDisabledCategories(context)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = primaryColor
                                        )
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Section: Built-in Categories
                item {
                    Text(
                        text = "ALL ACTIVE CATEGORIES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                items(DataManager.DEFAULT_CATEGORIES, key = { "default_$it" }) { catName ->
                    val isEnabled = !disabledCategories.contains(catName)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF242426) else Color(0xFFF7F7F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showEmojis) catName else DataManager.stripEmoji(catName),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isEnabled) textColor else Color.Gray
                            )

                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    DataManager.setCategoryEnabled(context, catName, checked)
                                    disabledCategories = DataManager.getDisabledCategories(context)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = primaryColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category", fontWeight = FontWeight.Bold, color = textColor) },
            text = { Text("Are you sure you want to delete '${categoryToDelete}'? Existing transactions will retain their names.", color = Color.Gray, fontSize = 14.sp) },
            containerColor = cardColor,
            confirmButton = {
                Button(
                    onClick = {
                        categoryToDelete?.let { DataManager.deleteCategory(context, it) }
                        categoryToDelete = null
                        Toast.makeText(context, "Category deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showCreateDialog) {
        CreateCategoryDialog(
            onDismiss = { showCreateDialog = false },
            onCategoryCreated = {}
        )
    }
}

/**
 * 🌟 All Categories Quick-Picker Bottom Sheet for "✨ More..." option
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllCategoriesPickerSheet(
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onCreateNewClicked: () -> Unit
) {
    val context = LocalContext.current
    val customCategories by DataManager.getCategoriesFlow(context).collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }

    val isDark = ThemeState.isDark.value
    val cardColor = ThemeState.cardBackground.value
    val primaryColor = ThemeState.primaryAccent.value
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)

    val allCategoriesList = remember(customCategories) {
        (DataManager.DEFAULT_CATEGORIES + customCategories.map { it.name }).distinct()
    }

    val filteredList = remember(searchQuery, allCategoriesList) {
        if (searchQuery.isBlank()) allCategoriesList
        else allCategoriesList.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cardColor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Choose Category",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = "Select any category for this expense",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                TextButton(onClick = {
                    onDismiss()
                    onCreateNewClicked()
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search category (e.g. Shopping, Rent)...", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedBorderColor = primaryColor
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                items(filteredList) { catName ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCategorySelected(catName)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = DataManager.formatCategoryDisplay(context, catName),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
