import { Share, Smartphone, X } from 'lucide-react';
import React, { useEffect, useState } from 'react';
import { IosAddToHomeScreenSheet, isRunningStandalone } from '../discente/DiscenteScreen';
import { EditorialCard } from './CommonComponents';

const INSTALL_DISMISSED_KEY = 'unb_pocket_install_card_dismissed_v1';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

function isAppleMobileDevice(): boolean {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent || '';
  if (/iPhone|iPad|iPod/i.test(ua)) return true;
  return navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1;
}

function readDismissedFlag(): boolean {
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem(INSTALL_DISMISSED_KEY) === '1';
  } catch {
    return false;
  }
}

export const IosInstallCard: React.FC = () => {
  const [standalone, setStandalone] = useState<boolean>(() => isRunningStandalone());
  const [dismissed, setDismissed] = useState<boolean>(() => readDismissedFlag());
  const [showGuide, setShowGuide] = useState(false);
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);

  const isIos = isAppleMobileDevice();

  useEffect(() => {
    if (typeof window === 'undefined') return;

    const onDisplayChange = (): void => {
      setStandalone(isRunningStandalone());
    };
    const mql = window.matchMedia?.('(display-mode: standalone)');
    mql?.addEventListener?.('change', onDisplayChange);

    const onBeforeInstall = (e: Event): void => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    };
    window.addEventListener('beforeinstallprompt', onBeforeInstall);

    return () => {
      mql?.removeEventListener?.('change', onDisplayChange);
      window.removeEventListener('beforeinstallprompt', onBeforeInstall);
    };
  }, []);

  if (standalone || dismissed || (!isIos && !deferredPrompt)) {
    return null;
  }

  const handleDismiss = (): void => {
    setDismissed(true);
    try {
      localStorage.setItem(INSTALL_DISMISSED_KEY, '1');
    } catch {
      // Ignore private browsing storage quota restrictions
    }
  };

  const handleAction = (): void => {
    if (deferredPrompt) {
      void deferredPrompt.prompt().then(async () => {
        const choice = await deferredPrompt.userChoice;
        if (choice.outcome === 'accepted') {
          setDeferredPrompt(null);
          handleDismiss();
        }
      });
      return;
    }
    setShowGuide(true);
  };

  return (
    <>
      <EditorialCard style={{ gap: '10px', padding: '14px 16px' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '8px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0 }}>
            <Smartphone size={18} strokeWidth={1.75} style={{ color: 'var(--ink)', flexShrink: 0 }} />
            <p className="ty-title" style={{ color: 'var(--ink)' }}>
              {isIos ? 'Instalar no iPhone' : 'Instalar aplicativo'}
            </p>
          </div>

          <button
            type="button"
            aria-label="Dispensar aviso de instalação"
            onClick={handleDismiss}
            style={{
              width: '32px',
              height: '32px',
              marginRight: '-6px',
              borderRadius: '50%',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--muted)',
              flexShrink: 0,
            }}
          >
            <X size={16} strokeWidth={1.75} />
          </button>
        </div>

        <p className="ty-body" style={{ color: 'var(--muted)' }}>
          {isIos
            ? 'Toque em Compartilhar no Safari e selecione "Adicionar à Tela de Início" para abrir em tela cheia e offline.'
            : 'Instale o UnB Pocket na tela inicial para acesso rápido em tela cheia e funcionamento offline.'}
        </p>

        <button
          type="button"
          className="btn-tonal"
          style={{ width: '100%', minHeight: '42px' }}
          onClick={handleAction}
        >
          <Share size={16} strokeWidth={1.75} />
          <span className="ty-label">
            {isIos ? 'Ver passo a passo' : 'Adicionar à tela inicial'}
          </span>
        </button>
      </EditorialCard>

      <IosAddToHomeScreenSheet open={showGuide} onDismiss={() => setShowGuide(false)} />
    </>
  );
};
