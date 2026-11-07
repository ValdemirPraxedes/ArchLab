package br.com.archlab.simulation.core.v41;

import br.com.archlab.simulation.core.v2.DataResourceV6;
import br.com.archlab.simulation.core.v41.BehaviorPlanV41.Action;
import java.util.*;

/** Logical records owned by one UnifiedSimulationV41 invocation. No external I/O. */
final class VirtualDataResourcesV6 {
    record Outcome(String code, long readCount, long writtenCount, long rejectedCount,
                   long logicalBytes, Map<String,Object> details) {}
    private record Record(String field, String value, int bytes) {}
    private final Map<String,Map<String,Map<String,Record>>> records=new HashMap<>();
    private final Map<String,Long> counts=new HashMap<>();
    private final Map<String,Long> bytes=new HashMap<>();
    private static final long MAX_RECORDS=1_000_000L, MAX_BYTES=1_073_741_824L;

    long count(DataResourceV6 resource) {
        return counts.getOrDefault(resource.id(),(long)resource.initialRecordCount());
    }

    Outcome load(DataResourceV6 resource,long amount) {
        if (amount<0 || amount>MAX_RECORDS) throw new IllegalArgumentException("Invalid ETL load count");
        long current=count(resource);
        long used=bytes.getOrDefault(resource.id(),current*resource.averageRecordBytes());
        long added=amount*resource.averageRecordBytes();
        if (amount>MAX_RECORDS-current || added>MAX_BYTES-used)
            return new Outcome("LIMIT_EXCEEDED",0,0,amount,0,Map.of("errorCode","DATA_LIMIT_EXCEEDED"));
        counts.put(resource.id(),current+amount);
        bytes.put(resource.id(),used+added);
        return new Outcome("LOADED",0,amount,0,added,Map.of());
    }

    Outcome execute(DataResourceV6 resource, Action action) {
        String id=resource.id(), kind=action.kind();
        Map<String,Object> p=action.options();
        String collection=string(p,"collection","default");
        Map<String,Map<String,Record>> groups=records.computeIfAbsent(id,k -> new LinkedHashMap<>());
        Map<String,Record> entries=groups.computeIfAbsent(collection,k -> new TreeMap<>());
        long current=counts.computeIfAbsent(id,k -> (long)resource.initialRecordCount());
        long used=bytes.computeIfAbsent(id,k -> (long)resource.initialRecordCount()*resource.averageRecordBytes());
        String key=string(p,"key",null);
        Record previous=key==null?null:entries.get(key);
        int size=(int)number(p,"averageRecordBytes",resource.averageRecordBytes());
        String field=string(p,"field",null), value=string(p,"equals",null);
        switch(kind) {
            case "DB_GET" -> {
                return new Outcome(previous==null?"NOT_FOUND":"FOUND",previous==null?0:1,0,0,
                        previous==null?0:previous.bytes(),Map.of());
            }
            case "DB_SELECT", "DB_FIND" -> {
                long found;
                if (key!=null) found=previous==null?0:1;
                else if (field!=null) found=entries.values().stream().filter(r ->
                        field.equals(r.field()) && Objects.equals(value,r.value())).count();
                else found=current;
                found=Math.min(found,number(p,"limit",100_000));
                return new Outcome(found==0?"NOT_FOUND":"FOUND",found,0,0,
                        Math.min(MAX_BYTES,found*resource.averageRecordBytes()),Map.of());
            }
            case "DB_INSERT", "DB_PUT", "DB_BULK_INSERT", "DB_UPDATE" -> {
                long amount="DB_BULK_INSERT".equals(kind)?number(p,"recordCount",1):1;
                if ("DB_UPDATE".equals(kind) && previous==null)
                    return new Outcome("NOT_FOUND",0,0,0,0,Map.of());
                if ("DB_INSERT".equals(kind) && previous!=null)
                    return new Outcome("FAILED",0,0,1,0,Map.of("errorCode","DUPLICATE_KEY"));
                if (amount>MAX_RECORDS-current+(previous==null?0:1)
                        || amount*size>MAX_BYTES-used+(previous==null?0:previous.bytes()))
                    return new Outcome("LIMIT_EXCEEDED",0,0,amount,0,Map.of("errorCode","DATA_LIMIT_EXCEEDED"));
                if ("DB_BULK_INSERT".equals(kind)) {
                    // A batch is represented by a bounded logical count, not one heap object per row.
                    counts.put(id,current+amount);
                    bytes.put(id,used+amount*size);
                } else {
                    // DB_UPDATE modifies an existing record: attributes it does not declare are kept.
                    // DB_INSERT/DB_PUT write the declared record as a whole.
                    boolean keep="DB_UPDATE".equals(kind) && field==null;
                    entries.put(key,new Record(keep?previous.field():field,keep?previous.value():value,size));
                    counts.put(id,current+(previous==null?1:0));
                    bytes.put(id,used+size-(previous==null?0:previous.bytes()));
                }
                String code="DB_UPDATE".equals(kind)?"UPDATED":
                        "DB_PUT".equals(kind)?"STORED":"INSERTED";
                return new Outcome(code,0,amount,0,amount*size,Map.of());
            }
            case "DB_DELETE" -> {
                if (previous==null) return new Outcome("NOT_FOUND",0,0,0,0,Map.of());
                entries.remove(key);
                counts.put(id,current-1);
                bytes.put(id,used-previous.bytes());
                return new Outcome("DELETED",0,1,0,previous.bytes(),Map.of());
            }
            default -> throw new IllegalArgumentException("Unsupported data Action: "+kind);
        }
    }
    private static String string(Map<String,Object> p,String name,String fallback) {
        Object value=p.get(name);
        return value instanceof String s?s:fallback;
    }
    private static long number(Map<String,Object> p,String name,long fallback) {
        Object value=p.get(name);
        return value instanceof Number n?n.longValue():fallback;
    }
}
