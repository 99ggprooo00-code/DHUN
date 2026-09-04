package dev.dhun.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dhun.data.DataLayer
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.player.DhunPlayer

/**
 * Factory for [SearchViewModel] — scoped to the app's ViewModelStore.
 */
class SearchViewModelFactory(
    private val innerTubeClient: InnerTubeClient,
    private val player: DhunPlayer,
    private val data: DataLayer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SearchViewModel(
            innerTubeClient = innerTubeClient,
            player = player,
            data = data,
            scope = viewModelScope,
        ) as T
    }
}
