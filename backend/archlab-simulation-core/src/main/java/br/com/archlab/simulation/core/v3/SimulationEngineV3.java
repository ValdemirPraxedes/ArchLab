package br.com.archlab.simulation.core.v3;
import java.util.*;
import static br.com.archlab.simulation.core.v3.ArchitectureV3.*;
import static br.com.archlab.simulation.core.v3.ExecutionPlanV3.*;
import br.com.archlab.simulation.core.v3.ExecutionPlanV3.Process;
import static br.com.archlab.simulation.core.v3.RuntimeEntityV3.*;
import br.com.archlab.simulation.core.v3.RuntimeEntityV3.Resource;
import br.com.archlab.simulation.core.v3.RuntimeEntityV3.Component;
import br.com.archlab.simulation.core.v3.RuntimeEntityV3.Timer;
import static br.com.archlab.simulation.core.v3.DeterministicSchedulerV3.Phase.*;
import static br.com.archlab.simulation.core.v3.SimulationEventV3.*;
import static br.com.archlab.simulation.core.v3.InterpreterStateV3.*;
/** Event-driven interpreter. Handler closures retain only the immutable input definition. */
public final class SimulationEngineV3 {
    public static final long MAX_DISPATCHES=100_000;
    public record Checkpoint(SimulationRequestV3 request,AtomicDispatcherV3.Snapshot initial,AtomicDispatcherV3.Snapshot current) {public Checkpoint{Objects.requireNonNull(request);Objects.requireNonNull(initial);Objects.requireNonNull(current);}}
    private final SimulationRequestV3 request;private final ArchitectureV3 architecture;private final String prefix;private final IdentifierV3 controlId,principalId;
    private final Map<IdentifierV3,Operation> operations=new LinkedHashMap<>();private final Map<IdentifierV3,ExecutionPlanV3> plans=new LinkedHashMap<>();
    private final AtomicDispatcherV3 dispatcher;private final AtomicDispatcherV3.Snapshot initial;
    public SimulationEngineV3(SimulationRequestV3 request){this(request,null);}
    public static SimulationEngineV3 resume(Checkpoint checkpoint){return new SimulationEngineV3(checkpoint.request(),checkpoint);}
    private SimulationEngineV3(SimulationRequestV3 request,Checkpoint checkpoint){
        this.request=Objects.requireNonNull(request);architecture=request.architecture();prefix=prefix(architecture);controlId=id(prefix+"control");principalId=id(prefix+"principal");
        for(var op:architecture.operations())operations.put(op.id(),op);for(var plan:architecture.executionPlans())plans.put(plan.operationId(),plan);
        for(var op:operations.values())if(op.execution().kind()==BindingKind.NATIVE_BEHAVIOR){long duration=property(component(op.ownerId()).properties(),"processingMs",0);
            var pool=architecture.resources().stream().filter(r->r.ownerId().equals(op.ownerId()) && r.typeId().value().equals("PROCESSING_POOL")).map(ArchitectureV3.Resource::id).sorted(Comparator.comparing(IdentifierV3::value)).findFirst().orElse(null);
            plans.put(op.id(),new ExecutionPlanV3(id(prefix+"native-"+plans.size()),op.id(),id("process"),new Limits(10,2),List.of(new Process(id("process"),new DurationMsV3(duration),pool,Map.of(Outcome.NEXT,id("done"))),new Complete(id("done"),Scope.ACTIVATION,id("native-success")))));}
        var handlers=new EnumMap<SimulationEventV3.Type,AtomicDispatcherV3.Handler>(SimulationEventV3.Type.class);for(var type:SimulationEventV3.Type.values())handlers.put(type,this::decide);
        if(checkpoint==null){var state=RuntimeStateV3.empty();
            for(var c:architecture.components()){boolean available=flag(c.properties(),"available",true);for(var value:request.scenario().initialStates())if(value instanceof InitialStateV3.Component s && s.componentId().equals(c.id()) && s.available()!=null)available=s.available();state=state.create(new Component(c.id(),0,available?ComponentStatus.AVAILABLE:ComponentStatus.UNAVAILABLE));}
            for(var r:architecture.resources()){boolean available=flag(r.properties(),"available",true);for(var value:request.scenario().initialStates())if(value instanceof InitialStateV3.Resource s && s.resourceId().equals(r.id()) && s.available()!=null)available=s.available();Long capacity=null;if(r.typeId().value().equals("PROCESSING_POOL"))capacity=property(r.properties(),"capacity",1);else if(r.typeId().value().equals("MESSAGE_QUEUE"))capacity=property(r.properties(),"capacity",100);
                state=state.create(new Resource(r.id(),available?"AVAILABLE":"UNAVAILABLE",capacity,r.typeId().value().equals("PROCESSING_POOL")?0L:null,0));}
            state=state.create(new Principal(principalId,0,PrincipalStatus.IN_PROGRESS)).create(new Control(controlId,0,InterpreterStateV3.empty(prefix)));
            var agenda=new DeterministicSchedulerV3<SimulationEventV3>();agenda.schedule(0,CONTINUATION,new ExecutionStartDue(principalId));
            if(request.options().completionMode()==SimulationRequestV3.CompletionMode.UNTIL_HORIZON)agenda.schedule(request.options().horizonAtMs(),TERMINATION_CHECK,new HorizonDue(new DurationMsV3(request.options().horizonAtMs())));
            dispatcher=new AtomicDispatcherV3(id(prefix+"execution"),state,agenda.snapshot(),request.options().seed(),handlers);initial=dispatcher.snapshot();
        }else{initial=checkpoint.initial();dispatcher=new AtomicDispatcherV3(checkpoint.current(),handlers);if(!((Control)dispatcher.snapshot().state().entities().get(controlId)).data().prefix().equals(prefix))throw new IllegalArgumentException("Checkpoint input mismatch");}
    }
    public Checkpoint checkpoint(){return new Checkpoint(request,initial,dispatcher.snapshot());}
    public InterpreterStateV3 control(){return ((Control)dispatcher.snapshot().state().entities().get(controlId)).data();}
    public boolean step(){if(control().globalStop()!=null)return false;return dispatcher.dispatchNext().isPresent();}
    public Checkpoint run(){while(step()){}return checkpoint();}
    private DispatchDecisionV3 decide(DeterministicSchedulerV3.Event<SimulationEventV3> event,AtomicDispatcherV3.ReadView view){var tx=new Tx(event,view);
        if(view.dispatchSequence()>=MAX_DISPATCHES-1){tx.stop("LIMIT_REACHED");return tx.finish();}
        switch(event.payload()){
            case ExecutionStartDue e -> {tx.emit(TraceRecordV3.Kind.EXECUTION_STARTED,new TraceRecordV3.Execution("STARTED",request.scenario().entry().operationId()));tx.schedule(0,CONTINUATION,new RootOperationStartDue(principalId,request.scenario().entry().componentId(),request.scenario().entry().operationId()));}
            case RootOperationStartDue e -> {tx.emit(TraceRecordV3.Kind.ROOT_OPERATION_STARTED,new TraceRecordV3.Execution("ROOT_STARTED",e.entryOperationId()));if(((Component)tx.get(e.entryComponentId())).status()==ComponentStatus.UNAVAILABLE){tx.principal("FAILED","SOURCE_UNAVAILABLE");tx.checkTermination();}else tx.call(null,null,e.entryOperationId(),request.scenario().entry().connectionId(),true,true,null);}
            case MessageSendDue e -> tx.messageSend(e);
            case MessageArrivalDue e -> tx.messageArrive(e);
            case MessageDeliveryFailureDue e -> tx.deliveryFailed(e.attemptId(),e.failureCode().value());
            case ActivationStartDue e -> tx.startActivation(e.activationId());
            case StepStartDue e -> tx.startStep(e);
            case StepCompletionDue e -> tx.completeStep(e);
            case ActivationContinuationDue e -> tx.continueActivation(e);
            case ResponseSendDue e -> tx.responseSend(e);
            case ResponseArrivalDue e -> tx.responseArrive(e);
            case InteractionDeadlineDue e -> tx.deadline(e.interactionId(),e.timerId());
            case AttemptDeadlineDue e -> tx.deadline(e.interactionId(),e.timerId());
            case CancellationDeliveryDue e -> {if(e.target().kind().equals("ACTIVATION"))tx.endActivation(e.target().id(),"CANCELLED");else throw new IllegalArgumentException("Unsupported cancellation target");}
            case ResourceAcquisitionDue e -> tx.acquire(e.resourceId(),e.claimId());
            case ResourceWakeupDue e -> tx.acquire(e.resourceId(),e.claimId());
            case ResourceReleaseDue e -> tx.release(e.resourceId(),e.leaseId());
            case ResourceWaitTimeoutDue e -> tx.resourceTimeout(e);
            case ResourceStateChangeDue e -> tx.resourceChange(e);
            case TerminationCheckDue e -> tx.termination();
            case HorizonDue e -> tx.stop("HORIZON_REACHED");
            default -> throw new IllegalStateException("Async handler not implemented: "+event.payload().eventType());
        }
        return tx.finish();
    }
    private final class Tx {
        final DeterministicSchedulerV3.Event<SimulationEventV3> event;final AtomicDispatcherV3.ReadView view;final Control original;final Builder m;RuntimeStateV3 state;final List<EffectBatchV3.Effect> effects=new ArrayList<>();
        Tx(DeterministicSchedulerV3.Event<SimulationEventV3> event,AtomicDispatcherV3.ReadView view){this.event=event;this.view=view;state=view.state();original=(Control)state.entities().get(controlId);m=new Builder(original.data());}
        long now(){return view.nowMs();}IdentifierV3 next(String kind){return m.id(kind);}RuntimeEntityV3 get(IdentifierV3 key){return state.entities().get(key);}
        Step step(IdentifierV3 activation,IdentifierV3 key){return plan(m.activations.get(activation).operationId()).steps().stream().filter(s->s.id().equals(key)).findFirst().orElseThrow();}
        void add(EffectBatchV3.Effect e){switch(e){case EffectBatchV3.Create c->state=state.create(c.entity());case EffectBatchV3.Replace r->state=state.replace(r.expectedVersion(),r.entity());case EffectBatchV3.Transition t->state=state.transition(t.id(),t.expectedVersion(),t.target());default->{}}effects.add(e);}
        void create(RuntimeEntityV3 value){add(new EffectBatchV3.Create(value));}void replace(RuntimeEntityV3 value){add(new EffectBatchV3.Replace(get(value.id()).version(),value));}
        void transition(IdentifierV3 key,String target){var value=get(key);if(value!=null && !target.equals(value.stateName()))add(new EffectBatchV3.Transition(key,value.version(),target));}
        void schedule(long delay,DeterministicSchedulerV3.Phase phase,SimulationEventV3 payload){add(new EffectBatchV3.Schedule(Math.addExact(now(),delay),phase,payload));}
        void emit(TraceRecordV3.Kind kind,TraceRecordV3.Detail detail){add(new EffectBatchV3.Emit(kind,null,detail));}
        DispatchDecisionV3 finish(){replace(new Control(controlId,original.version()+1,m.freeze()));return new DispatchDecisionV3.Apply(new EffectBatchV3(List.of(),effects));}
        void checkTermination(){schedule(0,TERMINATION_CHECK,new TerminationCheckDue(id("WORK_CHANGED")));}
        void work(IdentifierV3 owner,String kind){create(new WorkItem(id(owner.value()+"-work"),0,owner,kind,WorkItemStatus.PENDING,"GLOBAL"));}
        void resolve(IdentifierV3 owner){var key=id(owner.value()+"-work");if(get(key) instanceof WorkItem w && w.status()==WorkItemStatus.PENDING)transition(key,"RESOLVED");}
        IdentifierV3 interactionForAttempt(IdentifierV3 attempt){return m.calls.entrySet().stream().filter(e->e.getValue().attempt().equals(attempt)).map(Map.Entry::getKey).findFirst().orElseThrow();}
        void call(IdentifierV3 callerActivation,IdentifierV3 cursor,IdentifierV3 operation,IdentifierV3 connection,boolean await,boolean root,IdentifierV3 policy){
            long latency=connection==null?0:property(communication(connection).properties(),"latencyMs",0);var inter=next("interaction");var attempt=next("attempt");var message=next("request");var response=next("response");var callee=next("activation");
            m.calls.put(inter,new CallData(cursor,callerActivation,operation,callee,attempt,message,response,connection,latency,await,root,null));
            create(new Interaction(inter,InteractionStatus.CREATED,List.of(attempt),0));create(new Attempt(attempt,inter,AttemptStatus.SCHEDULED,0));work(inter,"INTERACTION");transition(inter,"IN_FLIGHT");transition(attempt,"IN_FLIGHT");
            if(cursor!=null && await)waitCursor(cursor,inter);
            schedule(0,DELIVERY_OR_COMPLETION,new MessageSendDue(message,attempt,connection==null?id("DIRECT"):connection));
            Long timeout=policy==null?(root && connection!=null?optionalNumber(communication(connection).properties(),"timeoutMs"):null):optionalNumber(resource(policy).properties(),"timeoutMs");
            if(timeout!=null){var timer=id(inter.value()+"-timer");create(new Timer(timer,0,inter,new DurationMsV3(Math.addExact(now(),timeout)),TimerStatus.SCHEDULED,"INTERACTION"));schedule(timeout,DEADLINE,new InteractionDeadlineDue(inter,timer,new DurationMsV3(Math.addExact(now(),timeout))));}
        }
        void messageSend(MessageSendDue e){var inter=interactionForAttempt(e.attemptId());var data=m.calls.get(inter);if(terminal(get(inter)))return;
            emit(TraceRecordV3.Kind.MESSAGE_SENT,new TraceRecordV3.Message(e.messageId(),"SENT",null,e.attemptId()));
            var op=operations.get(data.targetOperation());var endpoint=data.connection()==null?op.ownerId():communication(data.connection()).targetEndpointId();schedule(data.latency(),DELIVERY_OR_COMPLETION,new MessageArrivalDue(e.messageId(),e.attemptId(),endpoint,e.pathId()));}
        void messageArrive(MessageArrivalDue e){var inter=interactionForAttempt(e.attemptId());var data=m.calls.get(inter);if(terminal(get(inter)))return;
            m.add(m.communication,data.connection()==null?id("DIRECT"):data.connection(),data.latency());emit(TraceRecordV3.Kind.MESSAGE_ARRIVED,new TraceRecordV3.Message(e.messageId(),"ARRIVED",e.destinationEndpointId(),e.attemptId()));
            var op=operations.get(data.targetOperation());if(((Component)get(op.ownerId())).status()==ComponentStatus.UNAVAILABLE){deliveryFailed(e.attemptId(),"TARGET_UNAVAILABLE");return;}
            transition(inter,"AWAITING_RESULT");transition(data.attempt(),"AWAITING_RESULT");m.activations.put(data.callee(),new ActivationData(op.id(),inter,now()));
            create(new Activation(data.callee(),op.ownerId(),ActivationStatus.READY,plan(op.id()).entryStepId(),0));work(data.callee(),"ACTIVATION");emit(TraceRecordV3.Kind.ACTIVATION_CREATED,new TraceRecordV3.StateChange("ACTIVATION",data.callee(),null,"READY","MESSAGE_ARRIVAL",0,0));schedule(0,CONTINUATION,new ActivationStartDue(data.callee()));}
        void startActivation(IdentifierV3 activation){if(terminal(get(activation)))return;transition(activation,"RUNNING");var cursor=next("cursor");queueStep(activation,cursor,plan(m.activations.get(activation).operationId()).entryStepId());}
        void queueStep(IdentifierV3 activation,IdentifierV3 cursor,IdentifierV3 step){var run=next("step");create(new StepExecution(run,0,StepExecutionStatus.READY));m.runs.put(run,new Run(activation,cursor,step,now(),0,null,null,null));
            if(get(cursor)==null)create(new Cursor(cursor,0,activation,run,step,CursorStatus.READY,null));else {var old=(Cursor)get(cursor);replace(new Cursor(cursor,old.version()+1,activation,run,step,old.status()==CursorStatus.WAITING?CursorStatus.RUNNING:old.status(),null));}
            var old=(Activation)get(activation);replace(new Activation(activation,old.componentId(),old.status(),step,old.version()+1));schedule(0,CONTINUATION,new StepStartDue(activation,run,step,cursor));}
        void startStep(StepStartDue e){if(terminal(get(e.activationId())) || terminal(get(e.stepExecutionId())))return;var cursor=(Cursor)get(e.cursorId());if(!cursor.stepExecutionId().equals(e.stepExecutionId()))return;
            var plan=plan(m.activations.get(e.activationId()).operationId());if(m.increment(e.activationId().value()+"/steps")>plan.limits().maxStepExecutions() || m.increment(e.activationId().value()+"/visits/"+e.stepId().value())>plan.limits().maxIterations()){endActivation(e.activationId(),"FAILED");stop("LIMIT_REACHED");return;}
            transition(e.cursorId(),"RUNNING");transition(e.stepExecutionId(),"RUNNING");emit(TraceRecordV3.Kind.STEP_STARTED,new TraceRecordV3.Step(e.stepExecutionId(),e.stepId(),"STARTED",null));var step=step(e.activationId(),e.stepId());
            switch(step){
                case Process process -> {var claim=process.resourceProfileId()==null?null:next("claim");m.runs.put(e.stepExecutionId(),new Run(e.activationId(),e.cursorId(),e.stepId(),now(),process.durationMs().value(),process.resourceProfileId(),claim,null));
                    if(claim==null)schedule(process.durationMs().value(),DELIVERY_OR_COMPLETION,new StepCompletionDue(e.stepExecutionId(),id(e.stepExecutionId().value()+"-completion")));
                    else {create(new WaitClaim(claim,0,process.resourceProfileId(),e.stepExecutionId(),m.nextId,WaitClaimStatus.WAITING));waitCursor(e.cursorId(),claim);transition(e.stepExecutionId(),"WAITING");schedule(0,CONTINUATION,new ResourceAcquisitionDue(process.resourceProfileId(),claim,new RuntimeEntityRefV3("STEP_EXECUTION",e.stepExecutionId())));}}
                case Delay delay -> {m.runs.put(e.stepExecutionId(),new Run(e.activationId(),e.cursorId(),e.stepId(),now(),delay.durationMs().value(),null,null,null));schedule(delay.durationMs().value(),DELIVERY_OR_COMPLETION,new StepCompletionDue(e.stepExecutionId(),id(e.stepExecutionId().value()+"-completion")));}
                case Call call -> {call(e.activationId(),e.cursorId(),call.targetOperationId(),call.connectionId(),call.awaitMode()==AwaitCall.RESPONSE,false,call.policyRef());if(call.awaitMode()==AwaitCall.NONE)finishStep(e.stepExecutionId(),"DISPATCHED",true);else transition(e.stepExecutionId(),"WAITING");}
                case Condition condition -> {String outcome;try{outcome=ConditionsV3.evaluate(condition.expression(),request.scenario().parameters())?"TRUE":"FALSE";}catch(IllegalArgumentException ex){outcome="ERROR";}finishStep(e.stepExecutionId(),outcome,true);}
                case Complete complete -> {finishStep(e.stepExecutionId(),"SUCCESS",false);endActivation(e.activationId(),"SUCCESS");}
                case Fail fail -> {finishStep(e.stepExecutionId(),"FAILURE",false);endActivation(e.activationId(),"FAILURE");}
                default -> throw new IllegalStateException("Async step not implemented: "+step.getClass().getSimpleName());
            }
        }
        void waitCursor(IdentifierV3 key,IdentifierV3 waiting){var cursor=(Cursor)get(key);replace(new Cursor(key,cursor.version()+1,cursor.activationId(),cursor.stepExecutionId(),cursor.currentStepId(),CursorStatus.WAITING,waiting));transition(cursor.activationId(),"WAITING");}
        void resumeCursor(IdentifierV3 key){var cursor=(Cursor)get(key);if(terminal(cursor))return;replace(new Cursor(key,cursor.version()+1,cursor.activationId(),cursor.stepExecutionId(),cursor.currentStepId(),CursorStatus.RUNNING,null));if(!terminal(get(cursor.activationId())))transition(cursor.activationId(),"RUNNING");}
        void completeStep(StepCompletionDue e){var run=m.runs.get(e.stepExecutionId());if(run==null || terminal(get(e.stepExecutionId())) || terminal(get(run.activation())))return;
            if(!e.completionToken().equals(id(e.stepExecutionId().value()+"-completion")))return;
            if(step(run.activation(),run.step()) instanceof Process)m.add(m.processing,((Activation)get(run.activation())).componentId(),now()-run.startedAt());
            if(run.lease()!=null)schedule(0,DELIVERY_OR_COMPLETION,new ResourceReleaseDue(run.resource(),run.lease()));finishStep(e.stepExecutionId(),"NEXT",true);}
        void finishStep(IdentifierV3 key,String outcome,boolean continuePlan){if(terminal(get(key)))return;var run=m.runs.get(key);transition(key,"COMPLETED");var result=next("result");create(new StepResult(result,0,key,outcome,null));emit(TraceRecordV3.Kind.STEP_FINISHED,new TraceRecordV3.Step(key,run.step(),"FINISHED",outcome));
            if(continuePlan){var cursor=(Cursor)get(run.cursor());if(cursor.status()==CursorStatus.WAITING)resumeCursor(cursor.id());cursor=(Cursor)get(run.cursor());schedule(0,CONTINUATION,new ActivationContinuationDue(run.activation(),run.cursor(),cursor.version(),result));}}
        void continueActivation(ActivationContinuationDue e){if(terminal(get(e.activationId())))return;var cursor=(Cursor)get(e.cursorId());if(cursor.version()!=e.expectedCursorVersion() || terminal(cursor))return;var result=(StepResult)get(e.outcomeRef());var current=step(e.activationId(),cursor.currentStepId());IdentifierV3 next;
            if(current instanceof Delay d)next=d.nextStepId();else if(current instanceof Condition c)next=result.outcome().equals("TRUE")?c.onTrue():result.outcome().equals("FALSE")?c.onFalse():c.onError();else next=transitions(current).get(Outcome.valueOf(result.outcome()));
            emit(TraceRecordV3.Kind.STEP_TRANSITION_SELECTED,new TraceRecordV3.Step(cursor.stepExecutionId(),cursor.currentStepId(),"TRANSITION_SELECTED",result.outcome()));if(next==null)endActivation(e.activationId(),"FAILURE");else queueStep(e.activationId(),e.cursorId(),next);}
        void endActivation(IdentifierV3 key,String outcome){var entity=get(key);if(entity==null || terminal(entity))return;var activation=(Activation)entity;String target=outcome.equals("SUCCESS")?"COMPLETED":outcome.equals("CANCELLED")?"CANCELLED":"FAILED";
            transition(key,target);for(var value:new ArrayList<>(state.entities().values()))if(value instanceof Cursor c && c.activationId().equals(key) && !terminal(c))transition(c.id(),target);
            for(var entry:m.runs.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(IdentifierV3::value))).toList())if(entry.getValue().activation().equals(key) && !terminal(get(entry.getKey()))){var run=entry.getValue();if(get(entry.getKey()).stateName().equals("RUNNING") && step(key,run.step()) instanceof Process)m.add(m.processing,activation.componentId(),now()-run.startedAt());transition(entry.getKey(),target.equals("COMPLETED")?"CANCELLED":target);if(run.lease()!=null && get(run.lease()) instanceof Lease l && l.status()==LeaseStatus.HELD)schedule(0,DELIVERY_OR_COMPLETION,new ResourceReleaseDue(run.resource(),run.lease()));if(run.claim()!=null && get(run.claim()) instanceof WaitClaim c && c.status()==WaitClaimStatus.WAITING)transition(c.id(),"REJECTED");}
            resolve(key);emit(TraceRecordV3.Kind.ACTIVATION_STATE_CHANGED,new TraceRecordV3.StateChange("ACTIVATION",key,activation.stateName(),target,outcome,activation.version(),get(key).version()));
            for(var entry:m.calls.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(IdentifierV3::value))).toList()){var call=entry.getValue();if(key.equals(call.callerActivation()) && call.awaitResponse() && !terminal(get(entry.getKey()))){transition(entry.getKey(),"CANCELLED");if(!terminal(get(call.attempt())))transition(call.attempt(),"CANCELLED");cancelTimer(entry.getKey());resolve(entry.getKey());if(get(call.callee())!=null && !terminal(get(call.callee())))schedule(0,DELIVERY_OR_COMPLETION,new CancellationDeliveryDue(next("cancel"),new RuntimeEntityRefV3("ACTIVATION",call.callee()),entry.getKey()));}}
            var data=m.activations.get(key);if(data!=null && data.returnInteraction()!=null){var call=m.calls.get(data.returnInteraction());m.calls.put(data.returnInteraction(),call.outcome(outcome));schedule(0,DELIVERY_OR_COMPLETION,new ResponseSendDue(data.returnInteraction(),call.attempt(),call.responseMessage()));}
            checkTermination();}
        void responseSend(ResponseSendDue e){var call=m.calls.get(e.interactionId());if(call==null)return;emit(TraceRecordV3.Kind.RESPONSE_SENT,new TraceRecordV3.Message(e.responseMessageId(),"RESPONSE_SENT",null,e.attemptId()));schedule(call.latency(),DELIVERY_OR_COMPLETION,new ResponseArrivalDue(e.interactionId(),e.attemptId(),e.responseMessageId(),call.callerActivation()==null?principalId:call.callerActivation()));}
        void responseArrive(ResponseArrivalDue e){var call=m.calls.get(e.interactionId());if(call==null)return;
            if(terminal(get(e.interactionId()))){emit(TraceRecordV3.Kind.RESPONSE_ARRIVED_LATE,new TraceRecordV3.Message(e.responseMessageId(),"RESPONSE_ARRIVED_LATE",null,e.attemptId()));return;}
            m.add(m.communication,call.connection()==null?id("DIRECT"):call.connection(),call.latency());emit(TraceRecordV3.Kind.RESPONSE_ARRIVED,new TraceRecordV3.Message(e.responseMessageId(),"RESPONSE_ARRIVED",null,e.attemptId()));
            boolean success="SUCCESS".equals(call.outcome());transition(e.interactionId(),success?"COMPLETED":"FAILED");transition(call.attempt(),success?"COMPLETED":"FAILED");cancelTimer(e.interactionId());resolve(e.interactionId());
            if(call.root())principal(success?"RESPONDED":"FAILED",success?"SUCCESS":Objects.requireNonNullElse(call.outcome(),"FAILURE"));
            else if(call.awaitResponse() && !terminal(get(call.callerActivation())))finishStep(((Cursor)get(call.callerCursor())).stepExecutionId(),success?"SUCCESS":"FAILURE",true);checkTermination();}
        void principal(String status,String outcome){if(((Principal)get(principalId)).status()!=PrincipalStatus.IN_PROGRESS)return;transition(principalId,status);m.principalTerminalAt=now();m.principalOutcome=outcome;if(status.equals("RESPONDED")){m.principalResponseAt=now();emit(TraceRecordV3.Kind.PRINCIPAL_RESPONDED,new TraceRecordV3.Execution("PRINCIPAL_RESPONDED",request.scenario().entry().operationId()));}else if(status.equals("TIMED_OUT"))emit(TraceRecordV3.Kind.PRINCIPAL_TIMED_OUT,new TraceRecordV3.Execution("PRINCIPAL_TIMED_OUT",request.scenario().entry().operationId()));}
        void cancelTimer(IdentifierV3 inter){var timer=id(inter.value()+"-timer");if(get(timer) instanceof Timer t && t.status()==TimerStatus.SCHEDULED)transition(timer,"CANCELLED");}
        void deadline(IdentifierV3 inter,IdentifierV3 timer){if(!(get(timer) instanceof Timer t) || t.status()!=TimerStatus.SCHEDULED || terminal(get(inter)))return;transition(timer,"CONSUMED");var call=m.calls.get(inter);transition(inter,"TIMED_OUT");if(!terminal(get(call.attempt())))transition(call.attempt(),"TIMED_OUT");resolve(inter);
            if(call.root())principal("TIMED_OUT","TIMEOUT");else if(call.awaitResponse() && !terminal(get(call.callerActivation())))finishStep(((Cursor)get(call.callerCursor())).stepExecutionId(),"TIMEOUT",true);
            if(get(call.callee())!=null && !terminal(get(call.callee())))schedule(0,DELIVERY_OR_COMPLETION,new CancellationDeliveryDue(next("cancel"),new RuntimeEntityRefV3("ACTIVATION",call.callee()),inter));checkTermination();}
        void deliveryFailed(IdentifierV3 attempt,String reason){var inter=interactionForAttempt(attempt);if(terminal(get(inter)))return;var call=m.calls.get(inter);transition(inter,"FAILED");transition(attempt,"FAILED");cancelTimer(inter);resolve(inter);emit(TraceRecordV3.Kind.MESSAGE_DELIVERY_FAILED,new TraceRecordV3.Message(call.requestMessage(),"DELIVERY_FAILED",null,attempt));
            if(call.root())principal("FAILED",reason);else if(call.awaitResponse() && !terminal(get(call.callerActivation())))finishStep(((Cursor)get(call.callerCursor())).stepExecutionId(),"FAILURE",true);checkTermination();}
        void acquire(IdentifierV3 resourceId,IdentifierV3 claimId){if(!(get(claimId) instanceof WaitClaim claim) || claim.status()!=WaitClaimStatus.WAITING || terminal(get(claim.ownerId())))return;var resource=(Resource)get(resourceId);if(resource.status().equals("UNAVAILABLE") || resource.capacity()==null || resource.inUse()>=resource.capacity())return;
            var first=state.entities().values().stream().filter(e->e instanceof WaitClaim c && c.resourceId().equals(resourceId) && c.status()==WaitClaimStatus.WAITING).map(e->(WaitClaim)e).min(Comparator.comparingLong(WaitClaim::queueOrdinal).thenComparing(c->c.id().value())).orElseThrow();if(!first.id().equals(claimId))return;
            var run=m.runs.get(claim.ownerId());var lease=next("lease");transition(claimId,"ACQUIRED");create(new Lease(lease,0,resourceId,claimId,LeaseStatus.HELD,1));long used=resource.inUse()+1;replace(new Resource(resourceId,used==resource.capacity()?"EXHAUSTED":"AVAILABLE",resource.capacity(),used,resource.version()+1));
            resumeCursor(run.cursor());transition(claim.ownerId(),"RUNNING");m.runs.put(claim.ownerId(),new Run(run.activation(),run.cursor(),run.step(),now(),run.duration(),resourceId,claimId,lease));emit(TraceRecordV3.Kind.RESOURCE_ACQUIRED,new TraceRecordV3.Resource(resourceId,"ACQUIRED",lease));schedule(run.duration(),DELIVERY_OR_COMPLETION,new StepCompletionDue(claim.ownerId(),id(claim.ownerId().value()+"-completion")));}
        void release(IdentifierV3 resourceId,IdentifierV3 leaseId){if(!(get(leaseId) instanceof Lease lease) || lease.status()!=LeaseStatus.HELD)return;var resource=(Resource)get(resourceId);transition(leaseId,"RELEASED");replace(new Resource(resourceId,resource.status().equals("UNAVAILABLE")?"UNAVAILABLE":"AVAILABLE",resource.capacity(),resource.inUse()-lease.units(),resource.version()+1));emit(TraceRecordV3.Kind.RESOURCE_RELEASED,new TraceRecordV3.Resource(resourceId,"RELEASED",leaseId));
            for(var value:state.entities().values())if(value instanceof WaitClaim claim && claim.resourceId().equals(resourceId) && claim.status()==WaitClaimStatus.WAITING)schedule(0,CONTINUATION,new ResourceWakeupDue(resourceId,claim.id(),get(resourceId).version()));checkTermination();}
        void resourceTimeout(ResourceWaitTimeoutDue e){if(get(e.claimId()) instanceof WaitClaim claim && claim.status()==WaitClaimStatus.WAITING){transition(claim.id(),"TIMED_OUT");var run=m.runs.get(claim.ownerId());endActivation(run.activation(),"FAILURE");}}
        void resourceChange(ResourceStateChangeDue e){var resource=(Resource)get(e.resourceId());transition(resource.id(),e.newStateRef().value());if(e.newStateRef().value().equals("AVAILABLE"))for(var value:state.entities().values())if(value instanceof WaitClaim c && c.resourceId().equals(resource.id()) && c.status()==WaitClaimStatus.WAITING)schedule(0,CONTINUATION,new ResourceWakeupDue(resource.id(),c.id(),get(resource.id()).version()));}
        void termination(){boolean work=state.entities().values().stream().anyMatch(e->e instanceof WorkItem w && w.status()==WorkItemStatus.PENDING);if(!work && ((Principal)get(principalId)).status()!=PrincipalStatus.IN_PROGRESS)stop("COMPLETED");else if(view.pending().isEmpty())stop("ABORTED");}
        void stop(String reason){m.globalStop=reason;var kind=reason.equals("HORIZON_REACHED")?TraceRecordV3.Kind.HORIZON_REACHED:reason.equals("LIMIT_REACHED")?TraceRecordV3.Kind.LIMIT_REACHED:TraceRecordV3.Kind.GLOBAL_COMPLETED;emit(kind,new TraceRecordV3.Termination(reason,reason));}
    }
    private ExecutionPlanV3 plan(IdentifierV3 op){return Objects.requireNonNull(plans.get(op),"Operation has no plan");}
    private Step step(IdentifierV3 activation,IdentifierV3 step){var data=((Control)dispatcher.snapshot().state().entities().get(controlId)).data().activations().get(activation);return plan(data.operationId()).steps().stream().filter(s->s.id().equals(step)).findFirst().orElseThrow();}
    private ArchitectureV3.Component component(IdentifierV3 key){return architecture.components().stream().filter(c->c.id().equals(key)).findFirst().orElseThrow();}
    private ArchitectureV3.Resource resource(IdentifierV3 key){return architecture.resources().stream().filter(r->r.id().equals(key)).findFirst().orElseThrow();}
    private Communication communication(IdentifierV3 key){return architecture.relations().stream().filter(r->r.id().equals(key) && r instanceof Communication).map(r->(Communication)r).findFirst().orElseThrow();}
    private static Map<Outcome,IdentifierV3> transitions(Step step){return switch(step){case Process p->p.transitions();case Call c->c.transitions();case Publish p->p.transitions();case Join j->j.transitions();default->Map.of();};}
    private static boolean terminal(RuntimeEntityV3 value){return value==null || value.stateName()!=null && StateTransitionsV3.terminal(value.kind(),value.stateName());}
    private static IdentifierV3 id(String value){return new IdentifierV3(value);}
    private static long property(Map<String,PropertyValueV3> values,String key,long fallback){var value=values.get(key);return value instanceof PropertyValueV3.IntegerValue n?n.value():fallback;}
    private static Long optionalNumber(Map<String,PropertyValueV3> values,String key){return values.get(key) instanceof PropertyValueV3.IntegerValue n?n.value():null;}
    private static boolean flag(Map<String,PropertyValueV3> values,String key,boolean fallback){return values.get(key) instanceof PropertyValueV3.Flag b?b.value():fallback;}
    private static String prefix(ArchitectureV3 architecture){var ids=new HashSet<String>();architecture.components().forEach(x->ids.add(x.id().value()));architecture.resources().forEach(x->ids.add(x.id().value()));String prefix;int i=0;while(true){prefix="@v3-"+i+++"/";String candidate=prefix;if(ids.stream().noneMatch(x->x.startsWith(candidate)))return prefix;}}
}
