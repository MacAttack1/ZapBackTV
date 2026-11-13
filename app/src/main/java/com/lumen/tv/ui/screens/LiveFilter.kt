package com.lumen.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.data.Catalog
import com.lumen.tv.data.CategoryInfo
import com.lumen.tv.data.Genre
import com.lumen.tv.data.Kind
import com.lumen.tv.data.Library
import com.lumen.tv.data.Region
import com.lumen.tv.data.Stream
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.components.ListRow
import com.lumen.tv.ui.components.Pill
import com.lumen.tv.ui.components.focusGlow
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.Hue
import com.lumen.tv.ui.components.glass

fun genreIcon(g: Genre): ImageVector = when (g) {
    Genre.ENTERTAINMENT -> Icons.Rounded.Theaters
    Genre.SPORTS -> Icons.Rounded.SportsSoccer
    Genre.NEWS -> Icons.Rounded.Newspaper
    Genre.MOVIES -> Icons.Rounded.Movie
    Genre.KIDS -> Icons.Rounded.ChildCare
    Genre.DOCUMENTARY -> Icons.Rounded.Public
    Genre.MUSIC -> Icons.Rounded.MusicNote
    Genre.FAITH -> Icons.Rounded.Church
    Genre.LOCAL -> Icons.Rounded.LocationOn
    Genre.OTHER -> Icons.Rounded.Category
    Genre.ADULT -> Icons.Rounded.Whatshot
}

/** Everything the Live TV and Guide screens need to know about the current country / genre / category choice. */
class LiveFilter(
    val regions: List<Region>,
    val hasUnsorted: Boolean,
    val region: String,
    val genres: List<Pair<Genre, Int>>,
    val genre: Genre?,
    val categories: List<CategoryInfo>,
    val catId: String,
    val channels: List<Stream>,
    val setRegion: (String) -> Unit,
    val setGenre: (Genre?) -> Unit,
    val setCategory: (String) -> Unit,
    val onHide: ((CategoryInfo) -> Unit)? = null,
) {
    val regionLabel: String
        get() = when (region) {
            Catalog.ALL_REGIONS -> "All countries"
            Catalog.NO_REGION -> "International"
            else -> regions.firstOrNull { it.code == region }?.label ?: region
        }
    val categoryLabel: String
        get() = when (catId) {
            Library.ALL -> "All ${genre?.label?.lowercase() ?: "channels"}"
            Library.FAVORITES -> "Favorites"
            Library.RECENT -> "Recently watched"
            else -> categories.firstOrNull { it.category.id == catId }?.category?.name ?: "Category"
        }
}

@Composable
fun rememberLiveFilter(vm: AppViewModel, lib: Library, query: String = ""): LiveFilter {
    val catalog by vm.liveCatalog.collectAsStateWithLifecycle()
    val favs by vm.store.favorites.collectAsStateWithLifecycle()
    val history by vm.store.history.collectAsStateWithLifecycle()
    val settings by vm.store.settings.collectAsStateWithLifecycle()

    var regionRaw by rememberSaveable { mutableStateOf("") } // "" = not chosen yet → preferred country
    var genreName by rememberSaveable { mutableStateOf("") }
    var catId by rememberSaveable { mutableStateOf(Library.ALL) }

    val regions = remember(catalog, settings.preferredRegion) { Catalog.regionsByWeight(catalog, settings.preferredRegion) }
    val hasUnsorted = remember(catalog) { catalog.any { it.region == null } }
    val region = regionRaw.ifEmpty {
        // In country-only mode the list is already filtered, so start with everything that's left.
        if (!settings.countryOnly && regions.any { it.code == settings.preferredRegion }) settings.preferredRegion else Catalog.ALL_REGIONS
    }
    val genre = Genre.entries.firstOrNull { it.name == genreName }

    val inRegion = remember(catalog, region) {
        when (region) {
            Catalog.ALL_REGIONS -> catalog
            Catalog.NO_REGION -> catalog.filter { it.region == null }
            else -> catalog.filter { it.region?.code == region }
        }
    }
    val genres = remember(inRegion) {
        inRegion.groupBy { it.genre }.map { (g, l) -> g to l.sumOf { it.category.count } }.sortedBy { it.first.ordinal }
    }
    // Adult categories are removed from the library unless enabled in Settings; when they are
    // enabled they still only appear under their own "Adult" chip, never mixed into "All".
    val visible = remember(inRegion, genre) {
        if (genre != null) inRegion.filter { it.genre == genre } else inRegion.filter { it.genre != Genre.ADULT }
    }
    val channels = remember(lib, visible, catId, favs, history, query) {
        val base = when (catId) {
            Library.FAVORITES -> vm.favoritesOf(Kind.LIVE, lib, favs)
            Library.RECENT -> history.filter { it.sourceId == lib.sourceId && it.kind == Kind.LIVE }.mapNotNull { lib.find(it.key) }
            Library.ALL -> visible.flatMap { lib.inCategory(Kind.LIVE, it.category.id) }
            else -> lib.inCategory(Kind.LIVE, catId)
        }
        val q = query.trim()
        if (q.isEmpty()) base else base.filter { it.name.contains(q, ignoreCase = true) }
    }

    return LiveFilter(
        regions = regions, hasUnsorted = hasUnsorted, region = region,
        genres = genres, genre = genre, categories = visible, catId = catId, channels = channels,
        setRegion = { regionRaw = it; genreName = ""; catId = Library.ALL },
        setGenre = { genreName = it?.name ?: ""; catId = Library.ALL },
        setCategory = { catId = it },
        onHide = { info ->
            vm.hideCategory(Kind.LIVE, info.category.id, info.category.name)
            if (catId == info.category.id) catId = Library.ALL
        },
    )
}

