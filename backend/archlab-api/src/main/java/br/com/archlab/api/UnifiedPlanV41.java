package br.com.archlab.api;

import br.com.archlab.simulation.core.v2.*;
import br.com.archlab.simulation.core.v41.BehaviorPlanV41.Action;
import br.com.archlab.simulation.core.v41.BehaviorPlanV41.Behavior;
import br.com.archlab.simulation.core.v41.UnifiedExecutionPlanV41;
import br.com.archlab.simulation.core.v41.UnifiedExecutionPlanV41.Entry;
import br.com.archlab.simulation.core.v41.UnifiedExecutionPlanV41.SessionSetup;
import br.com.archlab.simulation.core.validation.ValidationResult;
import br.com.archlab.simulation.core.validation.Violation;
import tools.jackson.databind.JsonNode;
import java.util.*;

/** Typed, finite behavior graph decoded from the v0.4.1 wire contract. */
final class UnifiedPlanV41 {

    final ArchitectureV2 architecture;
    final Entry entry;
    final List<SessionSetup> sessionSetup;
    final Map<String,List<Behavior>> behaviors;
    private final List<Violation> errors = new ArrayList<>();

    private UnifiedPlanV41(ArchitectureV2 architecture, Entry entry, List<SessionSetup> setup,
                           Map<String,List<Behavior>> behaviors) {
        this.architecture=architecture; this.entry=entry; this.sessionSetup=List.copyOf(setup);
        this.behaviors=Map.copyOf(behaviors);
    }
    UnifiedExecutionPlanV41 toExecutionPlan() {
        return new UnifiedExecutionPlanV41(architecture,entry,sessionSetup,behaviors);
    }

    static UnifiedPlanV41 parse(JsonNode root, ArchitectureV2 architecture) {
        return decode(root,architecture,true);
    }
    static UnifiedPlanV41 parseArchitecture(JsonNode root, ArchitectureV2 architecture) {
        return decode(root,architecture,false);
    }
    private static UnifiedPlanV41 decode(JsonNode root,ArchitectureV2 architecture,boolean full) {
        if (full) only(root,"architecture","scenario");
        JsonNode scenario=full?field(root,"scenario"):null;
        JsonNode entry=full?field(scenario,"entry"):null;
        if (full) {only(scenario,"entry","sessionSetup");only(entry,"componentId","connectionId","operation");}
        Entry start=full?new Entry(text(entry,"componentId"),text(entry,"connectionId"),optional(entry,"operation")):null;
        List<SessionSetup> setup = new ArrayList<>();
        for (JsonNode node : array(scenario,"sessionSetup"))
            { only(node,"connectionId","sessionAlias","autoCloseAtEnd");
            if (node.has("autoCloseAtEnd") && !node.get("autoCloseAtEnd").isBoolean())
                throw new IllegalArgumentException("autoCloseAtEnd must be boolean");
            setup.add(new SessionSetup(text(node,"connectionId"),text(node,"sessionAlias"),
                    !node.has("autoCloseAtEnd") || node.get("autoCloseAtEnd").asBoolean())); }
        Map<String,List<Behavior>> plans = new LinkedHashMap<>();
        JsonNode architectureNode=full?field(root,"architecture"):root;
        for (JsonNode component : array(architectureNode,"components")) {
            if (!"API".equals(text(component,"type"))) continue;
            List<Behavior> parsed = new ArrayList<>();
            for (JsonNode behavior : array(component,"behaviors")) {
                only(behavior,"id","trigger","match","actions");
                JsonNode match = behavior.get("match");
                if (match!=null) only(match,"operation","destinationName","websocketPath");
                List<Action> actions = new ArrayList<>();
                for (JsonNode action : array(behavior,"actions")) {
                    String kind=text(action,"kind");
                    switch(kind) {
                        case "PROCESS" -> only(action,"id","kind","dependsOn","durationMs");
                        case "CALL" -> only(action,"id","kind","dependsOn","connectionId","timeoutMs");
                        case "PUBLISH" -> only(action,"id","kind","dependsOn","connectionId","destinationName");
                        case "RESPOND" -> only(action,"id","kind","dependsOn","statusCode");
                        case "OPEN_SESSION" -> only(action,"id","kind","dependsOn","connectionId","sessionAlias");
                        case "WS_SEND" -> only(action,"id","kind","dependsOn","sessionAlias","payload","sender");
                        case "CLOSE_SESSION" -> only(action,"id","kind","dependsOn","sessionAlias");
                        case "FORK", "JOIN" -> only(action,"id","kind","dependsOn","actionIds");
                        default -> throw new IllegalArgumentException("Unknown action kind");
                    }
                    List<String> dependencies = new ArrayList<>(), actionIds = new ArrayList<>();
                    for (JsonNode dependency : array(action,"dependsOn")) dependencies.add(dependency.asText());
                    for (JsonNode reference : array(action,"actionIds")) actionIds.add(reference.asText());
                    actions.add(new Action(text(action,"id"),kind,List.copyOf(dependencies),
                            optional(action,"connectionId"),optional(action,"destinationName"),
                            optional(action,"sessionAlias"),optional(action,"sender"),optional(action,"payload"),
                            number(action,"durationMs",0),number(action,"timeoutMs",0),
                            (int)number(action,"statusCode",200),List.copyOf(actionIds)));
                }
                parsed.add(new Behavior(text(behavior,"id"),text(behavior,"trigger"),
                        optional(match,"operation"),optional(match,"destinationName"),
                        optional(match,"websocketPath"),List.copyOf(actions)));
            }
            plans.put(text(component,"id"),List.copyOf(parsed));
        }
        return new UnifiedPlanV41(architecture,start,setup,plans);
    }

