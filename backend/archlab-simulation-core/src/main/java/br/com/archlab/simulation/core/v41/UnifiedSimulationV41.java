package br.com.archlab.simulation.core.v41;

import br.com.archlab.simulation.core.v2.*;
import br.com.archlab.simulation.core.v41.BehaviorPlanV41.Action;
import br.com.archlab.simulation.core.v41.BehaviorPlanV41.Behavior;
import br.com.archlab.simulation.core.v4.WebSocketRuntimeV4;
import br.com.archlab.simulation.core.v4.BrokerRoutingV4;
import java.util.*;

/** One virtual clock for every flow reachable from the selected starting component. */
public final class UnifiedSimulationV41 {
    private static final long MAX_TIME = 3_600_000L;
    private static final int MAX_EVENTS = 50_000;
    private record Scheduled(long at, long order, Runnable action) {}
    private record Resource(String id, String kind, String status) {}
    private record Session(String id, String alias, ConnectionV2 edge, boolean autoClose, String status) {}
    private interface Completion { void finish(boolean successful); }

    private final ArchitectureV2 architecture;
    private final UnifiedExecutionPlanV41 plan;
    private final br.com.archlab.simulation.core.v4.ExecutionLifecycleV4 lifecycle =
            new br.com.archlab.simulation.core.v4.ExecutionLifecycleV4();
    private final String startId;
    private final Map<String,Session> sessions = new LinkedHashMap<>();
    private final Map<String,Scheduled> sessionTimeouts = new HashMap<>();
    private final Map<String,Integer> activeSessionBehaviors = new HashMap<>();
    private final Map<String,Runnable> pendingSessionClose = new HashMap<>();
    private final Map<String,ComponentV2> components = new LinkedHashMap<>();
    private final Map<String,List<ConnectionV2>> outgoing = new LinkedHashMap<>();
    private final Map<String,ConnectionV2> connections = new LinkedHashMap<>();
    private final Map<String,Resource> resources = new LinkedHashMap<>();
    private final Map<String,Integer> activeProcessing = new HashMap<>();
    private final Map<String,String> componentStatus = new HashMap<>();
    private final Map<String,Integer> queueCursors = new HashMap<>();
    private final Map<String,ConnectionV2> usedConnections = new LinkedHashMap<>();
    private final PriorityQueue<Scheduled> queue = new PriorityQueue<>(Comparator.comparingLong(Scheduled::at).thenComparingLong(Scheduled::order));
    private final List<Map<String,Object>> trace = new ArrayList<>();
    private final List<Map<String,Object>> timeline = new ArrayList<>();
    private final List<Map<String,Object>> waits = new ArrayList<>();
    private final List<Map<String,Object>> exchanges = new ArrayList<>();
    private final List<Map<String,Object>> websocketMetrics = new ArrayList<>();
    private final Map<String,long[]> websocketCounts = new LinkedHashMap<>();
    private final Map<String,long[]> messagingMetrics = new LinkedHashMap<>();
    private final List<Map<String,Object>> rootOperations = new ArrayList<>();
    private final List<Map<String,Object>> resourceOutcomes = new ArrayList<>();
    private final List<Map<String,Object>> dataOutcomes = new ArrayList<>();
    private final VirtualResourcesV5 virtualResources = new VirtualResourcesV5();
    private final VirtualDataResourcesV6 virtualData = new VirtualDataResourcesV6();
    private final Map<String,Integer> activeData = new HashMap<>();
    private final Map<String,Deque<Runnable>> waitingData = new HashMap<>();
    private long now, scheduleOrder;
    private int messageNumber, exchangeNumber, resourceNumber, activityNumber, pendingRoots, scheduledCount,
            scheduleInvocationNumber;
    private boolean mainFinished, mainTimedOut, derivedFailed;
    private Integer responseStatusCode;

    public UnifiedSimulationV41(UnifiedExecutionPlanV41 plan) {
        this.plan = Objects.requireNonNull(plan);
        this.architecture = plan.architecture();
        this.startId = plan.entry().componentId();
        for (ComponentV2 component : architecture.components()) {
            components.put(component.id(), component);
            outgoing.put(component.id(), new ArrayList<>());
        }
        for (ConnectionV2 connection : architecture.connections()) {
            connections.put(connection.id(),connection);
            outgoing.computeIfAbsent(connection.sourceId(), ignored -> new ArrayList<>()).add(connection);
        }
    }

    public Map<String,Object> run() {
        ConnectionV2 root = connections.get(plan.entry().connectionId());
        if (root == null) throw new IllegalArgumentException("Missing entry connection");
        List<ConnectionV2> roots = List.of(root);
        state("REQUEST","request-1","CREATED","IN_PROGRESS","SIMULATION_STARTED");
        pendingRoots = 1;
        if (plan.sessionSetup().isEmpty()) startRoot(root);
        else {
            int[] remaining = {plan.sessionSetup().size()};
            for (UnifiedExecutionPlanV41.SessionSetup setup : plan.sessionSetup())
                openSession(connections.get(setup.connectionId()),setup.alias(),setup.autoClose(),null,successful -> {
                    if (!successful) derivedFailed = true;
                    if (--remaining[0] == 0) startRoot(root);
                });
        }
        while (!queue.isEmpty()) {
            Scheduled next = queue.remove();
            now = next.at();
            next.action().run();
            if (queue.isEmpty() && mainFinished) closeAutomaticSessions();
        }
        if (!mainFinished || pendingRoots != 0) throw new IllegalStateException("Main execution did not finish");
        if (resources.values().stream().anyMatch(r -> "ACTIVE".equals(r.status())))
            throw new IllegalStateException("Active resource remained after event queue drained");
        if (derivedFailed) lifecycle.markDerivedFailure();
        String globalStatus = Objects.requireNonNull(lifecycle.globalStatus()).name();
        state("EXECUTION","exec-001","ACTIVE",globalStatus,"ALL_RESOURCES_TERMINAL");
        return response(globalStatus,roots);
    }

    private void startRoot(ConnectionV2 edge) {
        Map<String,Object> operation = fields("id","root-1","connectionId",edge.id(),
                "kind",edge.integration().kind().name(),"status","IN_PROGRESS");
        rootOperations.add(operation);
        launch(edge,null,successful -> {
            operation.put("status",successful ? "COMPLETED" : "TIMED_OUT");
            operation.put("completedAtMs",now);
            rootDone(successful);
        });
    }

    private void rootDone(boolean successful) {
        if (!successful) mainTimedOut = true;
        if (--pendingRoots == 0) {
            mainFinished = true;
            lifecycle.finishMain(mainTimedOut
                    ? br.com.archlab.simulation.core.v4.ExecutionLifecycleV4.MainStatus.TIMED_OUT
                    : br.com.archlab.simulation.core.v4.ExecutionLifecycleV4.MainStatus.COMPLETED);
            if (components.get(startId) instanceof ClientV2)
                componentState(startId, mainTimedOut ? "TIMED_OUT" : "COMPLETED", "ROOT_OPERATIONS_TERMINAL");
            state("REQUEST","request-1","IN_PROGRESS",mainTimedOut ? "TIMED_OUT" : "COMPLETED","ROOT_OPERATIONS_TERMINAL");
        }
    }

    private void launch(ConnectionV2 edge, String parentExchangeId, Completion done) {
        IntegrationKindV2 kind = edge.integration().kind();
        if (kind.isMessaging()) launchPublication(edge,parentExchangeId,done);
        else if (kind == IntegrationKindV2.WEBSOCKET) launchWebSocket(edge,parentExchangeId,done);
        else launchSynchronous(edge,parentExchangeId,done);
    }

