package com.neighborly.app.feature.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.neighborly.app.core.designsystem.SelectField
import com.neighborly.app.data.model.Neighborhood
import com.neighborly.app.data.repository.AuthRepo
import com.neighborly.app.data.repository.LookupRepo
import com.neighborly.app.data.repository.SignUpForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignupUiState(
    val fullName: String = "",
    val username: String = "",
    val phone: String = "",
    val neighborhood: Neighborhood? = null,
    val email: String = "",
    val password: String = "",
    val neighborhoods: List<Neighborhood> = emptyList(),
    @StringRes val fieldError: Int? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val checkEmail: Boolean = false,
)

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val auth: AuthRepo,
    private val lookups: LookupRepo,
) : ViewModel() {
    private val _state = MutableStateFlow(SignupUiState())
    val state: StateFlow<SignupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            lookups.neighborhoods().onSuccess { list -> _state.update { it.copy(neighborhoods = list) } }
        }
    }

    fun update(transform: SignupUiState.() -> SignupUiState) =
        _state.update { it.transform().copy(fieldError = null, error = null) }

    /** Returns a string resource for the first invalid field, or null. */
    fun validate(s: SignupUiState): Int? = when {
        s.fullName.isBlank() -> R.string.err_name_required
        !AuthRepo.USERNAME_REGEX.matches(s.username.trim().lowercase()) -> R.string.err_username
        s.neighborhood == null -> R.string.err_neighborhood
        !android.util.Patterns.EMAIL_ADDRESS.matcher(s.email.trim()).matches() -> R.string.err_email
        s.password.length < AuthRepo.MIN_PASSWORD -> R.string.err_password
        else -> null
    }

    fun submit() {
        val s = _state.value
        if (s.busy) return
        validate(s)?.let { err -> _state.update { it.copy(fieldError = err) }; return }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            auth.signUp(SignUpForm(s.fullName, s.username, s.phone, s.neighborhood?.id, s.email, s.password))
                .onSuccess { signedIn -> _state.update { it.copy(busy = false, checkEmail = !signedIn) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }
}

@Composable
fun SignupScreen(onLogin: () -> Unit, vm: SignupViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    AuthScaffold {
        NbCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s.checkEmail) {
                    Text(stringResource(R.string.auth_check_email_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.auth_check_email_text, s.email))
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(stringResource(R.string.action_log_in))
                    }
                    return@Column
                }
                Text(stringResource(R.string.auth_create_account), style = MaterialTheme.typography.titleMedium)
                NbTextField(s.fullName, { v -> vm.update { copy(fullName = v) } }, stringResource(R.string.field_full_name))
                NbTextField(
                    s.username, { v -> vm.update { copy(username = v.lowercase().trim()) } },
                    stringResource(R.string.field_username),
                    supporting = stringResource(R.string.hint_username),
                )
                NbTextField(s.phone, { v -> vm.update { copy(phone = v) } }, stringResource(R.string.field_phone), keyboardType = KeyboardType.Phone)
                SelectField(
                    label = stringResource(R.string.field_neighborhood),
                    options = s.neighborhoods,
                    selected = s.neighborhood,
                    optionLabel = { it.name },
                    onSelect = { n -> vm.update { copy(neighborhood = n) } },
                )
                NbTextField(s.email, { v -> vm.update { copy(email = v) } }, stringResource(R.string.field_email), keyboardType = KeyboardType.Email)
                OutlinedTextField(
                    value = s.password,
                    onValueChange = { v -> vm.update { copy(password = v) } },
                    label = { Text(stringResource(R.string.field_password)) },
                    supportingText = { Text(stringResource(R.string.hint_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                val err = s.fieldError?.let { stringResource(it) } ?: s.error
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = vm::submit, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    if (s.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.action_sign_up))
                }
                TextButton(onClick = onLogin, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.auth_have_account))
                }
            }
        }
    }
}
