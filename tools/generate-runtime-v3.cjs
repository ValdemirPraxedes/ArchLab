const fs=require('node:fs'),path=require('node:path'),root=process.argv[2],yaml=require(process.argv[3]);
const schemas=yaml.parse(fs.readFileSync(path.join(root,'docs/api/openapi-v3.yaml'),'utf8')).components.schemas;
const selected=['Activation','Interaction','Attempt','ParallelGroup','Resource','QueueEntry','Publication','DeliveryAttempt'].map(n=>[n,schemas[n+'SnapshotV3']]);
for(const [name,s] of Object.entries(schemas))if(name.endsWith('RuntimeEntityV3')&&s.properties)selected.push([name.replace('RuntimeEntityV3',''),s]);
const kind=n=>n.replace(/([a-z])([A-Z])/g,'$1_$2').toUpperCase();
function type(s,cls,k){if(s.$ref?.endsWith('/IdentifierV3')||s.$ref?.endsWith('/NullableIdentifierV3'))return 'IdentifierV3';if(s.$ref?.endsWith('/DurationMsV3'))return 'DurationMsV3';if(s.$ref?.endsWith('/SimulationEventPayloadV3'))return 'SimulationEventV3';if(s.type==='array')return 'List<IdentifierV3>';if(k==='status'&&s.enum)return cls+'Status';if(k==='phase')return 'DeterministicSchedulerV3.Phase';if(k==='causalEpoch')return 'DeterministicSchedulerV3.Epoch';if(s.type==='integer')return 'long';if(s.type==='string')return 'String';if(s.oneOf?.some(x=>x.type==='null'))return s.oneOf.some(x=>x.type==='integer')?'Long':'IdentifierV3';throw Error('Unmapped '+cls+'.'+k);}
function nullable(s){return s.$ref?.endsWith('/NullableIdentifierV3')||s.oneOf?.some(x=>x.type==='null');}
let enums='',records='';
for(const [cls,schema] of selected){
 const props=Object.entries(schema.properties).filter(([k])=>k!=='entityKind');
 const id=props.find(([k])=>k==='entityId')?.[0]||props.find(([k])=>k.endsWith('Id'))[0];
 const status=schema.properties.status;
 if(status?.enum)enums+=`    enum ${cls}Status {${status.enum.join(',')}}\n`;
 records+=`    record ${cls}(${props.map(([k,s])=>type(s,cls,k)+' '+k).join(',')}) implements RuntimeEntityV3 {\n`;
 records+=`        public ${cls} { ${props.map(([k,s])=>{const t=type(s,cls,k);if(t==='long')return `if(${k}<${s.minimum??0})throw new IllegalArgumentException("Invalid ${k}");`;if(t==='List<IdentifierV3>')return `${k}=List.copyOf(${k});`;if(t==='String')return `if(${k}==null || ${k}.isEmpty())throw new IllegalArgumentException("${k} required");`;if(t==='Long')return `if(${k}!=null && ${k}<0)throw new IllegalArgumentException("Invalid ${k}");`;return nullable(s)?'':`Objects.requireNonNull(${k},"${k}");`;}).join(' ')} ${cls==='Resource'?'if(capacity!=null && inUse!=null && inUse>capacity)throw new IllegalArgumentException("Resource over capacity");':''} }\n`;
 records+=`        public Kind kind(){return Kind.${kind(cls)};}\n        public IdentifierV3 id(){return ${id};}\n        public String stateName(){return ${status?(status.enum?'status.name()':'status'):'null'};}\n`;
 records+=`        public RuntimeEntityV3 transition(String target){${status?`StateTransitionsV3.require(kind(),stateName(),target);return new ${cls}(${props.map(([k,s])=>k==='version'?'Math.addExact(version,1)':k==='status'?(s.enum?cls+'Status.valueOf(target)':'target'):k).join(',')});`:'throw new IllegalStateException("Entity has no lifecycle state");'}}\n    }\n`;
}
for(const [cls,states] of [['Component',['AVAILABLE','UNAVAILABLE']],['Principal',['IN_PROGRESS','RESPONDED','FAILED','TIMED_OUT','CANCELLED']],['StepExecution',['READY','RUNNING','WAITING','COMPLETED','FAILED','CANCELLED']]]){
 enums+=`    enum ${cls}Status {${states.join(',')}}\n`;
 records+=`    record ${cls}(IdentifierV3 entityId,long version,${cls}Status status) implements RuntimeEntityV3 {\n        public ${cls} {Objects.requireNonNull(entityId);Objects.requireNonNull(status);if(version<0)throw new IllegalArgumentException("Negative version");}\n        public Kind kind(){return Kind.${kind(cls)};}\n        public IdentifierV3 id(){return entityId;}\n        public String stateName(){return status.name();}\n        public RuntimeEntityV3 transition(String target){StateTransitionsV3.require(kind(),stateName(),target);return new ${cls}(entityId,Math.addExact(version,1),${cls}Status.valueOf(target));}\n    }\n`;
}
let body=`package br.com.archlab.simulation.core.v3;\nimport java.util.*;\n/** Immutable runtime values. Public snapshot fields are kept typed, with mandatory core versions. */\npublic sealed interface RuntimeEntityV3 {\n    enum Kind {${[...selected.map(([n])=>kind(n)),'COMPONENT','PRINCIPAL','STEP_EXECUTION'].join(',')}}\n    Kind kind(); IdentifierV3 id(); long version(); String stateName(); RuntimeEntityV3 transition(String target);\n${enums}${records}}\n`;
const controlTemplate=path.join(root,'tools/templates/control-v3.java.txt');
if(fs.existsSync(controlTemplate)){body=body.replace('COMPONENT,PRINCIPAL,STEP_EXECUTION}','COMPONENT,PRINCIPAL,STEP_EXECUTION,CONTROL}');body=body.slice(0,body.lastIndexOf('}'))+fs.readFileSync(controlTemplate,'utf8')+'}\n';}
fs.writeFileSync(path.join(root,'backend/archlab-simulation-core/src/main/java/br/com/archlab/simulation/core/v3/RuntimeEntityV3.java'),body);
