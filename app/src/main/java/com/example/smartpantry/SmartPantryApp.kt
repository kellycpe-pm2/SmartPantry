package com.example.smartpantry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics


private val PageWhite: Color
    @Composable get() = MaterialTheme.colorScheme.background

private val DeepTeal: Color
    @Composable get() = MaterialTheme.colorScheme.primary

private val Ink: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground

private val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

private val SelectedCyan: Color
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer

private enum class MainTab(val label: String, val iconPath: String) {
    HOME("Home", "M3 10 L12 3 L21 10 M5 9 V21 H10 V15 H14 V21 H19 V9"),
    PANTRY("Pantry", "M7 3 H17 V6 H7 Z M8 6 L5 10 V19 Q5 21 7 21 H17 Q19 21 19 19 V10 L16 6 M5 12 H19"),
    RECIPES("Recipes", "M7 14 C2 14 2 7 7 7 C8 2 16 2 17 7 C22 7 22 14 17 14 V21 H7 Z M7 17 H17"),
    LOG("Log", "M5 20 V14 M10 20 V10 M15 20 V6 M20 20 V3"),
    PROFILE("Profile", "M16 8 A4 4 0 1 1 8 8 A4 4 0 1 1 16 8 M4 21 C4 13 20 13 20 21")
}

@Composable
fun SmartPantryApp() {
    var showSplash by rememberSaveable { mutableStateOf(true) }

    if (showSplash) {
        LaunchedEffect(Unit) {
            delay(800)
            showSplash = false
        }
        SplashScreen()
    } else {
        MainScreen()
    }
}

@Composable
private fun MainScreen() {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }

    // 记住 Shopping List 有没有打开。关闭后会回到原来的 Tab。
    var showShoppingList by rememberSaveable { mutableStateOf(false) }

// 手机的返回键和页面上的 Back 按钮执行同一个动作。
    BackHandler(enabled = showShoppingList) {
        showShoppingList = false
    }

    if (showShoppingList) {
        ShoppingListScreen(onBack = { showShoppingList = false })
        return
    }

    Scaffold(
        containerColor = PageWhite,
        bottomBar = {
            FloatingBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (selectedTab == MainTab.HOME) {
                HomeContent(
                    onOpenTab = { selectedTab = it },
                    onOpenShoppingList = { showShoppingList = true }
                )
            } else {
                Text(
                    text = selectedTab.label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink
                )
            }
        }
    }
}

