// Usage: node tools/validate-v3-results.cjs <repo-root> <node_modules-directory>
// Independently checks actual Maven-generated HTTP/result artifacts; does not regenerate goldens.
const fs=require('node:fs'),path=require('node:path');const root=path.resolve(process.argv[2]||'.'),modules=path.resolve(process.argv[3]||'frontend/node_modules');
const yaml=require(path.join(modules,'yaml')),Ajv=require(path.join(modules,'ajv/dist/2020')),formats=require(path.join(modules,'ajv-formats'));
const document=yaml.parse(fs.readFileSync(path.join(root,'docs/api/openapi-v3.yaml'),'utf8'));const ajv=new Ajv({strict:false,allErrors:true});formats(ajv);ajv.addSchema({$id:'archlab-v3',components:document.components});let count=0;
function verify(file,schema){const validate=ajv.compile({$ref:'archlab-v3#/components/schemas/'+schema});if(!validate(JSON.parse(fs.readFileSync(file,'utf8')))){console.error(path.relative(root,file),validate.errors);process.exitCode=1;}else{count++;console.log('PASS',path.relative(root,file),schema);}}
verify(path.join(root,'docs/examples/v3/ce-ref-01-request.json'),'SimulationRequestV3');
for(const family of ['v3-results','v3-http']){const folder=path.join(root,'backend/archlab-api/target',family);if(!fs.existsSync(folder))throw Error('Run Maven verify first: missing '+folder);const files=fs.readdirSync(folder).filter(x=>x.endsWith('.json'));if(!files.length)throw Error('No actual artifacts in '+folder);for(const file of files){const schema=family==='v3-results'||/^(ce-ref|simulated|horizon)/.test(file)?'SimulationResponseV3':/valid/.test(file)&&file!=='invalid-simulation.json'?'ValidationResponseV3':'ProblemV3';verify(path.join(folder,file),schema);}}
for(const file of fs.readdirSync(path.join(root,'docs/examples/v3/events')))if(file.endsWith('.json'))verify(path.join(root,'docs/examples/v3/events',file),'SimulationEventPayloadV3');
console.log('Validated '+count+' actual artifacts/fixtures.');if(process.exitCode)process.exit(process.exitCode);
