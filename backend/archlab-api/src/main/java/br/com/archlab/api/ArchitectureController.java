package br.com.archlab.api;

import br.com.archlab.api.architecture.ArchitectureRequestMapper;
import br.com.archlab.api.simulation.SimulationResponseMapper;
import br.com.archlab.api.v2.V2RequestMapper;
import br.com.archlab.api.v2.V2ResponseMapper;
import br.com.archlab.simulation.core.domain.Architecture;
import br.com.archlab.simulation.core.engine.SimulationEngine;
import br.com.archlab.simulation.core.validation.ArchitectureValidator;
import br.com.archlab.simulation.core.validation.ValidationResult;
import br.com.archlab.simulation.core.validation.Violation;
import br.com.archlab.simulation.core.v2.ArchitectureGraphValidatorV2;
import br.com.archlab.simulation.core.v2.ScenarioValidatorV2;
import br.com.archlab.simulation.core.v2.WebSocketIntegrationV2;
import br.com.archlab.simulation.core.v4.WebSocketRuntimeV4;
import br.com.archlab.simulation.core.v4.BrokerRuntimeV4;
import br.com.archlab.simulation.core.v2.BrokerV2;
import br.com.archlab.simulation.core.v41.UnifiedSimulationV41;
import tools.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping(path = "/api/v1", consumes = MediaType.APPLICATION_JSON_VALUE)
public class ArchitectureController {
    private final ArchitectureRequestMapper mapper;
    private final SimulationResponseMapper responseMapper;
    private final V2RequestMapper canonicalMapper;
    private final V2ResponseMapper canonicalResponses;
    private final ArchitectureMigrationServiceV6 migrationV6;
    private final ArchitectureValidator validator = new ArchitectureValidator();
    private final ArchitectureGraphValidatorV2 canonicalGraph = new ArchitectureGraphValidatorV2();
    private final ScenarioValidatorV2 canonicalScenarios = new ScenarioValidatorV2();
    private final CanonicalScenarioValidator newScenarios = new CanonicalScenarioValidator();
    private final SimulationEngine engine = new SimulationEngine();

    public ArchitectureController(ArchitectureRequestMapper mapper, SimulationResponseMapper responseMapper,
                                  V2RequestMapper canonicalMapper, V2ResponseMapper canonicalResponses,
                                  ArchitectureMigrationServiceV6 migrationV6) {
        this.mapper = mapper;
        this.responseMapper = responseMapper;
        this.canonicalMapper = canonicalMapper;
        this.canonicalResponses = canonicalResponses;
        this.migrationV6 = migrationV6;
    }

    @PostMapping(path = "/architectures/migrate-v6", produces = MediaType.APPLICATION_JSON_VALUE)
    public ArchitectureMigrationServiceV6.Result migrateV6(@RequestBody JsonNode input) {
        return migrationV6.migrate(input);
    }

    @PostMapping(path = "/architectures/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object validate(@RequestBody JsonNode input) {
        if (legacy(input)) return validator.validate(mapper.toDomain(input));
        var architecture=canonicalMapper.canonicalArchitecture(input);
        ValidationResult graph=canonicalGraph.validate(architecture);
        if (!graph.valid()) return graph;
        return UnifiedPlanV41.parseArchitecture(input,architecture).validateArchitecture();
    }

    @PostMapping(path = "/scenarios/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ValidationResult validateScenario(@RequestBody JsonNode input) {
        JsonNode scenario = input == null ? null : input.get("scenario");
        if (scenario != null && scenario.get("entry") != null) return newScenarios.validateUnified(input,canonicalMapper);
        JsonNode kind = scenario == null ? null : scenario.get("kind");
        if (kind != null && kind.isTextual() && !"REQUEST_RESPONSE".equals(kind.asText()))
            return newScenarios.validate(input, canonicalMapper);
        var request = canonicalMapper.canonicalRequest(input);
        return canonicalScenarios.validate(request.architecture(), request.scenario());
    }

