import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { registerSW } from 'virtual:pwa-register';
import type { NotificationDeepLinkEvent } from './core/data/notification/pwaNotificationCoordinator';
import { UnbPocketCoreFactory } from './core/data/unbPocketCoreImpl';
import type { RuDeepLinkTarget, SigaaDeepLinkTarget } from './core/model/models';
import './ui/theme/tokens.css';
import './ui/theme/mobile-native.css';
import { ThemeManager } from './ui/theme/themeManager';
import { UnbPocketApp } from './ui/UnbPocketApp';

const themeManager = new ThemeManager();
const deepLinkListeners = new Set<(ev: NotificationDeepLinkEvent) => void>();

const core = UnbPocketCoreFactory.create((event) => {
  for (const listener of deepLinkListeners) {
    listener(event);
  }
});

if (typeof window !== 'undefined' && 'serviceWorker' in navigator) {
  const updateSW = registerSW({
    immediate: true,
    onRegisteredSW(_swUrl, registration) {
      core.pwaUpdates.bindServiceWorker({
        registration: registration ?? null,
        applyUpdate: () => updateSW(true),
      });
    },
    onNeedRefresh() {
      core.pwaUpdates.bindServiceWorker({
        updateReady: true,
        applyUpdate: () => updateSW(true),
      });
    },
  });
}

const RootWrapper: React.FC = () => {
  const [ruDeepLink, setRuDeepLink] = useState<RuDeepLinkTarget | null>(null);
  const [sigaaDeepLink, setSigaaDeepLink] = useState<SigaaDeepLinkTarget | null>(null);

  useEffect(() => {
    const handler = (ev: NotificationDeepLinkEvent): void => {
      if (ev.kind === 'ru') setRuDeepLink(ev.payload);
      else setSigaaDeepLink(ev.payload);
    };
    deepLinkListeners.add(handler);
    return () => {
      deepLinkListeners.delete(handler);
    };
  }, []);

  return (
    <UnbPocketApp
      core={core}
      themeManager={themeManager}
      pendingRuDeepLink={ruDeepLink}
      onConsumeRuDeepLink={() => setRuDeepLink(null)}
      pendingSigaaDeepLink={sigaaDeepLink}
      onConsumeSigaaDeepLink={() => setSigaaDeepLink(null)}
    />
  );
};

const rootElement = document.getElementById('root');
if (rootElement) {
  createRoot(rootElement).render(
    <React.StrictMode>
      <RootWrapper />
    </React.StrictMode>,
  );
}
