import { openDB, type IDBPDatabase } from 'idb';
import {
  CAMPUS_RU_BY_ID,
  DEFAULT_RU_PREFERENCIAS,
  DEFAULT_SIGAA_NOTIFICACAO_CONFIG,
  MutableStateValue,
  type Alergenico,
  type CampusRu,
  type DataOrigin,
  type ObservableValue,
  type RuNotificacaoConfig,
  type RuPreferencias,
  type SigaaNotificacaoConfig,
  type SyncedData,
} from '../../model/models';

interface SnapshotEntity {
  cacheKey: string;
  domain: string;
  payloadJson: string;
  origin: DataOrigin;
  syncedAtEpochMillis: number;
}

interface UnbCacheDbSchema {
  snapshots: {
    key: string;
    value: SnapshotEntity;
    indexes: { 'by-domain': string };
  };
  preferences: {
    key: string;
    value: unknown;
  };
}

const CACHE_DB_NAME = 'unb_pocket_cache_v1';

export const CacheDomains = {
  RU: 'ru',
  TURMAS: 'turmas',
  DISCENTE: 'discente',
  META: 'meta',
} as const;

export const CacheKeys = {
  cardapio: (campus: CampusRu) => `ru_cardapio_${CAMPUS_RU_BY_ID[campus].slug}`,
  RU_SALDO: 'ru_saldo',
  RU_PRECOS: 'ru_precos',
  RU_NOTICIAS: 'ru_noticias',
  RU_AUTO_SYNC_META: 'ru_auto_sync_meta',
  TURMAS_LIST: 'turmas_semestre',
  TURMAS_FEED: 'turmas_feed',
  turmaDetalhe: (frontEndId: string) => `turma_detalhe_${frontEndId}`,
  turmaFrequencia: (frontEndId: string) => `turma_freq_${frontEndId}`,
  turmaNotas: (frontEndId: string) => `turma_notas_${frontEndId}`,
  turmaAvaliacoes: (frontEndId: string) => `turma_aval_${frontEndId}`,
  turmaTarefas: (frontEndId: string) => `turma_tarefas_${frontEndId}`,
  turmaNoticia: (frontEndId: string, idNoticia: string) => `turma_noticia_${frontEndId}_${idNoticia}`,
  DISCENTE_RESUMO: 'discente_resumo',
  DISCENTE_CARTEIRINHA: 'discente_carteirinha',
  DISCENTE_BOLETIM: 'discente_boletim',
  DISCENTE_DOCUMENTOS: 'discente_documentos',
  SIGAA_NOTIF_BASELINE: 'sigaa_notif_baseline',
  SIGAA_NOTIF_SENT_KEYS: 'sigaa_notif_sent_keys',
} as const;

let sharedDbPromise: Promise<IDBPDatabase<UnbCacheDbSchema>> | null = null;

function getSharedCacheDb(dbName: string = CACHE_DB_NAME): Promise<IDBPDatabase<UnbCacheDbSchema>> {
  if (dbName === CACHE_DB_NAME && sharedDbPromise) {
    return sharedDbPromise;
  }
  const p = openDB<UnbCacheDbSchema>(dbName, 1, {
    upgrade(db) {
      if (!db.objectStoreNames.contains('snapshots')) {
        const store = db.createObjectStore('snapshots', { keyPath: 'cacheKey' });
        store.createIndex('by-domain', 'domain');
      }
      if (!db.objectStoreNames.contains('preferences')) {
        db.createObjectStore('preferences');
      }
    },
  });
  if (dbName === CACHE_DB_NAME) {
    sharedDbPromise = p;
  }
  return p;
}

/**
 * Camada de cache offline-first sobre IndexedDB com comparacao estrutural
 * (Diff-Before-Write) identica ao `SnapshotCacheStore` do Android Room.
 */
export class SnapshotCacheStore {
  private readonly dbPromise: Promise<IDBPDatabase<UnbCacheDbSchema>>;
  private readonly channels = new Map<string, MutableStateValue<SyncedData<unknown> | null>>();
  private readonly hydratedKeys = new Set<string>();

