# ArchLab — Frontend MVP v0.1

React + TypeScript + Vite. Primeira etapa do editor visual: paleta e canvas React Flow, adição de Client/API/Database e movimentação visual. Conexões, edição, simulação e playback virão nas próximas entregas.

## Requisitos
Node.js 22 LTS e npm.

## Executar
```bash
cd frontend
npm install
npm run dev
```
Acesse http://localhost:5173.

## Scripts
- `npm run typegen`: gera tipos a partir de `../docs/api/openapi.yaml`.
- `npm test`: Vitest com mocks MSW e React Testing Library.
- `npm run build`: TypeScript + Vite.
- `npm run lint`: ESLint.

## Editor
Arraste Client, API ou Database da paleta para o canvas; também é possível clicar para adicionar. São permitidas múltiplas instâncias, cada uma com ID único. Componentes podem ser movimentados no canvas. As regras de validação e execução continuam no backend.

O cliente REST usa `/api/v1` via proxy do Vite para http://localhost:8080. Sob outra URL, configure `VITE_API_BASE_URL` em `.env.local`.
