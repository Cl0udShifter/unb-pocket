package com.unbpocket.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.unbpocket.core.model.notification.AulaLeadMinutes
import com.unbpocket.core.model.notification.SigaaNotificacaoConfig
import com.unbpocket.core.model.notification.SigaaSyncIntervalHours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sigaaNotificationsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sigaa_notifications",
)

/**
 * Persistencia em DataStore para configuracoes de notificacao de Aulas & Turmas e Painel Discente.
 */
class SigaaNotificationPreferencesStore(
    private val context: Context,
) : SigaaNotifPrefsSource {

    override val config: Flow<SigaaNotificacaoConfig> = context.sigaaNotificationsDataStore.data.map { prefs ->
        val rawInterval = prefs[KEY_SYNC_INTERVAL]
        val syncInterval = SigaaSyncIntervalHours.entries.firstOrNull { it.name == rawInterval }
            ?: SigaaSyncIntervalHours.H4

        SigaaNotificacaoConfig(
            habilitado = prefs[KEY_HABILITADO] ?: false,
            turmasHabilitado = prefs[KEY_TURMAS_HABILITADO] ?: true,
            aulasLembrete = prefs[KEY_AULAS_LEMBRETE] ?: true,
            antecedenciaAulaMinutos = prefs[KEY_ANTECEDENCIA_AULA_MIN] ?: AulaLeadMinutes.MIN_30.minutes,
            avisosEMateriaisNovos = prefs[KEY_AVISOS_E_MATERIAIS] ?: true,
            provasETarefasLembrete = prefs[KEY_PROVAS_E_TAREFAS] ?: true,
            notasParciaisAlteradas = prefs[KEY_NOTAS_PARCIAIS] ?: true,
            riscoFaltaAlerta = prefs[KEY_RISCO_FALTA] ?: true,
            turmasSilenciadas = prefs[KEY_TURMAS_SILENCIADAS].orEmpty(),
            discenteHabilitado = prefs[KEY_DISCENTE_HABILITADO] ?: true,
            saldoRuBaixoAlerta = prefs[KEY_SALDO_RU_BAIXO] ?: true,
            limiteRefeicoesRuBaixo = prefs[KEY_LIMITE_REFEICOES_RU]
                ?: SigaaNotificacaoConfig.DEFAULT_LOW_RU_MEALS_THRESHOLD,
            extratoRuMovimentacaoAlerta = prefs[KEY_EXTRATO_RU_MOV] ?: true,
            boletimAlteradoAlerta = prefs[KEY_BOLETIM_ALTERADO] ?: true,
            indicesAlteradosAlerta = prefs[KEY_INDICES_ALTERADOS] ?: true,
            syncIntervalHours = syncInterval,
            somenteWifi = prefs[KEY_SOMENTE_WIFI] ?: false,
            userConfigured = prefs[KEY_USER_CONFIGURED] ?: false,
        )
    }

    override suspend fun setConfig(config: SigaaNotificacaoConfig) {
        context.sigaaNotificationsDataStore.edit { prefs ->
            prefs[KEY_HABILITADO] = config.habilitado
            prefs[KEY_TURMAS_HABILITADO] = config.turmasHabilitado
            prefs[KEY_AULAS_LEMBRETE] = config.aulasLembrete
            prefs[KEY_ANTECEDENCIA_AULA_MIN] = config.antecedenciaAulaMinutos
            prefs[KEY_AVISOS_E_MATERIAIS] = config.avisosEMateriaisNovos
            prefs[KEY_PROVAS_E_TAREFAS] = config.provasETarefasLembrete
            prefs[KEY_NOTAS_PARCIAIS] = config.notasParciaisAlteradas
            prefs[KEY_RISCO_FALTA] = config.riscoFaltaAlerta
            prefs[KEY_TURMAS_SILENCIADAS] = config.turmasSilenciadas
            prefs[KEY_DISCENTE_HABILITADO] = config.discenteHabilitado
            prefs[KEY_SALDO_RU_BAIXO] = config.saldoRuBaixoAlerta
            prefs[KEY_LIMITE_REFEICOES_RU] = config.limiteRefeicoesRuBaixo
            prefs[KEY_EXTRATO_RU_MOV] = config.extratoRuMovimentacaoAlerta
            prefs[KEY_BOLETIM_ALTERADO] = config.boletimAlteradoAlerta
            prefs[KEY_INDICES_ALTERADOS] = config.indicesAlteradosAlerta
            prefs[KEY_SYNC_INTERVAL] = config.syncIntervalHours.name
            prefs[KEY_SOMENTE_WIFI] = config.somenteWifi
            prefs[KEY_USER_CONFIGURED] = config.userConfigured
        }
    }

    override suspend fun resetForLogout() {
        context.sigaaNotificationsDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private companion object {
        val KEY_HABILITADO = booleanPreferencesKey("sigaa_notif_habilitado")
        val KEY_TURMAS_HABILITADO = booleanPreferencesKey("sigaa_notif_turmas_habilitado")
        val KEY_AULAS_LEMBRETE = booleanPreferencesKey("sigaa_notif_aulas_lembrete")
        val KEY_ANTECEDENCIA_AULA_MIN = intPreferencesKey("sigaa_notif_antecedencia_aula_min")
        val KEY_AVISOS_E_MATERIAIS = booleanPreferencesKey("sigaa_notif_avisos_materiais")
        val KEY_PROVAS_E_TAREFAS = booleanPreferencesKey("sigaa_notif_provas_tarefas")
        val KEY_NOTAS_PARCIAIS = booleanPreferencesKey("sigaa_notif_notas_parciais")
        val KEY_RISCO_FALTA = booleanPreferencesKey("sigaa_notif_risco_falta")
        val KEY_TURMAS_SILENCIADAS = stringSetPreferencesKey("sigaa_notif_turmas_silenciadas")
        val KEY_DISCENTE_HABILITADO = booleanPreferencesKey("sigaa_notif_discente_habilitado")
        val KEY_SALDO_RU_BAIXO = booleanPreferencesKey("sigaa_notif_saldo_ru_baixo")
        val KEY_LIMITE_REFEICOES_RU = intPreferencesKey("sigaa_notif_limite_refeicoes_ru")
        val KEY_EXTRATO_RU_MOV = booleanPreferencesKey("sigaa_notif_extrato_ru")
        val KEY_BOLETIM_ALTERADO = booleanPreferencesKey("sigaa_notif_boletim")
        val KEY_INDICES_ALTERADOS = booleanPreferencesKey("sigaa_notif_indices")
        val KEY_SYNC_INTERVAL = stringPreferencesKey("sigaa_notif_sync_interval")
        val KEY_SOMENTE_WIFI = booleanPreferencesKey("sigaa_notif_somente_wifi")
        val KEY_USER_CONFIGURED = booleanPreferencesKey("sigaa_notif_user_configured")
    }
}
