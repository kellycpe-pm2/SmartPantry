package com.example.smartpantry.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun ImageInput(imagePath : String ="") {
    val scheme = MaterialTheme.colorScheme

    Box(
    modifier = Modifier
    .size(120.dp)
    .border(
    border = BorderStroke(1.dp, scheme.outlineVariant),
    shape = RoundedCornerShape(12.dp)
    )
    .clickable { /* Handle image selection */ },
    contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Select Image",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(50.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (imagePath != null) "Image Selected" else "Select Image",
                fontSize = 14.sp,
                color = if (imagePath != null) scheme.primary else scheme.onSurfaceVariant
            )
        }
    }

}