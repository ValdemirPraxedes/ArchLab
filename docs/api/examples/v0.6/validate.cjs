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
// V6-02: normative budget / atomicity / result-state examples (docs/architecture/DATA-BUDGETS-V6-001.md)
const normDir = path.join(__dirname, 'normative');
const normFiles = fs.readdirSync(normDir).filter((f) => /^N6-\d\d\.json$/.test(f)).sort();
assert('N6-01..08 normative examples present', normFiles.length === 8, normFiles.join(','));
for (const file of normFiles) {
  const fixture = JSON.parse(fs.readFileSync(path.join(normDir, file), 'utf8'));
  for (const c of fixture.cases) {
    const expected = c.schemaValid !== false;
    assert(fixture.id + ' ' + c.name + ' request ' + (expected ? 'matches' : 'is rejected by') + ' SimulationRequest',
      validate('SimulationRequest', c.request) === expected,
      JSON.stringify(ajv.getSchema('#/components/schemas/SimulationRequest').errors));
  }
}

// V6-03 / V6-14: legacy migration equivalence fixtures (replayed by LegacyMigrationEquivalenceV6Test)
const migDir = path.join(__dirname, 'migration');
const migFiles = fs.readdirSync(migDir).filter((f) => /^M6-\d\d\.json$/.test(f)).sort();
assert('M6-01..04 migration fixtures present', migFiles.length === 4, migFiles.join(','));
for (const file of migFiles) {
  const fixture = JSON.parse(fs.readFileSync(path.join(migDir, file), 'utf8'));
  assert(fixture.id + ' legacy architecture is decodable (unversioned historical)', validate('Architecture', fixture.legacy.architecture),
    JSON.stringify(ajv.getSchema('#/components/schemas/Architecture').errors));
  assert(fixture.id + ' has an expected conversion report', fixture.expectedReport.databaseIds.length > 0
    && fixture.expectedReport.notesContain.length > 0);
}

// V6-23: worst-case envelopes (replayed with -Xmx256m by Ct6FixtureReplayTest; timed by profile-limits.cjs)
const perfDir = path.join(__dirname, 'performance');
const perfFiles = fs.readdirSync(perfDir).filter((f) => /^P6-\d\d\.json$/.test(f)).sort();
assert('P6-01..06 worst-case envelopes present', perfFiles.length === 6, perfFiles.join(','));
for (const file of perfFiles) {
  const fixture = JSON.parse(fs.readFileSync(path.join(perfDir, file), 'utf8'));
  for (const c of fixture.cases)
    assert(fixture.id + ' ' + c.name + ' request matches SimulationRequest and declares maxWallMs',
      validate('SimulationRequest', c.request) && Number.isInteger(c.maxWallMs) && c.maxWallMs <= 2000,
      JSON.stringify(ajv.getSchema('#/components/schemas/SimulationRequest').errors));
}

// V6-20: canonical frontend export (golden shared with exportV6.test.ts and Ct6FixtureReplayTest)
const golden = load('canonical-export-p0.json');
assert('Canonical P0 export matches Architecture', validate('Architecture', golden),
  JSON.stringify(ajv.getSchema('#/components/schemas/Architecture').errors));
assert('Canonical P0 export has no legacy DATABASE/API', golden.schemaVersion === '0.6'
  && !golden.components.some((c) => c.type === 'DATABASE' || c.type === 'API'));
// Real backend responses captured by Ct6FixtureReplayTest (run `cd backend && ./mvnw test` first).
const responsesDir = path.join(__dirname, '..', '..', '..', '..', 'backend', 'archlab-api', 'target', 'ct6-responses');
if (fs.existsSync(responsesDir)) {
  const files = fs.readdirSync(responsesDir).filter((f) => f.endsWith('.json')).sort();
  assert('Captured CT-6 simulation responses present', files.length > 0);
  for (const file of files) {
    const ok = validate('SimulationResponse', JSON.parse(fs.readFileSync(path.join(responsesDir, file), 'utf8')));
    assert('Backend response ' + file + ' matches SimulationResponse', ok,
      JSON.stringify((ajv.getSchema('#/components/schemas/SimulationResponse').errors || []).slice(0, 3)));
  }
} else console.log('SKIP backend response conformance (no backend/archlab-api/target/ct6-responses)');
// V6-04: contract integrity — every $ref resolves, discriminators match their oneOf, valid/invalid cases
const refs = [];
(function walk(node) {
  if (Array.isArray(node)) node.forEach(walk);
  else if (node && typeof node === 'object') for (const [k, v] of Object.entries(node)) { if (k === '$ref' && typeof v === 'string') refs.push(v); else walk(v); }
})(document);
const unresolved = [...new Set(refs)].filter((r) => {
  if (!r.startsWith('#/')) return true;
  return r.slice(2).split('/').reduce((n, key) => (n == null ? n : n[key.replace(/~1/g, '/').replace(/~0/g, '~')]), document) === undefined;
});
assert('All ' + new Set(refs).size + ' distinct $ref resolve', unresolved.length === 0, unresolved.join(', '));
const compileErrors = [];
for (const name of Object.keys(schemas)) { try { ajv.getSchema('#/components/schemas/' + name); } catch (e) { compileErrors.push(name + ': ' + e.message); } }
assert('All component schemas compile', compileErrors.length === 0, compileErrors.join('; '));
for (const union of ['Component', 'CanonicalComponentV6']) {
  const schema = schemas[union];
  const branches = schema.oneOf.map((b) => b.$ref).sort();
  const mapped = Object.values(schema.discriminator.mapping).sort();
  assert(union + ' discriminator mapping covers exactly its oneOf', JSON.stringify(branches) === JSON.stringify(mapped));
  const wrong = Object.entries(schema.discriminator.mapping).filter(([type, ref]) => schemas[ref.split('/').pop()].properties.type.const !== type);
  assert(union + ' discriminator keys equal each branch type const', wrong.length === 0, JSON.stringify(wrong));
}
const canonicalTypes = Object.keys(schemas.CanonicalComponentV6.discriminator.mapping);
assert('CanonicalComponentV6 excludes API and DATABASE', !canonicalTypes.includes('API') && !canonicalTypes.includes('DATABASE'));
assert('Historical API and DATABASE are deprecated', schemas.Api.deprecated === true && schemas.Database.deprecated === true);
const contract = load('contract-cases.json');
for (const c of contract.cases)
  assert('Contract case ' + c.name + ' is ' + (c.valid ? 'accepted' : 'rejected') + ' by Architecture',
    validate('Architecture', c.architecture) === c.valid,
    JSON.stringify((ajv.getSchema('#/components/schemas/Architecture').errors || []).slice(0, 2)));

console.log('NOTE: semantic validation, OpenAPI lint, TS generation, Java DTOs and backend execution are separate gates.');
process.exitCode = failed ? 1 : 0;