/** Country chips (preferred first) followed by genre chips. */
@Composable
fun FilterChips(f: LiveFilter, pad: androidx.compose.ui.unit.Dp) {
    if (f.regions.size > 1 || f.hasUnsorted) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = pad, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(f.regions, key = { it.code }) { r -> Pill(r.label, f.region == r.code, { f.setRegion(r.code) }) }
            if (f.hasUnsorted) item(key = "intl") { Pill("🌐 International", f.region == Catalog.NO_REGION, { f.setRegion(Catalog.NO_REGION) }) }
            item(key = "all") { Pill("All countries", f.region == Catalog.ALL_REGIONS, { f.setRegion(Catalog.ALL_REGIONS) }) }
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = pad, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") { Pill("All", f.genre == null, { f.setGenre(null) }, icon = Icons.Rounded.ViewList) }
        items(f.genres, key = { it.first.name }) { (g, count) ->
            Pill(g.label, f.genre == g, onClick = { f.setGenre(g) }, icon = genreIcon(g), count = count)
        }
    }
}

/** Category rows grouped under genre headers (used in the TV rail and the phone sheet). */
fun LazyListScope.groupedCategories(f: LiveFilter, onPick: (String) -> Unit) {
    item(key = "c-all") {
        ListRow(f.categoryLabelFor(Library.ALL), selected = f.catId == Library.ALL, icon = Icons.Rounded.ViewList,
            trailing = f.categories.sumOf { it.category.count }.takeIf { it > 0 }?.toString()) { onPick(Library.ALL) }
    }
    item(key = "c-fav") { ListRow("Favorites", selected = f.catId == Library.FAVORITES, icon = Icons.Rounded.Favorite) { onPick(Library.FAVORITES) } }
    item(key = "c-recent") { ListRow("Recently watched", selected = f.catId == Library.RECENT, icon = Icons.Rounded.History) { onPick(Library.RECENT) } }
    val groups = f.categories.groupBy { it.genre }.toSortedMap()
    groups.forEach { (g, list) ->
        if (f.genre == null || groups.size > 1) item(key = "h-${g.name}") { GroupHeader(g) }
        items(list, key = { "cat-" + it.category.id }) { info ->
            ListRow(
                cleanCategoryName(info),
                selected = f.catId == info.category.id,
                trailing = info.category.count.takeIf { it > 0 }?.toString(),
                onLongClick = f.onHide?.let { hide -> { hide(info) } },
            ) { onPick(info.category.id) }
        }
    }
}

private fun LiveFilter.categoryLabelFor(id: String) = if (id == Library.ALL) "All ${genre?.label?.lowercase() ?: "channels"}" else id

@Composable
private fun GroupHeader(g: Genre) {
    val accent = LocalAccent.current
    Row(Modifier.padding(start = 14.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(genreIcon(g), null, tint = accent.main, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(g.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent.main)
    }
}

private val countryPrefix = Regex("""^[^\p{L}\p{N}]*[\p{L}\-]{2,14}[\s]*[|:\-\]\)–]+\s*""")

/** "|US| SPORTS HD" → "SPORTS HD" for categories that carry a country prefix. */
fun cleanCategoryName(name: String, region: Region?): String {
    if (region == null) return name
    return name.replace(countryPrefix, "").trim().ifBlank { name }
}

fun cleanCategoryName(info: CategoryInfo): String = cleanCategoryName(info.category.name, info.region)

/** Phone: a button showing the current category that opens a grouped picker. */
@Composable
fun CategoryPickerButton(f: LiveFilter, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    var open by remember { mutableStateOf(false) }
    Row(
        modifier.focusGlow(RoundedCornerShape(16.dp), scale = 1.02f).glass(RoundedCornerShape(16.dp), fill = Hue.GlassHi).clickable { open = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Category, null, tint = accent.main, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(f.categoryLabel, style = MaterialTheme.typography.titleSmall, color = Hue.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("${f.channels.size}", style = MaterialTheme.typography.labelMedium, color = Hue.TextLo)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.ExpandMore, "Choose category", tint = Hue.TextMid)
    }
    if (open) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheet, containerColor = Hue.Night) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Text(
                    "${f.regionLabel} · ${f.genre?.label ?: "All genres"}",
                    style = MaterialTheme.typography.titleLarge, color = Hue.TextHi,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp)) {
                    groupedCategories(f) { f.setCategory(it); open = false }
                }
            }
        }
    }
}