    @PostMapping(path = "/simulations", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object simulate(@RequestBody JsonNode input, HttpServletRequest httpRequest) {
        if (legacy(input)) {
            Architecture architecture = mapper.toDomain(input);
            return responseMapper.map(engine.execute(architecture));
        }
        JsonNode scenarioNode = input == null ? null : input.get("scenario");
        if (scenarioNode != null && scenarioNode.get("entry") != null) {
            ValidationResult validation=newScenarios.validateUnified(input,canonicalMapper);
            if (!validation.valid()) return invalid(validation,httpRequest);
            try {
                var architecture=canonicalMapper.canonicalArchitecture(input.get("architecture"));
                return new UnifiedSimulationV41(UnifiedPlanV41.parse(input,architecture).toExecutionPlan()).run();
            } catch (ArithmeticException | IllegalArgumentException ex) {
                return invalid(new ValidationResult(false,List.of(new Violation(
                        "EXECUTION_LIMIT_EXCEEDED","scenario",ex.getMessage()))),httpRequest);
            }
        }
        JsonNode kindNode = scenarioNode == null ? null : scenarioNode.get("kind");
        if (kindNode != null && "WEBSOCKET_SESSION".equals(kindNode.asText())) {
            ValidationResult validation = newScenarios.validate(input, canonicalMapper);
            if (!validation.valid()) return invalid(validation, httpRequest);
            var architecture = canonicalMapper.canonicalArchitecture(input.get("architecture"));
            String entryId = scenarioNode.get("entryConnectionId").asText();
            var connection = architecture.connections().stream().filter(c -> entryId.equals(c.id())).findFirst().orElseThrow();
            try {
                var result = new WebSocketRuntimeV4().run((WebSocketIntegrationV2) connection.integration(),
                        connection.latencyMs());
                return new WebSocketResponseMapperV4().map(architecture, connection,
                        (WebSocketIntegrationV2) connection.integration(),result);
            } catch (ArithmeticException | IllegalArgumentException ex) {
                return invalid(new ValidationResult(false, List.of(new Violation(
                        "EXCESSIVE_EXECUTION_DURATION", "execution.totalDurationMs",
                        "WebSocket execution exceeds virtual limits"))), httpRequest);
            }
        }
        if (kindNode != null && "MESSAGE_PUBLISH".equals(kindNode.asText())) {
            ValidationResult validation = newScenarios.validate(input, canonicalMapper);
            if (!validation.valid()) return invalid(validation, httpRequest);
            var architecture = canonicalMapper.canonicalArchitecture(input.get("architecture"));
            String entryId = scenarioNode.get("entryConnectionId").asText();
            var connection = architecture.connections().stream().filter(c -> entryId.equals(c.id())).findFirst().orElseThrow();
            var broker = architecture.components().stream().filter(c -> c.id().equals(connection.targetId()))
                    .map(c -> (BrokerV2)c).findFirst().orElseThrow();
            int publishCount = scenarioNode.get("publishCount") == null ? 1 : scenarioNode.get("publishCount").asInt();
            try {
                var result = new BrokerRuntimeV4().run(architecture, connection, broker,
                        scenarioNode.get("destinationName").asText(), publishCount);
                return new BrokerResponseMapperV4().map(architecture, connection, result);
            } catch (ArithmeticException | IllegalArgumentException ex) {
                return invalid(new ValidationResult(false, List.of(new Violation(
                        "EXCESSIVE_EXECUTION_DURATION", "execution.totalDurationMs",
                        "Broker execution exceeds virtual limits"))), httpRequest);
            }
        }
        var request = canonicalMapper.canonicalRequest(input);
        var valid = canonicalScenarios.validate(request.architecture(), request.scenario());
        if (!valid.valid()) return invalid(valid, httpRequest);
        br.com.archlab.simulation.core.v2.NestedExecutionResultV2 executed;
        try {
            executed = engine.execute(request.architecture(), request.scenario());
        } catch (IllegalArgumentException ex) {
            return invalid(new ValidationResult(false, List.of(new Violation(
                    "INVALID_MESSAGE_TEMPLATE", "architecture.connections",
                    "Invalid illustrative integration template"))), httpRequest);
        }
        if (executed.totalDurationMs() > 3_600_000L) {
            return invalid(new ValidationResult(false, List.of(new Violation(
                    "EXCESSIVE_EXECUTION_DURATION", "execution.totalDurationMs",
                    "Execution exceeds the 3600000 ms virtual duration limit"))), httpRequest);
        }
        Map<String,Object> result = canonicalResponses.map(executed,
                request.architecture(), request.scenario());
        @SuppressWarnings("unchecked") Map<String,Object> execution = (Map<String,Object>) result.get("execution");
        execution.put("mainStatus", "COMPLETED");
        @SuppressWarnings("unchecked") List<Map<String,Object>> representations =
                (List<Map<String,Object>>) result.get("messageRepresentations");
        for (Map<String,Object> representation : representations) {
            @SuppressWarnings("unchecked") Map<String,Object> integration =
                    (Map<String,Object>) representation.get("integration");
            if (integration.get("request") instanceof br.com.archlab.simulation.core.v2.HttpRequestV2 requestBody) {
                Map<String,Object> projected = new LinkedHashMap<>();
                projected.put("method", requestBody.method().name());
                projected.put("path", requestBody.path());
                if (requestBody.headers() != null) projected.put("headers", requestBody.headers());
                if (requestBody.body() != null) projected.put("body", requestBody.body());
                integration.put("request", projected);
            }
            if (integration.get("response") instanceof br.com.archlab.simulation.core.v2.HttpResponseV2 responseBody) {
                Map<String,Object> projected = new LinkedHashMap<>();
                projected.put("statusCode", responseBody.statusCode());
                if (responseBody.headers() != null) projected.put("headers", responseBody.headers());
                if (responseBody.body() != null) projected.put("body", responseBody.body());
                integration.put("response", projected);
            }
            stripOptionalNulls(integration);
        }
        return result;
    }

    private static void stripOptionalNulls(Map<String,Object> fields) {
        fields.values().removeIf(java.util.Objects::isNull);
        for (Object value : fields.values()) {
            if (value instanceof Map<?,?> nested) {
                @SuppressWarnings("unchecked") Map<String,Object> typed = (Map<String,Object>) nested;
                stripOptionalNulls(typed);
            }
        }
    }


    private static boolean legacy(JsonNode input) {
        if (input == null || !input.isObject() || input.get("architecture") != null) return false;
        JsonNode connections = input.get("connections");
        if (connections != null && connections.isArray()) {
            for (JsonNode connection : connections) {
                if (connection.get("protocol") != null) return true;
                if (connection.get("integration") != null) return false;
            }
        }
        JsonNode components = input.get("components");
        if (components != null && components.isArray()) {
            for (JsonNode component : components) if (component.get("displayName") != null) return false;
        }
        return true;
    }

    private static ResponseEntity<Map<String,Object>> invalid(ValidationResult validation, HttpServletRequest request) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("type", "/problems/invalid-simulation");
        body.put("title", "Simulação inválida");
        body.put("status", 422);
        body.put("detail", "A arquitetura ou cenário é inválido.");
        body.put("instance", request.getRequestURI());
        body.put("code", "SIMULATION_INVALID");
        body.put("violations", validation.violations());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.valueOf("application/problem+json")).body(body);
    }
}
