package br.com.archlab.simulation.core.v41;

import br.com.archlab.simulation.core.v2.ServiceComponentV5;
import br.com.archlab.simulation.core.v41.BehaviorPlanV41.Action;
import java.util.*;

/** Bounded per-run resource state. The caller schedules operations on the shared virtual clock. */
final class VirtualResourcesV5 {
    record Outcome(String resultCode, Map<String,Object> details) {
        Outcome { details=Map.copyOf(details); }
    }
    private record CacheEntry(String metadata,long expiresAt) {}
    private record ObjectEntry(long sizeBytes) {}
    private record DocumentEntry(Map<String,String> tags) {}
    private record Instance(String id,String target) {}
    private final Map<String,Map<String,CacheEntry>> caches=new HashMap<>();
    private final Map<String,Map<String,ObjectEntry>> objects=new HashMap<>();
    private final Map<String,Map<String,Map<String,DocumentEntry>>> indexes=new HashMap<>();
    private final Map<String,Map<String,Map<String,Instance>>> registries=new HashMap<>();
    private static final int MAX_ENTRIES=1000;

    Outcome execute(ServiceComponentV5 resource,Action action,long now,Map<String,String> priorResults) {
        String id=resource.id(), kind=action.kind();
        Map<String,Object> p=action.options();
        return switch(kind) {
            case "CACHE_GET" -> {
                CacheEntry entry=caches.getOrDefault(id,Map.of()).get(str(p,"key"));
                yield entry==null || now>=entry.expiresAt()
                    ? new Outcome("MISS",Map.of())
                    : new Outcome("HIT",Map.of("valueMetadata",entry.metadata()));
            }
            case "CACHE_PUT" -> {
                Map<String,CacheEntry> entries=caches.computeIfAbsent(id,k -> new LinkedHashMap<>());
                String key=str(p,"key"); limit(entries,key);
                long ttl=p.containsKey("ttlMs")?number(p,"ttlMs"):resource.defaultTtlMs();
                long expires=ttl==0?now:Math.addExact(now,ttl);
                entries.put(key,new CacheEntry(str(p,"valueMetadata"),expires));
                yield new Outcome("STORED",Map.of("expiresAtMs",expires));
            }
            case "CACHE_INVALIDATE" -> {
                Map<String,CacheEntry> entries=caches.get(id);
                boolean removed=entries!=null && entries.remove(str(p,"key"))!=null;
                yield new Outcome(removed?"REMOVED":"NOT_FOUND",Map.of());
            }
            case "OBJECT_GET" -> {
                ObjectEntry entry=objects.getOrDefault(id,Map.of()).get(str(p,"objectKey"));
                yield entry==null?new Outcome("NOT_FOUND",Map.of())
                    : new Outcome("FOUND",Map.of("sizeBytes",entry.sizeBytes()));
            }
            case "OBJECT_PUT" -> {
                Map<String,ObjectEntry> entries=objects.computeIfAbsent(id,k -> new LinkedHashMap<>());
                String key=str(p,"objectKey"); limit(entries,key);
                ObjectEntry previous=entries.put(key,new ObjectEntry(number(p,"sizeBytes")));
                yield new Outcome(previous==null?"CREATED":"REPLACED",Map.of("sizeBytes",number(p,"sizeBytes")));
            }
            case "OBJECT_DELETE" -> {
                Map<String,ObjectEntry> entries=objects.get(id);
                boolean removed=entries!=null && entries.remove(str(p,"objectKey"))!=null;
                yield new Outcome(removed?"DELETED":"NOT_FOUND",Map.of());
            }
            case "INDEX_DOCUMENT" -> {
                String index=str(p,"indexName"), document=str(p,"documentId");
                Map<String,DocumentEntry> entries=indexes.computeIfAbsent(id,k -> new HashMap<>())
                    .computeIfAbsent(index,k -> new TreeMap<>());
                limit(entries,document);
                DocumentEntry previous=entries.put(document,new DocumentEntry(tags(p)));
                yield new Outcome(previous==null?"INDEXED":"UPDATED",Map.of("documentId",document));
            }
            case "SEARCH_DOCUMENTS" -> {
                Map<String,String> criteria=tags(p);
                List<String> found=new ArrayList<>();
                indexes.getOrDefault(id,Map.of()).getOrDefault(str(p,"indexName"),Map.of())
                    .forEach((document,entry) -> {
                        if (criteria.entrySet().stream().allMatch(e -> e.getValue().equals(entry.tags().get(e.getKey()))))
                            found.add(document);
                    });
                Collections.sort(found);
                yield new Outcome("FOUND",Map.of("documentIds",String.join(",",found),"count",found.size()));
            }
            case "REGISTER_SERVICE" -> {
                String name=str(p,"serviceName"), instance=str(p,"instanceId");
                Map<String,Instance> entries=registries.computeIfAbsent(id,k -> new HashMap<>())
                    .computeIfAbsent(name,k -> new TreeMap<>());
                limit(entries,instance);
                entries.put(instance,new Instance(instance,str(p,"targetComponentId")));
                yield new Outcome("REGISTERED",Map.of("instanceId",instance));
            }
            case "UNREGISTER_SERVICE" -> {
                Map<String,Map<String,Instance>> services=registries.get(id);
                Map<String,Instance> entries=services==null?null:services.get(str(p,"serviceName"));
                boolean removed=entries!=null && entries.remove(str(p,"instanceId"))!=null;
                yield new Outcome(removed?"REMOVED":"NOT_FOUND",Map.of());
            }
            case "DISCOVER_SERVICE" -> {
                Map<String,Instance> entries=registries.getOrDefault(id,Map.of())
                    .getOrDefault(str(p,"serviceName"),Map.of());
                String targets=entries.values().stream().map(Instance::target).sorted().distinct()
                    .reduce((a,b) -> a+","+b).orElse("");
                yield new Outcome(entries.isEmpty()?"UNAVAILABLE":"FOUND",Map.of("targetComponentIds",targets));
            }
            case "EVALUATE_RULE" -> {
                ServiceComponentV5.Rule selected=resource.rules().stream()
                    .sorted(Comparator.comparingInt(ServiceComponentV5.Rule::priority).reversed())
                    .filter(rule -> matches(rule,priorResults.get(rule.field())))
                    .findFirst().orElse(null);
                yield new Outcome(selected==null?resource.defaultDecision():selected.decision(),
                    selected==null?Map.of():Map.of("ruleId",selected.id()));
            }
            case "SEND_NOTIFICATION" -> new Outcome((String)p.getOrDefault("simulatedResult","SENT"),
                    Map.of("channel",str(p,"channel"),"recipientAlias",str(p,"recipientAlias")));
            case "TRANSFORM" -> new Outcome("TRANSFORMED",Map.of());
            default -> throw new IllegalArgumentException("Unsupported virtual resource operation: "+kind);
        };
    }
    private static String str(Map<String,Object> options,String key) {
        Object value=options.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Missing "+key);
        return text;
    }
    private static long number(Map<String,Object> options,String key) {
        Object value=options.get(key);
        if (!(value instanceof Number number) || number.longValue()<0 || number.longValue()>1_000_000_000)
            throw new IllegalArgumentException("Invalid "+key);
        return number.longValue();
    }
    @SuppressWarnings("unchecked")
    private static Map<String,String> tags(Map<String,Object> options) {
        Object value=options.get("tags");
        if (value==null) return Map.of();
        Map<String,Object> raw=(Map<String,Object>)value;
        if (raw.size()>32) throw new IllegalArgumentException("Too many tags");
        Map<String,String> result=new TreeMap<>();
        raw.forEach((k,v) -> result.put(k,(String)v));
        return result;
    }
    private static void limit(Map<String,?> entries,String key) {
        if (!entries.containsKey(key) && entries.size()>=MAX_ENTRIES)
            throw new IllegalArgumentException("Virtual resource capacity exceeded");
    }
    private static boolean matches(ServiceComponentV5.Rule rule,String actual) {
        if (actual==null) return false;
        String expected=String.valueOf(rule.expectedValue());
        return switch(rule.operator()) {
            case "EQ" -> actual.equals(expected);
            case "NE" -> !actual.equals(expected);
            case "GT","GTE","LT","LTE" -> {
                int comparison;
                try {comparison=new java.math.BigDecimal(actual).compareTo(new java.math.BigDecimal(expected));}
                catch(NumberFormatException ex) {yield false;}
                yield switch(rule.operator()) {
                    case "GT" -> comparison>0;
                    case "GTE" -> comparison>=0;
                    case "LT" -> comparison<0;
                    default -> comparison<=0;
                };
            }
            case "IN" -> rule.expectedValue() instanceof List<?> values
                    && values.stream().map(String::valueOf).anyMatch(actual::equals);
            default -> false;
        };
    }
}