    private void launchSynchronous(ConnectionV2 edge, String parentExchangeId, Completion done) {
        String exchangeId = "exchange-"+(++exchangeNumber);
        long started = now;
        String intent = edge.integration().kind() == IntegrationKindV2.DATABASE_QUERY ? "DATABASE_QUERY" : "API_REQUEST";
        send(edge,edge.sourceId(),edge.targetId(),"REQUEST",intent,exchangeId,parentExchangeId,() -> {
            ComponentV2 target = components.get(edge.targetId());
            Completion reply = successful -> send(edge,edge.targetId(),edge.sourceId(),"RESPONSE",intent,
                    exchangeId,parentExchangeId,() -> {
                        exchanges.add(fields("exchangeId",exchangeId,"parentExchangeId",parentExchangeId,
                                "callerComponentId",edge.sourceId(),"calleeComponentId",edge.targetId(),
                                "connectionId",edge.id(),"startMs",started,"endMs",now,"durationMs",now-started));
                        done.finish(successful);
                    });
            if (target != null && target.type().isProcessor()) activateApi(target,edge,exchangeId,"REQUEST_RECEIVED",operation(edge),null,null,reply);
            else if (target instanceof DatabaseV2 database)
                process(database.id(),"DATABASE_PROCESSING",database.processingMs(),() -> reply.finish(true));
            else throw new IllegalArgumentException("Invalid synchronous recipient");
        });
    }

    private void activateApi(ComponentV2 api, ConnectionV2 incoming, String parentExchangeId,
                             String trigger, String operation, String destinationName, String websocketPath,
                             Completion done) {
        long pre = incoming != null && incoming.apiProcessingOverride() != null
                ? incoming.apiProcessingOverride().preProcessingMs() : pre(api);
        long post = incoming != null && incoming.apiProcessingOverride() != null
                ? incoming.apiProcessingOverride().postProcessingMs() : post(api);
        process(api.id(),"API_PRE_PROCESSING",pre,() -> {
            Behavior behavior=plan.match(api.id(),trigger,operation,destinationName,websocketPath);
            if (behavior==null) {
                boolean legacy=api.type()==ComponentTypeV2.API;
                process(api.id(),"API_POST_PROCESSING",post,() -> done.finish(legacy));
                return;
            }
            executeBehavior(api,behavior,parentExchangeId,post,done);
        });
    }

    private static String operation(ConnectionV2 edge) {
        if (edge.integration() instanceof HttpRestIntegrationV2 rest && rest.request()!=null)
            return rest.request().method()+" "+rest.request().path();
        return edge.integration().kind().name();
    }

    private static long pre(ComponentV2 component) {
        return component instanceof ApiV2 api?api.preProcessingMs():
                component instanceof ServiceComponentV5 service?service.preProcessingMs():0;
    }
    private static long post(ComponentV2 component) {
        return component instanceof ApiV2 api?api.postProcessingMs():
                component instanceof ServiceComponentV5 service?service.postProcessingMs():0;
    }

    private void executeBehavior(ComponentV2 api, Behavior behavior, String parent,
                                 long postDuration, Completion done) {
        new BehaviorRun(api,behavior,parent,postDuration,done).pump();
    }

    private final class BehaviorRun {
        private final ComponentV2 api;
        private final Behavior behavior;
        private final String parent;
        private final long postDuration;
        private final Completion done;
        private final Map<String,String> status=new LinkedHashMap<>();
        private final Map<String,String> resultCodes=new HashMap<>();
        private record Batch(long count,boolean normalized) {}
        private final Map<String,Batch> batchRefs=new HashMap<>();
        private boolean responseSent, completionSent;

