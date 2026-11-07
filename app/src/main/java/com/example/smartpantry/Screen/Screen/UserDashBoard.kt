package com.ecopantry.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// === YOUR COLOR PALETTE ===
val Primary = Color(0xFFB8E7E7)
val PrimaryDark = Color(0xFF235F62)
val Background = Color(0xFFFFFFFF)
val SecondaryBg = Color(0xFFF3FAFA)
val BodyText = Color(0xFF263B3D)
val MutedText = Color(0xFF667B7D)
val Border = Color(0xFFDCEAEA)
val DarkButton = Color(0xFF1A2426)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    UserDashboardScreen()
                }
            }
        }
    }
}

@Composable
fun UserDashboardScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
    ) {
        // === TOP HEADER SECTION ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Primary)
                .padding(top = 40.dp, bottom = 32.dp, start = 24.dp, end = 24.dp)
        ) {
            Column {
                // Status row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "9:41",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BodyText
                    )
                    Text(
                        text = "●●●", // Replace with actual status icons
                        fontSize = 14.sp,
                        color = BodyText
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // #B8E7E7 label
                Text(
                    text = "#B8E7E7",
                    fontSize = 18.sp,
                    color = BodyText.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Welcome back!",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = BodyText,
                    lineHeight = 40.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "This week you saved 1.2kg of food and reduced 3.4kg CO2",
                    fontSize = 18.sp,
                    color = BodyText.copy(alpha = 0.8f),
                    lineHeight = 26.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Stats Cards Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(
                        title = "Food Used",
                        value = "86%",
                        iconBg = Primary,
                        icon = "🛒",
                        trendIcon = "✓"
                    )
                    StatCard(
                        title = "Food Wasted",
                        value = "14%",
                        iconBg = SecondaryBg,
                        icon = "☀",
                        trendIcon = "↓"
                    )
                }
            }

            // Notification Bell
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp)
                    .size(40.dp)
                    .background(Background, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🔔", fontSize = 18.sp)
                // Badge dot
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .background(Color.Red, shape = CircleShape)
                )
            }
        }

        // === QUICK ACTIONS SECTION ===
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SecondaryBg)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Actions",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = BodyText
                )
                Text(
                    text = "See All >",
                    fontSize = 14.sp,
                    color = PrimaryDark // Adjusted to match your palette
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionCard(
                        title = "Scan Fridge",
                        containerColor = Primary.copy(alpha = 0.3f),
                        iconBg = Primary.copy(alpha = 0.5f),
                        icon = "📷",
                        textColor = BodyText,
                        modifier = Modifier.weight(1f)
                    )
                    ActionCard(
                        title = "Find Recipes",
                        containerColor = DarkButton,
                        iconBg = Color.White.copy(alpha = 0.2f),
                        icon = "🛒",
                        textColor = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionCard(
                        title = "Log Waste",
                        subtitle = "Body Text #667B3D",
                        containerColor = MutedText.copy(alpha = 0.1f),
                        iconBg = MutedText.copy(alpha = 0.2f),
                        icon = "📋",
                        textColor = BodyText,
                        modifier = Modifier.weight(1f)
                    )
                    ActionCard(
                        title = "Shopping List",
                        subtitle = "Support #DCEAEA",
                        containerColor = Border.copy(alpha = 0.3f),
                        iconBg = Border,
                        icon = "☰",
                        textColor = BodyText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

}

@Composable
fun StatCard(
    title: String,
    value: String,
    iconBg: Color,
    icon: String,
    trendIcon: String
) {
    Card(
        modifier = Modifier.size(160.dp, 140.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(iconBg, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 18.sp)
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(SecondaryBg, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = trendIcon, fontSize = 14.sp, color = PrimaryDark)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                fontSize = 16.sp,
                color = MutedText
            )

            Text(
                text = value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = BodyText
            )
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String? = null,
    containerColor: Color,
    iconBg: Color,
    icon: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconBg, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 20.sp, color = textColor)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )

            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}


@Composable
fun NavItem(icon: String, label: String, selected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = icon,
            fontSize = 20.sp,
            color = if (selected) PrimaryDark else MutedText
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (selected) PrimaryDark else MutedText
        )
    }
}
