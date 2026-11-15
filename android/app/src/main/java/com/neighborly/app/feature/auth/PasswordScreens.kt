package com.neighborly.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.core.designsystem.NbTextField
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.data.repository.AuthRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PasswordUiState(val busy: Boolean = false, val done: Boolean = false, val error: String? = null)

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val auth: AuthRepo,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(PasswordUiState())
    val state = _state.asStateFlow()

    fun sendReset(email: String) {
        if (email.isBlank()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            auth.sendPasswordReset(email)
                .onSuccess { _state.update { it.copy(busy = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun setNewPassword(password: String, invalidMessage: String) {
        if (password.length < AuthRepo.MIN_PASSWORD) {
            _state.update { it.copy(error = invalidMessage) }; return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            auth.updatePassword(password)
                .onSuccess { session.recoveryFinished() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }
}

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, vm: PasswordViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    AuthScaffold {
        NbCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.auth_reset_title), style = MaterialTheme.typography.titleMedium)
                if (s.done) {
                    Text(stringResource(R.string.auth_reset_sent, email))
                } else {
                    Text(stringResource(R.string.auth_reset_text), style = MaterialTheme.typography.bodySmall)
                    NbTextField(email, { email = it }, stringResource(R.string.field_email), keyboardType = KeyboardType.Email)
                    s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { vm.sendReset(email) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(stringResource(R.string.auth_send_link))
                    }
                }
                TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.auth_back_to_login))
                }
            }
        }
    }
}

@Composable
fun NewPasswordScreen(onSignOut: () -> Unit, vm: PasswordViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var password by rememberSaveable { mutableStateOf("") }
    val invalid = stringResource(R.string.err_password)
    AuthScaffold {
        NbCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.auth_new_password_title), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.field_new_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { vm.setNewPassword(password, invalid) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(R.string.action_save_password))
                }
                TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.action_log_out))
                }
            }
        }
    }
}
