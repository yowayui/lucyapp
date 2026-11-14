@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.lucy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lucy.data.db.CatalogDatabase
import app.lucy.data.model.MangaItem
import app.lucy.ui.components.MangaCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BrowseScreen(
    catalogDb: CatalogDatabase,
    onMangaClick: (String) -> Unit
) {
    val types = listOf("All", "Manga", "Manhwa", "Manhua")
    val languages = listOf("All", "EN", "ID")
    val genres = listOf(
        "All",
        "Action", "Adventure", "Comedy", "Cooking", "Crime", "Demons",
        "Drama", "Ecchi", "Fantasy", "Game", "Gender Bender", "Harem",
        "Historical", "Horror", "Isekai", "Josei", "Magic", "Martial Arts",
        "Mature", "Mecha", "Military", "Music", "Mystery", "One Shot",
        "Psychological", "Reincarnation", "Romance", "School", "Sci-Fi",
        "Seinen", "Shoujo", "Shoujo Ai", "Shounen", "Shounen Ai",
        "Slice of Life", "Sports", "Super Power", "Supernatural",
        "Survival", "Thriller", "Tragedy", "Vampire", "Webtoons", "Yaoi", "Yuri"
    )

    var selectedType by rememberSaveable { mutableStateOf("All") }
    var selectedLanguage by rememberSaveable { mutableStateOf("All") }
    var selectedGenre by rememberSaveable { mutableStateOf("All") }
    var mangaList by remember { mutableStateOf<List<MangaItem>>(emptyList()) }
    var page by remember(selectedType, selectedLanguage, selectedGenre) { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var canLoadMore by remember(selectedType, selectedLanguage, selectedGenre) { mutableStateOf(true) }

    val gridState = rememberLazyGridState()

    // Initial load for selected filters
    LaunchedEffect(selectedType, selectedLanguage, selectedGenre) {
        loading = true
        withContext(Dispatchers.IO) {
            mangaList = catalogDb.getBrowse(
                type = selectedType,
                genre = selectedGenre,
                language = selectedLanguage,
                limit = 60,
                offset = 0
            )
        }
        loading = false
    }

    // Infinite scroll pagination
    LaunchedEffect(gridState, selectedType, selectedLanguage, selectedGenre) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 9
        }.collect { shouldLoad ->
            if (shouldLoad && !loading && !loadingMore && canLoadMore) {
                loadingMore = true
                val nextPage = page + 1
                val more = withContext(Dispatchers.IO) {
                    catalogDb.getBrowse(
                        type = selectedType,
                        genre = selectedGenre,
                        language = selectedLanguage,
                        limit = 60,
                        offset = nextPage * 60
                    )
                }
                if (more.isEmpty()) {
                    canLoadMore = false
                } else {
                    mangaList = (mangaList + more).distinctBy { it.slug }
                    page = nextPage
                }
                loadingMore = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Browse",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        // Type filter row: All, Manga, Manhwa, Manhua
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(types) { type ->
                val isSelected = type == selectedType
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { selectedType = type }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (type == "All") "All Types" else type,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Language filter row: All, EN, ID
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(languages) { lang ->
                val isSelected = lang == selectedLanguage
                val label = when (lang) {
                    "All" -> "All Languages"
                    "EN" -> "English"
                    "ID" -> "Indonesia"
                    else -> lang
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { selectedLanguage = lang }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Genre filter row: All, Action, Adventure...
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            items(genres) { genre ->
                val isSelected = genre == selectedGenre
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { selectedGenre = genre }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (genre == "All") "All Genres" else genre,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(mangaList) { manga ->
                    MangaCard(
                        manga = manga,
                        onClick = { onMangaClick(manga.slug) }
                    )
                }

                if (loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
