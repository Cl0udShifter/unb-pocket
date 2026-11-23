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
import com.unbpocket.core.model.ru.DiaSemanaRu
import com.unbpocket.core.model.ru.RuNotificacaoConfig
import com.unbpocket.core.model.ru.RuPreferencias
import com.unbpocket.core.model.ru.TipoRefeicao
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
        val seenNoticeIds = prefs[KEY_NOTICIAS_VISTAS].orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
        val notifEnabled = prefs[KEY_NOTIF_ENABLED] ?: false
        val notifSlots = parseNotificacaoSlots(prefs[KEY_NOTIF_SLOTS].orEmpty())
        RuPreferencias(
            campusSelecionado = campus,
            alergenicosRestritos = alergenicos,
            ocultarPratosComAlergenicos = ocultar,
            noticiasVistasIds = seenNoticeIds,
            notificacoes = RuNotificacaoConfig(
                habilitado = notifEnabled,
                refeicoesPorDia = notifSlots,
            ),
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

    override suspend fun setNotificacoes(config: RuNotificacaoConfig) {
        context.ruPrefsDataStore.edit { prefs ->
            prefs[KEY_NOTIF_ENABLED] = config.habilitado
            prefs[KEY_NOTIF_SLOTS] = serializeNotificacaoSlots(config.refeicoesPorDia)
        }
    }

    override suspend fun markNoticiasVistas(ids: Set<Long>) {
        if (ids.isEmpty()) return
        context.ruPrefsDataStore.edit { prefs ->
            val current = prefs[KEY_NOTICIAS_VISTAS].orEmpty()
                .mapNotNull { it.toLongOrNull() }
            val merged = (current + ids).distinct().sorted().takeLast(MAX_TRACKED_NOTICES)
            prefs[KEY_NOTICIAS_VISTAS] = merged.map { it.toString() }.toSet()
        }
    }

    private companion object {
        const val MAX_TRACKED_NOTICES = 100
        val KEY_CAMPUS = stringPreferencesKey("campus_slug")
        val KEY_ALERGENICOS = stringSetPreferencesKey("restricted_allergens")
        val KEY_OCULTAR_ALERGENICOS = booleanPreferencesKey("hide_dishes_with_allergens")
        val KEY_NOTICIAS_VISTAS = stringSetPreferencesKey("seen_notice_ids")
        val KEY_NOTIF_ENABLED = booleanPreferencesKey("meal_notifications_enabled")
        val KEY_NOTIF_SLOTS = stringSetPreferencesKey("meal_notification_slots")

        fun serializeNotificacaoSlots(slots: Map<DiaSemanaRu, Set<TipoRefeicao>>): Set<String> =
            slots.flatMap { (dia, refeicoes) ->
                refeicoes.map { refeicao -> "${dia.dayOfWeekIso}:${refeicao.name}" }
            }.toSet()

        fun parseNotificacaoSlots(raw: Set<String>): Map<DiaSemanaRu, Set<TipoRefeicao>> {
            if (raw.isEmpty()) return emptyMap()
            val result = mutableMapOf<DiaSemanaRu, MutableSet<TipoRefeicao>>()
            raw.forEach { entry ->
                val parts = entry.split(':', limit = 2)
                if (parts.size != 2) return@forEach
                val dia = parts[0].toIntOrNull()?.let(DiaSemanaRu::fromDayOfWeekIso) ?: return@forEach
                val meal = TipoRefeicao.entries.firstOrNull { it.name == parts[1] } ?: return@forEach
                result.getOrPut(dia) { mutableSetOf() }.add(meal)
            }
            return result
        }
    }
}
