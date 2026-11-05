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
  const request = openapi.components.examples.v5CacheBranchScenario.value;
  const response = await fetch(`${url}/api/v1/simulations`, {
    method: 'POST', headers: {'content-type': 'application/json'}, body: JSON.stringify(request)
  });
  const result = await response.json();
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${JSON.stringify(result)}`);
  if (!validateResponse(result))
    throw new Error(`Response schema violations: ${JSON.stringify(validateResponse.errors)}`);
  const miss = result.resourceOutcomes?.find(outcome => outcome.resultCode === 'MISS');
  if (!miss || !result.trace?.some(event => event.status === 'SKIPPED'))
    throw new Error('Expected a typed Cache MISS and skipped HIT branch');
  console.log('PASS v5CacheBranchScenario live response and SimulationResponse schema');
}
main().catch(error => {console.error(error); process.exitCode = 1;});
