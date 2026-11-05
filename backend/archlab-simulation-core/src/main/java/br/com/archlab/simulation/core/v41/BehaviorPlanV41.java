package br.com.archlab.simulation.core.v41;

import java.util.List;
import java.util.Objects;

/** Immutable protocol-neutral actions; transport decoding lives in archlab-api. */
public final class BehaviorPlanV41 {
    private BehaviorPlanV41() {}

    public enum TriggerKind { REQUEST_RECEIVED, MESSAGE_CONSUMED, WS_MESSAGE_RECEIVED, SESSION_OPENED }
    public enum ActionKind { PROCESS, CALL, PUBLISH, RESPOND, OPEN_SESSION, WS_SEND, CLOSE_SESSION, FORK, JOIN }

    public record Action(String id, String kind, List<String> dependsOn, String connectionId,
                         String destinationName, String sessionAlias, String sender, String payload,
                         long durationMs, long timeoutMs, int statusCode, List<String> actionIds) {
        public Action {
            Objects.requireNonNull(id);
            ActionKind.valueOf(Objects.requireNonNull(kind));
            dependsOn=List.copyOf(dependsOn);
            actionIds=List.copyOf(actionIds);
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