@Composable
private fun FloatingBottomBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val tint = if (selected) DeepTeal else Muted

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onTabSelected(tab) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 30.dp)
                            .background(
                                if (selected) SelectedCyan else Color.Transparent,
                                RoundedCornerShape(15.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        TabIcon(tab, tint)
                    }

                    Text(
                        text = tab.label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold
                        else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: MainTab, tint: Color) {
    OutlineIcon(tab.iconPath, tint)
}

@Composable
private fun OutlineIcon(iconPath: String, tint: Color) {
    // 底栏和购物摘要共用同一套线条绘制方式。
    val path = remember(iconPath) {
        PathParser().parsePathString(iconPath).toPath()
    }

    Canvas(modifier = Modifier.size(21.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = center.copy(x = 0f, y = 0f)) {
            drawPath(
                path = path,
                color = tint,
                style = Stroke(
                    width = 1.7f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun HomeContent(
    onOpenTab: (MainTab) -> Unit,
    onOpenShoppingList: () -> Unit
) {
    // 首页顶部只放品牌和问候，把空间留给实际内容。
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SmartPantry",
                color = DeepTeal,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SelectedCyan)
                    .clickable(onClickLabel = "Open profile") {
                        onOpenTab(MainTab.PROFILE)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("MH", color = DeepTeal, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = "Good morning, Min Hui",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Text(
            text = "Make the most of what you have.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
    }

    // Box 让蔬菜贴住右下角。clip 把图片裁在圆角卡片范围内。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.secondaryContainer)
                )
            )
    ) {
        Image(
            painter = painterResource(R.drawable.home_vegetables),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(116.dp)
        )

        // 右侧留给插图。文字变长时，卡片可随内容加高。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 122.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "THIS WEEK",
                color = DeepTeal,
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "0.8 kg",
                color = DeepTeal,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Recorded food waste", color = Ink, fontSize = 13.sp)
            Text(
                text = "Sample data · Weight-based only",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }

    // 本周浪费是时间区间统计。这里显示的是当前库存。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClickLabel = "Open pantry") {
                onOpenTab(MainTab.PANTRY)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabIcon(MainTab.PANTRY, DeepTeal)
        Text(
            text = "12 ingredients · 3 batches expiring soon",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = Ink
        )
        Text("Open →", color = DeepTeal, fontSize = 12.sp)
    }

    // 两条到期记录放在同一组，用 Divider 分隔，减少卡片堆叠。
    Column {
        Text(
            text = "Use these first",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        CompactExpiryRow("Eggs", "6 pcs · Expires tomorrow", R.drawable.home_eggs) {
            onOpenTab(MainTab.PANTRY)
        }
        HorizontalDivider(color = 	MaterialTheme.colorScheme.outlineVariant)
        CompactExpiryRow("Tomatoes", "3 pcs · Expires in 2 days", R.drawable.home_tomatoes) {
            onOpenTab(MainTab.PANTRY)
        }
    }

    // 复用底栏的线条图标，并让快捷入口和底栏共用切换逻辑。
    // 快捷入口和底栏共用同一套 Tab 切换逻辑。
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        HomeShortcut("Pantry", MainTab.PANTRY, onOpenTab)
        HomeShortcut("Find recipes", MainTab.RECIPES, onOpenTab)
        HomeShortcut("Food log", MainTab.LOG, onOpenTab)
    }

    // 首页卡片发出点击事件，由 MainScreen 决定打开哪个页面。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                onClickLabel = "Open shopping list",
                onClick = onOpenShoppingList
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(SelectedCyan, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            OutlineIcon(
                iconPath = "M9 5 H6 Q5 5 5 6 V20 Q5 21 6 21 H18 Q19 21 19 20 V6 Q19 5 18 5 H15 " +
                        "M9 3 H15 V7 H9 Z M9 14 L11 16 L15 12",
                tint = DeepTeal
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Before you shop", fontWeight = FontWeight.SemiBold, color = Ink)
            Text("3 pending items", color = Muted, fontSize = 12.sp)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Discover community recipes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Ink
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClickLabel = "Open recipes") {
                    onOpenTab(MainTab.RECIPES)
                }
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Crop 保持原图比例，并裁掉超出缩略图边框的部分。
            Image(
                painter = painterResource(R.drawable.home_nasi_goreng),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 88.dp, height = 60.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Column(modifier = Modifier.weight(1f)) {
                Text("Nasi Goreng", fontWeight = FontWeight.SemiBold, color = Ink)
                Text("by Aina", color = Muted, fontSize = 12.sp)
            }

            Text("→", color = DeepTeal, modifier = Modifier.padding(end = 4.dp))
        }
    }
}

@Composable
private fun CompactExpiryRow(
    name: String,
    detail: String,
    imageRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open pantry for $name", onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 同一个 Row 可以接收不同的 drawable，例如鸡蛋或番茄。
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(44.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, color = Ink)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 黄点跟到期文字放在一起，文字也能独立传达提醒。
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(Color(0xFFF2AB39), RoundedCornerShape(50))
                )
                Text(detail, fontSize = 12.sp, color = Muted)
            }
        }

        Text("→", color = DeepTeal)
    }
}

@Composable
private fun HomeShortcut(
    label: String,
    tab: MainTab,
    onOpenTab: (MainTab) -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenTab(tab) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(SelectedCyan, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            TabIcon(tab, DeepTeal)
        }
        Text(label, fontSize = 12.sp, color = Ink)
    }
}

@Composable
private fun ShoppingItemRow(
    name: String,
    initialQuantity: Int,
    unit: String
) {
    // 每一行各自记住当前数量和已购状态。
    var quantity by rememberSaveable { mutableStateOf(initialQuantity) }
    var purchased by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = purchased,
            onCheckedChange = { purchased = it },
            modifier = Modifier.semantics {
                contentDescription = "Purchased: $name"
            }
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "$quantity $unit",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 数量最低为 1。标记已购后，先取消勾选才能改数量。
        TextButton(
            onClick = { quantity-- },
            enabled = quantity > 1 && !purchased
        ) {
            Text("−")
        }

        TextButton(
            onClick = { quantity++ },
            enabled = !purchased
        ) {
            Text("+")
        }
    }
}