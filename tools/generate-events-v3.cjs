const fs=require('node:fs'),path=require('node:path');
const root=process.argv[2]||process.cwd(),yaml=require(process.argv[3]||'yaml');
const schemas=yaml.parse(fs.readFileSync(path.join(root,'docs/api/openapi-v3.yaml'),'utf8')).components.schemas;
const entries=Object.entries(schemas).filter(([name])=>name.startsWith('EventPayload'));
if(entries.length!==32)throw Error('Expected closed catalog of 32 events');
const pkg='backend/archlab-simulation-core/src/main/java/br/com/archlab/simulation/core/v3/';
function put(relative,content){const target=path.join(root,relative);fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,content);}
function type(s){if(s.$ref?.endsWith('/IdentifierV3'))return 'IdentifierV3';if(s.$ref?.endsWith('/DurationMsV3'))return 'DurationMsV3';if(s.$ref?.endsWith('/RuntimeEntityRefV3'))return 'RuntimeEntityRefV3';if(s.type==='integer')return 'long';throw Error('Unmapped event field '+JSON.stringify(s));}
function name(x){return x.toLowerCase().split('_').map(x=>x[0].toUpperCase()+x.slice(1)).join('');}
let records='',decode='',encode='';
for(const [key,schema] of entries){
 const event=schema.properties.eventType.const,cls=name(event),props=Object.entries(schema.properties).filter(([k])=>k!=='eventType');
 if(props.some(([k])=>!schema.required.includes(k)))throw Error('Optional event fields require explicit mapping');
 records+=`    record ${cls}(${props.map(([k,s])=>type(s)+' '+k).join(',')}) implements SimulationEventV3 {\n`;
 records+=`        public ${cls} { ${props.map(([k,s])=>type(s)==='long'?`if(${k}<${s.minimum??0})throw new IllegalArgumentException("Invalid ${k}");`:`Objects.requireNonNull(${k},"${k}");`).join(' ')} }\n`;
 records+=`        public Type eventType(){return Type.${event};}\n    }\n`;
 decode+=`            case ${event} -> {object(n,${Object.keys(schema.properties).map(k=>JSON.stringify(k)).join(',')});yield new SimulationEventV3.${cls}(${props.map(([k,s])=>{let t=type(s);return t==='IdentifierV3'?`id(n,"${k}")`:t==='DurationMsV3'?`new DurationMsV3(number(n,"${k}"))`:t==='RuntimeEntityRefV3'?`reference(required(n,"${k}"))`:`number(n,"${k}")`;}).join(',')});}\n`;
 encode+=`            case SimulationEventV3.${cls} e -> {${props.map(([k,s])=>{let t=type(s);return `result.put("${k}",${t==='IdentifierV3'||t==='DurationMsV3'?`e.${k}().value()`:t==='RuntimeEntityRefV3'?`Map.of("kind",e.${k}().kind(),"id",e.${k}().id().value())`:`e.${k}()`});`;}).join('')}}\n`;
 const fixture={eventType:event};for(const [k,s] of props)fixture[k]=type(s)==='long'?(s.minimum??0):type(s)==='DurationMsV3'?0:type(s)==='RuntimeEntityRefV3'?{kind:'ACTIVATION',id:'activation-1'}:k+'-1';
 put('docs/examples/v3/events/'+event+'.json',JSON.stringify(fixture,null,2)+'\n');
}
put(pkg+'SimulationEventV3.java',`package br.com.archlab.simulation.core.v3;\nimport java.util.Objects;\n/** Closed payload catalog derived from OpenAPI v3; scheduler metadata remains in its envelope. */\npublic sealed interface SimulationEventV3 {\n    enum Type { ${entries.map(([,s])=>s.properties.eventType.const).join(',')} }\n    Type eventType();\n${records}}\n`);
put(pkg+'RuntimeEntityRefV3.java',`package br.com.archlab.simulation.core.v3;\nimport java.util.Objects;\npublic record RuntimeEntityRefV3(String kind,IdentifierV3 id) {\n    public RuntimeEntityRefV3 {Objects.requireNonNull(id);if(kind==null || kind.isEmpty())throw new IllegalArgumentException("Entity kind required");}\n}\n`);
put('backend/archlab-api/src/main/java/br/com/archlab/api/v3/InternalEventCodecV3.java',`package br.com.archlab.api.v3;
import br.com.archlab.simulation.core.v3.*;
import tools.jackson.databind.JsonNode;
import java.util.*;
/** Internal journal/snapshot codec, without exposing a new HTTP route. */
public final class InternalEventCodecV3 {
    public SimulationEventV3 decode(JsonNode n){return switch(SimulationEventV3.Type.valueOf(text(n,"eventType"))){
${decode}        };}
    public Map<String,Object> encode(SimulationEventV3 event){var result=new LinkedHashMap<String,Object>();result.put("eventType",event.eventType().name());switch(event){
${encode}        }return Collections.unmodifiableMap(result);}
    private RuntimeEntityRefV3 reference(JsonNode n){object(n,"kind","id");return new RuntimeEntityRefV3(text(n,"kind"),id(n,"id"));}
    private static void object(JsonNode n,String... keys){if(n==null || !n.isObject())throw new IllegalArgumentException("Expected object");var allowed=Set.of(keys);for(var key:n.propertyNames())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown event field: "+key);}
    private static JsonNode required(JsonNode n,String key){if(n==null || !n.isObject())throw new IllegalArgumentException("Expected object");var v=n.get(key);if(v==null || v.isNull())throw new IllegalArgumentException("Missing event field: "+key);return v;}
    private static String text(JsonNode n,String key){var v=required(n,key);if(!v.isTextual())throw new IllegalArgumentException("Expected string");return v.asText();}
    private static long number(JsonNode n,String key){var v=required(n,key);if(!v.isIntegralNumber() || !v.canConvertToLong())throw new IllegalArgumentException("Expected int64");return v.longValue();}
    private static IdentifierV3 id(JsonNode n,String key){return new IdentifierV3(text(n,key));}
}
`);
