package br.com.archlab.simulation.core.v2;
import java.util.List;
/** Inclusive/waiting durations are diagnostic: they must not be added to totalDurationMs. */
public record SimulationMetricsV2(long totalDurationMs,
        List<ProcessingMetricV2> processingByComponent,
        List<CommunicationMetricV2> communicationByConnection,
        List<WaitingMetricV2> waitingByComponent,
        List<ExchangeMetricV2> exchanges) {
    public SimulationMetricsV2 {
        processingByComponent=List.copyOf(processingByComponent);
        communicationByConnection=List.copyOf(communicationByConnection);
        waitingByComponent=List.copyOf(waitingByComponent);
        exchanges=List.copyOf(exchanges);
    }
}
