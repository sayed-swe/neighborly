package com.neighborly.app.feature.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.LoadContent
import com.neighborly.app.data.model.Reaction
import com.neighborly.app.data.model.Reactor
import com.neighborly.app.data.repository.FeedRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReactorsViewModel @Inject constructor(private val feed: FeedRepo) : ViewModel() {
    private val _state = MutableStateFlow<Load<List<Reactor>>>(Load.Loading)
    val state = _state.asStateFlow()
    private var loadedFor: Long? = null

    fun load(postId: Long) {
        if (loadedFor == postId) return
        loadedFor = postId
        _state.value = Load.Loading
        viewModelScope.launch {
            feed.reactors(postId)
                .onSuccess { _state.value = Load.Data(it) }
                .onFailure { _state.value = Load.Error(it.message.orEmpty()) }
        }
    }
}

/** "Who reacted" with a filter chip per reaction type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactorsSheet(
    postId: Long,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
    vm: ReactorsViewModel = hiltViewModel(key = "reactors-$postId"),
) {
    vm.load(postId)
    val state by vm.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.reactors_title), style = MaterialTheme.typography.titleMedium)
            LoadContent(state, onRetry = { vm.load(postId) }) { list ->
                val types = list.map { it.reactionType }.distinct()
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter == null, { filter = null }, label = { Text(stringResource(R.string.reactors_all, list.size)) })
                    types.forEach { t ->
                        FilterChip(filter == t, { filter = t }, label = {
                            Text("${Reaction.of(t).emoji} ${list.count { it.reactionType == t }}")
                        })
                    }
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list.filter { filter == null || it.reactionType == filter }) { r ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { r.user?.id?.let(onOpenProfile) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(r.user?.avatarUrl, r.user?.fullName, 40.dp)
                            Text(r.user?.fullName.orEmpty(), Modifier.padding(start = 12.dp).weight(1f))
                            Text(Reaction.of(r.reactionType).emoji)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
