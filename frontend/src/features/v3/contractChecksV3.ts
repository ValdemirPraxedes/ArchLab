import type { SchemaV3,SnapshotV3 } from './types';
// Derived from OpenAPI TraceRecordV3 allOf; typegen makes new kinds a compile error here.
export const TRACE_RULES_V3 = {
  "EXECUTION_STARTED": {
    "kind": "EXECUTION",
    "action": "STARTED"
  },
  "ROOT_OPERATION_STARTED": {
    "kind": "EXECUTION",
    "action": "ROOT_STARTED"
  },
  "ACTIVATION_CREATED": {
    "kind": "STATECHANGE"
  },
  "ACTIVATION_STATE_CHANGED": {
    "kind": "STATECHANGE"
  },
  "INTERACTION_STATE_CHANGED": {
    "kind": "STATECHANGE"
  },
  "ATTEMPT_STATE_CHANGED": {
    "kind": "STATECHANGE"
  },
  "STEP_STARTED": {
    "kind": "STEP",
    "action": "STARTED"
  },
  "STEP_FINISHED": {
    "kind": "STEP",
    "action": "FINISHED"
  },
  "STEP_TRANSITION_SELECTED": {
    "kind": "STEP",
    "action": "TRANSITION_SELECTED"
  },
  "MESSAGE_SENT": {
    "kind": "MESSAGE",
    "action": "SENT"
  },
  "MESSAGE_ARRIVED": {
    "kind": "MESSAGE",
    "action": "ARRIVED"
  },
  "MESSAGE_DELIVERY_FAILED": {
    "kind": "MESSAGE",
    "action": "DELIVERY_FAILED"
  },
  "RESPONSE_SENT": {
    "kind": "MESSAGE",
    "action": "RESPONSE_SENT"
  },
  "RESPONSE_ARRIVED": {
    "kind": "MESSAGE",
    "action": "RESPONSE_ARRIVED"
  },
  "RESPONSE_ARRIVED_LATE": {
    "kind": "MESSAGE",
    "action": "RESPONSE_ARRIVED_LATE"
  },
  "JOIN_EVALUATED": {
    "kind": "JOIN",
    "action": "EVALUATED"
  },
  "JOIN_SATISFIED": {
    "kind": "JOIN",
    "action": "SATISFIED"
  },
  "JOIN_UNSATISFIABLE": {
    "kind": "JOIN",
    "action": "UNSATISFIABLE"
  },
  "RESOURCE_ACQUIRED": {
    "kind": "RESOURCE",
    "action": "ACQUIRED"
  },
  "RESOURCE_RELEASED": {
    "kind": "RESOURCE",
    "action": "RELEASED"
  },
  "RESOURCE_WAIT_STARTED": {
    "kind": "RESOURCE",
    "action": "WAIT_STARTED"
  },
  "RESOURCE_STATE_CHANGED": {
    "kind": "RESOURCE",
    "action": "STATE_CHANGED"
  },
  "RETRY_SCHEDULED": {
    "kind": "RETRY",
    "action": "SCHEDULED"
  },
  "RETRY_STARTED": {
    "kind": "RETRY",
    "action": "STARTED"
  },
  "BROKER_ACCEPTED": {
    "kind": "QUEUE",
    "action": "BROKER_ACCEPTED"
  },
  "BROKER_REJECTED": {
    "kind": "QUEUE",
    "action": "BROKER_REJECTED"
  },
  "QUEUE_MESSAGE_ENQUEUED": {
    "kind": "QUEUE",
    "action": "ENQUEUED"
  },
  "QUEUE_DELIVERY_STARTED": {
    "kind": "QUEUE",
    "action": "DELIVERY_STARTED"
  },
  "CONSUMER_ACKNOWLEDGED": {
    "kind": "QUEUE",
    "action": "CONSUMER_ACKNOWLEDGED"
  },
  "QUEUE_MESSAGE_ACKNOWLEDGED": {
    "kind": "QUEUE",
    "action": "MESSAGE_ACKNOWLEDGED"
  },
  "QUEUE_MESSAGE_EXPIRED": {
    "kind": "QUEUE",
    "action": "EXPIRED"
  },
  "PRINCIPAL_RESPONDED": {
    "kind": "EXECUTION",
    "action": "PRINCIPAL_RESPONDED"
  },
  "PRINCIPAL_TIMED_OUT": {
    "kind": "EXECUTION",
    "action": "PRINCIPAL_TIMED_OUT"
  },
  "GLOBAL_COMPLETED": {
    "kind": "TERMINATION"
  },
  "HORIZON_REACHED": {
    "kind": "TERMINATION"
  },
  "LIMIT_REACHED": {
    "kind": "TERMINATION"
  },
  "RUNTIME_STATE_DELTA": {
    "kind": "RUNTIME_DELTA"
  },
  "SCHEDULER_POSITION_CHANGED": {
    "kind": "SCHEDULER_POSITION"
  }
} satisfies Record<SchemaV3['TraceKindV3'],{kind:SchemaV3['TraceDetailV3']['kind'];action?:string}>;
export type SnapshotCollectionV3=Exclude<keyof SnapshotV3,'virtualTimeMs'>;
export const SNAPSHOT_RULES_V3={activations:{idKey:'activationId'},interactions:{idKey:'interactionId'},attempts:{idKey:'attemptId'},parallelGroups:{idKey:'parallelGroupId'},resources:{idKey:'resourceId'},queueEntries:{idKey:'queueEntryId'},publications:{idKey:'publicationId'},deliveryAttempts:{idKey:'deliveryAttemptId'}} as const satisfies {[K in SnapshotCollectionV3]:{idKey:keyof SnapshotV3[K][number]&string}};
export const SNAPSHOT_COLLECTIONS_V3=Object.keys(SNAPSHOT_RULES_V3) as SnapshotCollectionV3[];

export const SNAPSHOT_STATUSES_V3:Record<SnapshotCollectionV3,readonly string[]|null>={
  "activations": [
    "READY",
    "RUNNING",
    "WAITING",
    "COMPLETED",
    "FAILED",
    "CANCELLED"
  ],
  "interactions": [
    "CREATED",
    "IN_FLIGHT",
    "AWAITING_RESULT",
    "COMPLETED",
    "FAILED",
    "TIMED_OUT",
    "CANCELLED"
  ],
  "attempts": [
    "SCHEDULED",
    "IN_FLIGHT",
    "AWAITING_RESULT",
    "COMPLETED",
    "FAILED",
    "TIMED_OUT",
    "CANCELLED"
  ],
  "parallelGroups": [
    "CREATED",
    "RUNNING",
    "JOIN_SATISFIED",
    "JOIN_UNSATISFIABLE",
    "COMPLETED",
    "CANCELLED"
  ],
  "resources": null,
  "queueEntries": [
    "READY",
    "IN_DELIVERY",
    "ACKNOWLEDGED",
    "EXPIRED",
    "DEAD_LETTERED",
    "DISCARDED"
  ],
  "publications": [
    "CREATED",
    "IN_TRANSIT",
    "ACCEPTED",
    "REJECTED",
    "FAILED",
    "TIMED_OUT"
  ],
  "deliveryAttempts": [
    "SCHEDULED",
    "IN_TRANSIT",
    "DELIVERED",
    "AWAITING_ACK",
    "ACKNOWLEDGED",
    "REJECTED",
    "TIMED_OUT",
    "FAILED",
    "CANCELLED"
  ]
};