        BehaviorRun(ComponentV2 api, Behavior behavior, String parent,long postDuration,Completion done) {
            this.api=api; this.behavior=behavior; this.parent=parent; this.postDuration=postDuration; this.done=done;
            for (Action action:behavior.actions()) status.put(action.id(),"PENDING");
        }
        void pump() {
            for (Action action:behavior.actions()) {
                if (!"PENDING".equals(status.get(action.id()))) continue;
                List<String> dependencies=new ArrayList<>(action.dependsOn());
                if ("JOIN".equals(action.kind())) dependencies.addAll(action.actionIds());
                if (dependencies.stream().anyMatch(id -> "SKIPPED".equals(status.get(id)))) {
                    status.put(action.id(),"SKIPPED"); actionEvent(api.id(),action.id(),"SKIPPED","BRANCH_NOT_SELECTED",parent);
                } else if (dependencies.stream().anyMatch(id -> "FAILED".equals(status.get(id)))
                        && !("BRANCH".equals(action.kind())
                            && resultCodes.containsKey(action.options().get("resultRef")))) {
                    boolean skipped="ETL_LOAD".equals(action.kind());
                    status.put(action.id(),skipped?"SKIPPED":"FAILED");
                    actionEvent(api.id(),action.id(),skipped?"SKIPPED":"FAILED","DEPENDENCY_FAILED",parent);
                    if (skipped) {
                        ConnectionV2 edge=connections.get(action.connectionId());
                        Map<String,Object> result=fields("operationId",parent==null?"request-1":parent,
                                "actionId",action.id(),"componentId",api.id(),
                                "resourceComponentId",edge.targetId(),"connectionId",edge.id(),
                                "timestampMs",now,"status","SKIPPED","resultCode","FAILED",
                                "readCount",0,"writtenCount",0,"rejectedCount",0,"logicalBytes",0,
                                "errorCode","ETL_UPSTREAM_FAILED");
                        dataOutcomes.add(result);
                        event(fields("type","ACTION_EVENT","actionId",action.id(),
                                "componentId",edge.targetId(),"operationId",parent==null?"request-1":parent,
                                "status","SKIPPED","cause","ETL_UPSTREAM_FAILED","outcome",result));
                    }
                } else if (dependencies.stream().allMatch(id -> "COMPLETED".equals(status.get(id))
                        || "BRANCH".equals(action.kind()) && "FAILED".equals(status.get(id)))) {
                    status.put(action.id(),"SCHEDULED");
                    schedule(now,() -> execute(action));
                }
            }
            if (status.values().stream().allMatch(s -> "COMPLETED".equals(s) || "FAILED".equals(s) || "SKIPPED".equals(s)) && !completionSent) {
                completionSent=true;
                if (!responseSent) process(api.id(),"API_POST_PROCESSING",postDuration,
                        () -> done.finish(status.values().stream().noneMatch("FAILED"::equals)));
            }
        }
        void execute(Action action) {
            actionEvent(api.id(),action.id(),"STARTED","DEPENDENCIES_SATISFIED",parent);
            Completion finish=successful -> {
                status.put(action.id(),successful?"COMPLETED":"FAILED");
                actionEvent(api.id(),action.id(),successful?"COMPLETED":"FAILED",
                        successful?"ACTION_COMPLETED":"ACTION_FAILED",parent);
                if (!successful) derivedFailed=true;
                pump();
            };
            switch (action.kind()) {
                case "PROCESS" -> process(api.id(),"API_MESSAGE_PROCESSING",action.durationMs(),() -> finish.finish(true));
                case "CALL" -> {
                    ConnectionV2 edge=connections.get(action.connectionId());
                    long started=now; boolean[] terminal={false}; Scheduled[] timeoutEvent={null};
                    launchSynchronous(edge,parent,successful -> {
                        if (!terminal[0]) {terminal[0]=true;
                            if (timeoutEvent[0]!=null) queue.remove(timeoutEvent[0]);
                            waits.add(fields("componentId",api.id(),"exchangeId",parent==null?"request-1":parent,
                                    "startMs",started,"endMs",now,"durationMs",now-started));
                            finish.finish(successful);
                        }
                    });
                    if (action.timeoutMs()>0) timeoutEvent[0]=schedule(add(started,action.timeoutMs()),() -> {
                        if (!terminal[0]) {terminal[0]=true; finish.finish(false);}
                    });
                }
                case "PUBLISH" -> launchPublication(connections.get(action.connectionId()),parent,finish);
                case "RESPOND" -> {
                    if (!completionSent) {
                        responseSent=true; completionSent=true;
                        if ("exchange-1".equals(parent)) responseStatusCode=action.statusCode();
                        process(api.id(),"API_POST_PROCESSING",postDuration,() -> {
                            done.finish(true); finish.finish(true);
                        });
                    } else finish.finish(false);
                }
                case "OPEN_SESSION" -> openSession(connections.get(action.connectionId()),
                        action.sessionAlias()==null?action.id():action.sessionAlias(),true,parent,finish);
                case "WS_SEND" -> sendSessionMessage(api.id(),action,parent,finish);
                case "CLOSE_SESSION" -> closeSession(action.sessionAlias(),"CLOSE_ACTION",finish);
                case "FORK", "JOIN" -> finish.finish(true);
                case "SCHEDULE_TRIGGER" -> {
                    if (!(api instanceof ServiceComponentV5 scheduler)
                            || scheduler.type()!=ComponentTypeV2.SCHEDULER) {finish.finish(false);break;}
                    String scheduleId=(String)action.options().get("scheduleId");
                    ServiceComponentV5.Schedule config=scheduler.schedules().stream()
                            .filter(s -> s.id().equals(scheduleId)).findFirst().orElse(null);
                    if (config==null) {finish.finish(false);break;}
                    int invocation=++scheduleInvocationNumber;
                    for (int i=0;i<config.occurrenceCount();i++) {
                        int occurrence=i+1;
                        long planned=Math.addExact(config.firstAtMs(),
                                Math.multiplyExact(config.intervalMs(),i));
                        long at=Math.max(now,planned);
                        schedule(at,() -> {
                            String child="schedule-"+scheduleId+"-"+invocation+"-"+occurrence;
                            event(fields("type","SCHEDULED_TRIGGER","componentId",scheduler.id(),
                                    "operationId",child,"parentExchangeId",parent,
                                    "rootOperationId","request-1","scheduleId",scheduleId,
                                    "occurrenceId",child,"occurrenceIndex",occurrence,
                                    "scheduledAtMs",planned,"firedAtMs",now));
                            Behavior selected=config.behaviorId()==null
                                    ?plan.match(scheduler.id(),"SCHEDULED_TRIGGER",null,null,null)
                                    :plan.behaviors().getOrDefault(scheduler.id(),List.of()).stream()
                                        .filter(b -> b.id().equals(config.behaviorId())
                                                && b.trigger().equals("SCHEDULED_TRIGGER"))
                                        .findFirst().orElse(null);
                            if (selected!=null) executeBehavior(scheduler,selected,child,0,
                                    successful -> {if (!successful) derivedFailed=true;});
                        });
                    }
                    finish.finish(true);
                }
                case "BRANCH" -> {
                    String code=resultCodes.get(action.options().get("resultRef"));
                    if (code==null) {finish.finish(false);break;}
                    @SuppressWarnings("unchecked") List<Map<String,Object>> cases=
                            (List<Map<String,Object>>)action.options().get("cases");
                    @SuppressWarnings("unchecked") List<String> defaults=
                            (List<String>)action.options().getOrDefault("defaultActionIds",List.of());
                    List<String> selected=defaults;
                    for (Map<String,Object> item:cases) if (code.equals(item.get("equals"))) {
                        @SuppressWarnings("unchecked") List<String> ids=(List<String>)item.get("actionIds");
                        selected=ids; break;
                    }
                    Set<String> chosen=Set.copyOf(selected);
                    for (Map<String,Object> item:cases) {
                        @SuppressWarnings("unchecked") List<String> ids=(List<String>)item.get("actionIds");
                        for (String id:ids) if (!chosen.contains(id)) skip(id);
                    }
                    for (String id:defaults) if (!chosen.contains(id)) skip(id);
                    finish.finish(true);
                }
                case "CACHE_GET", "CACHE_PUT", "CACHE_INVALIDATE", "OBJECT_GET", "OBJECT_PUT",
                     "OBJECT_DELETE", "SEND_NOTIFICATION", "INDEX_DOCUMENT", "SEARCH_DOCUMENTS",
                     "REGISTER_SERVICE", "UNREGISTER_SERVICE", "DISCOVER_SERVICE", "EVALUATE_RULE"
                     -> resourceAction(action,finish);
                case "DB_SELECT", "DB_INSERT", "DB_UPDATE", "DB_DELETE", "DB_GET", "DB_PUT",
                     "DB_FIND", "DB_BULK_INSERT" -> dataAction(action,finish);
                case "ETL_EXTRACT", "ETL_TRANSFORM", "ETL_LOAD" -> etlAction(action,finish);
                case "TRANSFORM" -> {
                    Object ref=action.options().get("outputRef");
                    if (ref instanceof String name) resultCodes.put(name,"TRANSFORMED");
                    Map<String,Object> result=new ResourceOutcomeV5(action.id(),
                            ResourceOutcomeV5.Status.SUCCEEDED,ResourceOutcomeV5.Code.TRANSFORMED,
                            ref instanceof String name?name:null,now,
                            parent==null?"request-1":parent,api.id(),action.connectionId(),Map.of()).toMap();
                    resourceOutcomes.add(result);
                    event(fields("type","ACTION_EVENT","actionId",action.id(),"componentId",api.id(),
                            "operationId",parent==null?"request-1":parent,"status","COMPLETED",
                            "cause","VIRTUAL_TRANSFORM","outcome",result));
                    finish.finish(true);
                }
                default -> finish.finish(false);
            }
        }
        void skip(String id) {
            if ("PENDING".equals(status.get(id))) {
                status.put(id,"SKIPPED");
                actionEvent(api.id(),id,"SKIPPED","BRANCH_NOT_SELECTED",parent);
            }
        }
        void resourceAction(Action action,Completion finish) {
            ConnectionV2 edge=connections.get(action.connectionId());
            ServiceComponentV5 resource=(ServiceComponentV5)components.get(edge.targetId());
            String operationId="exchange-"+(++exchangeNumber);
            send(edge,edge.sourceId(),edge.targetId(),"REQUEST",action.kind(),operationId,parent,() -> {
                long delay=action.durationMs();
                boolean timedOut=action.timeoutMs()>0 && delay>action.timeoutMs();
                if (timedOut) delay=action.timeoutMs();
                final boolean deadline=timedOut;
                process(resource.id(),"VIRTUAL_RESOURCE_PROCESSING",delay,() -> {
                VirtualResourcesV5.Outcome outcome=deadline
                        ?new VirtualResourcesV5.Outcome("TIMED_OUT",Map.of())
                        :virtualResources.execute(resource,action,now,resultCodes);
                boolean successful=!Set.of("FAILED","TIMED_OUT").contains(outcome.resultCode());
                ResourceOutcomeV5.Status outcomeStatus=successful?ResourceOutcomeV5.Status.SUCCEEDED:
                        ResourceOutcomeV5.Status.valueOf(outcome.resultCode());
                Object ref=action.options().get("outputRef");
                if (ref instanceof String name) {
                    resultCodes.put(name,outcome.resultCode());
                }
                Map<String,Object> result=new ResourceOutcomeV5(action.id(),outcomeStatus,
                        ResourceOutcomeV5.Code.valueOf(outcome.resultCode()),
                        ref instanceof String name?name:null,now,operationId,
                        resource.id(),edge.id(),outcome.details()).toMap();
                resourceOutcomes.add(result);
                event(fields("type","ACTION_EVENT","actionId",action.id(),"componentId",resource.id(),
                        "operationId",operationId,"parentExchangeId",parent,
                        "status","COMPLETED","cause","RESOURCE_OPERATION",
                        "outcome",result));
                if (deadline) finish.finish(false);
                else send(edge,edge.targetId(),edge.sourceId(),"RESPONSE",action.kind(),operationId,parent,
                        () -> finish.finish(successful));
                });
            });
        }
        void dataAction(Action action,Completion finish) {
            ConnectionV2 edge=connections.get(action.connectionId());
            DataResourceV6 resource=(DataResourceV6)components.get(edge.targetId());
            String operationId="exchange-"+(++exchangeNumber);
            send(edge,edge.sourceId(),edge.targetId(),"REQUEST",action.kind(),operationId,parent,() -> {
                long arrived=now;
                int capacity=Math.min(resource.connectionPoolSize(),resource.maxConcurrentOperations());
                Runnable start=() -> {
                    activeData.merge(resource.id(),1,Integer::sum);
                    long started=now, wait=started-arrived;
                    long count=action.options().get("recordCount") instanceof Number n?n.longValue():1;
                    boolean read=Set.of("DB_GET","DB_SELECT","DB_FIND").contains(action.kind());
                    long duration=Math.multiplyExact(read?resource.readLatencyMs():resource.writeLatencyMs(),count);
                    boolean deadline=action.timeoutMs()>0 && duration>action.timeoutMs()-wait;
                    long processing=deadline?Math.max(0,action.timeoutMs()-wait):duration;
                    process(resource.id(),"DATA_OPERATION",processing,() -> {
                        VirtualDataResourcesV6.Outcome outcome=deadline
                                ?new VirtualDataResourcesV6.Outcome("TIMED_OUT",0,0,0,0,
                                        Map.of("errorCode","DATA_TIMEOUT"))
                                :virtualData.execute(resource,action);
                        boolean successful=!Set.of("FAILED","LIMIT_EXCEEDED","TIMED_OUT").contains(outcome.code());
                        Map<String,Object> result=fields("operationId",operationId,"actionId",action.id(),
                                "componentId",api.id(),"resourceComponentId",resource.id(),
                                "connectionId",edge.id(),"timestampMs",now,"startedAtMs",started,
                                "completedAtMs",now,"queueWaitMs",wait,"processingMs",processing,
                                "status",deadline?"TIMED_OUT":successful?"SUCCEEDED":"FAILED","resultCode",outcome.code(),
                                "readCount",outcome.readCount(),"writtenCount",outcome.writtenCount(),
                                "rejectedCount",outcome.rejectedCount(),"logicalBytes",outcome.logicalBytes());
                        if (parent!=null) result.put("parentOperationId",parent);
                        if (action.options().get("outputRef") instanceof String ref) {
                            result.put("outputRef",ref); resultCodes.put(ref,outcome.code());
                        }
                        result.putAll(outcome.details());
                        dataOutcomes.add(result);
                        event(fields("type","ACTION_EVENT","actionId",action.id(),"componentId",resource.id(),
                                "operationId",operationId,"parentExchangeId",parent,"status","COMPLETED",
                                "cause","DATA_OPERATION","outcome",result));
                        activeData.merge(resource.id(),-1,Integer::sum);
                        Deque<Runnable> waiting=waitingData.get(resource.id());
                        if (waiting!=null && !waiting.isEmpty()) waiting.removeFirst().run();
                        send(edge,edge.targetId(),edge.sourceId(),"RESPONSE",action.kind(),operationId,parent,
                                () -> finish.finish(successful));
                    });
                };
                if (activeData.getOrDefault(resource.id(),0)<capacity) start.run();
                else {
                    Deque<Runnable> waiting=waitingData.computeIfAbsent(resource.id(),k -> new ArrayDeque<>());
                    if (waiting.size()>=resource.queueCapacity()) {
                        dataOutcomes.add(fields("operationId",operationId,"actionId",action.id(),
                                "componentId",api.id(),"resourceComponentId",resource.id(),
                                "connectionId",edge.id(),"timestampMs",now,"status","REJECTED",
                                "resultCode","LIMIT_EXCEEDED","readCount",0,"writtenCount",0,
                                "rejectedCount",1,"logicalBytes",0,"errorCode","DATA_QUEUE_FULL"));
                        finish.finish(false);
                    } else {
                        boolean[] terminal={false};
                        Scheduled[] timeout={null};
                        Runnable queued=() -> {
                            if (terminal[0]) return;
                            terminal[0]=true;
                            if (timeout[0]!=null) queue.remove(timeout[0]);
                            start.run();
                        };
                        waiting.addLast(queued);
                        if (action.timeoutMs()>0) timeout[0]=schedule(add(arrived,action.timeoutMs()),() -> {
                            if (terminal[0]) return;
                            terminal[0]=true; waiting.remove(queued);
                            dataOutcomes.add(fields("operationId",operationId,"actionId",action.id(),
                                    "componentId",api.id(),"resourceComponentId",resource.id(),
                                    "connectionId",edge.id(),"timestampMs",now,"queueWaitMs",now-arrived,
                                    "status","TIMED_OUT","resultCode","TIMED_OUT","readCount",0,
                                    "writtenCount",0,"rejectedCount",0,"logicalBytes",0,
                                    "errorCode","DATA_TIMEOUT"));
                            finish.finish(false);
                        });
                    }
                }
            });
        }
        void etlAction(Action action,Completion finish) {
            String kind=action.kind();
            String ref=(String)action.options().get("outputRef");
            String input=(String)action.options().get("inputRef");
            if ("ETL_TRANSFORM".equals(kind)) {
                Batch batch=batchRefs.get(input);
                if (batch==null) {finish.finish(false);return;}
                long count=batch.count();
                ConnectionV2 transformEdge=action.connectionId()==null?null:connections.get(action.connectionId());
                ComponentV2 transformTarget=transformEdge==null?api:components.get(transformEdge.targetId());
                long cost=transformTarget instanceof DataProcessorV6 processor?processor.transformCostPerRecordMs():0;
                long duration=Math.multiplyExact(cost,count);
                String transform=String.valueOf(action.options().get("transform"));
                long invalid="FILTER_VALID".equals(transform)
                        ?((Number)action.options().get("invalidRecordCount")).longValue():0;
                boolean failed=Boolean.TRUE.equals(action.options().get("simulateFailure")) || invalid>count;
                long outputCount=failed?0:count-invalid;
                boolean normalized=batch.normalized() || "NORMALIZE_METADATA".equals(transform);
                String operationId=transformEdge==null?(parent==null?"request-1":parent):"exchange-"+(++exchangeNumber);
                Runnable execute=() -> {
                long started=now;
                process(transformTarget.id(),"ETL_TRANSFORM",duration,() -> {
                    if (!failed && ref!=null) batchRefs.put(ref,new Batch(outputCount,normalized));
                    Map<String,Object> result=fields("operationId",operationId,
                            "actionId",action.id(),"componentId",api.id(),"resourceComponentId",transformTarget.id(),
                            "connectionId",transformEdge==null?"local":transformEdge.id(),"timestampMs",now,"startedAtMs",started,
                            "completedAtMs",now,"queueWaitMs",0,"processingMs",duration,
                            "status",failed?"FAILED":"SUCCEEDED","resultCode",failed?"FAILED":"TRANSFORMED","readCount",count,
                            "writtenCount",outputCount,"rejectedCount",failed?count:invalid,"logicalBytes",0,
                            "transform",transform,"normalizedCount",failed?0:normalized?outputCount:0);
                    if (failed) result.put("errorCode",invalid>count?"ETL_INVALID_REJECTION_COUNT":"ETL_TRANSFORM_FAILED");
                    if (ref!=null) result.put("outputRef",ref);
                    dataOutcomes.add(result);
                    event(fields("type","ACTION_EVENT","actionId",action.id(),"componentId",transformTarget.id(),
                            "operationId",operationId,"status","COMPLETED",
                            "cause","ETL_TRANSFORM","outcome",result));
                    if (transformEdge==null) finish.finish(!failed);
                    else send(transformEdge,transformEdge.targetId(),transformEdge.sourceId(),"RESPONSE",
                            kind,operationId,parent,() -> finish.finish(!failed));
                });
                };
                if (transformEdge==null) execute.run();
                else send(transformEdge,transformEdge.sourceId(),transformEdge.targetId(),"REQUEST",kind,
                        operationId,parent,execute);
                return;
            }
            ConnectionV2 edge=connections.get(action.connectionId());
            DataResourceV6 resource=(DataResourceV6)components.get(edge.targetId());
            if ("ETL_LOAD".equals(kind) && !batchRefs.containsKey(input)) {
                finish.finish(false);return;
            }
            String operationId="exchange-"+(++exchangeNumber);
            send(edge,edge.sourceId(),edge.targetId(),"REQUEST",kind,operationId,parent,() -> {
                long started=now;
                Batch sourceBatch="ETL_LOAD".equals(kind)?batchRefs.get(input):null;
                long count="ETL_EXTRACT".equals(kind)?Math.min(virtualData.count(resource),
                        action.options().get("maxRecords") instanceof Number n?n.longValue():1_000_000):sourceBatch.count();
                if (count>1_000_000) throw new IllegalArgumentException("ETL record budget exceeded");
                long duration=Math.multiplyExact("ETL_EXTRACT".equals(kind)
                        ?resource.readLatencyMs():resource.writeLatencyMs(),count);
                process(resource.id(),kind,duration,() -> {
                    VirtualDataResourcesV6.Outcome outcome="ETL_LOAD".equals(kind)
                            ?virtualData.load(resource,count)
                            :new VirtualDataResourcesV6.Outcome("EXTRACTED",count,0,0,
                                    count*resource.averageRecordBytes(),Map.of());
                    boolean successful=!"LIMIT_EXCEEDED".equals(outcome.code());
                    if (successful && ref!=null) batchRefs.put(ref,new Batch(count,
                            sourceBatch!=null && sourceBatch.normalized()));
                    Map<String,Object> result=fields("operationId",operationId,"actionId",action.id(),
                            "componentId",api.id(),"resourceComponentId",resource.id(),
                            "connectionId",edge.id(),"timestampMs",now,"startedAtMs",started,
                            "completedAtMs",now,"queueWaitMs",0,"processingMs",duration,
                            "status",successful?"SUCCEEDED":"FAILED","resultCode",outcome.code(),
                            "readCount",outcome.readCount(),"writtenCount",outcome.writtenCount(),
                            "rejectedCount",outcome.rejectedCount(),"logicalBytes",outcome.logicalBytes());
                    if (sourceBatch!=null && sourceBatch.normalized()) result.put("normalizedCount",count);
                    if (ref!=null) result.put("outputRef",ref);
                    dataOutcomes.add(result);
                    event(fields("type","ACTION_EVENT","actionId",action.id(),"componentId",resource.id(),
                            "operationId",operationId,"status","COMPLETED","cause",kind,"outcome",result));
                    send(edge,edge.targetId(),edge.sourceId(),"RESPONSE",kind,operationId,parent,
                            () -> finish.finish(successful));
                });
            });
        }
    }

