package br.com.archlab.simulation.core.v2;

import br.com.archlab.simulation.core.engine.SimulationTraceEntry;
import br.com.archlab.simulation.core.event.*;
import java.util.*;

/**
 * Pure trace projection. Consumes completed v2 execution histories; never mutates or
 * affects scheduling. Processing intervals and transport traversals partition virtual
 * time; waits and inclusive exchange spans overlap and are reported separately.
 */
public final class TimelineMetricsProjectorV2 {
    public Projection project(long totalDurationMs, List<SimulationTraceEntry> events,
            List<ExchangeTraceV2> exchanges, List<CorrelatedMessageV2> messages,
            List<ComponentTransitionV2> transitions) {
        List<TimelineActivityV2> activities = new ArrayList<>();
        Map<String, ExchangeTraceV2> byExchange = new HashMap<>();
        for (var e : exchanges) {
            if (e.completedAtMs() < e.startedAtMs())
                throw new IllegalArgumentException("Unfinished exchange: " + e.exchangeId());
            byExchange.put(e.exchangeId(), e);
        }

        Map<String, CorrelatedMessageV2> starts = new HashMap<>();
        int communicationSequence=0;
        for (var message : messages) {
            if ("SENT".equals(message.phase())) {
                if (starts.putIfAbsent(message.messageId(), message) != null)
                    throw new IllegalArgumentException("Duplicate message sent");
            } else if ("ARRIVED".equals(message.phase())) {
                var sent=starts.remove(message.messageId());
                if (sent==null || !sent.exchangeId().equals(message.exchangeId()))
                    throw new IllegalArgumentException("Unmatched message arrival");
                var exchange=Objects.requireNonNull(byExchange.get(message.exchangeId()));
                activities.add(new CommunicationActivityV2("communication-"+(++communicationSequence),
                        message.messageId(), message.exchangeId(), exchange.connectionId(),
                        sent.sourceId(), sent.targetId(), sent.timestampMs(), message.timestampMs(),
                        duration(sent.timestampMs(), message.timestampMs())));
            } else throw new IllegalArgumentException("Unknown message phase");
        }
        if (!starts.isEmpty()) throw new IllegalArgumentException("Unarrived messages");

        Map<String, Long> running=new HashMap<>();
        Map<String,Integer> processingOrdinal=new HashMap<>();
        int processingSequence=0;
        for (var entry:events) {
            if (entry.event() instanceof ProcessingStarted started) {
                if (running.putIfAbsent(started.componentId(),entry.timestampMs())!=null)
                    throw new IllegalArgumentException("Nested processing interval");
            } else if (entry.event() instanceof ProcessingCompleted finished) {
                Long began=running.remove(finished.componentId());
                if (began==null) throw new IllegalArgumentException("Processing complete without start");
                int ordinal=processingOrdinal.merge(finished.componentId(),1,Integer::sum);
                // Each API has PRE and POST; Database has one processing interval.
                boolean isDatabase=exchanges.stream().anyMatch(x->x.targetId().equals(finished.componentId())
                        && messages.stream().anyMatch(m->m.exchangeId().equals(x.exchangeId())
                        && "REQUEST".equals(m.direction())))
                        && !transitions.stream().anyMatch(t->t.componentId().equals(finished.componentId())
                        && t.to()==ComponentRuntimeStateV2.WAITING)
                        && ordinal==1 && !hasSecondStart(events, finished.componentId());
                String phase=isDatabase?"DATABASE_PROCESSING":(ordinal==1?"API_PRE_PROCESSING":"API_POST_PROCESSING");
                activities.add(new ProcessingActivityV2("processing-"+(++processingSequence),
                        finished.componentId(),finished.requestId(),phase,began,entry.timestampMs(),
                        duration(began,entry.timestampMs())));
            }
        }
        if (!running.isEmpty()) throw new IllegalArgumentException("Unfinished processing");

        activities.sort(Comparator.comparingLong(TimelineActivityV2::startMs)
                .thenComparingLong(TimelineActivityV2::endMs).thenComparing(TimelineActivityV2::id));

        Map<String,Long> own=new LinkedHashMap<>();
        Map<String,Long> network=new LinkedHashMap<>();
        Map<String,Integer> traversals=new LinkedHashMap<>();
        for(var a:activities) {
            if (a instanceof ProcessingActivityV2 p)
                own.merge(p.componentId(),p.durationMs(),Math::addExact);
            else if(a instanceof CommunicationActivityV2 c) {
                network.merge(c.connectionId(),c.durationMs(),Math::addExact);
                traversals.merge(c.connectionId(),1,Integer::sum);
            }
        }

        List<WaitingMetricV2> waits=new ArrayList<>();
        Map<String,ComponentTransitionV2> waitStarts=new HashMap<>();
        for(var t:transitions) {
            if (t.to()==ComponentRuntimeStateV2.WAITING
                    && t.from()==ComponentRuntimeStateV2.PROCESSING) {
                waitStarts.put(t.componentId(),t);
            } else if(t.from()==ComponentRuntimeStateV2.WAITING
                    && t.to()==ComponentRuntimeStateV2.PROCESSING) {
                var began=waitStarts.remove(t.componentId());
                if(began==null) throw new IllegalArgumentException("Wait exit without entry");
                var child=exchanges.stream().filter(x->x.sourceId().equals(t.componentId())
                        && x.startedAtMs()==began.timestampMs()).findFirst()
                        .orElseThrow(()->new IllegalArgumentException("Wait without child exchange"));
                waits.add(new WaitingMetricV2(t.componentId(),child.exchangeId(),
                        began.timestampMs(),t.timestampMs(),duration(began.timestampMs(),t.timestampMs())));
            }
        }
        if(!waitStarts.isEmpty())throw new IllegalArgumentException("Open component waits");

        List<ExchangeMetricV2> inclusive=exchanges.stream().map(e->new ExchangeMetricV2(
                e.exchangeId(),e.parentExchangeId(),e.sourceId(),e.targetId(),e.connectionId(),
                e.startedAtMs(),e.completedAtMs(),duration(e.startedAtMs(),e.completedAtMs()))).toList();
        List<ProcessingMetricV2> ownMetrics=own.entrySet().stream()
                .map(e->new ProcessingMetricV2(e.getKey(),e.getValue())).toList();
        List<CommunicationMetricV2> networkMetrics=network.entrySet().stream()
                .map(e->new CommunicationMetricV2(e.getKey(),traversals.get(e.getKey()),e.getValue())).toList();

        // Single synchronous request: processing and communication are disjoint
        // (except zero-duration boundaries); sum equals elapsed time.
        long exclusive=0;
        for (var a:activities) exclusive=Math.addExact(exclusive,a.durationMs());
        if(exclusive!=totalDurationMs)
            throw new IllegalStateException("Exclusive activities do not reconcile with total time: "+
                    exclusive+" != "+totalDurationMs);
        return new Projection(List.copyOf(activities),
                new SimulationMetricsV2(totalDurationMs,ownMetrics,networkMetrics,waits,inclusive));
    }

    private static boolean hasSecondStart(List<SimulationTraceEntry> events,String componentId) {
        long count=events.stream().filter(e->e.event() instanceof ProcessingStarted s
                && s.componentId().equals(componentId)).count();
        return count>1;
    }
    private static long duration(long start,long end) {
        if(start<0 || end<start)throw new IllegalArgumentException("Invalid time interval");
        return Math.subtractExact(end,start);
    }
    public record Projection(List<TimelineActivityV2> timeline, SimulationMetricsV2 metrics) {}
}
