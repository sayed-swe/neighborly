package com.neighborly.app.feature.map

import android.graphics.Color as AColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.CollectMessages
import com.neighborly.app.core.designsystem.EmptyState
import com.neighborly.app.core.designsystem.ErrorState
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.UiMessage
import com.neighborly.app.core.designsystem.toUiMessage
import com.neighborly.app.core.location.LocationProvider
import com.neighborly.app.core.location.rememberLocationRequest
import com.neighborly.app.core.util.Format
import com.neighborly.app.core.util.Geo
import com.neighborly.app.core.util.LatLng
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.MapItem
import com.neighborly.app.data.model.MapKind
import com.neighborly.app.data.repository.MapRepo
import com.neighborly.app.navigation.AlertsRoute
import com.neighborly.app.navigation.EventRoute
import com.neighborly.app.navigation.HelpRoute
import com.neighborly.app.navigation.ListingRoute
import com.neighborly.app.navigation.LostFoundRoute
import com.neighborly.app.navigation.PostRoute
import com.neighborly.app.navigation.ServiceRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import javax.inject.Inject

/** Where a map item opens — same targets as kindLink() in constants.js. */
fun MapItem.route(): Any? {
    val id = itemId.toLongOrNull() ?: return null
    return when (MapKind.of(kind)) {
        MapKind.POST -> PostRoute(id)
        MapKind.LISTING -> ListingRoute(id)
        MapKind.LOST_FOUND -> LostFoundRoute(id)
        MapKind.EVENT -> EventRoute(id)
        MapKind.HELP -> HelpRoute(id)
        MapKind.ALERT -> AlertsRoute
        MapKind.SERVICE -> ServiceRoute(id)
    }
}

data class MapUiState(
    val items: Load<List<MapItem>> = Load.Loading,
    val kinds: Set<MapKind> = MapKind.entries.toSet(),
    val me: LatLng? = null,
    val radiusKm: Int = 3,
    val locating: Boolean = false,
) {
    val center get() = me ?: Geo.DEFAULT_CENTER

    /** Visible items; with "Near me" on, only those inside the radius. Sorted by distance. */
    val visible: List<Pair<MapItem, Double?>>
        get() {
            val list = (items as? Load.Data)?.value.orEmpty().filter { MapKind.of(it.kind) in kinds }
            return list.map { it to Geo.distanceKm(center, LatLng(it.latitude, it.longitude)) }
                .filter { (_, d) -> me == null || (d != null && d <= radiusKm) }
                .sortedBy { it.second ?: Double.MAX_VALUE }
        }
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repo: MapRepo,
    private val location: LocationProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(MapUiState())
    val state = _state.asStateFlow()
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            repo.items()
                .onSuccess { v -> _state.update { it.copy(items = Load.Data(v)) } }
                .onFailure { e -> _state.update { it.copy(items = Load.Error(e.message.orEmpty())) } }
        }
    }

    fun toggle(kind: MapKind) = _state.update {
        it.copy(kinds = if (kind in it.kinds) it.kinds - kind else it.kinds + kind)
    }

    fun setRadius(km: Int) = _state.update { it.copy(radiusKm = km) }

    fun nearMe() {
        if (_state.value.me != null) { _state.update { it.copy(me = null) }; return }
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            appResult { location.current() }
                .onSuccess { p -> _state.update { it.copy(me = p, locating = false) } }
                .onFailure { e -> _state.update { it.copy(locating = false) }; _messages.send(e.toUiMessage()) }
        }
    }
}

