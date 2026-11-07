const fs = require('node:fs');
const path = require('node:path');
const YAML = require('yaml');
const Ajv = require('ajv/dist/2020').default;

const root = path.resolve(__dirname, '..', '..');
const document = YAML.parse(fs.readFileSync(path.join(root, 'openapi.yaml'), 'utf8'), {uniqueKeys: true});
const schemas = document.components.schemas;
const ajv = new Ajv({strict: false, allErrors: true, validateFormats: false});
for (const [name, schema] of Object.entries(schemas)) ajv.addSchema(schema, '#/components/schemas/' + name);
let failed = 0;
function assert(label, condition, detail = '') {
  console.log((condition ? 'PASS' : 'FAIL') + ' ' + label + (condition ? '' : ' ' + detail));
  if (!condition) failed++;
}
function load(name) { return JSON.parse(fs.readFileSync(path.join(__dirname, name), 'utf8')); }
function validate(name, value) { return ajv.getSchema('#/components/schemas/' + name)(value); }

const types = ['RELATIONAL_DATABASE','DOCUMENT_DATABASE','KEY_VALUE_STORE','ETL_PIPELINE','DATA_TRANSFORMER'];
const actions = ['DB_SELECT','DB_INSERT','DB_UPDATE','DB_DELETE','DB_GET','DB_PUT','DB_FIND','DB_BULK_INSERT','ETL_EXTRACT','ETL_TRANSFORM','ETL_LOAD'];
const pascal = s => s.toLowerCase().split('_').map(w => w[0].toUpperCase() + w.slice(1)).join('') + 'ActionV6';
for (const t of types) assert('Component discriminator ' + t, Boolean(schemas.Component.discriminator.mapping[t]));
for (const a of actions) assert('Action discriminator ' + a, schemas.BehaviorAction.discriminator.mapping[a] === '#/components/schemas/' + pascal(a));
assert('Legacy DATABASE remains decodable', Boolean(schemas.Component.discriminator.mapping.DATABASE));
// Component currently accepts DATABASE for both legacy input and new payloads. This
// validator cannot claim that canonical creation rejects it until separate schemas exist.
const relational = load('relational-insert-select.json');
assert('Relational request schema', validate('SimulationRequest', relational), JSON.stringify(ajv.getSchema('#/components/schemas/SimulationRequest').errors));
const kv = load('key-value-actions.json');
assert('KV component schema', validate('Component', kv.component));
for (const a of kv.actions) assert('KV action ' + a.kind, validate('BehaviorAction', a));
const etl = load('etl-actions.json');
assert('ETL component schema', validate('Component', etl.component));
for (const a of etl.actions) assert('ETL action ' + a.kind, validate('BehaviorAction', a), JSON.stringify(ajv.getSchema('#/components/schemas/BehaviorAction').errors));
const filterWithoutCount = {...etl.actions.find(action => action.kind === 'ETL_TRANSFORM')};
delete filterWithoutCount.invalidRecordCount;
assert('FILTER_VALID requires invalidRecordCount', !validate('BehaviorAction', filterWithoutCount));
assert('ETL identity simulation schema', validate('SimulationRequest', load('etl-identity-simulation.json')),
  JSON.stringify(ajv.getSchema('#/components/schemas/SimulationRequest').errors));
const invalid = load('invalid-action.invalid.json');
assert('Invalid example is structurally valid (semantic rejection belongs to backend)', validate('BehaviorAction', invalid.actions[0]));
assert('Invalid action is rejected by capability matrix', invalid.component.type === 'KEY_VALUE_STORE' && invalid.actions[0].kind === 'DB_SELECT' && invalid.expectedError === 'DATA_ACTION_INCOMPATIBLE');
const migration = load('migration-legacy.json');
assert('Legacy input retained for migration', validate('Component', migration.legacy.components[0]) && migration.migration.requiresConfirmation === true);
assert('Versioned canonical architecture rejects DATABASE', !validate('Architecture', {
  schemaVersion: '0.6', components: migration.legacy.components, connections: []
}));
assert('Unversioned historical architecture remains decodable', validate('Architecture', migration.legacy));
assert('dataOutcomes is additive', Boolean(schemas.SimulationResponse.properties.dataOutcomes && schemas.SimulationResponse.properties.resourceOutcomes));
// V6-21: CT-6-01..10 acceptance fixtures (replayed against the backend by Ct6FixtureReplayTest).
const ctDir = path.join(__dirname, 'ct');
const ctFiles = fs.readdirSync(ctDir).filter((f) => /^CT-6-\d\d\.json$/.test(f)).sort();
assert('CT-6-01..10 fixtures present', ctFiles.length === 10, ctFiles.join(','));
for (const file of ctFiles) {
  const fixture = JSON.parse(fs.readFileSync(path.join(ctDir, file), 'utf8'));
  for (const c of fixture.cases) {
    const schema = c.endpoint.endsWith('/migrate-v6') ? 'Architecture' : 'SimulationRequest';
    const expected = c.schemaValid !== false;
    const ok = validate(schema, c.request) === expected;
    assert(fixture.id + ' ' + c.name + ' request ' + (expected ? 'matches' : 'is rejected by') + ' ' + schema, ok,
      JSON.stringify(ajv.getSchema('#/components/schemas/' + schema).errors));
  }
}
// V6-20: canonical frontend export (golden shared with exportV6.test.ts and Ct6FixtureReplayTest)
const golden = load('canonical-export-p0.json');
assert('Canonical P0 export matches Architecture', validate('Architecture', golden),
  JSON.stringify(ajv.getSchema('#/components/schemas/Architecture').errors));
assert('Canonical P0 export has no legacy DATABASE/API', golden.schemaVersion === '0.6'
  && !golden.components.some((c) => c.type === 'DATABASE' || c.type === 'API'));
console.log('NOTE: semantic validation, OpenAPI lint, TS generation, Java DTOs and backend execution are separate gates.');
process.exitCode = failed ? 1 : 0;
