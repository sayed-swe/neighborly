package com.neighborly.app.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.NbTextField
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.Enums
import com.neighborly.app.data.model.ReportTarget
import com.neighborly.app.data.repository.ReportRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportUiState(val busy: Boolean = false, val sent: Boolean = false, val error: String? = null)

@HiltViewModel
class ReportViewModel @Inject constructor(private val reports: ReportRepo) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState())
    val state = _state.asStateFlow()

    fun submit(target: ReportTarget, id: String, reason: String, details: String) {
        _state.value = ReportUiState(busy = true)
        viewModelScope.launch {
            reports.create(target.value, id, reason, details)
                .onSuccess { _state.value = ReportUiState(sent = true) }
                .onFailure { _state.value = ReportUiState(error = it.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(
    target: ReportTarget,
    targetId: String,
    onDismiss: () -> Unit,
    onSent: () -> Unit = {},
    vm: ReportViewModel = hiltViewModel(key = "report-${target.value}-$targetId"),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var reason by rememberSaveable { mutableStateOf(Enums.REPORT_REASONS.first()) }
    var details by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(s.sent) { if (s.sent) { onSent(); onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.report_title, Format.labelize(target.value).lowercase()), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.report_text), style = MaterialTheme.typography.bodySmall)
            Column(Modifier.selectableGroup()) {
                Enums.REPORT_REASONS.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().height(48.dp)
                            .selectable(selected = reason == r, onClick = { reason = r }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Text(Format.labelize(r), Modifier.padding(start = 12.dp))
                    }
                }
            }
            NbTextField(details, { details = it.take(2000) }, stringResource(R.string.report_details), singleLine = false, minLines = 2)
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { vm.submit(target, targetId, reason, details) },
                enabled = !s.busy,
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 0.dp),
            ) { Text(stringResource(R.string.action_send_report)) }
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        }
    }
}
