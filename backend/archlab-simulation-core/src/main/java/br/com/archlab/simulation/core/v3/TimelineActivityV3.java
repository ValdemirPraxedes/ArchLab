package br.com.archlab.simulation.core.v3;
public record TimelineActivityV3(IdentifierV3 activityId,IdentifierV3 parentActivityId,IdentifierV3 componentId,IdentifierV3 activationId,String kind,long startedAtMs,Long endedAtMs,String status,IdentifierV3 sourceTraceRecordId,IdentifierV3 endTraceRecordId){}