    ValidationResult validate() {return validate(true);}
    ValidationResult validateArchitecture() {return validate(false);}
    private ValidationResult validate(boolean full) {
        Map<String,ComponentV2> components = new HashMap<>();
        Map<String,ConnectionV2> connections = new HashMap<>();
        architecture.components().forEach(c -> components.put(c.id(),c));
        architecture.connections().forEach(c -> connections.put(c.id(),c));
        ConnectionV2 first=null;
        if (full) {
            ComponentV2 start = components.get(entry.componentId());
            first = connections.get(entry.connectionId());
            if (!(start instanceof ClientV2 || start instanceof ApiV2) || first == null || !first.sourceId().equals(entry.componentId()))
                error("INVALID_ENTRY_CONNECTION","scenario.entry","Entry must select an outgoing connection of a Client or API");
            if (first != null && entry.operation() != null && first.integration() instanceof HttpRestIntegrationV2 rest
                    && rest.request() != null && !entry.operation().equals(rest.request().method()+" "+rest.request().path()))
                error("INVALID_ENTRY_OPERATION","scenario.entry.operation","Operation does not match entry connection");
        }
        Map<String,ConnectionV2> aliases = new HashMap<>();
        if (first != null && first.integration().kind()==IntegrationKindV2.WEBSOCKET) aliases.put(first.id(),first);
        for (int i=0;i<sessionSetup.size();i++) {
            SessionSetup setup = sessionSetup.get(i);
            ConnectionV2 connection = connections.get(setup.connectionId());
            if (connection == null || connection.integration().kind() != IntegrationKindV2.WEBSOCKET)
                error("INVALID_SESSION_REFERENCE","scenario.sessionSetup["+i+"]","Session setup needs a WebSocket connection");
            if (aliases.putIfAbsent(setup.alias(),connection)!=null) error("INVALID_SESSION_REFERENCE","scenario.sessionSetup["+i+"].sessionAlias","Duplicate session alias");
            if (!setup.autoClose() && (connection == null || !(connection.integration() instanceof WebSocketIntegrationV2 ws) || ws.idleTimeoutMs() == null))
                error("UNBOUNDED_EXECUTION","scenario.sessionSetup["+i+"]","Persistent session needs a finite idle timeout");
        }
        int totalActions=0;
        for (var item : behaviors.entrySet()) {
            String componentId=item.getKey();
            if (!(components.get(componentId) instanceof ApiV2)) error("INVALID_BEHAVIOR_TRIGGER","architecture.components","Behaviors require API component");
            Set<String> behaviorIds=new HashSet<>();
            List<Behavior> seenMatches=new ArrayList<>();
            for (int i=0;i<item.getValue().size();i++) {
                Behavior behavior=item.getValue().get(i);
                String path="architecture.components["+componentId+"].behaviors["+i+"]";
                if (!behaviorIds.add(behavior.id())) error("DUPLICATE_BEHAVIOR_ID",path+".id","Duplicate behavior ID");
                if (!Set.of("REQUEST_RECEIVED","MESSAGE_CONSUMED","WS_MESSAGE_RECEIVED","SESSION_OPENED").contains(behavior.trigger()))
                    error("INVALID_BEHAVIOR_TRIGGER",path+".trigger","Unknown trigger");
                if (seenMatches.stream().anyMatch(other -> overlaps(other,behavior)))
                    error("AMBIGUOUS_BEHAVIOR_MATCH",path+".match","Overlapping trigger matchers");
                seenMatches.add(behavior);
                if ((behavior.operation()!=null && !"REQUEST_RECEIVED".equals(behavior.trigger()))
                        || (behavior.destinationName()!=null && !"MESSAGE_CONSUMED".equals(behavior.trigger()))
                        || (behavior.websocketPath()!=null && !("WS_MESSAGE_RECEIVED".equals(behavior.trigger()) || "SESSION_OPENED".equals(behavior.trigger()))))
                    error("INVALID_BEHAVIOR_MATCH",path+".match","Matcher does not apply to trigger");
                Map<String,Action> actions=new LinkedHashMap<>();
                for (Action action : behavior.actions()) {
                    if (actions.putIfAbsent(action.id(),action)!=null) error("DUPLICATE_ACTION_ID",path+".actions","Duplicate action ID");
                    totalActions++;
                }
                if (behavior.actions().isEmpty() || behavior.actions().size()>100) error("INVALID_BEHAVIOR_ACTIONS",path+".actions","Action count must be 1..100");
                for (Action action : behavior.actions()) {
                    String actionPath=path+".actions["+action.id()+"]";
                    Set<String> dependencies=new HashSet<>(action.dependsOn());
                    if (dependencies.size()!=action.dependsOn().size() || dependencies.contains(action.id())
                            || dependencies.stream().anyMatch(id -> !actions.containsKey(id)))
                        error("INVALID_ACTION_DEPENDENCY",actionPath+".dependsOn","Unknown, duplicate or self dependency");
                    if (!Set.of("PROCESS","CALL","PUBLISH","RESPOND","OPEN_SESSION","WS_SEND","CLOSE_SESSION","FORK","JOIN").contains(action.kind()))
                        error("UNKNOWN_ACTION_KIND",actionPath+".kind","Unknown action kind");
                    ConnectionV2 connection=connections.get(action.connectionId());
                    if (Set.of("CALL","PUBLISH","OPEN_SESSION").contains(action.kind())) {
                        if (connection==null || !(connection.sourceId().equals(componentId)
                                || ("OPEN_SESSION".equals(action.kind()) && connection.targetId().equals(componentId))))
                            error("INVALID_ACTION_CONNECTION",actionPath+".connectionId","Connection does not belong to action component");
                        else if (("CALL".equals(action.kind()) && (connection.integration().kind().isMessaging() || connection.integration().kind()==IntegrationKindV2.WEBSOCKET))
                                || ("PUBLISH".equals(action.kind()) && !connection.integration().kind().isMessaging())
                                || ("OPEN_SESSION".equals(action.kind()) && connection.integration().kind()!=IntegrationKindV2.WEBSOCKET))
                            error("INVALID_ACTION_CAPABILITY",actionPath,"Connection profile is incompatible with action");
                    }
                    if ("PUBLISH".equals(action.kind()) && connection!=null && components.get(connection.targetId()) instanceof BrokerV2 broker
                            && broker.destinations().stream().noneMatch(d -> d.name().equals(action.destinationName()) && d.kind()==connection.integration().kind()))
                        error("INVALID_BROKER_DESTINATION",actionPath+".destinationName","Broker destination does not match publication");
                    if ("RESPOND".equals(action.kind()) && !"REQUEST_RECEIVED".equals(behavior.trigger()))
                        error("INVALID_RESPONSE_CONTEXT",actionPath,"RESPOND needs request context");
                    if ("RESPOND".equals(action.kind()) && (action.statusCode()<100 || action.statusCode()>599))
                        error("INVALID_RESPONSE_CONTEXT",actionPath+".statusCode","HTTP status must be 100..599");
                    if ("OPEN_SESSION".equals(action.kind())) {
                        String alias=action.sessionAlias()==null?action.id():action.sessionAlias();
                        if (aliases.putIfAbsent(alias,connection)!=null)
                            error("INVALID_SESSION_REFERENCE",actionPath+".sessionAlias","Duplicate session alias");
                    }
                    if (("FORK".equals(action.kind()) || "JOIN".equals(action.kind())) &&
                            (action.actionIds().isEmpty() || action.actionIds().stream().anyMatch(id -> !actions.containsKey(id) || id.equals(action.id()))))
                        error("INVALID_ACTION_DEPENDENCY",actionPath+".actionIds","FORK/JOIN references must name other local actions");
                    if ("FORK".equals(action.kind()) && action.actionIds().stream()
                            .anyMatch(id -> actions.containsKey(id) && !actions.get(id).dependsOn().contains(action.id())))
                        error("INVALID_ACTION_DEPENDENCY",actionPath+".actionIds","Fork children must depend on the FORK action");
                }
                if (cycle(actions)) error("INVALID_ACTION_DEPENDENCY",path+".actions","Action dependency cycle");
            }
        }
        if (totalActions>10_000) error("UNBOUNDED_EXECUTION","architecture.components","Too many actions");
        if (full) for (var item : behaviors.entrySet()) for (Behavior behavior : item.getValue()) for (Action action : behavior.actions())
            if (("WS_SEND".equals(action.kind()) || "CLOSE_SESSION".equals(action.kind())) && !aliases.containsKey(action.sessionAlias()))
                error("SESSION_NOT_OPEN","architecture.components["+item.getKey()+"].behaviors["+behavior.id()+"].actions["+action.id()+"]",
                        "Session alias has no setup or OPEN_SESSION action");
            else if (("WS_SEND".equals(action.kind()) || "CLOSE_SESSION".equals(action.kind())) &&
                    aliases.get(action.sessionAlias())!=null &&
                    !(aliases.get(action.sessionAlias()).sourceId().equals(item.getKey()) ||
                      aliases.get(action.sessionAlias()).targetId().equals(item.getKey())))
                error("INVALID_SESSION_REFERENCE","architecture.components["+item.getKey()+"].behaviors["+behavior.id()+"].actions["+action.id()+"]",
                        "API is not a participant of the referenced session");
        return new ValidationResult(errors.isEmpty(),List.copyOf(errors));
    }

