package br.com.archlab.simulation.core.v2;

/** Common immutable activity contract for the v2 timeline. */
public sealed interface TimelineActivityV2 permits CommunicationActivityV2, ProcessingActivityV2 {
    String id();
    long startMs();
    long endMs();
    long durationMs();
    String kind();
}