    private void actionEvent(String componentId,String actionId,String status,String cause,String parent) {
        event(fields("type","ACTION_EVENT","actionId",actionId,"componentId",componentId,
                "operationId",parent==null?"request-1":parent,"status",status,"cause",cause));
    }

    private void openSession(ConnectionV2 edge,String alias,boolean autoClose,String parent,Completion done) {
        if (edge==null || !(edge.integration() instanceof WebSocketIntegrationV2 ws) || sessions.containsKey(alias)) {
            done.finish(false); return;
        }
        String id="session-"+nextResource();
        Session session=new Session(id,alias,edge,autoClose,"CONNECTING");
        sessions.put(alias,session);
        resources.put(id,new Resource(id,"WEBSOCKET_SESSION","ACTIVE"));
        lifecycle.open(id,br.com.archlab.simulation.core.v4.ExecutionLifecycleV4.ResourceKind.WEBSOCKET_SESSION);
        state("WEBSOCKET_SESSION",id,null,"CONNECTING","OPEN_SESSION_ACTION");
        long started=now, timeout=ws.connectTimeoutMs();
        long elapsed=Math.min(edge.latencyMs(),timeout);
        String messageId="message-"+(++messageNumber);
        message(now,"SENT",messageId,id,parent,edge,edge.sourceId(),edge.targetId(),"REQUEST","WEBSOCKET_OPEN");
        communication(messageId,id,edge,edge.sourceId(),edge.targetId(),started,add(started,elapsed));
        schedule(add(started,elapsed),() -> {
            if (edge.latencyMs()>timeout) {
                state("WEBSOCKET_SESSION",id,"CONNECTING","TIMED_OUT","CONNECT_TIMEOUT");
                sessions.put(alias,new Session(id,alias,edge,autoClose,"TIMED_OUT"));
                finishResource(id,"TIMED_OUT"); done.finish(false); return;
            }
            message(now,"ARRIVED",messageId,id,parent,edge,edge.sourceId(),edge.targetId(),"REQUEST","WEBSOCKET_OPEN");
            state("WEBSOCKET_SESSION",id,"CONNECTING","OPEN","CONNECT_ACCEPTED");
            sessions.put(alias,new Session(id,alias,edge,autoClose,"OPEN"));
            if (!autoClose && ws.idleTimeoutMs()!=null) sessionTimeouts.put(alias,schedule(add(now,ws.idleTimeoutMs()),() -> {
                if ("OPEN".equals(sessions.get(alias).status())) {
                    state("WEBSOCKET_SESSION",id,"OPEN","TIMED_OUT","IDLE_TIMEOUT");
                    sessions.put(alias,new Session(id,alias,edge,autoClose,"TIMED_OUT"));
                    finishResource(id,"TIMED_OUT"); derivedFailed=true;
                }
            }));
            ComponentV2 target=components.get(edge.targetId());
            if (target!=null && target.type().isProcessor() &&
                    plan.match(target.id(),"SESSION_OPENED",null,null,ws.path())!=null)
                activateApi(target,edge,id,"SESSION_OPENED",null,null,ws.path(),ok -> {if (!ok) derivedFailed=true;});
            done.finish(true);
        });
    }

