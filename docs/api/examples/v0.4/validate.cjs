const fs = require('fs');
const path = require('path');
const YAML = require('yaml');
const Ajv = require('ajv/dist/2020').default;

const openapi = YAML.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'openapi.yaml'), 'utf8'));
const ajv = new Ajv({strict: false, allErrors: true, validateFormats: false});
for (const [name, schema] of Object.entries(openapi.components.schemas)) {
  ajv.addSchema(schema, '#/components/schemas/' + name);
}
const validateRequest = ajv.compile({$ref: '#/components/schemas/SimulationRequest'});

function domainValid(request) {
  const {architecture, scenario} = request;
  const components = new Map(architecture.components.map(component => [component.id, component]));
  const connections = new Map(architecture.connections.map(connection => [connection.id, connection]));
  const entry = connections.get(scenario.entryConnectionId);
  if (!entry || entry.sourceId !== scenario.entryComponentId) return false;
  if (architecture.connections.some(connection => !components.has(connection.sourceId) || !components.has(connection.targetId))) return false;
  if (scenario.kind === 'WEBSOCKET_SESSION') return entry.integration.kind === 'WEBSOCKET';
  if (scenario.kind === 'REQUEST_RESPONSE') return !['WEBSOCKET', 'MQTT', 'QUEUE', 'TOPIC'].includes(entry.integration.kind);
  const broker = components.get(entry.targetId);
  if (broker?.type !== 'BROKER') return false;
  const destination = broker.destinations?.find(item => item.name === scenario.destinationName);
  if (!destination || destination.kind !== entry.integration.kind) return false;
  return (broker.subscriptions ?? []).every(subscription => {
    const connection = connections.get(subscription.connectionId);
    return connection?.sourceId === broker.id
      && connection.targetId === subscription.consumerComponentId
      && components.get(subscription.consumerComponentId)?.type === 'API'
      && subscription.destinationName === destination.name
      && connection.integration.kind === destination.kind;
  });
}

let failed = 0;
for (const file of fs.readdirSync(__dirname).filter(name => name.endsWith('.json') && name !== 'package.json').sort()) {
  const request = JSON.parse(fs.readFileSync(path.join(__dirname, file), 'utf8'));
  const schemaValid = validateRequest(request);
  const semanticValid = domainValid(request);
  const expected = !file.endsWith('.invalid.json');
  const passed = (schemaValid && semanticValid) === expected;
  console.log(`${passed ? 'PASS' : 'FAIL'} ${file} (schema=${schemaValid}, domain=${semanticValid})`);
  if (!passed) failed++;
}
process.exitCode = failed ? 1 : 0;