@Composable
fun MapScreen(onOpen: (Any) -> Unit, vm: MapViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val requestLocation = rememberLocationRequest(onGranted = vm::nearMe)
    CollectMessages(vm.messages)
    val visible = s.visible

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    s.me != null, { if (s.me != null) vm.nearMe() else requestLocation() },
                    label = { Text(stringResource(R.string.search_near_me)) },
                    leadingIcon = { Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp)) },
                    enabled = !s.locating,
                )
            }
            if (s.me != null) items(Geo.RADII_KM) { km ->
                FilterChip(s.radiusKm == km, { vm.setRadius(km) }, label = { Text(stringResource(R.string.radius_km, km)) })
            }
            items(MapKind.entries) { k ->
                val c = Color(k.color)
                FilterChip(
                    k in s.kinds, { vm.toggle(k) },
                    label = { Text(kindLabel(k)) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(c)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.15f)),
                )
            }
        }
        // osmdroid draws outside its bounds unless clipped.
        Box(Modifier.fillMaxWidth().weight(1.2f).clipToBounds()) {
            OsmMap(s, visible.map { it.first }, onOpen)
            if (s.items is Load.Loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surface)) {
            when (val l = s.items) {
                Load.Loading -> Unit
                is Load.Error -> ErrorState(l.message, vm::load)
                is Load.Data -> if (visible.isEmpty()) EmptyState(stringResource(R.string.map_empty)) else
                    LazyColumn {
                        item {
                            MetaText(stringResource(R.string.map_count, visible.size), Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                        items(visible, key = { "${it.first.kind}-${it.first.itemId}" }) { (item, km) ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { item.route()?.let(onOpen) }.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(MapKind.of(item.kind).color)))
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(item.title.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    MetaText(listOfNotNull(kindLabel(MapKind.of(item.kind)), item.subtitle?.let(Format::labelize)).joinToString(" · "), maxLines = 1)
                                }
                                MetaText(Geo.formatDistance(km))
                            }
                        }
                    }
            }
        }
    }
}

@Composable
fun kindLabel(k: MapKind): String = stringResource(
    when (k) {
        MapKind.POST -> R.string.map_kind_posts
        MapKind.ALERT -> R.string.map_kind_alerts
        MapKind.HELP -> R.string.map_kind_help
        MapKind.LOST_FOUND -> R.string.nearby_lost_found
        MapKind.LISTING -> R.string.tab_market
        MapKind.EVENT -> R.string.tab_events
        MapKind.SERVICE -> R.string.menu_services
    },
)

@Composable
private fun OsmMap(s: MapUiState, items: List<MapItem>, onOpen: (Any) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.5)
            controller.setCenter(GeoPoint(Geo.DEFAULT_CENTER.lat, Geo.DEFAULT_CENTER.lng))
        }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }
    val iconPx = with(density) { 18.dp.roundToPx() }
    val strokePx = with(density) { 2.dp.roundToPx() }
    AndroidView(
        factory = { map },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            view.overlays.clear()
            val me = s.me
            if (me != null) {
                val circle = Polygon(view).apply {
                    points = Polygon.pointsAsCircle(GeoPoint(me.lat, me.lng), s.radiusKm * 1000.0)
                    fillPaint.color = AColor.argb(40, AColor.red(primary), AColor.green(primary), AColor.blue(primary))
                    outlinePaint.color = primary
                    outlinePaint.strokeWidth = strokePx.toFloat()
                    setOnClickListener { _, _, _ -> false }
                }
                view.overlays.add(circle)
                view.overlays.add(Marker(view).apply {
                    position = GeoPoint(me.lat, me.lng)
                    icon = dot(primary, iconPx, strokePx)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = context.getString(R.string.cd_my_location)
                })
            }
            items.forEach { item ->
                view.overlays.add(Marker(view).apply {
                    position = GeoPoint(item.latitude, item.longitude)
                    icon = dot(MapKind.of(item.kind).color.toInt(), iconPx, strokePx)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = item.title
                    snippet = item.subtitle
                    setOnMarkerClickListener { m, _ ->
                        if (m.isInfoWindowShown) { item.route()?.let(onOpen); true } else { m.showInfoWindow(); true }
                    }
                })
            }
            val center = s.center
            if (view.tag != center) {
                view.tag = center
                view.controller.animateTo(GeoPoint(center.lat, center.lng), if (me != null) 15.0 else 15.5, 400L)
            }
            view.invalidate()
        },
    )
}

private fun dot(color: Int, size: Int, stroke: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
    setStroke(stroke, AColor.WHITE)
    setSize(size, size)
}