    private void sendSessionMessage(String actor,Action action,String parent,Completion done) {
        Session session=sessions.get(action.sessionAlias());
        if (session==null || !"OPEN".equals(session.status())) {
            actionEvent(actor,action.id(),"FAILED","SESSION_NOT_OPEN",parent);
            done.finish(false); return;
        }
        ConnectionV2 edge=session.edge();
        String source=actor, target;
        if (actor.equals(edge.sourceId())) target=edge.targetId();
        else if (actor.equals(edge.targetId())) target=edge.sourceId();
        else {done.finish(false);return;}
        String sender=source.equals(edge.sourceId())?"SOURCE":"TARGET";
        if (action.sender()!=null && !action.sender().equals(sender)) {done.finish(false);return;}
        send(edge,source,target,"MESSAGE","WEBSOCKET_MESSAGE",session.id(),parent,() -> {
            websocketCounts.computeIfAbsent(session.id(), ignored -> new long[2])[source.equals(edge.sourceId()) ? 0 : 1]++;
            ComponentV2 peer=components.get(target);
            if (peer!=null && peer.type().isProcessor() && edge.integration() instanceof WebSocketIntegrationV2 ws
                    && plan.match(peer.id(),"WS_MESSAGE_RECEIVED",null,null,ws.path())!=null)
                activateApi(peer,edge,session.id(),"WS_MESSAGE_RECEIVED",null,null,ws.path(),ok -> {if (!ok) derivedFailed=true;});
            done.finish(true);
        });
    }

