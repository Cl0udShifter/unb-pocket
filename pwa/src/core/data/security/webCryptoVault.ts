import { openDB, type IDBPDatabase } from 'idb';

export interface StoredCredentials {
  readonly matricula: string;
  readonly password: string;
}

export interface CredentialVault {
  save(matricula: string, password: string): Promise<void>;
  load(): Promise<StoredCredentials | null>;
  exists(): Promise<boolean>;
  clear(): Promise<void>;
}

interface VaultDbSchema {
  keys: {
    key: string;
    value: CryptoKey;
  };
  secrets: {
    key: string;
    value: {
      iv: Uint8Array;
      ciphertext: ArrayBuffer;
    };
  };
}

const VAULT_DB_NAME = 'unb_pocket_vault_v1';
const KEY_ID = 'aes_gcm_master_key';
const SECRET_ID = 'sigaa_credentials';

/**
 * Cofre de credenciais cifrado via Web Crypto API (`AES-GCM` 256-bit) com
 * `CryptoKey` nao-extraivel (`extractable: false`) persistida no IndexedDB.
 */
export class WebCryptoCredentialVault implements CredentialVault {
  private dbPromise: Promise<IDBPDatabase<VaultDbSchema>> | null = null;

  private getDb(): Promise<IDBPDatabase<VaultDbSchema>> {
    if (!this.dbPromise) {
      this.dbPromise = openDB<VaultDbSchema>(VAULT_DB_NAME, 1, {
        upgrade(db) {
          if (!db.objectStoreNames.contains('keys')) {
            db.createObjectStore('keys');
          }
          if (!db.objectStoreNames.contains('secrets')) {
            db.createObjectStore('secrets');
          }
        },
      });
    }
    return this.dbPromise;
  }

  private async getOrCreateMasterKey(): Promise<CryptoKey> {
    const db = await this.getDb();
    const existing = await db.get('keys', KEY_ID);
    if (existing) return existing;

    const generated = await crypto.subtle.generateKey(
      { name: 'AES-GCM', length: 256 },
      false, // non-extractable
      ['encrypt', 'decrypt'],
    );
    await db.put('keys', generated, KEY_ID);
    return generated;
  }

  async save(matricula: string, password: string): Promise<void> {
    const db = await this.getDb();
    const key = await this.getOrCreateMasterKey();
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const plaintext = new TextEncoder().encode(JSON.stringify({ matricula, password }));
    const ciphertext = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      key,
      plaintext,
    );
    await db.put('secrets', { iv, ciphertext }, SECRET_ID);
  }

  async load(): Promise<StoredCredentials | null> {
    try {
      const db = await this.getDb();
      const record = await db.get('secrets', SECRET_ID);
      const key = await db.get('keys', KEY_ID);
      if (!record || !key) return null;

      const ivCopy = new Uint8Array(record.iv);
      const decrypted = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: ivCopy },
        key,
        record.ciphertext,
      );
      const decoded = JSON.parse(new TextDecoder('utf-8').decode(decrypted)) as StoredCredentials;
      if (!decoded?.matricula || !decoded?.password) return null;
      return { matricula: decoded.matricula, password: decoded.password };
    } catch {
      return null;
    }
  }

  async exists(): Promise<boolean> {
    try {
      const db = await this.getDb();
      const record = await db.get('secrets', SECRET_ID);
      return record !== undefined && record.ciphertext.byteLength > 0;
    } catch {
      return false;
    }
  }

  async clear(): Promise<void> {
    const db = await this.getDb();
    const tx = db.transaction(['keys', 'secrets'], 'readwrite');
    await Promise.all([
      tx.objectStore('secrets').delete(SECRET_ID),
      tx.objectStore('keys').delete(KEY_ID),
      tx.done,
    ]);
  }
}
