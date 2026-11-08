package com.example.smartpantry.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartpantry.R

@Composable
fun UserDashboardScreen() {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f

    // Hero card gradient: light cyan in light mode, deep teal in dark mode
    val heroGradient = if (isDark) {
        Brush.horizontalGradient(listOf(
            scheme.primaryContainer,
            scheme.surfaceVariant
        ))
    } else {
        Brush.horizontalGradient(listOf(
            Color(0xFFEFFBFB),
            Color(0xFFCCEFEF)
        ))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Greeting header ---
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SmartPantry", color = scheme.primary,
                    fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text("MH", color = scheme.primary, fontWeight = FontWeight.Bold) }
            }
            Text("Good morning, Min Hui",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = scheme.onBackground)
            Text("Make the most of what you have.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }

        // --- Weekly waste hero card ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(heroGradient)
        ) {
            Image(
                painter = painterResource(R.drawable.home_vegetables),
                contentDescription = null,
                modifier = Modifier.align(Alignment.BottomEnd).width(116.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 122.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("THIS WEEK", color = scheme.primary, fontSize = 11.sp,
                    letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                Text("0.8 kg", color = scheme.primary, fontSize = 32.sp,
                    lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                Text("Recorded food waste", color = scheme.onBackground, fontSize = 13.sp)
                Text("Sample data · Weight-based only",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }

        // --- Pantry summary ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.ShoppingCart,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text("12 ingredients · 3 batches expiring soon",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = scheme.onBackground)
            Text("Open →", color = scheme.primary, fontSize = 12.sp)
        }

        // --- Expiring items ---
        Column {
            Text("Use these first",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = scheme.onBackground)
            CompactExpiryRow("Eggs", "6 pcs · Expires tomorrow",
                R.drawable.home_eggs)
            HorizontalDivider(color = scheme.outlineVariant)
            CompactExpiryRow("Tomatoes", "3 pcs · Expires in 2 days",
                R.drawable.home_tomatoes)
        }

        // --- Shortcuts ---
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            HomeShortcut("Pantry",       Icons.Filled.ShoppingCart)
            HomeShortcut("Find recipes", Icons.Filled.RestaurantMenu)
            HomeShortcut("Food log",     Icons.Filled.EditNote)
        }

        // --- Shopping summary ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceVariant, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp)
                    .background(scheme.primaryContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Before you shop", fontWeight = FontWeight.SemiBold, color = scheme.onBackground)
                Text("3 pending items", color = scheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        // --- Community recipe ---
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Discover community recipes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = scheme.onBackground)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceVariant)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.home_nasi_goreng),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 88.dp, height = 60.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Column(Modifier.weight(1f)) {
                    Text("Nasi Goreng", fontWeight = FontWeight.SemiBold, color = scheme.onBackground)
                    Text("by Aina", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text("→", color = scheme.primary, modifier = Modifier.padding(end = 4.dp))
            }
        }
    }
}

@Composable
private fun CompactExpiryRow(name: String, detail: String, imageRes: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(44.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, color = scheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp)
                    .background(Color(0xFFF2AB39), RoundedCornerShape(50)))
                Text(detail, fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
        }
        Text("→", color = scheme.primary)
    }
}

@Composable
private fun HomeShortcut(label: String, icon: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(38.dp)
                .background(scheme.primaryContainer, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = scheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(label, fontSize = 12.sp, color = scheme.onBackground)
    }
}