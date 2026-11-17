package com.lumen.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.data.Catalog
import com.lumen.tv.data.Kind
import com.lumen.tv.data.LibState
import com.lumen.tv.data.Library
import com.lumen.tv.data.PickCategory
import com.lumen.tv.data.PickGroup
import com.lumen.tv.data.Region
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.Screen
import com.lumen.tv.ui.components.AmbientBackground
import com.lumen.tv.ui.components.Eyebrow
import com.lumen.tv.ui.components.LoadProgressCard
import com.lumen.tv.ui.components.Pill
import com.lumen.tv.ui.components.LumenButton
import com.lumen.tv.ui.components.BrandMark
import com.lumen.tv.ui.components.PulseLoader
import com.lumen.tv.ui.components.focusGlow
import com.lumen.tv.ui.components.glass
import com.lumen.tv.ui.components.initialFocus
import com.lumen.tv.ui.theme.Hue
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.LocalIsTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * First-run setup, also reachable from Settings.
 *  Step 1: where are you watching from?  Step 2: which categories do you want to see?
 * Nothing is saved until the last step is confirmed.
 */
@Composable
fun SetupFlow(vm: AppViewModel, firstRun: Boolean, startAtCategories: Boolean) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val state by vm.repo.state.collectAsStateWithLifecycle()
    val active by vm.activeSource.collectAsStateWithLifecycle()
    // Builds from before the category step stopped after choosing a country: carry on from there.
    val resumed = firstRun && settings.setupVersion == 1
    val hasSaved = settings.setupVersion > 0

    var step by rememberSaveable { mutableIntStateOf(if (startAtCategories || resumed) 1 else 0) }
    var region by rememberSaveable { mutableStateOf(if (hasSaved) settings.preferredRegion else "") }
    var only by rememberSaveable { mutableStateOf(if (hasSaved) settings.countryOnly else true) }
    // Fresh suggestions on first run or when the country changed; otherwise show what's currently on.
    val useSuggestions = remember(step, region, only) {
        !settings.setupDone || region != settings.preferredRegion || only != settings.countryOnly
    }

    BackHandler(enabled = step == 1 && !startAtCategories) { step = 0 }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        // Wait for the *active* playlist: right after adding one, the previous library is still loaded for a moment.
        val lib = (state as? LibState.Ready)?.library?.takeIf { it.sourceId == active?.id }
        if (step == 0 || lib == null) {
            CountryStep(vm, firstRun = firstRun, waiting = step == 1, initialRegion = region, initialOnly = only) { code, o ->
                region = code; only = o; step = 1
            }
        } else {
            CategoryStep(
                vm, lib, region = region, countryOnly = only && region.isNotBlank(), useSuggestions = useSuggestions,
                showBack = !startAtCategories, cancelable = !firstRun,
                onBack = { step = 0 },
                onDone = { offered, checked -> vm.finishSetup(region, only, offered, checked) },
            )
        }
    }
}

