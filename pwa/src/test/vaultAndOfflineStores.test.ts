import 'fake-indexeddb/auto';
import { describe, expect, it } from 'vitest';
import { BlobFileStore } from '../core/data/local/blobFileStore';
import {
  CacheDomains,
  CacheKeys,
  SnapshotCacheStore,
} from '../core/data/local/snapshotCacheStore';
import { WebCryptoCredentialVault } from '../core/data/security/webCryptoVault';
import { createRgbImageBuffer } from '../core/parser/ruAllergenClassifier';

describe('WebCryptoCredentialVault, SnapshotCacheStore & BlobFileStore', () => {
  it('encrypts credentials with AES-GCM 256 in IndexedDB and wipes them on clear', async () => {
    const vault = new WebCryptoCredentialVault();
    expect(await vault.exists()).toBe(false);

    await vault.save('190026308', 'Senha#Segura!2026');
    expect(await vault.exists()).toBe(true);

    const loaded = await vault.load();
    expect(loaded).toEqual({
      matricula: '190026308',
      password: 'Senha#Segura!2026',
    });

    await vault.clear();
    expect(await vault.exists()).toBe(false);
    expect(await vault.load()).toBeNull();
  });

  it('enforces Diff-Before-Write and clears only private SIGAA domains on logout', async () => {
    const store = new SnapshotCacheStore('test_cache_db_1');

    const firstWrite = await store.put({
      cacheKey: CacheKeys.cardapio('DARCY_RIBEIRO'),
      domain: CacheDomains.RU,
      origin: 'REMOTE',
      syncedAtEpochMillis: 1000,
      value: { campus: 'DARCY_RIBEIRO', dias: [] },
    });
    expect(firstWrite).toBe(true);

    // Identical payload and origin must return false (no redundant write/re-emission)
    const duplicateWrite = await store.put({
      cacheKey: CacheKeys.cardapio('DARCY_RIBEIRO'),
      domain: CacheDomains.RU,
      origin: 'REMOTE',
      syncedAtEpochMillis: 2000,
      value: { campus: 'DARCY_RIBEIRO', dias: [] },
    });
    expect(duplicateWrite).toBe(false);

    await store.put({
      cacheKey: CacheKeys.DISCENTE_RESUMO,
      domain: CacheDomains.DISCENTE,
      origin: 'REMOTE',
      syncedAtEpochMillis: 1500,
      value: { matricula: '190026308' },
    });
    await store.put({
      cacheKey: CacheKeys.RU_SALDO,
      domain: CacheDomains.RU,
      origin: 'REMOTE',
      syncedAtEpochMillis: 1500,
      value: { saldoAtualCentavos: 865 },
    });

    await store.clearSigaaUserData();

    expect(await store.get(CacheKeys.DISCENTE_RESUMO)).toBeNull();
    expect(await store.get(CacheKeys.RU_SALDO)).toBeNull();
    // Public RU menu remains intact!
    expect(await store.get(CacheKeys.cardapio('DARCY_RIBEIRO'))).not.toBeNull();
  });

  it('stores binary blobs/PNGs and preserves public RU PDFs when clearing SIGAA files', async () => {
    const files = new BlobFileStore('test_files_db_1');
    const ruPdf = await files.saveBytes(
      'pdfs',
      'cardapio_darcy-ribeiro.pdf',
      new Uint8Array([0x25, 0x50, 0x44, 0x46]),
      'application/pdf',
    );
    const rgb = createRgbImageBuffer(4, 4, new Uint8Array(4 * 4 * 3).fill(128));
    const qrPng = await files.saveRgbAsPng('carteirinha', 'qr_img4.png', rgb);

    expect(await files.exists(ruPdf.path)).toBe(true);
    expect(await files.exists(qrPng.path)).toBe(true);

    const qrBlob = await files.readBlob(qrPng.path);
    expect(qrBlob?.type).toBe('image/png');

    await files.clearSigaaUserFiles();
    expect(await files.exists(qrPng.path)).toBe(false);
    expect(await files.exists(ruPdf.path)).toBe(true);
  });
});