  constructor(dbName: string = CACHE_DB_NAME) {
    this.dbPromise = getSharedCacheDb(dbName);
  }

  observe<T>(
    cacheKey: string,
    fallbackIfMissing: SyncedData<T> | null = null,
  ): ObservableValue<SyncedData<T> | null> {
    let channel = this.channels.get(cacheKey);
    if (!channel) {
      channel = new MutableStateValue<SyncedData<unknown> | null>(fallbackIfMissing);
      this.channels.set(cacheKey, channel);
    }

    if (!this.hydratedKeys.has(cacheKey)) {
      this.hydratedKeys.add(cacheKey);
      void this.get<T>(cacheKey).then((stored) => {
        if (stored !== null) {
          channel!.value = stored;
        } else if (channel!.value === null && fallbackIfMissing !== null) {
          channel!.value = fallbackIfMissing;
        }
      });
    }

    return channel as ObservableValue<SyncedData<T> | null>;
  }

  async get<T>(cacheKey: string): Promise<SyncedData<T> | null> {
    try {
      const db = await this.dbPromise;
      const entity = await db.get('snapshots', cacheKey);
      if (!entity) return null;
      const parsed = JSON.parse(entity.payloadJson) as T;
      return {
        value: parsed,
        origin: entity.origin,
        syncedAtEpochMillis: entity.syncedAtEpochMillis,
      };
    } catch {
      return null;
    }
  }

  async put<T>(params: {
    cacheKey: string;
    domain: string;
    origin: DataOrigin;
    syncedAtEpochMillis: number;
    value: T;
  }): Promise<boolean> {
    const db = await this.dbPromise;
    const payloadJson = JSON.stringify(params.value);
    const existing = await db.get('snapshots', params.cacheKey);

    if (
      existing &&
      existing.payloadJson === payloadJson &&
      existing.origin === params.origin
    ) {
      // Ensure in-memory channel reflects cached state on cold start
      const channel = this.channels.get(params.cacheKey);
      if (channel && channel.value === null) {
        channel.value = {
          value: params.value,
          origin: existing.origin,
          syncedAtEpochMillis: existing.syncedAtEpochMillis,
        };
      }
      return false;
    }

    const entity: SnapshotEntity = {
      cacheKey: params.cacheKey,
      domain: params.domain,
      payloadJson,
      origin: params.origin,
      syncedAtEpochMillis: params.syncedAtEpochMillis,
    };
    await db.put('snapshots', entity);

    const synced: SyncedData<T> = {
      value: params.value,
      origin: params.origin,
      syncedAtEpochMillis: params.syncedAtEpochMillis,
    };
    this.hydratedKeys.add(params.cacheKey);
    const channel = this.channels.get(params.cacheKey);
    if (channel) {
      channel.value = synced;
    } else {
      this.channels.set(
        params.cacheKey,
        new MutableStateValue<SyncedData<unknown> | null>(synced),
      );
    }
    return true;
  }

  async clearKey(cacheKey: string): Promise<void> {
    const db = await this.dbPromise;
    await db.delete('snapshots', cacheKey);
    const channel = this.channels.get(cacheKey);
    if (channel) {
      channel.value = null;
    }
  }

  async clearDomain(domain: string): Promise<void> {
    const db = await this.dbPromise;
    const tx = db.transaction('snapshots', 'readwrite');
    const idx = tx.store.index('by-domain');
    let cursor = await idx.openCursor(domain);
    const clearedKeys: string[] = [];
    while (cursor) {
      clearedKeys.push(String(cursor.primaryKey));
      await cursor.delete();
      cursor = await cursor.continue();
    }
    await tx.done;

    for (const key of clearedKeys) {
      const channel = this.channels.get(key);
      if (channel) {
        channel.value = null;
      }
    }
  }