// =========================================================================== step 1: country

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountryStep(
    vm: AppViewModel,
    firstRun: Boolean,
    waiting: Boolean, // the country is already chosen; just show progress until the playlist is ready
    initialRegion: String,
    initialOnly: Boolean,
    onContinue: (region: String, only: Boolean) -> Unit,
) {
    val accent = LocalAccent.current
    val isTv = LocalIsTv.current
    val state by vm.repo.state.collectAsStateWithLifecycle()
    val progress by vm.repo.progress.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val source by vm.activeSource.collectAsStateWithLifecycle()

    var selected by rememberSaveable { mutableStateOf(initialRegion) }
    var only by rememberSaveable { mutableStateOf(initialOnly) }
    var showAll by remember { mutableStateOf(false) }

    val list = if (waiting) null else countries
    val error = (state as? LibState.Error)?.message
    // The saved/tapped country if this playlist has it, otherwise USA, otherwise the biggest one.
    val chosen: String? = list?.let { l ->
        selected.takeIf { s -> l.any { it.first.code == s } }
            ?: l.firstOrNull { it.first.code == "US" }?.first?.code
            ?: l.firstOrNull()?.first?.code
    }
    val chosenRegion = list?.firstOrNull { it.first.code == chosen }?.first

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardWidth = if (maxWidth < 600.dp) (maxWidth - 54.dp) / 2 else 168.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = if (isTv) 36.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (list == null && error == null) PulseLoader(diameter = 84.dp) else BrandMark(72.dp)
            Spacer(Modifier.height(16.dp))
            Eyebrow(if (list == null) "Setting up" else "Step 1 of 2")
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    error != null -> "Couldn't load your playlist"
                    list == null -> "Loading ${source?.name ?: "your playlist"}"
                    else -> "Where are you watching from?"
                },
                style = MaterialTheme.typography.displayMedium, color = Hue.TextHi, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    error != null -> error
                    list == null -> "Fetching your channels, movies and series. Big playlists can take a minute the first time."
                    list.isEmpty() -> "This playlist doesn't label countries, so next you'll pick from all of its categories."
                    else -> "Pick your country. Next you'll choose categories, with this country's suggested first."
                },
                style = MaterialTheme.typography.bodyLarge, color = Hue.TextMid, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 560.dp),
            )
            Spacer(Modifier.height(26.dp))

            when {
                error != null -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LumenButton("Try again", onClick = { vm.refresh() }, modifier = Modifier.initialFocus())
                    LumenButton("Edit playlist", onClick = { vm.navigate(Screen.EditSource(source)) }, primary = false)
                }
                list == null -> LoadProgressCard(progress, (state as? LibState.Loading)?.message ?: "Getting ready…")
                list.isEmpty() -> LumenButton(
                    "Choose categories", onClick = { onContinue("", false) },
                    icon = Icons.AutoMirrored.Rounded.ArrowForward, modifier = Modifier.initialFocus(),
                )
                else -> {
                    val visible = if (showAll) list else list.take(12)
                    FlowRow(
                        Modifier.widthIn(max = 760.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        visible.forEachIndexed { i, (region, count) ->
                            CountryCard(
                                region, count, selected = region.code == chosen, width = cardWidth,
                                modifier = if (i == 0) Modifier.initialFocus() else Modifier,
                            ) { selected = region.code }
                        }
                    }
                    if (list.size > 12) {
                        Spacer(Modifier.height(14.dp))
                        LumenButton(if (showAll) "Show fewer" else "Show all ${list.size} countries", onClick = { showAll = !showAll }, primary = false)
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(
                        Modifier.widthIn(max = 560.dp).fillMaxWidth()
                            .focusGlow(RoundedCornerShape(20.dp), scale = 1.02f)
                            .glass(RoundedCornerShape(20.dp))
                            .clickable { only = !only }.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.FilterAlt, null, tint = accent.main)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Only show ${chosenRegion?.label ?: "this country"}", style = MaterialTheme.typography.titleMedium, color = Hue.TextHi)
                            Text(
                                if (only) "Other countries start switched off. You can still add any of them in the next step."
                                else "Everything starts switched on, with this country listed first.",
                                style = MaterialTheme.typography.bodySmall, color = Hue.TextMid,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = only, onCheckedChange = { only = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = accent.main, checkedThumbColor = Color.White),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LumenButton("Next: categories", onClick = { onContinue(chosen ?: "", only) }, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                        if (!firstRun) LumenButton("Cancel", onClick = { vm.back() }, primary = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryCard(
    region: Region,
    count: Int,
    selected: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.width(width)
            .focusGlow(shape, scale = 1.05f)
            .clip(shape)
            .then(
                if (selected) Modifier.background(accent.soft).border(2.dp, accent.brush, shape)
                else Modifier.background(Hue.Glass).border(1.dp, Hue.Stroke, shape)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(region.flag, fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text(region.name, style = MaterialTheme.typography.titleMedium, color = Hue.TextHi, maxLines = 1)
        Text(
            "${String.format(Locale.US, "%,d", count)} items", style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent.main else Hue.TextLo,
        )
    }
}

// =========================================================================== step 2: categories

private sealed interface PickRow {
    val id: String

    data class Label(val text: String, val hint: String) : PickRow { override val id: String get() = "l:$text" }
    data class Header(
        val section: String, val title: String, val keys: List<String>, val collapsible: Boolean, val open: Boolean, val itemCount: Int,
    ) : PickRow { override val id: String get() = "h:$section" }
    data class Item(val pick: PickCategory, val key: String) : PickRow { override val id: String get() = key }
}

private fun kindLabel(kind: Kind) = when (kind) { Kind.LIVE -> "Live TV"; Kind.MOVIE -> "Movies"; Kind.SERIES -> "Series" }
private fun kindNoun(kind: Kind) = when (kind) { Kind.LIVE -> "channels"; Kind.MOVIE -> "movies"; Kind.SERIES -> "series" }
private fun number(n: Int) = String.format(Locale.US, "%,d", n)

@Composable
private fun CategoryStep(
    vm: AppViewModel,
    lib: Library,
    region: String,
    countryOnly: Boolean,
    useSuggestions: Boolean,
    showBack: Boolean,
    cancelable: Boolean,
    onBack: () -> Unit,
    onDone: (offered: List<PickCategory>, checked: Set<String>) -> Unit,
) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val pad = hPad()
    val sid = lib.sourceId
    val showAdult = settings.showAdult

    val picks by produceState<List<PickCategory>?>(null, lib, region, showAdult) {
        value = withContext(Dispatchers.Default) { Catalog.pickList(lib, region, showAdult) }
    }
    val list = picks
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PulseLoader() }
        return
    }

    fun keyOf(p: PickCategory) = Catalog.categoryKey(sid, p.kind, p.category.id)
    fun suggested(p: PickCategory) = p.group != PickGroup.OTHER || !countryOnly

    var checked by remember(list, useSuggestions) {
        val start = if (useSuggestions) list.filter { suggested(it) } else list.filter { p ->
            val key = keyOf(p)
            key !in settings.hiddenCategories && (p.group != PickGroup.OTHER || !settings.countryOnly || key in settings.approvedForeign)
        }
        mutableStateOf<Set<String>>(start.mapTo(HashSet()) { keyOf(it) })
    }
    val kinds = remember(list) { Kind.entries.filter { k -> list.any { it.kind == k } } }
    var tab by remember(kinds) { mutableStateOf(kinds.firstOrNull() ?: Kind.LIVE) }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(emptySet<String>()) }

    val regionLabel = remember(region) { if (region.isBlank()) "" else Catalog.region(region).label }
    val hasCountries = region.isNotBlank()
    val tabKeys = remember(list, tab) { list.filter { it.kind == tab }.map { keyOf(it) } }

    val rows = remember(list, tab, query, expanded) {
        val q = query.trim()
        val inTab = list.filter { it.kind == tab && (q.isEmpty() || it.category.name.contains(q, ignoreCase = true)) }
        buildList<PickRow> {
            fun section(id: String, title: String, items: List<PickCategory>, collapsible: Boolean) {
                if (items.isEmpty()) return
                val open = !collapsible || q.isNotEmpty() || id in expanded
                add(PickRow.Header(id, title, items.map { keyOf(it) }, collapsible, open, items.sumOf { it.category.count }))
                if (open) items.forEach { add(PickRow.Item(it, keyOf(it))) }
            }
            section("suggested", "Suggested for $regionLabel", inTab.filter { it.group == PickGroup.SUGGESTED }, false)
            section("general", if (hasCountries) "Not tied to a country" else "All categories", inTab.filter { it.group == PickGroup.GENERAL }, false)
            val byRegion = LinkedHashMap<String, MutableList<PickCategory>>()
            inTab.filter { it.group == PickGroup.OTHER }.forEach { byRegion.getOrPut(it.region?.code ?: "?") { ArrayList() }.add(it) }
            if (byRegion.isNotEmpty()) add(PickRow.Label("Other countries", "Open a country to add its categories."))
            byRegion.forEach { (code, items) -> section("r:$code", items.first().region?.label ?: code, items, true) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = isTv || maxWidth >= 840.dp
        val actions: @Composable (Modifier) -> Unit = { doneModifier ->
            if (showBack) LumenButton("Back", onClick = onBack, icon = Icons.AutoMirrored.Rounded.ArrowBack, primary = false)
            else if (cancelable) LumenButton("Cancel", onClick = { vm.back() }, primary = false)
            LumenButton(
                "Done", onClick = { onDone(list, checked) }, icon = Icons.Rounded.Check,
                enabled = checked.isNotEmpty(), modifier = doneModifier,
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ---- heading
            Row(
                Modifier.fillMaxWidth().padding(start = pad, end = pad, top = if (isTv) 28.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(if (showBack) "Step 2 of 2" else "Categories")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Choose what you want to see",
                        style = if (wide) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge, color = Hue.TextHi,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (if (hasCountries && useSuggestions) "$regionLabel is already picked for you. " else "") +
                            "${number(checked.size)} of ${number(list.size)} categories on. Change this any time in Settings.",
                        style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid,
                    )
                }
                if (wide) {
                    Spacer(Modifier.width(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { actions(Modifier.initialFocus()) }
                }
            }
            // ---- tabs: live / movies / series
            LazyRow(
                contentPadding = PaddingValues(horizontal = pad, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(kinds.size, key = { kinds[it].name }) { i ->
                    val k = kinds[i]
                    val total = list.count { it.kind == k }
                    val on = list.count { it.kind == k && keyOf(it) in checked }
                    Pill("${kindLabel(k)}  $on/$total", selected = tab == k, onClick = { tab = k })
                }
            }
            // ---- filter + quick picks for the current tab
            Row(Modifier.padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
                FilterField(query, { query = it }, "Find a category", Modifier.weight(1f))
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = pad, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "suggested") {
                    Pill("Suggested", selected = false, onClick = {
                        checked = (checked - tabKeys.toSet()) + list.filter { it.kind == tab && suggested(it) }.map { keyOf(it) }
                    })
                }
                item(key = "all") { Pill("All ${kindLabel(tab).lowercase()}", selected = false, onClick = { checked = checked + tabKeys }) }
                item(key = "none") { Pill("None", selected = false, onClick = { checked = checked - tabKeys.toSet() }) }
            }
            // ---- the categories
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    count = rows.size,
                    key = { rows[it].id },
                    span = { i -> if (rows[i] is PickRow.Item) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
                ) { i ->
                    when (val row = rows[i]) {
                        is PickRow.Label -> Column(Modifier.padding(top = 18.dp)) {
                            Eyebrow(row.text)
                            Text(row.hint, style = MaterialTheme.typography.bodySmall, color = Hue.TextLo)
                        }
                        is PickRow.Header -> {
                            val on = row.keys.count { it in checked }
                            SectionHeader(
                                row, on,
                                onToggleAll = { checked = if (on == row.keys.size) checked - row.keys.toSet() else checked + row.keys },
                                onToggleOpen = { expanded = if (row.section in expanded) expanded - row.section else expanded + row.section },
                            )
                        }
                        is PickRow.Item -> {
                            val p = row.pick
                            CategoryTile(
                                name = cleanCategoryName(p.category.name, if (p.group == PickGroup.GENERAL) null else p.region),
                                detail = "${number(p.category.count)} ${kindNoun(p.kind)}",
                                checked = row.key in checked,
                                icon = if (p.kind == Kind.LIVE) genreIcon(p.genre) else null,
                            ) { checked = if (row.key in checked) checked - row.key else checked + row.key }
                        }
                    }
                }
                if (!showAdult) item(key = "adult-note", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Adult categories are hidden automatically. You can change that in Settings › Parental controls.",
                        style = MaterialTheme.typography.bodySmall, color = Hue.TextLo, modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            // ---- phone: actions pinned to the bottom
            if (!wide) Row(
                Modifier.fillMaxWidth().background(Hue.Night).navigationBarsPadding().padding(horizontal = pad, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions(Modifier) }
        }
    }
}

@Composable
private fun SectionHeader(row: PickRow.Header, on: Int, onToggleAll: () -> Unit, onToggleOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleLarge, color = Hue.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$on of ${row.keys.size} on · ${number(row.itemCount)} items",
                style = MaterialTheme.typography.bodySmall, color = Hue.TextLo, maxLines = 1,
            )
        }
        if (row.collapsible) {
            Pill(if (row.open) "Hide" else "Show", selected = false, onClick = onToggleOpen)
            Spacer(Modifier.width(8.dp))
        }
        Pill(if (on == row.keys.size) "Clear" else "Add all", selected = on == row.keys.size, onClick = onToggleAll)
    }
}

@Composable
private fun CategoryTile(name: String, detail: String, checked: Boolean, icon: ImageVector?, onToggle: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth()
            .focusGlow(shape, scale = 1.03f)
            .clip(shape)
            .then(
                if (checked) Modifier.background(accent.soft).border(1.5.dp, accent.brush, shape)
                else Modifier.background(Hue.Glass).border(1.dp, Hue.Stroke, shape)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .then(if (checked) Modifier.background(accent.brush) else Modifier.border(1.5.dp, Hue.TextLo, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Rounded.Check, null, tint = Hue.OnAccent, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name, style = MaterialTheme.typography.titleSmall, color = if (checked) Hue.TextHi else Hue.TextMid,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Hue.TextLo, maxLines = 1)
        }
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(icon, null, tint = if (checked) accent.main else Hue.TextLo, modifier = Modifier.size(18.dp))
        }
    }
}
