package br.com.archlab.api.v3;
import br.com.archlab.simulation.core.v3.*;import java.util.*;
/** Explicit record boundary; domain value objects never leak as JSON objects. */
public final class WireValuesV3 {
    private WireValuesV3(){}
    public static Map<String,Object> object(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],plain(pairs[i+1]));return result;}
    public static Object plain(Object value){
        if(value==null || value instanceof String || value instanceof Number || value instanceof Boolean)return value;
        if(value instanceof IdentifierV3 id)return id.value();if(value instanceof DurationMsV3 d)return d.value();if(value instanceof Enum<?> e)return e.name();
        if(value instanceof Map<?,?> m){var r=new TreeMap<String,Object>();m.forEach((k,v)->r.put(k.toString(),plain(v)));return r;}
        if(value instanceof Collection<?> c)return c.stream().map(WireValuesV3::plain).toList();
        if(value.getClass().isRecord()){var r=new TreeMap<String,Object>();if(value instanceof TraceRecordV3.Detail d)r.put("kind",d.kind());
            try{for(var c:value.getClass().getRecordComponents())r.put(c.getName(),plain(c.getAccessor().invoke(value)));}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot serialize contract record",e);}return r;}
        throw new IllegalArgumentException("Unsupported contract value: "+value.getClass().getName());
    }
}
