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
            String componentType=text(component,"type");
            if (!br.com.archlab.simulation.core.v2.ComponentTypeV2.valueOf(componentType).isProcessor()) continue;
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
                        case "BRANCH" -> only(action,"id","kind","dependsOn","resultRef","cases","defaultActionIds");
                        case "CACHE_GET", "CACHE_INVALIDATE" -> only(action,"id","kind","dependsOn","connectionId","key","outputRef");
                        case "CACHE_PUT" -> only(action,"id","kind","dependsOn","connectionId","key","valueMetadata","ttlMs","outputRef");
                        case "OBJECT_GET", "OBJECT_DELETE" -> only(action,"id","kind","dependsOn","connectionId","objectKey","outputRef");
                        case "OBJECT_PUT" -> only(action,"id","kind","dependsOn","connectionId","objectKey","sizeBytes","metadata","outputRef");
                        case "SEND_NOTIFICATION" -> only(action,"id","kind","dependsOn","connectionId","channel","recipientAlias","outputRef","durationMs","timeoutMs","simulatedResult");
                        case "INDEX_DOCUMENT" -> only(action,"id","kind","dependsOn","connectionId","indexName","documentId","tags","metadata","outputRef");
                        case "SEARCH_DOCUMENTS" -> only(action,"id","kind","dependsOn","connectionId","indexName","tags","outputRef");
                        case "REGISTER_SERVICE" -> only(action,"id","kind","dependsOn","connectionId","serviceName","instanceId","targetComponentId","instanceStatus","outputRef");
                        case "UNREGISTER_SERVICE" -> only(action,"id","kind","dependsOn","connectionId","serviceName","instanceId","outputRef");
                        case "DISCOVER_SERVICE" -> only(action,"id","kind","dependsOn","connectionId","serviceName","outputRef");
                        case "EVALUATE_RULE" -> only(action,"id","kind","dependsOn","connectionId","outputRef");
                        case "SCHEDULE_TRIGGER" -> only(action,"id","kind","dependsOn","scheduleId");
                        case "TRANSFORM" -> only(action,"id","kind","dependsOn","connectionId","metadata","outputRef");
                        case "DB_SELECT", "DB_INSERT", "DB_UPDATE", "DB_DELETE", "DB_GET", "DB_PUT",
                                "DB_FIND", "DB_BULK_INSERT" -> only(action,"id","kind","dependsOn",
                                "connectionId","outputRef","inputRef","collection","key","recordCount",
                                "averageRecordBytes","field","equals","limit");
                        case "ETL_EXTRACT", "ETL_LOAD" -> only(action,"id","kind","dependsOn",
                                "connectionId","outputRef","inputRef","batchSize","maxRecords","transform");
                        case "ETL_TRANSFORM" -> only(action,"id","kind","dependsOn",
                                "outputRef","inputRef","batchSize","maxRecords","transform");
                        default -> throw new IllegalArgumentException("Unknown action kind");
                    }
                    List<String> dependencies = new ArrayList<>(), actionIds = new ArrayList<>();
                    for (JsonNode dependency : array(action,"dependsOn")) dependencies.add(dependency.asText());
                    for (JsonNode reference : array(action,"actionIds")) actionIds.add(reference.asText());
                    actions.add(new Action(text(action,"id"),kind,List.copyOf(dependencies),
                            optional(action,"connectionId"),optional(action,"destinationName"),
                            optional(action,"sessionAlias"),optional(action,"sender"),optional(action,"payload"),
                            number(action,"durationMs",0),number(action,"timeoutMs",0),
                            (int)number(action,"statusCode",200),List.copyOf(actionIds),options(action)));
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
            if (first != null && components.get(first.targetId()) instanceof ServiceComponentV5 gateway
                    && gateway.type()==ComponentTypeV2.API_GATEWAY) {
                String operation=entry.operation();
                if (operation==null && first.integration() instanceof HttpRestIntegrationV2 rest && rest.request()!=null)
                    operation=rest.request().method()+" "+rest.request().path();
                if (match(gateway.id(),"REQUEST_RECEIVED",operation,null,null)==null)
                    error("INVALID_ROUTING_RULE","scenario.entry","Gateway has no matching explicit route Behavior");
            }
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
            if (components.get(componentId)==null || !components.get(componentId).type().isProcessor())
                error("INVALID_BEHAVIOR_TRIGGER","architecture.components","Behaviors require a processing component");
            if (components.get(componentId) instanceof ServiceComponentV5 service) {
                for (var schedule:service.schedules())
                    if (schedule.behaviorId()!=null && item.getValue().stream().noneMatch(b ->
                            b.id().equals(schedule.behaviorId()) && b.trigger().equals("SCHEDULED_TRIGGER")))
                        error("INVALID_SCHEDULE","architecture.components["+componentId+"].schedules",
                                "Schedule behaviorId must reference a local SCHEDULED_TRIGGER Behavior");
                for (var route:service.routes()) {
                    ConnectionV2 routeConnection=connections.get(route.connectionId());
                    if (routeConnection==null || !componentId.equals(routeConnection.sourceId())
                            || routeConnection.integration().kind().isMessaging())
                        error("INVALID_ROUTING_RULE","architecture.components["+componentId+"].routes",
                                "Gateway route must reference an outgoing request connection");
                }
            }
            Set<String> behaviorIds=new HashSet<>();
            List<Behavior> seenMatches=new ArrayList<>();
            for (int i=0;i<item.getValue().size();i++) {
                Behavior behavior=item.getValue().get(i);
                String path="architecture.components["+componentId+"].behaviors["+i+"]";
                if (!behaviorIds.add(behavior.id())) error("DUPLICATE_BEHAVIOR_ID",path+".id","Duplicate behavior ID");
                if (!Set.of("REQUEST_RECEIVED","MESSAGE_CONSUMED","WS_MESSAGE_RECEIVED","SESSION_OPENED","SCHEDULED_TRIGGER").contains(behavior.trigger()))
                    error("INVALID_BEHAVIOR_TRIGGER",path+".trigger","Unknown trigger");
                if (seenMatches.stream().anyMatch(other -> overlaps(other,behavior)))
                    error("AMBIGUOUS_BEHAVIOR_MATCH",path+".match","Overlapping trigger matchers");
                seenMatches.add(behavior);
                if ((behavior.operation()!=null && !"REQUEST_RECEIVED".equals(behavior.trigger()))
                        || (behavior.destinationName()!=null && !"MESSAGE_CONSUMED".equals(behavior.trigger()))
                        || (behavior.websocketPath()!=null && !("WS_MESSAGE_RECEIVED".equals(behavior.trigger()) || "SESSION_OPENED".equals(behavior.trigger()))))
                    error("INVALID_BEHAVIOR_MATCH",path+".match","Matcher does not apply to trigger");
                if (components.get(componentId) instanceof ServiceComponentV5 gateway
                        && gateway.type()==ComponentTypeV2.API_GATEWAY) {
                    if (!"REQUEST_RECEIVED".equals(behavior.trigger()) || behavior.operation()==null)
                        error("INVALID_ROUTING_RULE",path+".match","Gateway needs an explicit request operation matcher");
                    for (Action action:behavior.actions()) if ("CALL".equals(action.kind())
                            && gateway.routes().stream().noneMatch(route ->
                                    route.operation().equals(behavior.operation())
                                    && route.connectionId().equals(action.connectionId())))
                        error("INVALID_ROUTING_RULE",path+".actions["+action.id()+"]",
                                "Gateway CALL must select its matching route connection");
                }
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
                    if (!capability(components.get(componentId),behavior.trigger(),action.kind()))
                        error("INVALID_COMPONENT_CAPABILITY",actionPath,"Action or trigger is incompatible with component type");
                    validateActionShape(action,actionPath);
                    if (Set.of("ETL_TRANSFORM","ETL_LOAD").contains(action.kind())) {
                        Object inputRef=action.options().get("inputRef");
                        boolean source=actions.values().stream().anyMatch(other ->
                                other!=action && Objects.equals(inputRef,other.options().get("outputRef"))
                                        && action.dependsOn().contains(other.id()));
                        if (!source) error("DATA_OUTPUT_REF_INVALID",actionPath+".inputRef",
                                "ETL inputRef must name an output of a direct dependency");
                    }
                    ConnectionV2 connection=connections.get(action.connectionId());
                    if (Set.of("CALL","PUBLISH","OPEN_SESSION").contains(action.kind())) {
                        if (connection==null || !(connection.sourceId().equals(componentId)
                                || ("OPEN_SESSION".equals(action.kind()) && connection.targetId().equals(componentId))))
                            error("INVALID_ACTION_CONNECTION",actionPath+".connectionId","Connection does not belong to action component");
                        else if (("CALL".equals(action.kind()) && (connection.integration().kind().isMessaging() || connection.integration().kind()==IntegrationKindV2.WEBSOCKET))
                                || ("PUBLISH".equals(action.kind()) && !connection.integration().kind().isMessaging())
                                || ("OPEN_SESSION".equals(action.kind()) && connection.integration().kind()!=IntegrationKindV2.WEBSOCKET))
                            error("INVALID_ACTION_CAPABILITY",actionPath,"Connection profile is incompatible with action");
                        else if ("CALL".equals(action.kind()) && components.get(connection.targetId())!=null
                                && components.get(connection.targetId()).type().isVirtualResource())
                            error("INVALID_ACTION_CAPABILITY",actionPath,"Virtual resources require their typed Action");
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
                    String expectedTarget=resourceTarget(action.kind());
                    if (isDataAction(action.kind())) {
                        ComponentV2 target=connection==null?null:components.get(connection.targetId());
                        if (connection==null || !componentId.equals(connection.sourceId())
                                || !(target instanceof DataResourceV6 data)
                                || connection.integration().kind()!=IntegrationKindV2.DATABASE_QUERY
                                || !dataActionTarget(action.kind(),data.type()))
                            error("DATA_ACTION_INCOMPATIBLE",actionPath+".connectionId",
                                    "Data Action requires a directed DATABASE_QUERY connection to a compatible resource");
                    }
                    if (expectedTarget!=null) {
                        if (connection==null || !componentId.equals(connection.sourceId())
                                || components.get(connection.targetId())==null
                                || !expectedTarget.equals(components.get(connection.targetId()).type().name())
                                || connection.integration().kind().isMessaging()
                                || connection.integration().kind()==IntegrationKindV2.WEBSOCKET
                                || connection.integration().kind()==IntegrationKindV2.DATABASE_QUERY)
                            error("INVALID_RESOURCE_CONNECTION",actionPath+".connectionId",
                                    "Action requires an explicit synchronous connection to "+expectedTarget);
                    }
                    if ("TRANSFORM".equals(action.kind())
                            && (connection==null || !componentId.equals(connection.sourceId())))
                        error("INVALID_ACTION_CONNECTION",actionPath+".connectionId",
                                "TRANSFORM needs an explicit outgoing connection");
                    if ("SCHEDULE_TRIGGER".equals(action.kind()) && (!(components.get(componentId) instanceof ServiceComponentV5 scheduler)
                            || scheduler.schedules().stream().noneMatch(s -> s.id().equals(action.options().get("scheduleId")))))
                        error("INVALID_SCHEDULE",actionPath+".scheduleId","Unknown Scheduler scheduleId");
                    if ("REGISTER_SERVICE".equals(action.kind()) &&
                            (components.get(action.options().get("targetComponentId"))==null
                                || !components.get(action.options().get("targetComponentId")).type().isProcessor()))
                        error("INVALID_VIRTUAL_RESOURCE_OPERATION",actionPath+".targetComponentId",
                                "Registered target must be a processing component in the architecture");
                    if ("EVALUATE_RULE".equals(action.kind()) && connection!=null
                            && components.get(connection.targetId()) instanceof ServiceComponentV5 rules) {
                        Set<String> preceding=ancestors(action,actions);
                        for (var rule:rules.rules()) if (actions.values().stream().noneMatch(candidate ->
                                preceding.contains(candidate.id())
                                && rule.field().equals(candidate.options().get("outputRef"))))
                            error("INVALID_DECISION_RULE",actionPath,
                                    "Rule field must reference a preceding Action outputRef");
                    }
                }
                validateBranches(actions,path,behavior.trigger());
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
    private void validateBranches(Map<String,Action> actions,String path,String trigger) {
        Map<String,Action> producers=new HashMap<>();
        for (Action action:actions.values()) {
            Object output=action.options().get("outputRef");
            if (output instanceof String ref && producers.putIfAbsent(ref,action)!=null)
                error("INVALID_ACTION_DEPENDENCY",path+".actions["+action.id()+"].outputRef","Duplicate outputRef");
        }
        for (Action branch:actions.values()) {
            if (!"BRANCH".equals(branch.kind())) continue;
            String branchPath=path+".actions["+branch.id()+"]";
            Object ref=branch.options().get("resultRef");
            Action producer=ref instanceof String name?producers.get(name):null;
            if (producer==null || !ancestors(branch,actions).contains(producer.id()))
                error("INVALID_ACTION_DEPENDENCY",branchPath+".resultRef","BRANCH needs a completed predecessor outputRef");
            Object raw=branch.options().get("cases");
            if (!(raw instanceof List<?> cases) || cases.isEmpty() || cases.size()>20) {
                error("INVALID_ACTION_DEPENDENCY",branchPath+".cases","BRANCH needs 1..20 cases");
                continue;
            }
            Set<String> values=new HashSet<>(), targets=new HashSet<>();
            for (Object item:cases) {
                if (!(item instanceof Map<?,?> option) || !(option.get("equals") instanceof String equals)
                        || !(option.get("actionIds") instanceof List<?> ids) || ids.isEmpty()) {
                    error("INVALID_ACTION_DEPENDENCY",branchPath+".cases","Invalid BRANCH case");continue;
                }
                if (!values.add(equals)) error("INVALID_ACTION_DEPENDENCY",branchPath+".cases","Duplicate resultCode case");
                if (producer!=null && !resultDomain(producer.kind()).contains(equals))
                    error("INVALID_ACTION_DEPENDENCY",branchPath+".cases","Case value is outside the producer result domain");
                for (Object target:ids) {
                    if (!(target instanceof String id) || !targets.add(id) || !actions.containsKey(id)
                            || !ancestors(actions.get(id),actions).contains(branch.id())
                            || ancestors(branch,actions).contains(id))
                        error("INVALID_ACTION_DEPENDENCY",branchPath+".cases","Branch target must be a unique descendant");
                }
            }
            Object defaultRaw=branch.options().get("defaultActionIds");
            if (defaultRaw instanceof List<?> defaults) for (Object target:defaults)
                if (!(target instanceof String id) || !targets.add(id) || !actions.containsKey(id)
                        || !ancestors(actions.get(id),actions).contains(branch.id()))
                    error("INVALID_ACTION_DEPENDENCY",branchPath+".defaultActionIds","Default target must be a unique descendant");
            if ("REQUEST_RECEIVED".equals(trigger) && producer!=null
                    && actions.values().stream().anyMatch(a -> "RESPOND".equals(a.kind())
                        && ancestors(a,actions).contains(branch.id()))
                    && (defaultRaw==null || defaultRaw instanceof List<?> defaults && defaults.isEmpty())
                    && !values.containsAll(resultDomain(producer.kind())))
                error("INVALID_RESPONSE_CONTEXT",branchPath,
                        "Request BRANCH must cover every possible resultCode or declare a default response path");
            Set<String> allDirect=new HashSet<>();
            for (Action action:actions.values()) if (action.dependsOn().contains(branch.id())) allDirect.add(action.id());
            if (!targets.containsAll(allDirect))
                error("INVALID_ACTION_DEPENDENCY",branchPath,"All direct branch dependents must belong to a case or default");
        }
        List<Action> responses=actions.values().stream().filter(a -> "RESPOND".equals(a.kind())).toList();
        for (int i=0;i<responses.size();i++) for (int j=i+1;j<responses.size();j++)
            if (!exclusiveResponses(responses.get(i),responses.get(j),actions))
                error("INVALID_RESPONSE_CONTEXT",path+".actions",
                        "Two RESPOND actions may be reachable in the same request");
    }
    private static boolean exclusiveResponses(Action first,Action second,Map<String,Action> actions) {
        Set<String> firstAncestors=ancestors(first,actions), secondAncestors=ancestors(second,actions);
        for (Action branch:actions.values()) {
            if (!"BRANCH".equals(branch.kind())) continue;
            Object raw=branch.options().get("cases");
            if (!(raw instanceof List<?> cases)) continue;
            List<List<?>> groups=new ArrayList<>();
            for (Object item:cases) if (item instanceof Map<?,?> option
                    && option.get("actionIds") instanceof List<?> ids) groups.add(ids);
            if (branch.options().get("defaultActionIds") instanceof List<?> defaults) groups.add(defaults);
            Set<Integer> left=new HashSet<>(),right=new HashSet<>();
            for (int i=0;i<groups.size();i++) for (Object id:groups.get(i)) {
                if (id.equals(first.id()) || firstAncestors.contains(id)) left.add(i);
                if (id.equals(second.id()) || secondAncestors.contains(id)) right.add(i);
            }
            if (left.size()==1 && right.size()==1 && !left.equals(right)) return true;
        }
        return false;
    }
    private static Set<String> ancestors(Action action,Map<String,Action> actions) {
        Set<String> seen=new HashSet<>();
        Deque<String> pending=new ArrayDeque<>(action.dependsOn());
        while (!pending.isEmpty()) {
            String id=pending.pop();
            if (seen.add(id) && actions.containsKey(id)) pending.addAll(actions.get(id).dependsOn());
        }
        return seen;
    }
    private static String resourceTarget(String kind) {
        return switch(kind) {
            case "CACHE_GET","CACHE_PUT","CACHE_INVALIDATE" -> "CACHE";
            case "OBJECT_GET","OBJECT_PUT","OBJECT_DELETE" -> "OBJECT_STORAGE";
            case "SEND_NOTIFICATION" -> "NOTIFICATION_SERVICE";
            case "INDEX_DOCUMENT","SEARCH_DOCUMENTS" -> "SEARCH_ENGINE";
            case "REGISTER_SERVICE","UNREGISTER_SERVICE","DISCOVER_SERVICE" -> "SERVICE_REGISTRY";
            case "EVALUATE_RULE" -> "RULES_ENGINE";
            default -> null;
        };
    }
    private static boolean isDataAction(String kind) {
        return kind.startsWith("DB_") || "ETL_EXTRACT".equals(kind) || "ETL_LOAD".equals(kind);
    }
    private static boolean dataActionTarget(String kind,ComponentTypeV2 type) {
        return switch(type) {
            case RELATIONAL_DATABASE -> Set.of("DB_SELECT","DB_INSERT","DB_UPDATE","DB_DELETE",
                    "DB_BULK_INSERT","ETL_EXTRACT","ETL_LOAD").contains(kind);
            case DOCUMENT_DATABASE -> Set.of("DB_GET","DB_PUT","DB_FIND","DB_UPDATE",
                    "DB_DELETE","DB_BULK_INSERT","ETL_EXTRACT","ETL_LOAD").contains(kind);
            case KEY_VALUE_STORE -> Set.of("DB_GET","DB_PUT","DB_DELETE","ETL_EXTRACT","ETL_LOAD").contains(kind);
            default -> false;
        };
    }
    private static Set<String> resultDomain(String kind) {
        return switch(kind) {
            case "CACHE_GET" -> Set.of("HIT","MISS");
            case "CACHE_PUT" -> Set.of("STORED");
            case "CACHE_INVALIDATE","UNREGISTER_SERVICE" -> Set.of("REMOVED","NOT_FOUND");
            case "OBJECT_GET" -> Set.of("FOUND","NOT_FOUND");
            case "OBJECT_PUT" -> Set.of("CREATED","REPLACED");
            case "OBJECT_DELETE" -> Set.of("DELETED","NOT_FOUND");
            case "SEND_NOTIFICATION" -> Set.of("SENT","FAILED","TIMED_OUT");
            case "INDEX_DOCUMENT" -> Set.of("INDEXED","UPDATED");
            case "SEARCH_DOCUMENTS" -> Set.of("FOUND");
            case "DISCOVER_SERVICE" -> Set.of("FOUND","UNAVAILABLE");
            case "REGISTER_SERVICE" -> Set.of("REGISTERED");
            case "EVALUATE_RULE" -> Set.of("ALLOW","DENY","REVIEW");
            case "TRANSFORM" -> Set.of("TRANSFORMED");
            default -> Set.of();
        };
    }
    private void validateActionShape(Action action,String path) {
        Map<String,Object> values=action.options();
        String[] required=switch(action.kind()) {
            case "BRANCH" -> new String[]{"resultRef","cases"};
            case "CACHE_GET","CACHE_INVALIDATE" -> new String[]{"key"};
            case "CACHE_PUT" -> new String[]{"key","valueMetadata"};
            case "OBJECT_GET","OBJECT_DELETE" -> new String[]{"objectKey"};
            case "OBJECT_PUT" -> new String[]{"objectKey","sizeBytes"};
            case "SEND_NOTIFICATION" -> new String[]{"channel","recipientAlias"};
            case "INDEX_DOCUMENT" -> new String[]{"indexName","documentId"};
            case "SEARCH_DOCUMENTS" -> new String[]{"indexName"};
            case "REGISTER_SERVICE" -> new String[]{"serviceName","instanceId","targetComponentId"};
            case "UNREGISTER_SERVICE" -> new String[]{"serviceName","instanceId"};
            case "DISCOVER_SERVICE" -> new String[]{"serviceName"};
            case "SCHEDULE_TRIGGER" -> new String[]{"scheduleId"};
            case "DB_SELECT" -> new String[]{"collection"};
            case "DB_INSERT","DB_UPDATE","DB_DELETE","DB_GET","DB_PUT" -> new String[]{"key"};
            case "DB_FIND" -> new String[]{"field","equals"};
            case "DB_BULK_INSERT" -> new String[]{"recordCount"};
            case "ETL_TRANSFORM" -> new String[]{"inputRef","transform"};
            case "ETL_LOAD" -> new String[]{"inputRef"};
            default -> new String[]{};
        };
        for (String name:required) if (!values.containsKey(name)
                || values.get(name) instanceof String value && value.isBlank())
            error("INVALID_VIRTUAL_RESOURCE_OPERATION",path+"."+name,"Missing required Action property");
        if ("SEND_NOTIFICATION".equals(action.kind())) {
            if (!Set.of("EMAIL","SMS","PUSH").contains(String.valueOf(values.get("channel"))))
                error("INVALID_VIRTUAL_RESOURCE_OPERATION",path+".channel","Unknown notification channel");
            if (!Set.of("SENT","FAILED","TIMED_OUT").contains(String.valueOf(values.getOrDefault("simulatedResult","SENT"))))
                error("INVALID_VIRTUAL_RESOURCE_OPERATION",path+".simulatedResult","Unknown simulated result");
        }
        if (values.get("ttlMs") instanceof Number ttl && (ttl.longValue()<0 || ttl.longValue()>1_000_000_000))
            error("INVALID_VIRTUAL_RESOURCE_OPERATION",path+".ttlMs","TTL is outside virtual bounds");
        if (values.get("sizeBytes") instanceof Number size && (size.longValue()<0 || size.longValue()>1_000_000_000))
            error("INVALID_VIRTUAL_RESOURCE_OPERATION",path+".sizeBytes","Object size is outside virtual bounds");
        if ("REGISTER_SERVICE".equals(action.kind())
                && !Set.of("ACTIVE","INACTIVE").contains(String.valueOf(values.getOrDefault("instanceStatus","ACTIVE"))))
            error("INVALID_VIRTUAL_RESOURCE_OPERATION",path+".instanceStatus","Unknown logical instance status");
        if (isDataAction(action.kind()) || "ETL_TRANSFORM".equals(action.kind())) {
            for (String name:List.of("recordCount","averageRecordBytes","limit","batchSize","maxRecords")) {
                Object value=values.get(name);
                if (value!=null && (!(value instanceof Number n) || n.longValue()<1
                        || n.longValue()>("averageRecordBytes".equals(name)?1_048_576L:
                                "maxRecords".equals(name)?1_000_000L:100_000L)))
                    error("DATA_LIMIT_EXCEEDED",path+"."+name,"Data Action exceeds finite budget");
            }
        }
        if ("ETL_TRANSFORM".equals(action.kind())
                && !"IDENTITY".equals(values.get("transform")))
            error("DATA_COMPONENT_UNSUPPORTED",path+".transform",
                    "Only IDENTITY has defined P0 record semantics");
    }
    private static boolean capability(ComponentV2 component,String trigger,String kind) {
        if (component==null) return false;
        if (component.type()==ComponentTypeV2.API) return Set.of("PROCESS","CALL","PUBLISH","RESPOND",
                "OPEN_SESSION","WS_SEND","CLOSE_SESSION","FORK","JOIN").contains(kind)
                && !"SCHEDULED_TRIGGER".equals(trigger);
        if ("SCHEDULED_TRIGGER".equals(trigger) && !Set.of(ComponentTypeV2.SCHEDULER,
                ComponentTypeV2.WORKER,ComponentTypeV2.ORCHESTRATOR_SERVICE).contains(component.type())) return false;
        if ("MESSAGE_CONSUMED".equals(trigger) && !Set.of(ComponentTypeV2.SERVICE,
                ComponentTypeV2.WORKER,ComponentTypeV2.NOTIFICATION_SERVICE,
                ComponentTypeV2.ORCHESTRATOR_SERVICE,ComponentTypeV2.INTEGRATION_BUS).contains(component.type())) return false;
        if (("WS_MESSAGE_RECEIVED".equals(trigger) || "SESSION_OPENED".equals(trigger))
                && component.type()!=ComponentTypeV2.SERVICE) return false;
        if ("REQUEST_RECEIVED".equals(trigger) && component.type()==ComponentTypeV2.WORKER) return false;
        if ("RESPOND".equals(kind) && !"REQUEST_RECEIVED".equals(trigger)) return false;
        if ("SCHEDULE_TRIGGER".equals(kind)) return component.type()==ComponentTypeV2.SCHEDULER;
        if ("TRANSFORM".equals(kind)) return component.type()==ComponentTypeV2.INTEGRATION_BUS;
        if (kind.startsWith("DB_")) return Set.of(ComponentTypeV2.SERVICE,
                ComponentTypeV2.ORCHESTRATOR_SERVICE,ComponentTypeV2.WORKER,
                ComponentTypeV2.BFF,ComponentTypeV2.ETL_PIPELINE).contains(component.type());
        if (kind.startsWith("ETL_")) return component.type()==ComponentTypeV2.ETL_PIPELINE
                || "ETL_TRANSFORM".equals(kind) && component.type()==ComponentTypeV2.DATA_TRANSFORMER;
        if (Set.of("OPEN_SESSION","WS_SEND","CLOSE_SESSION").contains(kind))
            return component.type()==ComponentTypeV2.SERVICE;
        Set<String> common=Set.of("PROCESS","CALL","PUBLISH","RESPOND","FORK","JOIN","BRANCH");
        Set<String> resources=Set.of("CACHE_GET","CACHE_PUT","CACHE_INVALIDATE","OBJECT_GET",
                "OBJECT_PUT","OBJECT_DELETE","INDEX_DOCUMENT","SEARCH_DOCUMENTS",
                "REGISTER_SERVICE","UNREGISTER_SERVICE","DISCOVER_SERVICE","EVALUATE_RULE",
                "SEND_NOTIFICATION");
        return switch(component.type()) {
            case SERVICE,ORCHESTRATOR_SERVICE -> common.contains(kind) || resources.contains(kind);
            case BFF -> Set.of("PROCESS","CALL","RESPOND","FORK","JOIN","BRANCH").contains(kind)
                    || resources.contains(kind);
            case API_GATEWAY -> Set.of("PROCESS","CALL","RESPOND","BRANCH").contains(kind);
            case WORKER -> Set.of("PROCESS","CALL","PUBLISH","FORK","JOIN","BRANCH").contains(kind)
                    || resources.contains(kind);
            case SCHEDULER -> "PROCESS".equals(kind) || "RESPOND".equals(kind)
                    || "SCHEDULED_TRIGGER".equals(trigger) && Set.of("CALL","PUBLISH").contains(kind);
            case AUTHENTICATION_SERVICE -> Set.of("PROCESS","EVALUATE_RULE","BRANCH","RESPOND").contains(kind);
            case WEBHOOK_RECEIVER -> Set.of("PROCESS","CALL","PUBLISH","RESPOND").contains(kind);
            case INTEGRATION_BUS -> Set.of("PROCESS","CALL","PUBLISH","RESPOND","BRANCH").contains(kind);
            case NOTIFICATION_SERVICE -> Set.of("PROCESS","RESPOND","PUBLISH").contains(kind);
            default -> false;
        };
    }
    private static Map<String,Object> options(JsonNode action) {
        Set<String> standard=Set.of("id","kind","dependsOn","connectionId","destinationName",
                "sessionAlias","sender","payload","durationMs","timeoutMs","statusCode","actionIds");
        Map<String,Object> values=new LinkedHashMap<>();
        for (String name:action.propertyNames()) if (!standard.contains(name))
            values.put(name,value(action.get(name)));
        return values;
    }
    private static Object value(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isIntegralNumber()) return node.asLong();
        if (node.isObject()) {
            Map<String,Object> result=new LinkedHashMap<>();
            for (String name:node.propertyNames()) result.put(name,value(node.get(name)));
            return Map.copyOf(result);
        }
        if (node.isArray()) {List<Object> result=new ArrayList<>();node.forEach(n -> result.add(value(n)));return List.copyOf(result);}
        throw new IllegalArgumentException("Unsupported Action value");
    }
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