  async clearSigaaUserData(): Promise<void> {
    await this.clearDomain(CacheDomains.DISCENTE);
    await this.clearDomain(CacheDomains.TURMAS);
    await this.clearKey(CacheKeys.RU_SALDO);
  }
}

export class RuPreferencesStore {
  private readonly dbPromise: Promise<IDBPDatabase<UnbCacheDbSchema>>;
  private readonly state = new MutableStateValue<RuPreferencias>(DEFAULT_RU_PREFERENCIAS);
  private static readonly PREF_KEY = 'ru_preferencias_v1';

  constructor(dbName: string = CACHE_DB_NAME) {
    this.dbPromise = getSharedCacheDb(dbName);
    void this.hydrate();
  }

  get preferencias(): ObservableValue<RuPreferencias> {
    return this.state;
  }

  private async hydrate(): Promise<void> {
    try {
      const db = await this.dbPromise;
      const saved = (await db.get('preferences', RuPreferencesStore.PREF_KEY)) as
        | Partial<RuPreferencias>
        | undefined;
      if (saved) {
        this.state.value = {
          ...DEFAULT_RU_PREFERENCIAS,
          ...saved,
          notificacoes: saved.notificacoes ?? DEFAULT_RU_PREFERENCIAS.notificacoes,
        };
      }
    } catch {
      // Keep defaults
    }
  }

  private async persist(next: RuPreferencias): Promise<void> {
    this.state.value = next;
    const db = await this.dbPromise;
    await db.put('preferences', next, RuPreferencesStore.PREF_KEY);
  }

  async setCampus(campus: CampusRu): Promise<void> {
    await this.persist({ ...this.state.value, campusSelecionado: campus });
  }

  async setAlergenicos(
    alergenicos: readonly Alergenico[],
    ocultarPratos: boolean,
  ): Promise<void> {
    await this.persist({
      ...this.state.value,
      alergenicosRestritos: [...alergenicos],
      ocultarPratosComAlergenicos: ocultarPratos,
    });
  }

  async setNotificacoes(notificacoes: RuNotificacaoConfig): Promise<void> {
    await this.persist({ ...this.state.value, notificacoes });
  }

  async markNoticiasVistas(ids: readonly number[]): Promise<void> {
    const merged = Array.from(new Set([...this.state.value.noticiasVistasIds, ...ids]));
    await this.persist({ ...this.state.value, noticiasVistasIds: merged });
  }
}

export class SigaaNotificationPreferencesStore {
  private readonly dbPromise: Promise<IDBPDatabase<UnbCacheDbSchema>>;
  private readonly state = new MutableStateValue<SigaaNotificacaoConfig>(
    DEFAULT_SIGAA_NOTIFICACAO_CONFIG,
  );
  private static readonly PREF_KEY = 'sigaa_notif_prefs_v1';

  constructor(dbName: string = CACHE_DB_NAME) {
    this.dbPromise = getSharedCacheDb(dbName);
    void this.hydrate();
  }

  get config(): ObservableValue<SigaaNotificacaoConfig> {
    return this.state;
  }

  private async hydrate(): Promise<void> {
    try {
      const db = await this.dbPromise;
      const saved = (await db.get('preferences', SigaaNotificationPreferencesStore.PREF_KEY)) as
        | Partial<SigaaNotificacaoConfig>
        | undefined;
      if (saved) {
        this.state.value = {
          ...DEFAULT_SIGAA_NOTIFICACAO_CONFIG,
          ...saved,
        };
      }
    } catch {
      // Keep defaults
    }
  }

  async setConfig(config: SigaaNotificacaoConfig): Promise<void> {
    this.state.value = config;
    const db = await this.dbPromise;
    await db.put('preferences', config, SigaaNotificationPreferencesStore.PREF_KEY);
  }

  async resetForLogout(): Promise<void> {
    this.state.value = DEFAULT_SIGAA_NOTIFICACAO_CONFIG;
    const db = await this.dbPromise;
    await db.delete('preferences', SigaaNotificationPreferencesStore.PREF_KEY);
  }
}
