package app.lucy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lucy.data.model.MangaItem
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun MangaCard(
    manga: MangaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = manga.title.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )

            if (!manga.thumb.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(manga.thumb)
                        .crossfade(true)
                        .build(),
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top-left badge: Rank (if present) or Type
            if (rank != null) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#$rank",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = manga.type,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Score badge bottom-right
            if (manga.score != null && manga.score > 0) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = String.format("%.1f", manga.score),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 1.dp)
        )

        val meta = if (manga.latestChapter.isNotBlank()) "Ch. ${manga.latestChapter}" else "${manga.chapterCount} chapters"
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
