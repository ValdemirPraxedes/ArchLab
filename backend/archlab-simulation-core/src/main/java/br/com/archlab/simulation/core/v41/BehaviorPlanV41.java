package br.com.archlab.simulation.core.v41;

import java.util.List;
import java.util.Objects;

/** Immutable protocol-neutral actions; transport decoding lives in archlab-api. */
public final class BehaviorPlanV41 {
    private BehaviorPlanV41() {}

    public enum TriggerKind { REQUEST_RECEIVED, MESSAGE_CONSUMED, WS_MESSAGE_RECEIVED, SESSION_OPENED, SCHEDULED_TRIGGER }
    public enum ActionKind { PROCESS, CALL, PUBLISH, RESPOND, OPEN_SESSION, WS_SEND, CLOSE_SESSION, FORK, JOIN,
        BRANCH, CACHE_GET, CACHE_PUT, CACHE_INVALIDATE, OBJECT_GET, OBJECT_PUT, OBJECT_DELETE,
        SEND_NOTIFICATION, INDEX_DOCUMENT, SEARCH_DOCUMENTS, REGISTER_SERVICE, UNREGISTER_SERVICE,
        DISCOVER_SERVICE, EVALUATE_RULE, SCHEDULE_TRIGGER, TRANSFORM,
        DB_SELECT, DB_INSERT, DB_UPDATE, DB_DELETE, DB_GET, DB_PUT, DB_FIND,
        DB_BULK_INSERT, ETL_EXTRACT, ETL_TRANSFORM, ETL_LOAD }

    public record Action(String id, String kind, List<String> dependsOn, String connectionId,
                         String destinationName, String sessionAlias, String sender, String payload,
                         long durationMs, long timeoutMs, int statusCode, List<String> actionIds,
                         java.util.Map<String,Object> options) {
        public Action {
            Objects.requireNonNull(id);
            ActionKind.valueOf(Objects.requireNonNull(kind));
            dependsOn=List.copyOf(dependsOn);
            actionIds=List.copyOf(actionIds);
            options=java.util.Map.copyOf(options);
        }
        public Action(String id, String kind, List<String> dependsOn, String connectionId,
                      String destinationName, String sessionAlias, String sender, String payload,
                      long durationMs, long timeoutMs, int statusCode, List<String> actionIds) {
            this(id,kind,dependsOn,connectionId,destinationName,sessionAlias,sender,payload,
                    durationMs,timeoutMs,statusCode,actionIds,java.util.Map.of());
        }
        public ActionKind actionKind() { return ActionKind.valueOf(kind); }
    }

    public record Behavior(String id, String trigger, String operation, String destinationName,
                           String websocketPath, List<Action> actions) {
        public Behavior {
            Objects.requireNonNull(id);
            TriggerKind.valueOf(Objects.requireNonNull(trigger));
            actions=List.copyOf(actions);
        }
        public TriggerKind triggerKind() { return TriggerKind.valueOf(trigger); }
    }
}
