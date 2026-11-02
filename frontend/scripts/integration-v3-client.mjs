import { build } from 'vite';
import { fileURLToPath } from 'node:url';
const frontend=fileURLToPath(new URL('../',import.meta.url));
await build({configFile:false,root:frontend,define:{'import.meta.env.VITE_API_BASE_URL':JSON.stringify(process.env.ARCHLAB_API_URL??'http://localhost:8080')},build:{target:'node22',ssr:fileURLToPath(new URL('./integration-v3-client.ts',import.meta.url)),outDir:fileURLToPath(new URL('../target/v3-client',import.meta.url)),emptyOutDir:false,minify:false,rollupOptions:{output:{entryFileNames:'integration.mjs'}}}});
await import(new URL('../target/v3-client/integration.mjs',import.meta.url).href);
