const fs = require('fs');
const path = require('path');
const YAML = require('yaml');
const Ajv = require('ajv/dist/2020').default;

const openapi = YAML.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'openapi.yaml'), 'utf8'));
const ajv = new Ajv({strict: false, allErrors: true, validateFormats: false});
for (const [name, schema] of Object.entries(openapi.components.schemas))
  ajv.addSchema(schema, '#/components/schemas/' + name);
const validateResponse = ajv.compile({$ref: '#/components/schemas/SimulationResponse'});
const url = process.env.ARCHLAB_API_URL || 'http://127.0.0.1:18081';

async function main() {
  const source = fs.readFileSync(path.join(__dirname, '..', '..', '..', '..', 'backend',
    'archlab-api', 'src', 'test', 'java', 'br', 'com', 'archlab', 'api',
    'ServiceV5IntegrationTest.java'), 'utf8');
  const fixtures = new Map();
  for (const match of source.matchAll(/private static final String ([A-Z_]+)="""([\s\S]*?)""";/g))
    fixtures.set(match[1], JSON.parse(match[2]));
  fixtures.set('v5CacheBranchScenario', openapi.components.examples.v5CacheBranchScenario.value);
  const names = ['v5CacheBranchScenario', 'CACHE_MISS', 'GATEWAY_BFF',
    'WORKER_NOTIFICATION', 'AUTH_RULES', 'RESOURCES', 'WEBHOOK_ORCHESTRATOR_BUS', 'SCHEDULED'];
  for (const name of names) {
    const request = fixtures.get(name);
    if (!request) throw new Error(`Missing fixture ${name}`);
    const response = await fetch(`${url}/api/v1/simulations`, {
      method: 'POST', headers: {'content-type': 'application/json'}, body: JSON.stringify(request)
    });
    const result = await response.json();
    if (!response.ok) throw new Error(`${name}: HTTP ${response.status}: ${JSON.stringify(result)}`);
    if (!validateResponse(result))
      throw new Error(`${name}: response schema violations: ${JSON.stringify(validateResponse.errors)}`);
    if (name === 'v5CacheBranchScenario') {
      const miss = result.resourceOutcomes?.find(outcome => outcome.resultCode === 'MISS');
      if (!miss || !result.trace?.some(event => event.status === 'SKIPPED'))
        throw new Error('Expected a typed Cache MISS and skipped HIT branch');
    }
    console.log(`PASS ${name} live SimulationResponse schema`);
  }
}
main().catch(error => {console.error(error); process.exitCode = 1;});
