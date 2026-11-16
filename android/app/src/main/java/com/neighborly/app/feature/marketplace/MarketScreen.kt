package com.neighborly.app.feature.marketplace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.EmptyState
import com.neighborly.app.core.designsystem.ErrorState
import com.neighborly.app.core.designsystem.ListSkeleton
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.core.designsystem.PhotoBox
import com.neighborly.app.core.designsystem.Refreshable
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.Enums
import com.neighborly.app.data.model.Listing
import com.neighborly.app.data.repository.ListingFilter
import com.neighborly.app.data.repository.ListingRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MarketUiState(
    val filter: ListingFilter = ListingFilter(),
    val items: Load<List<Listing>> = Load.Loading,
    val refreshing: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class MarketViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ListingRepo,
) : ViewModel() {
    private val _state = MutableStateFlow(MarketUiState())
    val state = _state.asStateFlow()
    private val search = MutableStateFlow("")

    init {
        load()
        viewModelScope.launch { search.drop(1).debounce(350).collect { load() } }
    }

    fun load(refresh: Boolean = false) {
        _state.update { if (refresh) it.copy(refreshing = true) else it.copy(items = Load.Loading) }
        viewModelScope.launch {
            val r = repo.list(_state.value.filter)
            _state.update { s ->
                s.copy(
                    refreshing = false,
                    items = r.fold({ Load.Data(it) }, { Load.Error(it.message.orEmpty()) }),
                )
            }
        }
    }

    fun setType(t: String?) { _state.update { it.copy(filter = it.filter.copy(type = t)) }; load() }
    fun setCategory(c: String?) { _state.update { it.copy(filter = it.filter.copy(category = c)) }; load() }
    fun setSearch(q: String) { _state.update { it.copy(filter = it.filter.copy(search = q)) }; search.value = q }
}

@Composable
fun MarketScreen(onOpen: (Long) -> Unit, onCreate: () -> Unit, vm: MarketViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Refreshable(s.refreshing, { vm.load(true) }) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(2) }) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = s.filter.search.orEmpty(),
                            onValueChange = vm::setSearch,
                            placeholder = { Text(stringResource(R.string.market_search)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(s.filter.type == null, { vm.setType(null) }, label = { Text(stringResource(R.string.filter_all)) }) }
                            items(Enums.LISTING_TYPES) { t ->
                                FilterChip(s.filter.type == t, { vm.setType(t) }, label = { Text(listingTypeLabel(t)) })
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(s.filter.category == null, { vm.setCategory(null) }, label = { Text(stringResource(R.string.filter_all_categories)) }) }
                            items(Enums.LISTING_CATEGORIES) { c ->
                                FilterChip(s.filter.category == c, { vm.setCategory(c) }, label = { Text(c) })
                            }
                        }
                    }
                }
                when (val items = s.items) {
                    Load.Loading -> item(span = { GridItemSpan(2) }) { ListSkeleton(2) }
                    is Load.Error -> item(span = { GridItemSpan(2) }) { ErrorState(items.message, { vm.load() }) }
                    is Load.Data -> if (items.value.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            EmptyState(stringResource(R.string.market_empty), icon = Icons.Outlined.Storefront,
                                actionLabel = stringResource(R.string.market_sell), onAction = onCreate)
                        }
                    } else items(items.value, key = { it.id }) { l -> ListingTile(l) { onOpen(l.id) } }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onCreate,
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text(stringResource(R.string.market_sell)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
fun listingTypeLabel(t: String): String = when (t) {
    "sell" -> stringResource(R.string.listing_type_sell)
    "buy" -> stringResource(R.string.listing_type_buy)
    "exchange" -> stringResource(R.string.listing_type_exchange)
    "free" -> stringResource(R.string.listing_type_free)
    else -> Format.labelize(t)
}

@Composable
private fun ListingTile(l: Listing, onClick: () -> Unit) {
    NbCard(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (l.imageUrl.isNullOrBlank()) Icon(Icons.Outlined.Storefront, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else PhotoBox(l.imageUrl, Modifier.fillMaxSize(), stringResource(R.string.cd_item_photo))
        }
        Column(Modifier.padding(8.dp)) {
            Text(Format.price(l.price, l.listingType), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(l.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MetaText(listOfNotNull(l.locationName ?: l.neighborhood?.name, Format.timeAgo(l.createdAt)).joinToString(" · "), maxLines = 1)
        }
    }
}
