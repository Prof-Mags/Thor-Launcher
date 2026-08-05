package com.thor.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thor.core.model.GridEntry
import com.thor.data.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the search overlay renders. */
data class SearchUiState(
    val query: String = "",
    val results: List<GridEntry> = emptyList(),
    val isSearching: Boolean = false,
    val focusedIndex: Int = 0,
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val isEmpty: Boolean get() = hasQuery && !isSearching && results.isEmpty()
}

/**
 * Global search.
 *
 * Queries are debounced and the previous search is cancelled by `flatMapLatest`
 * when a new keystroke arrives, so typing quickly never queues up a backlog of
 * database scans that all resolve at once.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val focusedIndex = MutableStateFlow(0)

    val uiState: StateFlow<SearchUiState> = query
        .debounce { text -> if (text.isBlank()) 0L else DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (text.isBlank()) {
                // An empty query is the app drawer, not an empty result set —
                // this surface doubles as both, and showing nothing until the
                // user types would make the drawer look broken.
                libraryRepository.apps.map { apps ->
                    SearchUiState(
                        query = "",
                        results = apps.sortedBy { it.sortTitle },
                        isSearching = false,
                    )
                }
            } else {
                flow {
                    emit(SearchUiState(query = text, isSearching = true))
                    val results = libraryRepository.search(text)
                    emit(SearchUiState(query = text, results = results, isSearching = false))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState(),
        )

    val focused: StateFlow<Int> = focusedIndex.asStateFlow()

    fun onQueryChanged(text: String) {
        query.value = text
        focusedIndex.value = 0
    }

    fun clear() {
        query.value = ""
        focusedIndex.value = 0
    }

    fun moveFocus(delta: Int) {
        val size = uiState.value.results.size
        if (size == 0) return
        focusedIndex.value = (focusedIndex.value + delta).coerceIn(0, size - 1)
    }

    fun focusedEntry(): GridEntry? = uiState.value.results.getOrNull(focusedIndex.value)

    private companion object {
        /**
         * Long enough to skip intermediate keystrokes, short enough that
         * results still feel live while typing.
         */
        const val DEBOUNCE_MS = 180L
    }
}
