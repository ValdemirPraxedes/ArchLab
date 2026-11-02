# ArchLab — Frontend v0.1 / v0.2 / v0.3

React + TypeScript + Vite, React Flow (@xyflow/react) e Lucide. O seletor **Versão** mantém os editores legado e v3 isolados e seus rascunhos em memória. O seletor **Tema** oferece claro/escuro. O [guia v0.3](../docs/v3/frontend-v0.3-guide.md) descreve fluxos, arquitetura, limitações, diagramas e evidências reais.

## Executar

Node.js 22/npm. Com o backend Java 25/Spring Boot 4 ativo na porta 8080:

```powershell
cd frontend
npm install
npm run dev
```

Abra http://localhost:5173/#v3 para v0.3 ou selecione v0.1/v0.2. O proxy do Vite encaminha /api à porta8080. Configure VITE_API_BASE_URL antes de iniciar/buildar quando precisar de outra origem.

## Verificar

```powershell
npm run lint
npm test
npm run build
$env:ARCHLAB_API_URL='http://localhost:8080'
npm run test:integration
npm run test:integration:v3
npm run test:integration:v3-client
```

- typegen gera schema.d.ts, schema-v2.d.ts e schema-v3.d.ts dos YAMLs em docs/api. Os gerados são ignorados no Git; pretest/prebuild regeneram tudo.
- test executa Vitest/React Testing Library/MSW, incluindo regressão v1/v2/RF-044 e matriz v3.
- build executa TypeScript, typecheck:integration e Vite.
- test:integration verifica HTTP v1 real; test:integration:v3 verifica os quatro endpoints v3.
- test:integration:v3-client empacota via Vite SSR os clientes TypeScript reais e o adaptador/replay, executa v2/v3 contra Java e compara fixtures independentes. Inclui CE120/210/90, FORK/JOIN30/80, horizonte, limite, CONDITION/FAIL e RF-04457/102/47. Usa Node 22 e ferramentas existentes; nenhum Python é necessário.

## Contrato e comportamento

A v3 configura Client, API, Database, Broker, Queue e Telemetry, recursos/propriedades tipados, endpoints/operações/conexões, nove tipos de ExecutionStep e cenários. Validação semântica e execução pertencem ao backend. A UI consome exclusivamente os quatro POST v3: architectures/validate, execution-plans/validate, scenarios/validate, simulations.

Timeline/States/Details/Resultados inspecionam dados recebidos. Replay PUBLIC_SNAPSHOT verifica versões/before e igualdade final, preservando commits atômicos. Não executa o motor nem promete FULL_RUNTIME. Métricas finais e resultados principal/global permanecem distintos do cursor. Falhas simuladas200 não são erros da aplicação; errosHTTP422 preservam diagnósticos. Cancelar espera não cancela Java. Reiniciar libera o rascunho; recarregar a página não oferece persistência de projeto.

Não há infraestrutura Playwright instalada. E2E visual por navegador conectado está documentado com screenshots reais no guia; não existe comando Playwright a ser considerado aprovado.
