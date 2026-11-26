package app.youplay.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youplay.data.local.AppSettings
import app.youplay.data.repository.VideoRepository
import app.youplay.domain.AccountState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val account: StateFlow<AccountState> = repository.account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountState())

    val accounts: StateFlow<List<AccountState>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun pull() = run("Pulled %d items from your account") { repository.syncFromAccount() }
    fun push() = run("Pushed %d items to your account") { repository.pushLocalToAccount() }
    fun signOut() { viewModelScope.launch { repository.signOut() } }
    fun signOutAll() { viewModelScope.launch { repository.signOutAll() } }
    fun switchTo(id: String) { viewModelScope.launch { repository.switchAccount(id) } }
    fun dismiss() { _message.value = null }

    private fun run(fmt: String, block: suspend () -> Int) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = runCatching { fmt.format(block()) }.getOrElse { "Sync failed: ${it.message}" }
            _busy.value = false
        }
    }
}
