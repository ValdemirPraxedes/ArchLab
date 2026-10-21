package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.domain.*;
import br.com.archlab.simulation.core.event.*;
import br.com.archlab.simulation.core.transport.*;
import java.util.*;

/** Reconstructs ordered, non-overlapping activities from virtual-time event and message traces. */
public final class TimelineProjector {
    public List<TimelineActivity> project(Architecture architecture, List<SimulationTraceEntry> eventTrace,
                                          List<MessageTraceEntry> messageTrace) {
        Objects.requireNonNull(architecture);
        Objects.requireNonNull(eventTrace);
        Objects.requireNonNull(messageTrace);
        String apiId = architecture.components().stream().filter(c -> c instanceof ApiComponent)
                .findFirst().orElseThrow().id();
        String dbId = architecture.components().stream().filter(c -> c instanceof DatabaseComponent)
                .findFirst().orElseThrow().id();
        List<TimelineActivity> activities = new ArrayList<>();
        Map<String, MessageTraceEntry> sends = new HashMap<>();
        Map<String, Long> processingStart = new HashMap<>();
        int apiPhases = 0;
        long postStartedAt = -1;
        for (MessageTraceEntry entry : messageTrace) {
            String id = entry.message().id();
            if (entry.action() == MessageTraceEntry.Action.SENT) {
                if (sends.putIfAbsent(id, entry) != null) throw new IllegalStateException("Duplicate send: " + id);
            } else {
                MessageTraceEntry send = sends.remove(id);
                if (send == null || !send.connectionId().equals(entry.connectionId())
                        || !send.message().equals(entry.message())) {
                    throw new IllegalStateException("Unmatched message arrival: " + id);
                }
                activities.add(activity(TimelineActivity.Kind.COMMUNICATION, send.connectionId(), "TRANSPORT",
                        send.timestampMs(), entry.timestampMs()));
                if (entry.message().direction() == MessageDirection.RESPONSE
                        && entry.message().intent() == MessageIntent.DATABASE_QUERY) {
                    postStartedAt = entry.timestampMs();
                }
            }
        }
        if (!sends.isEmpty()) throw new IllegalStateException("Messages left in transit");

        for (SimulationTraceEntry entry : eventTrace) {
            SimulationEvent event = entry.event();
            if (event instanceof ProcessingStarted started) {
                if (processingStart.putIfAbsent(started.componentId(), entry.timestampMs()) != null)
                    throw new IllegalStateException("Duplicate processing start");
            } else if (event instanceof ProcessingCompleted completed) {
                Long start = processingStart.remove(completed.componentId());
                if (start == null) throw new IllegalStateException("Processing completed without start");
                String phase = completed.componentId().equals(dbId) ? "DATABASE_PROCESSING" : "API_PRE_PROCESSING";
                if (completed.componentId().equals(apiId)) apiPhases++;
                activities.add(activity(TimelineActivity.Kind.PROCESSING, completed.componentId(), phase,
                        start, entry.timestampMs()));
            } else if (event instanceof ResponseSent sent && sent.sourceId().equals(apiId)) {
                if (postStartedAt < 0) throw new IllegalStateException("API post-processing has no DB response");
                activities.add(activity(TimelineActivity.Kind.PROCESSING, apiId, "API_POST_PROCESSING",
                        postStartedAt, entry.timestampMs()));
                postStartedAt = -1;
                apiPhases++;
            }
        }
        if (!processingStart.isEmpty() || postStartedAt != -1 || apiPhases != 2)
            throw new IllegalStateException("Incomplete processing trace");
        activities.sort(Comparator.comparingLong(TimelineActivity::startMs)
                .thenComparingLong(TimelineActivity::endMs));
        long end = 0;
        for (TimelineActivity activity : activities) {
            if (activity.startMs() < end) throw new IllegalStateException("Overlapping timeline activities");
            end = activity.endMs();
        }
        return List.copyOf(activities);
    }

    public SimulationMetrics metrics(List<TimelineActivity> timeline, long apiWaitMs) {
        long pre = 0, post = 0, db = 0, communication = 0;
        for (TimelineActivity activity : timeline) {
            if (activity.kind() == TimelineActivity.Kind.COMMUNICATION) {
                communication = Math.addExact(communication, activity.durationMs());
            } else {
                switch (activity.phase()) {
                    case "API_PRE_PROCESSING" -> pre = Math.addExact(pre, activity.durationMs());
                    case "API_POST_PROCESSING" -> post = Math.addExact(post, activity.durationMs());
                    case "DATABASE_PROCESSING" -> db = Math.addExact(db, activity.durationMs());
                    default -> throw new IllegalStateException("Unknown phase: " + activity.phase());
                }
            }
        }
        return new SimulationMetrics(pre, post, db, communication, apiWaitMs);
    }

    private static TimelineActivity activity(TimelineActivity.Kind kind, String entity,
                                             String phase, long start, long end) {
        return new TimelineActivity(kind, entity, phase, start, end, Math.subtractExact(end, start));
    }
}