    private void closeSession(String alias,String cause,Completion done) {
        Session session=sessions.get(alias);
        if (session==null || !"OPEN".equals(session.status())) {done.finish(false);return;}
        state("WEBSOCKET_SESSION",session.id(),"OPEN","CLOSING",cause);
        state("WEBSOCKET_SESSION",session.id(),"CLOSING","CLOSED","CLOSE_COMPLETED");
        Scheduled timeout=sessionTimeouts.remove(alias);
        if (timeout!=null) queue.remove(timeout);
        sessions.put(alias,new Session(session.id(),alias,session.edge(),session.autoClose(),"CLOSED"));
        finishResource(session.id(),"COMPLETED");
        done.finish(true);
    }

    private void closeAutomaticSessions() {
        for (Session session : List.copyOf(sessions.values()))
            if (session.autoClose() && "OPEN".equals(session.status()))
                closeSession(session.alias(),"AUTO_CLOSE_AT_END",ignored -> {});
    }

    private void launchPublication(ConnectionV2 entry, String parentExchangeId, Completion acceptedCallback) {
        ComponentV2 target = components.get(entry.targetId());
        if (!(target instanceof BrokerV2 broker)) throw new IllegalArgumentException("Messaging target must be Broker");
        String destination = destination(entry.integration());
        String messageId = "broker-message-"+nextResource();
        resources.put(messageId,new Resource(messageId,"BROKER_MESSAGE","ACTIVE"));
        lifecycle.open(messageId,br.com.archlab.simulation.core.v4.ExecutionLifecycleV4.ResourceKind.BROKER_MESSAGE);
        state("BROKER_MESSAGE",messageId,null,"CREATED","MESSAGE_CREATED");
        long[] metric = messagingMetrics.computeIfAbsent(entry.id(),ignored -> new long[6]);
        metric[0]++;
        send(entry,entry.sourceId(),entry.targetId(),"MESSAGE","BROKER_PUBLISH",messageId,parentExchangeId,() -> {
            state("BROKER_MESSAGE",messageId,"CREATED","ACCEPTED","BROKER_ACCEPTED");
            acceptedCallback.finish(true);
            List<BrokerSubscriptionV2> eligible = BrokerRoutingV4.eligible(broker,destination);
            metric[5] = Math.max(metric[5],eligible.size());
            if (entry.integration().kind() == IntegrationKindV2.QUEUE && eligible.isEmpty()) {
                state("BROKER_MESSAGE",messageId,"ACCEPTED","QUEUED","NO_ELIGIBLE_CONSUMER");
                schedule(add(now,30_000),() -> {
                    state("BROKER_MESSAGE",messageId,"QUEUED","TIMED_OUT","QUEUE_WAIT_TIMEOUT");
                    finishResource(messageId,"TIMED_OUT"); metric[4]++;
                });
                return;
            }
            state("BROKER_MESSAGE",messageId,"ACCEPTED","COMPLETED","PUBLICATION_ACCEPTED");
            finishResource(messageId,"COMPLETED");
            List<BrokerSubscriptionV2> recipients;
            if (entry.integration().kind() == IntegrationKindV2.QUEUE) {
                String cursorKey = broker.id()+"\u0000"+destination;
                int index = queueCursors.getOrDefault(cursorKey,0);
                recipients = BrokerRoutingV4.recipients(eligible,entry.integration().kind(),index);
                queueCursors.put(cursorKey,index+1);
            } else recipients = eligible;
            for (BrokerSubscriptionV2 subscription : recipients) {
                if (metric[1] >= 1000) throw new IllegalArgumentException("Delivery limit exceeded");
                ConnectionV2 delivery = connections.get(subscription.connectionId());
                ComponentV2 consumer = components.get(subscription.consumerComponentId());
                String deliveryId = "delivery-"+nextResource();
                resources.put(deliveryId,new Resource(deliveryId,"DELIVERY","ACTIVE"));
                lifecycle.open(deliveryId,br.com.archlab.simulation.core.v4.ExecutionLifecycleV4.ResourceKind.DELIVERY);
                state("DELIVERY",deliveryId,null,"CREATED","DELIVERY_CREATED");
                metric[1]++; metric[3]++;
                send(delivery,broker.id(),consumer.id(),"MESSAGE","BROKER_DELIVERY",deliveryId,messageId,() -> {
                    state("DELIVERY",deliveryId,"CREATED","PROCESSING","DELIVERY_ARRIVED");
                    activateApi(consumer,delivery,deliveryId,"MESSAGE_CONSUMED",null,destination,null,successful -> {
                        state("DELIVERY",deliveryId,"PROCESSING",successful ? "COMPLETED" : "FAILED","CONSUMER_PROCESSED");
                        finishResource(deliveryId,successful ? "COMPLETED" : "FAILED");
                        metric[3]--;
                        if (successful) metric[2]++; else metric[4]++;
                    });
                });
            }
        });
    }

