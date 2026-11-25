import { openDB, type IDBPDatabase } from 'idb';
import type { RgbImageBuffer } from '../../parser/ruAllergenClassifier';
import { rgbImageBufferToPngBytes } from '../../pdf/pdfExtractorClient';

export interface StoredFileMeta {
  readonly path: string;
  readonly name: string;
  readonly size: number;
  readonly mimeType: string;
}

export interface FileStore {
  saveBytes(
    folder: string,
    fileName: string,
    bytes: Uint8Array,
    mimeType?: string,
  ): Promise<StoredFileMeta>;
  saveRgbAsPng(
    folder: string,
    fileName: string,
    rgb: RgbImageBuffer,
  ): Promise<StoredFileMeta>;
  exists(path: string): Promise<boolean>;
  readBlob(path: string): Promise<Blob | null>;
  deleteFile(path: string): Promise<void>;
  clearSigaaUserFiles(): Promise<void>;
}

interface BlobRecord {
  path: string;
  folder: string;
  name: string;
  mimeType: string;
  bytes: Uint8Array;
  savedAt: number;
}

interface BlobDbSchema {
  files: {
    key: string;
    value: BlobRecord;
    indexes: { 'by-folder': string };
  };
}

const FILES_DB_NAME = 'unb_pocket_files_v1';
const PRIVATE_SIGAA_PREFIXES = ['discente', 'carteirinha', 'documentos', 'materiais'];

function sanitizeSegment(raw: string): string {
  return raw.replace(/[\\:*?"<>|]+/g, '_').replace(/^\/+|\/+$/g, '').trim() || 'arquivo';
}

export class BlobFileStore implements FileStore {
  private readonly dbPromise: Promise<IDBPDatabase<BlobDbSchema>>;

  constructor(dbName: string = FILES_DB_NAME) {
    this.dbPromise = openDB<BlobDbSchema>(dbName, 1, {
      upgrade(db) {
        if (!db.objectStoreNames.contains('files')) {
          const store = db.createObjectStore('files', { keyPath: 'path' });
          store.createIndex('by-folder', 'folder');
        }
      },
    });
  }

  async saveBytes(
    folder: string,
    fileName: string,
    bytes: Uint8Array,
    mimeType: string = 'application/octet-stream',
  ): Promise<StoredFileMeta> {
    const cleanFolder = sanitizeSegment(folder);
    const cleanName = sanitizeSegment(fileName);
    const path = `unbpocket://${cleanFolder}/${cleanName}`;
    const copy = new Uint8Array(bytes);

    const db = await this.dbPromise;
    await db.put('files', {
      path,
      folder: cleanFolder,
      name: cleanName,
      mimeType,
      bytes: copy,
      savedAt: Date.now(),
    });

    return {
      path,
      name: cleanName,
      size: copy.byteLength,
      mimeType,
    };
  }

  async saveRgbAsPng(
    folder: string,
    fileName: string,
    rgb: RgbImageBuffer,
  ): Promise<StoredFileMeta> {
    const pngBytes = await rgbImageBufferToPngBytes(rgb);
    return this.saveBytes(folder, fileName, pngBytes, 'image/png');
  }

  async exists(path: string): Promise<boolean> {
    if (!path) return false;
    try {
      const db = await this.dbPromise;
      const rec = await db.get('files', path);
      return rec !== undefined && rec.bytes.byteLength > 0;
    } catch {
      return false;
    }
  }

  async readBlob(path: string): Promise<Blob | null> {
    if (!path) return null;
    try {
      const db = await this.dbPromise;
      const rec = await db.get('files', path);
      if (!rec) return null;
      return new Blob([new Uint8Array(rec.bytes)], { type: rec.mimeType });
    } catch {
      return null;
    }
  }

  async deleteFile(path: string): Promise<void> {
    if (!path) return;
    const db = await this.dbPromise;
    await db.delete('files', path);
  }

  async clearSigaaUserFiles(): Promise<void> {
    const db = await this.dbPromise;
    const tx = db.transaction('files', 'readwrite');
    let cursor = await tx.store.openCursor();
    while (cursor) {
      const folder = cursor.value.folder;
      const isPrivate = PRIVATE_SIGAA_PREFIXES.some(
        (prefix) => folder === prefix || folder.startsWith(`${prefix}/`),
      );
      if (isPrivate) {
        await cursor.delete();
      }
      cursor = await cursor.continue();
    }
    await tx.done;
  }
}
