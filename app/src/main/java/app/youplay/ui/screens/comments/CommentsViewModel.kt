package app.youplay.ui.screens.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.youplay.data.repository.VideoRepository
import app.youplay.domain.CommentItem
import app.youplay.domain.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CommentsUi(
    val comments: List<CommentItem> = emptyList(),
    val createParams: String? = null,
    val continuation: String? = null,
    val loadingMore: Boolean = false,
    val posting: Boolean = false,
    val liked: Set<String> = emptySet(),
)

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val repository: VideoRepository,
) : ViewModel() {
    private var videoId: String = ""
    private val _state = MutableStateFlow<UiState<CommentsUi>>(UiState.Loading)
    val state: StateFlow<UiState<CommentsUi>> = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    val signedIn: StateFlow<Boolean> = repository.account.map { it.signedIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val account: StateFlow<app.youplay.domain.AccountState> = repository.account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), app.youplay.domain.AccountState())

    /** Called by the sheet; loads this video's comments from the account session once. */
    fun start(id: String) { if (id != videoId) { videoId = id; refresh() } }

    private fun ui(): CommentsUi? = (_state.value as? UiState.Success)?.data
    private fun edit(f: (CommentsUi) -> CommentsUi) { ui()?.let { _state.value = UiState.Success(f(it)) } }
    fun messageShown() { _message.value = null }

    fun refresh() {
        if (videoId.isEmpty()) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching {
                val p = repository.commentsPage(videoId)
                UiState.Success(CommentsUi(p.comments, p.createParams, p.continuation))
            }.getOrElse { UiState.Error(it.message ?: "Failed to load comments", it) }
        }
    }

    fun loadMore() {
        val cur = ui() ?: return
        val token = cur.continuation ?: return
        if (cur.loadingMore) return
        edit { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val p = runCatching { repository.commentsPage(videoId, token) }.getOrNull()
            edit { c ->
                val have = c.comments.map { it.id }.toHashSet()
                c.copy(
                    comments = c.comments + p?.comments.orEmpty().filter { have.add(it.id) },
                    continuation = p?.continuation, loadingMore = false,
                )
            }
        }
    }

    /** Posts a top-level comment, or a reply when [replyTo] is set. */
    fun post(text: String, replyTo: CommentItem?, onDone: () -> Unit) {
        val cur = ui() ?: return
        val t = text.trim()
        if (t.isEmpty() || cur.posting) return
        edit { it.copy(posting = true) }
        viewModelScope.launch {
            runCatching {
                if (replyTo != null) {
                    val params = replyTo.replyParams ?: error("Replies aren't available for this comment")
                    repository.replyToComment(params, t)
                } else {
                    val params = cur.createParams ?: error("Comments are turned off or you're not signed in")
                    repository.postComment(params, t)
                }
            }.onSuccess { page ->
                onDone()
                if (replyTo == null && page.comments.isNotEmpty()) {
                    edit { it.copy(comments = page.comments.take(1) + it.comments.filter { c -> c.id != page.comments.first().id }, posting = false) }
                } else {
                    edit { c -> c.copy(posting = false, comments = c.comments.map { if (it.id == replyTo?.id) it.copy(replyCount = it.replyCount + 1) else it }) }
                }
                _message.value = if (replyTo == null) "Comment posted" else "Reply posted"
            }.onFailure {
                edit { c -> c.copy(posting = false) }
                _message.value = "Couldn't post: ${it.message}"
            }
        }
    }

    fun delete(c: CommentItem) {
        val token = c.deleteToken ?: return
        viewModelScope.launch {
            runCatching { repository.commentAction(token) }
                .onSuccess { edit { u -> u.copy(comments = u.comments.filter { it.id != c.id }) }; _message.value = "Comment deleted" }
                .onFailure { _message.value = "Couldn't delete: ${it.message}" }
        }
    }

    fun toggleLike(c: CommentItem) {
        val on = ui()?.liked?.contains(c.id) == true
        val token = (if (on) c.unlikeToken else c.likeToken) ?: return
        edit { u -> u.copy(liked = if (on) u.liked - c.id else u.liked + c.id) } // optimistic
        viewModelScope.launch {
            runCatching { repository.commentAction(token) }.onFailure {
                edit { u -> u.copy(liked = if (on) u.liked + c.id else u.liked - c.id) }
                _message.value = "Couldn't update like: ${it.message}"
            }
        }
    }
}
