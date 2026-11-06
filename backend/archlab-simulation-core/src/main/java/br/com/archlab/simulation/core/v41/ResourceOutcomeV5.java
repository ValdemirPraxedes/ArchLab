package br.com.archlab.simulation.core.v41;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Closed Action result domain used by BRANCH and projected on the v0.5 wire contract. */
public record ResourceOutcomeV5(String actionId,Status status,Code resultCode,String resultRef,
                                long timestampMs,String operationId,String componentId,
                                String connectionId,Map<String,Object> details) {
    public enum Status { SUCCEEDED, FAILED, TIMED_OUT }
    public enum Code { HIT, MISS, STORED, REMOVED, NOT_FOUND, FOUND, CREATED, REPLACED,
        DELETED, SENT, FAILED, TIMED_OUT, TRANSFORMED, INDEXED, UPDATED, REGISTERED,
        UNAVAILABLE, ALLOW, DENY, REVIEW }

    public ResourceOutcomeV5 {
        Objects.requireNonNull(actionId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(resultCode);
        Objects.requireNonNull(componentId);
        if (timestampMs<0) throw new IllegalArgumentException("Negative virtual timestamp");
        details=Map.copyOf(details);
    }
    public Map<String,Object> toMap() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("actionId",actionId);
        result.put("status",status.name());
        result.put("resultCode",resultCode.name());
        if (resultRef!=null) result.put("resultRef",resultRef);
        result.put("timestampMs",timestampMs);
        if (operationId!=null) result.put("operationId",operationId);
        result.put("componentId",componentId);
        if (connectionId!=null) result.put("connectionId",connectionId);
        result.put("details",details);
        return result;
    }
}