    private void launchWebSocket(ConnectionV2 edge, String parentExchangeId, Completion done) {
        WebSocketIntegrationV2 integration = (WebSocketIntegrationV2)edge.integration();
        WebSocketRuntimeV4.Result run = new WebSocketRuntimeV4().run(integration,edge.latencyMs());
        long started = now;
        String sessionId = "session-"+nextResource();
        sessions.put(edge.id(),new Session(sessionId,edge.id(),edge,false,"CONNECTING"));
        resources.put(sessionId,new Resource(sessionId,"WEBSOCKET_SESSION","ACTIVE"));
        lifecycle.open(sessionId,br.com.archlab.simulation.core.v4.ExecutionLifecycleV4.ResourceKind.WEBSOCKET_SESSION);
        state("WEBSOCKET_SESSION",sessionId,null,"CONNECTING","CONNECT_STARTED");
        String openingMessage = "message-"+(++messageNumber);
        message(now,"SENT",openingMessage,sessionId,parentExchangeId,edge,
                edge.sourceId(),edge.targetId(),"REQUEST","WEBSOCKET_OPEN");
        long openingEnd = add(started,run.openedAtMs() < 0 ? run.totalDurationMs() : run.openedAtMs());
        communication(openingMessage,sessionId,edge,edge.sourceId(),edge.targetId(),started,openingEnd);
        if (run.openedAtMs() >= 0) schedule(add(started,run.openedAtMs()),() -> {
            message(now,"ARRIVED",openingMessage,sessionId,parentExchangeId,edge,
                    edge.sourceId(),edge.targetId(),"REQUEST","WEBSOCKET_OPEN");
            state("WEBSOCKET_SESSION",sessionId,"CONNECTING","OPEN","CONNECT_ACCEPTED");
            sessions.put(edge.id(),new Session(sessionId,edge.id(),edge,false,"OPEN"));
        });
        for (WebSocketRuntimeV4.MessageSpan span : run.messages()) {
            String source = "TARGET".equals(span.message().sender()) ? edge.targetId() : edge.sourceId();
            String target = source.equals(edge.sourceId()) ? edge.targetId() : edge.sourceId();
            String id = "message-"+(++messageNumber);
            long sent = add(started,span.sentAtMs()), arrived = add(started,span.arrivedAtMs());
            communication(id,sessionId,edge,source,target,sent,arrived);
            schedule(sent,() -> message(now,"SENT",id,sessionId,parentExchangeId,edge,source,target,"MESSAGE","WEBSOCKET_MESSAGE"));
            schedule(arrived,() -> {
                message(now,"ARRIVED",id,sessionId,parentExchangeId,edge,source,target,"MESSAGE","WEBSOCKET_MESSAGE");
                ComponentV2 api=components.get(target);
                if (api!=null && api.type().isProcessor() &&
                        plan.match(api.id(),"WS_MESSAGE_RECEIVED",null,null,integration.path())!=null) {
                    activeSessionBehaviors.merge(sessionId,1,Integer::sum);
                    activateApi(api,edge,sessionId,"WS_MESSAGE_RECEIVED",null,null,integration.path(),
                            successful -> {
                                if (!successful) derivedFailed=true;
                                if (activeSessionBehaviors.merge(sessionId,-1,Integer::sum)==0) {
                                    Runnable pending=pendingSessionClose.remove(sessionId);
                                    if (pending!=null) pending.run();
                                }
                            });
                }
            });
        }
        schedule(add(started,run.totalDurationMs()),() -> {
            Runnable close=() -> {
            boolean success = run.mainStatus() == br.com.archlab.simulation.core.v4.ExecutionLifecycleV4.MainStatus.COMPLETED;
            String previous = run.openedAtMs() < 0 ? "CONNECTING" : "OPEN";
            if (success) {
                state("WEBSOCKET_SESSION",sessionId,previous,"CLOSING","CLOSE_STARTED");
                state("WEBSOCKET_SESSION",sessionId,"CLOSING","CLOSED","CLOSE_COMPLETED");
            } else state("WEBSOCKET_SESSION",sessionId,previous,"TIMED_OUT","SESSION_TIMEOUT");
            finishResource(sessionId,success ? "COMPLETED" : "TIMED_OUT");
            sessions.put(edge.id(),new Session(sessionId,edge.id(),edge,false,success?"CLOSED":"TIMED_OUT"));
            long sentCount = run.messages().stream().filter(span -> "SOURCE".equals(span.message().sender())).count();
            long[] additional = websocketCounts.getOrDefault(sessionId,new long[2]);
            websocketMetrics.add(fields("connectionId",edge.id(),"sessionId",sessionId,"messagesSent",sentCount+additional[0],
                    "messagesReceived",run.messages().size()-sentCount+additional[1],"durationMs",now-started));
            done.finish(success);
            };
            if (activeSessionBehaviors.getOrDefault(sessionId,0)>0) pendingSessionClose.put(sessionId,close);
            else close.run();
        });
    }

    private void process(String componentId, String phase, long duration, Runnable done) {
        long start = now, end = add(start,duration);
        if (activeProcessing.merge(componentId,1,Integer::sum) == 1)
            componentState(componentId,"PROCESSING",phase+"_STARTED");
        event(fields("type","PROCESSING_EVENT","action","STARTED","componentId",componentId,
                "requestId","request-1","phase",phase));
        timeline.add(fields("id","activity-"+(++activityNumber),"kind","PROCESSING","componentId",componentId,
                "requestId","request-1","phase",phase,"startMs",start,"endMs",end,"durationMs",duration));
        schedule(end,() -> {
            event(fields("type","PROCESSING_EVENT","action","COMPLETED","componentId",componentId,
                    "requestId","request-1","phase",phase));
            if (activeProcessing.merge(componentId,-1,Integer::sum) == 0)
                componentState(componentId,"COMPLETED",phase+"_COMPLETED");
            done.run();
        });
    }

    private void componentState(String id,String next,String cause) {
        String previous = componentStatus.getOrDefault(id,"IDLE");
        if (!previous.equals(next)) {
            state("COMPONENT",id,previous,next,cause);
            componentStatus.put(id,next);
        }
    }

    private void send(ConnectionV2 edge, String source, String target, String direction, String intent,
                      String exchangeId, String parentExchangeId, Runnable arrivedCallback) {
        usedConnections.putIfAbsent(edge.id(),edge);
        String messageId = "message-"+(++messageNumber);
        long start = now, end = add(start,edge.latencyMs());
        message(start,"SENT",messageId,exchangeId,parentExchangeId,edge,source,target,direction,intent);
        communication(messageId,exchangeId,edge,source,target,start,end);
        schedule(end,() -> {
            message(now,"ARRIVED",messageId,exchangeId,parentExchangeId,edge,source,target,direction,intent);
            arrivedCallback.run();
        });
    }

    private void message(long at, String action, String messageId, String exchangeId, String parent,
                         ConnectionV2 edge, String source, String target, String direction, String intent) {
        event(fields("type","MESSAGE_EVENT","action",action,"messageId",messageId,"requestId","request-1",
                "exchangeId",exchangeId,"parentExchangeId",parent,"connectionId",edge.id(),
                "sourceId",source,"targetId",target,"direction",direction,"intent",intent));
    }

    private void communication(String messageId,String exchangeId,ConnectionV2 edge,String source,String target,long start,long end) {
        usedConnections.putIfAbsent(edge.id(),edge);
        timeline.add(fields("id","activity-"+(++activityNumber),"kind","COMMUNICATION","messageId",messageId,
                "exchangeId",exchangeId,"connectionId",edge.id(),"sourceId",source,"targetId",target,
                "startMs",start,"endMs",end,"durationMs",end-start));
    }

    private void state(String kind,String id,String previous,String next,String cause) {
        event(fields("type","STATE_TRANSITION","entityKind",kind,"entityId",id,"previousState",previous,
                "newState",next,"cause",cause));
    }

    private void event(Map<String,Object> data) {
        if (trace.size() >= MAX_EVENTS) throw new IllegalArgumentException("Trace event limit exceeded");
        lifecycle.eventAt(now);
        Map<String,Object> row = fields("sequence",trace.size()+1,"timestampMs",now);
        String operationId=(String)data.getOrDefault("operationId",data.getOrDefault("exchangeId","request-1"));
        Object parent=data.get("parentExchangeId");
        Map<String,Object> correlation=fields("operationId",operationId,"parentOperationId",parent);
        if (data.get("connectionId") instanceof String connectionId && connections.get(connectionId)!=null)
            correlation.put("profile",connections.get(connectionId).integration().kind().name());
        if (operationId.startsWith("session-")) correlation.put("sessionId",operationId);
        row.put("correlation",correlation);
        row.putAll(data); trace.add(row);
    }

    private Scheduled schedule(long at,Runnable action) {
        if (at < now || at > MAX_TIME || ++scheduledCount > MAX_EVENTS)
            throw new IllegalArgumentException("Virtual duration or event limit exceeded");
        Scheduled scheduled=new Scheduled(at,++scheduleOrder,action);
        queue.add(scheduled);
        return scheduled;
    }

