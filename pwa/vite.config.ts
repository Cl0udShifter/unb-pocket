import { defineConfig } from 'vitest/config';
import type { Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import { handleProxyRequest } from './functions/api/proxy';
import { SigaaReadOnlyGuard } from './shared/network/sigaaReadOnlyGuard';
import type { IncomingMessage, ServerResponse } from 'node:http';

function localStatelessProxyPlugin(): Plugin {
  const attachMiddleware = (middlewares: {
    use: (route: string, fn: (req: IncomingMessage, res: ServerResponse, next: () => void) => void) => void;
  }) => {
    middlewares.use('/api/proxy', async (req, res) => {
      try {
        const chunks: Buffer[] = [];
        for await (const chunk of req) {
          chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
        }
        const rawBody = Buffer.concat(chunks);
        const hostHeader = req.headers.host ?? 'localhost:5173';
        const fullUrl = `http://${hostHeader}/api/proxy`;

        const headers = new Headers();
        for (const [key, val] of Object.entries(req.headers)) {
          if (Array.isArray(val)) {
            for (const item of val) headers.append(key, item);
          } else if (typeof val === 'string') {
            headers.set(key, val);
          }
        }

        const allowLocalMockHosts = process.env.UNB_ALLOW_LOCALHOST_PROXY === '1';
        const allowedHosts = allowLocalMockHosts
          ? new Set([...SigaaReadOnlyGuard.defaultAllowedHosts, '127.0.0.1', 'localhost'])
          : SigaaReadOnlyGuard.defaultAllowedHosts;

        const webReq = new Request(fullUrl, {
          method: req.method ?? 'POST',
          headers,
          body: req.method === 'GET' || req.method === 'HEAD' ? undefined : rawBody,
        });

        const webRes = await handleProxyRequest(webReq, { allowedHosts });
        res.statusCode = webRes.status;
        webRes.headers.forEach((value, key) => {
          res.setHeader(key, value);
        });
        const arrayBuf = await webRes.arrayBuffer();
        res.end(Buffer.from(arrayBuf));
      } catch (err) {
        res.statusCode = 500;
        res.setHeader('Content-Type', 'application/json');
        res.end(
          JSON.stringify({
            error: 'DEV_PROXY_ERROR',
            message: err instanceof Error ? err.message : 'Unknown error',
          }),
        );
      }
    });
  };

  return {
    name: 'unb-local-stateless-proxy',
    configureServer(server) {
      attachMiddleware(server.middlewares);
    },
    configurePreviewServer(server) {
      attachMiddleware(server.middlewares);
    },
  };
}

export default defineConfig({
  plugins: [
    react(),
    localStatelessProxyPlugin(),
    VitePWA({
      registerType: 'prompt',
      injectRegister: false,
      includeAssets: [
        'favicon.svg',
        'apple-touch-icon.png',
        'icons/icon-192.png',
        'icons/icon-512.png',
        'fonts/*.ttf',
      ],
      manifest: false,
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,ttf,webmanifest,mjs}'],
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024,
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//],
      },
    }),
  ],
  worker: {
    format: 'es',
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/react') || id.includes('node_modules/scheduler')) {
            return 'vendor-react';
          }
          if (id.includes('node_modules/lucide-react')) {
            return 'vendor-icons';
          }
          if (id.includes('node_modules/qrcode') || id.includes('node_modules/jsqr')) {
            return 'vendor-qr';
          }
          return undefined;
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
  },
});
