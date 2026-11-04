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
    private final ArchitectureValidator validator = new ArchitectureValidator();
    private final ArchitectureGraphValidatorV2 canonicalGraph = new ArchitectureGraphValidatorV2();
    private final ScenarioValidatorV2 canonicalScenarios = new ScenarioValidatorV2();
    private final SimulationEngine engine = new SimulationEngine();

    public ArchitectureController(ArchitectureRequestMapper mapper, SimulationResponseMapper responseMapper,
                                  V2RequestMapper canonicalMapper, V2ResponseMapper canonicalResponses) {
        this.mapper = mapper;
        this.responseMapper = responseMapper;
        this.canonicalMapper = canonicalMapper;
        this.canonicalResponses = canonicalResponses;
    }

    @PostMapping(path = "/architectures/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object validate(@RequestBody JsonNode input) {
        if (legacy(input)) return validator.validate(mapper.toDomain(input));
        return canonicalGraph.validate(canonicalMapper.canonicalArchitecture(input));
    }

    @PostMapping(path = "/scenarios/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ValidationResult validateScenario(@RequestBody JsonNode input) {
        var request = canonicalMapper.canonicalRequest(input);
        return canonicalScenarios.validate(request.architecture(), request.scenario());
    }

    @PostMapping(path = "/simulations", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object simulate(@RequestBody JsonNode input, HttpServletRequest httpRequest) {
        if (legacy(input)) {
            Architecture architecture = mapper.toDomain(input);
            return responseMapper.map(engine.execute(architecture));
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
            integration.values().removeIf(java.util.Objects::isNull);
        }
        return result;
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