    private static long add(long a,long b) {
        long value = Math.addExact(a,b);
        if (value > MAX_TIME) throw new IllegalArgumentException("Virtual duration limit exceeded");
        return value;
    }

    private int nextResource() {
        if (resourceNumber>=1000) throw new IllegalArgumentException("Resource limit exceeded");
        return ++resourceNumber;
    }

    private void finishResource(String id,String status) {
        Resource previous = resources.get(id);
        if (previous == null || !"ACTIVE".equals(previous.status())) throw new IllegalStateException("Invalid resource transition");
        resources.put(id,new Resource(id,previous.kind(),status));
        lifecycle.finish(id,br.com.archlab.simulation.core.v4.ExecutionLifecycleV4.ResourceStatus.valueOf(status));
    }

    private Map<String,Object> response(String globalStatus,List<ConnectionV2> roots) {
        timeline.sort(Comparator.comparingLong((Map<String,Object> row) -> (long)row.get("startMs"))
                .thenComparing(row -> (String)row.get("id")));
        Map<String,String> initialComponents = new LinkedHashMap<>();
        components.keySet().forEach(id -> initialComponents.put(id,"IDLE"));
        Map<String,long[]> processing = new LinkedHashMap<>(), communication = new LinkedHashMap<>();
        for (Map<String,Object> activity : timeline) {
            if ("PROCESSING".equals(activity.get("kind")))
                processing.computeIfAbsent((String)activity.get("componentId"),ignored -> new long[1])[0] += (long)activity.get("durationMs");
            else {
                long[] count = communication.computeIfAbsent((String)activity.get("connectionId"),ignored -> new long[2]);
                count[0]++; count[1] += (long)activity.get("durationMs");
            }
        }
        List<Map<String,Object>> processingMetrics = processing.entrySet().stream()
                .map(e -> fields("componentId",e.getKey(),"durationMs",e.getValue()[0])).toList();
        List<Map<String,Object>> communicationMetrics = communication.entrySet().stream()
                .map(e -> fields("connectionId",e.getKey(),"traversals",e.getValue()[0],"durationMs",e.getValue()[1])).toList();
        List<Map<String,Object>> messaging = messagingMetrics.entrySet().stream().map(e -> {
            long[] m = e.getValue();
            return fields("connectionId",e.getKey(),"published",m[0],"delivered",m[1],"processed",m[2],
                    "pending",m[3],"failures",m[4],"eligibleSubscribers",m[5]);
        }).toList();
        List<Map<String,Object>> representations = usedConnections.values().stream()
                .map(edge -> fields("connectionId",edge.id(),"integration",integration(edge.integration()))).toList();
        List<Map<String,Object>> resourceStates = resources.values().stream()
                .map(r -> fields("id",r.id(),"kind",r.kind(),"status",r.status())).toList();
        String firstConnection = roots.get(0).id();
        Map<String,Object> execution = fields("id","exec-001","status",globalStatus,
                "mainStatus",mainTimedOut ? "TIMED_OUT" : "COMPLETED","requestId","request-1",
                "entryComponentId",startId,"entryConnectionId",firstConnection,
                "entryConnectionIds",roots.stream().map(ConnectionV2::id).toList(),
                "mode","UNIFIED","totalDurationMs",now);
        if (responseStatusCode!=null) execution.put("responseStatusCode",responseStatusCode);
        return fields("execution",execution,"initialState",fields("components",initialComponents,
                        "requests",Map.of("request-1","CREATED"),"exchanges",Map.of()),
                "trace",trace,"timeline",timeline,"metrics",fields("totalDurationMs",now,
                        "processingByComponent",processingMetrics,"communicationByConnection",communicationMetrics,
                        "waitingByComponent",waits,"exchanges",exchanges,"websocket",websocketMetrics,"messaging",messaging),
                "messageRepresentations",representations,"resourceStates",resourceStates,
                "resourceOutcomes",resourceOutcomes,"dataOutcomes",dataOutcomes,"rootOperations",rootOperations);
    }

    private static String destination(IntegrationV2 integration) {
        if (integration instanceof QueueIntegrationV2 queue) return queue.queueName();
        if (integration instanceof TopicIntegrationV2 topic) return topic.topic();
        if (integration instanceof MqttIntegrationV2 mqtt) return mqtt.topic();
        throw new IllegalArgumentException("Not a messaging integration");
    }

    private static Map<String,Object> integration(IntegrationV2 value) {
        Map<String,Object> result = fields("kind",value.kind().name());
        if (value instanceof HttpRestIntegrationV2 rest) {
            if (rest.request() != null) {
                Map<String,Object> request = fields("method",rest.request().method().name(),"path",rest.request().path());
                if (rest.request().headers() != null) request.put("headers",rest.request().headers());
                if (rest.request().body() != null) request.put("body",rest.request().body());
                result.put("request",request);
            }
            if (rest.response() != null) {
                Map<String,Object> response = fields("statusCode",rest.response().statusCode());
                if (rest.response().headers() != null) response.put("headers",rest.response().headers());
                if (rest.response().body() != null) response.put("body",rest.response().body());
                result.put("response",response);
            }
        } else if (value instanceof SoapHttpIntegrationV2 soap) {
            result.put("soapVersion",soap.soapVersion());
            if (soap.operation()!=null) result.put("operation",soap.operation());
            if (soap.action()!=null) result.put("action",soap.action());
            if (soap.requestEnvelope()!=null) result.put("requestEnvelope",soap.requestEnvelope());
            if (soap.responseEnvelope()!=null) result.put("responseEnvelope",soap.responseEnvelope());
            if (soap.httpStatusCode()!=null) result.put("httpStatusCode",soap.httpStatusCode());
            if (soap.requestHeaders()!=null) result.put("requestHeaders",soap.requestHeaders());
            if (soap.responseHeaders()!=null) result.put("responseHeaders",soap.responseHeaders());
        } else if (value instanceof DatabaseQueryIntegrationV2 query) {
            if (query.queryText()!=null) result.put("queryText",query.queryText());
            if (query.responseText()!=null) result.put("responseText",query.responseText());
        } else if (value instanceof HttpGenericIntegrationV2 http) {
            result.put("method",http.method()); result.put("path",http.path());
        } else if (value instanceof GraphqlHttpIntegrationV2 graphql) {
            result.put("operationType",graphql.operationType()); result.put("operationName",graphql.operationName());
        } else if (value instanceof GrpcUnaryIntegrationV2 grpc) {
            result.put("service",grpc.service()); result.put("method",grpc.method());
        } else if (value instanceof CoapIntegrationV2 coap) {
            result.put("method",coap.method()); result.put("resource",coap.resource()); result.put("mode",coap.mode());
        } else if (value instanceof WebSocketIntegrationV2 ws) {
            result.put("path",ws.path()); result.put("connectTimeoutMs",ws.connectTimeoutMs());
            result.put("idleTimeoutMs",ws.idleTimeoutMs()); result.put("closeMode",ws.closeMode());
            result.put("messages",ws.messages().stream().map(message -> {
                Map<String,Object> row = fields("sender",message.sender(),"intervalMs",message.intervalMs());
                if (message.payload()!=null) row.put("payload",message.payload());
                return row;
            }).toList());
        } else if (value instanceof QueueIntegrationV2 queue) result.put("queueName",queue.queueName());
        else if (value instanceof TopicIntegrationV2 topic) result.put("topic",topic.topic());
        else if (value instanceof MqttIntegrationV2 mqtt) {
            result.put("topic",mqtt.topic()); result.put("qos",mqtt.qos());
        }
        return result;
    }

    private static Map<String,Object> fields(Object... pairs) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]);
        return result;
    }
}



