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

## Contrato v3

npm run typegen gera agora schema.d.ts, schema-v2.d.ts e schema-v3.d.ts a partir dos três OpenAPI em docs/api. Os arquivos ficam em src/api/generated, são ignorados no Git e reproduzidos por pretest/prebuild. ArchlabV3Client oferece validateArchitecture, validateExecutionPlan, validateScenario e simulate com tipos derivados do YAML. HTTP422 preserva violações; falha simulada200 permanece resultado de domínio.

Com o backend ativo, execute npm run test:integration (legado) e npm run test:integration:v3. ARCHLAB_API_URL seleciona a URL do backend (default http://localhost:8080). A integração v3 usa docs/examples/v3/ce-ref-01-request.json e verifica120/210/90ms, determinismo, horizonte e erros. Consulte docs/v3/backend-v0.3-report.md para o aceite consolidado.
