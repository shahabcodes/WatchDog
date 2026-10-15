package com.shahaabapps.watchdog.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.datastore.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Exposes the parent's chosen [MapStyle] so every map surface (live map,
 * dashboard hero) renders the same style. Read-only; the picker lives in
 * Settings.
 */
@HiltViewModel
class MapStyleViewModel @Inject constructor(
    themePreferences: ThemePreferences,
) : ViewModel() {
    val style: StateFlow<MapStyle> = themePreferences.mapStyleName
        .map { MapStyle.from(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapStyle.DEFAULT)
}
