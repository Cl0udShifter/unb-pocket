package com.unbpocket.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.unbpocket.core.model.ru.Alergenico
import com.unbpocket.core.model.ru.CampusRu
import com.unbpocket.core.model.ru.RuPreferencias
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ruPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ru_preferences")

/**
 * Armazenamento DataStore para preferencias do Restaurante Universitario.
 */
class RuPreferencesStore(
    private val context: Context,
) : RuPrefsSource {
    override val preferencias: Flow<RuPreferencias> = context.ruPrefsDataStore.data.map { prefs ->
        val campusSlug = prefs[KEY_CAMPUS] ?: CampusRu.DARCY_RIBEIRO.slug
        val campus = CampusRu.entries.firstOrNull { it.slug == campusSlug } ?: CampusRu.DARCY_RIBEIRO
        val alergenicoIds = prefs[KEY_ALERGENICOS] ?: emptySet()
        val alergenicos = alergenicoIds
            .mapNotNull { id -> Alergenico.entries.firstOrNull { it.id == id } }
            .toSet()
        val ocultar = prefs[KEY_OCULTAR_ALERGENICOS] ?: false
        RuPreferencias(
            campusSelecionado = campus,
            alergenicosRestritos = alergenicos,
            ocultarPratosComAlergenicos = ocultar,
        )
    }

    override suspend fun setCampus(campus: CampusRu) {
        context.ruPrefsDataStore.edit { prefs ->
            prefs[KEY_CAMPUS] = campus.slug
        }
    }

    override suspend fun setAlergenicos(alergenicos: Set<Alergenico>, ocultarPratos: Boolean) {
        context.ruPrefsDataStore.edit { prefs ->
            prefs[KEY_ALERGENICOS] = alergenicos.map { it.id }.toSet()
            prefs[KEY_OCULTAR_ALERGENICOS] = ocultarPratos
        }
    }

    private companion object {
        val KEY_CAMPUS = stringPreferencesKey("campus_slug")
        val KEY_ALERGENICOS = stringSetPreferencesKey("restricted_allergens")
        val KEY_OCULTAR_ALERGENICOS = booleanPreferencesKey("hide_dishes_with_allergens")
    }
}
