const fs = require('fs');
const path = require('path');
const YAML = require('yaml');
const Ajv = require('ajv/dist/2020').default;

const root = __dirname;
const base = process.env.ARCHLAB_API_URL || 'http://localhost:8080';
const openapi = YAML.parse(fs.readFileSync(path.join(root, '..', '..', 'openapi.yaml'), 'utf8'));
const ajv = new Ajv({strict: false, allErrors: true, validateFormats: false});
for (const [name, schema] of Object.entries(openapi.components.schemas)) {
  ajv.addSchema(schema, '#/components/schemas/' + name);
}
const responseValid = ajv.compile({$ref: '#/components/schemas/SimulationResponse'});
const positives = fs.readdirSync(root).filter(name => name.endsWith('.json') && name !== 'package.json' && !name.endsWith('.invalid.json'));
const negatives = fs.readdirSync(root).filter(name => name.endsWith('.invalid.json'));

async function post(route, payload) {
  const response = await fetch(new URL(route, base), {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(payload), signal: AbortSignal.timeout(10000),
  });
  return {status: response.status, body: await response.json()};
}

(async () => {
  for (const file of positives) {
    const payload = JSON.parse(fs.readFileSync(path.join(root, file), 'utf8'));
    const result = await post('/api/v1/simulations', payload);
    if (result.status !== 200 || !responseValid(result.body)) {
      throw new Error(`${file}: HTTP ${result.status}; ${JSON.stringify(responseValid.errors || result.body)}`);
    }
    const {execution, trace, timeline, metrics} = result.body;
    if (execution.totalDurationMs !== metrics.totalDurationMs ||
        trace.some((event, i) => event.sequence !== i + 1 || event.timestampMs > execution.totalDurationMs ||
          (i > 0 && event.timestampMs < trace[i - 1].timestampMs)) ||
        timeline.some(activity => activity.endMs > execution.totalDurationMs)) {
      throw new Error(`${file}: inconsistent virtual clock or trace sequence`);
    }
    console.log(`PASS ${file}: HTTP 200, SimulationResponse, ${execution.totalDurationMs} ms`);
  }
  for (const file of negatives) {
    const payload = JSON.parse(fs.readFileSync(path.join(root, file), 'utf8'));
    const result = await post('/api/v1/simulations', payload);
    if (![400, 422].includes(result.status) || !result.body.code) {
      throw new Error(`${file}: expected 400/422 problem, got HTTP ${result.status}`);
    }
    console.log(`PASS ${file}: HTTP ${result.status} ${result.body.code}`);
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
