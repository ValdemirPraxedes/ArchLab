# ArchLab — Frontend MVP v0.1

Bootstrap React + TypeScript (Vite). O editor visual não faz parte desta entrega.

## Requisitos
Node.js 22 LTS ou superior e npm.

## Executar
```bash
cd frontend
npm install
npm run dev
```
Acesse o endereço exibido pelo Vite (normalmente http://localhost:5173).

## Scripts
- `npm run typegen`: gera tipos diretamente de `../docs/api/openapi.yaml`.
- `npm test`: gera tipos e executa Vitest com MSW.
- `npm run build`: gera tipos, verifica TypeScript e compila produção.
- `npm run lint`: verifica estilo com ESLint.

O cliente utiliza URL relativa `/api/v1` por padrão. No desenvolvimento, o Vite encaminha `/api` para `http://localhost:8080`. Para outro servidor, configure `VITE_API_BASE_URL` em `.env.local` (sem barra final).

O diretório `src/api/generated` é reconstruído do OpenAPI, não deve ser editado. API: `POST /api/v1/architectures/validate` e `POST /api/v1/simulations`. Validação inválida pode retornar HTTP 200 com `valid: false`, enquanto simulação inválida usa HTTP 422 (Problem Details).

A API calcula todo o resultado; o frontend não implementa motor de simulação.
