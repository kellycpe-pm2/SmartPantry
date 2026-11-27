package com.example.smartpantry.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.smartpantry.community.*
import com.example.smartpantry.remote.CommunityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

@Composable
internal fun CommunityDiscover(onOpenPost: (Long) -> Unit, onBrowse: () -> Unit) {
    val repository = remember { CommunityRepository() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var refresh by remember { mutableIntStateOf(0) }
    var posts by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refresh) {
        loading = true; failed = false; posts = emptyList()
        try { posts = repository.call("feed").rows("posts").take(3) } catch (cancelled: CancellationException) { throw cancelled } catch (unavailable: Exception) { failed = true } finally { loading = false }
    }
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionTitle("Discover community recipes",onBrowse)
        when {
            loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading community recipes…", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
            failed -> Surface(shape = RoundedCornerShape(16.dp), color = c.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Unable to load community recipes", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { refresh++ }) { Text("Retry") }
                }
            }
            posts.isEmpty() -> Surface(shape = RoundedCornerShape(16.dp), color = c.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.RestaurantMenu, null, tint = c.primary)
                    Text("No community recipes yet", fontWeight = FontWeight.SemiBold)
                    Text("Published recipes will appear here.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
            }
            else -> posts.forEachIndexed {index,post->
                Row(Modifier.fillMaxWidth().clickable{onOpenPost(post.long("id"))}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    RecipePhoto(post.str("image"),Modifier.width(104.dp).height(70.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(post.str("title"),fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("by ${post.str("author")}",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                        Text(String.format(java.util.Locale.US,"★ %.1f (%d)",post.number("rating"),post.long("ratingCount")),style=MaterialTheme.typography.labelMedium,color=c.primary)
                    }
                    Icon(Icons.Default.ChevronRight,"View recipe",Modifier.size(24.dp),tint=c.primary)
                }
                if(index<posts.lastIndex)HorizontalDivider(color=c.outlineVariant)
            }
        }
    }
}
