import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
    // Tests read the shared contract (openapi.yaml) and fixtures; nothing else outside the app is served.
    fs: { allow: ['.', '../docs/api'] },
  },
  // css: true lets tests read style.css (?raw) to check the direction-aware edge animation contract.
  test: { environment: 'jsdom', setupFiles: ['./src/test/setup.ts'], clearMocks: true, css: true },
});
