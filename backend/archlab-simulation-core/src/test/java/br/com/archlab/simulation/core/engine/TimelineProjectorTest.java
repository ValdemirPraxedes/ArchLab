package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TimelineProjectorTest {
    private static Architecture architecture(long client, long db, long pre, long post, long dbProcessing) {
        return new Architecture(List.of(new ClientComponent("client-1"),
                new ApiComponent("api-1", pre, post), new DatabaseComponent("db-1", dbProcessing)),
                List.of(new Connection("connection-1", "client-1", "api-1", Protocol.REST, client),
                        new Connection("connection-2", "api-1", "db-1", Protocol.TCP, db)));
    }

    @Test void matchesVersioned260msFixtureActivitiesAndMetrics() {
        SimulationResult result = new SimulationEngine().execute(architecture(20, 10, 30, 20, 150));
        assertEquals(260, result.totalDurationMs());
        assertEquals(7, result.timeline().size());
        assertEquals(List.of(0L, 20L, 50L, 60L, 210L, 220L, 240L),
                result.timeline().stream().map(TimelineActivity::startMs).toList());
        assertEquals(List.of(20L, 50L, 60L, 210L, 220L, 240L, 260L),
                result.timeline().stream().map(TimelineActivity::endMs).toList());
        assertEquals(List.of(20L, 30L, 10L, 150L, 10L, 20L, 20L),
                result.timeline().stream().map(TimelineActivity::durationMs).toList());
        assertEquals(List.of(TimelineActivity.Kind.COMMUNICATION, TimelineActivity.Kind.PROCESSING,
                TimelineActivity.Kind.COMMUNICATION, TimelineActivity.Kind.PROCESSING,
                TimelineActivity.Kind.COMMUNICATION, TimelineActivity.Kind.PROCESSING,
                TimelineActivity.Kind.COMMUNICATION),
                result.timeline().stream().map(TimelineActivity::kind).toList());
        assertEquals(List.of("TRANSPORT", "API_PRE_PROCESSING", "TRANSPORT", "DATABASE_PROCESSING",
                "TRANSPORT", "API_POST_PROCESSING", "TRANSPORT"),
                result.timeline().stream().map(TimelineActivity::phase).toList());
        assertEquals(new SimulationMetrics(30, 20, 150, 60, 170), result.metrics());
        assertEquals(260, result.metrics().nonOverlappingTotalMs());
    }

    @Test void zeroLatencyAndDurationHaveStableTimeline() {
        Architecture architecture = architecture(0, 0, 0, 0, 0);
        SimulationResult result = new SimulationEngine().execute(architecture);
        assertEquals(7, result.timeline().size());
        assertEquals(0, result.totalDurationMs());
        assertEquals(new SimulationMetrics(0, 0, 0, 0, 0), result.metrics());
        assertEquals(result, new SimulationEngine().execute(architecture));
    }

    @Test void metricTotalsScaleWithDurationsWithoutDoubleCountingWaiting() {
        SimulationResult result = new SimulationEngine().execute(architecture(2, 3, 4, 5, 6));
        assertEquals(27, result.totalDurationMs());
        assertEquals(12, result.apiWaitingDurationMs());
        assertEquals(new SimulationMetrics(4, 5, 6, 12, 12), result.metrics());
        assertEquals(27, result.metrics().nonOverlappingTotalMs());
    }

    @Test void rejectsNonReconcilingAndInvalidIntervals() {
        assertThrows(IllegalArgumentException.class,
                () -> new TimelineActivity(TimelineActivity.Kind.PROCESSING, "api", "PRE", 10, 9, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationMetrics(0, 0, -1, 0, 0));
    }

    @Test void timelineCannotBeMutated() {
        SimulationResult result = new SimulationEngine().execute(architecture(20, 10, 30, 20, 150));
        assertThrows(UnsupportedOperationException.class, () -> result.timeline().clear());
    }
}