    Behavior match(String componentId,String trigger,String operation,String destination,String path) {
        for (Behavior behavior : behaviors.getOrDefault(componentId,List.of()))
            if (behavior.trigger().equals(trigger) && (behavior.operation()==null || behavior.operation().equals(operation))
                    && (behavior.destinationName()==null || behavior.destinationName().equals(destination))
                    && (behavior.websocketPath()==null || behavior.websocketPath().equals(path))) return behavior;
        return null;
    }

    private static boolean cycle(Map<String,Action> actions) {
        Map<String,Integer> status=new HashMap<>();
        for (String id:actions.keySet()) if (visit(id,actions,status)) return true;
        return false;
    }
    private static boolean visit(String id,Map<String,Action> actions,Map<String,Integer> status) {
        if (status.getOrDefault(id,0)==1) return true;
        if (status.getOrDefault(id,0)==2 || !actions.containsKey(id)) return false;
        status.put(id,1);
        for (String dependency:actions.get(id).dependsOn()) if (visit(dependency,actions,status)) return true;
        if ("JOIN".equals(actions.get(id).kind()))
            for (String dependency:actions.get(id).actionIds()) if (visit(dependency,actions,status)) return true;
        status.put(id,2); return false;
    }
    private static boolean overlaps(Behavior a,Behavior b) {
        return a.trigger().equals(b.trigger()) && matches(a.operation(),b.operation())
                && matches(a.destinationName(),b.destinationName()) && matches(a.websocketPath(),b.websocketPath());
    }
    private static boolean matches(String a,String b) {return a==null || b==null || a.equals(b);}
    private void error(String code,String path,String message) {errors.add(new Violation(code,path,message));}
    private static JsonNode field(JsonNode node,String name) {
        if (node==null || !node.isObject() || node.get(name)==null) throw new IllegalArgumentException("Missing "+name);
        return node.get(name);
    }
    private static String text(JsonNode node,String name) {
        JsonNode value=field(node,name);
        if (!value.isTextual()) throw new IllegalArgumentException("Expected text at "+name);
        return value.asText();
    }
    private static String optional(JsonNode node,String name) {
        JsonNode value=node==null?null:node.get(name);
        if (value==null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("Expected text at "+name);
        return value.asText();
    }
    private static long number(JsonNode node,String name,long fallback) {
        JsonNode value=node.get(name);
        if (value==null) return fallback;
        if (!value.isIntegralNumber() || value.asLong()<0 || value.asLong()>1_000_000_000L)
            throw new IllegalArgumentException("Invalid duration or number at "+name);
        return value.asLong();
    }
    private static List<JsonNode> array(JsonNode node,String name) {
        JsonNode value=node==null?null:node.get(name);
        if (value==null) return List.of();
        if (!value.isArray()) throw new IllegalArgumentException("Expected array at "+name);
        List<JsonNode> result=new ArrayList<>(); value.forEach(result::add); return result;
    }
    private static void only(JsonNode node,String... names) {
        if (node==null || !node.isObject()) throw new IllegalArgumentException("Expected object");
        Set<String> allowed=Set.of(names);
        for (String name:node.propertyNames())
            if (!allowed.contains(name)) throw new IllegalArgumentException("Unknown field "+name);
    }
}
