import { MutableStateValue, type ObservableValue } from '../../core/model/models';

export type ThemeMode = 'SYSTEM' | 'LIGHT' | 'DARK';

const STORAGE_KEY = 'color-scheme';
const LIGHT_GROUND = '#F5F5F3';
const DARK_GROUND = '#111112';

function readInitialThemeMode(): ThemeMode {
  try {
    if (typeof localStorage === 'undefined') return 'SYSTEM';
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === 'light') return 'LIGHT';
    if (raw === 'dark') return 'DARK';
    return 'SYSTEM';
  } catch {
    return 'SYSTEM';
  }
}

function readSystemDark(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return true;
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function syncDocumentMeta(mode: ThemeMode): void {
  if (typeof document === 'undefined') return;
  const metaScheme = document.querySelector<HTMLMetaElement>('meta[name="color-scheme"]');
  if (metaScheme) {
    metaScheme.content =
      mode === 'LIGHT' ? 'light' : mode === 'DARK' ? 'dark' : 'light dark';
  }

  // Sync theme-color meta tags so iOS Safari chrome matches the active ground
  const themeMetas = document.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]');
  if (mode === 'SYSTEM') {
    themeMetas.forEach((m) => {
      const media = m.getAttribute('media') ?? '';
      if (media.includes('light')) m.content = LIGHT_GROUND;
      else if (media.includes('dark')) m.content = DARK_GROUND;
    });
  } else {
    const pinned = mode === 'DARK' ? DARK_GROUND : LIGHT_GROUND;
    themeMetas.forEach((m) => {
      m.content = pinned;
    });
  }
}

export class ThemeManager {
  private readonly modeBox = new MutableStateValue<ThemeMode>(readInitialThemeMode());
  private readonly systemDarkBox = new MutableStateValue<boolean>(readSystemDark());

  constructor() {
    syncDocumentMeta(this.modeBox.value);

    if (typeof window !== 'undefined') {
      window.addEventListener('storage', (e) => {
        if (e.key === STORAGE_KEY) {
          const next = readInitialThemeMode();
          this.modeBox.value = next;
          syncDocumentMeta(next);
        }
      });

      if (typeof window.matchMedia === 'function') {
        const mql = window.matchMedia('(prefers-color-scheme: dark)');
        const onChange = (ev: MediaQueryListEvent): void => {
          // Per modern-web-guidance (dark-mode-toggle): never remove stored value on OS change
          this.systemDarkBox.value = ev.matches;
        };
        mql.addEventListener?.('change', onChange);
      }
    }
  }

  get mode(): ObservableValue<ThemeMode> {
    return this.modeBox;
  }

  get systemPrefersDark(): ObservableValue<boolean> {
    return this.systemDarkBox;
  }

  setMode = (next: ThemeMode): void => {
    this.modeBox.value = next;
    try {
      if (typeof localStorage !== 'undefined') {
        if (next === 'SYSTEM') {
          localStorage.removeItem(STORAGE_KEY);
        } else {
          localStorage.setItem(STORAGE_KEY, next === 'DARK' ? 'dark' : 'light');
        }
      }
    } catch {
      // Ensure theme still applies to the page even if storage is blocked
    }
    syncDocumentMeta(next);
  };
}
