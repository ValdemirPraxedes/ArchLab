package br.com.archlab.api.v2;

import br.com.archlab.simulation.core.engine.SimulationEngine;
import br.com.archlab.simulation.core.validation.ValidationResult;
import br.com.archlab.simulation.core.v2.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.util.*;

@RestController
@RequestMapping(path="/api/v2",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
public class V2Controller {
    private final V2RequestMapper mapper;
    private final V2ResponseMapper responses;
    private final ArchitectureGraphValidatorV2 graph=new ArchitectureGraphValidatorV2();
    private final ScenarioValidatorV2 scenarios=new ScenarioValidatorV2();
    private final SimulationEngine engine=new SimulationEngine();
    public V2Controller(V2RequestMapper mapper,V2ResponseMapper responses) {
        this.mapper=mapper;this.responses=responses;
    }
    @PostMapping("/architectures/validate")
    public ValidationResult validateArchitecture(@RequestBody JsonNode input) {
        return graph.validate(mapper.architecture(input));
    }
    @PostMapping("/scenarios/validate")
    public ValidationResult validateScenario(@RequestBody JsonNode input) {
        var request=mapper.scenarioRequest(input);
        return scenarios.validate(request.architecture(),request.scenario());
    }
    @PostMapping("/simulations")
    public Object simulate(@RequestBody JsonNode input,HttpServletRequest servletRequest) {
        var request=mapper.scenarioRequest(input);
        var valid=scenarios.validate(request.architecture(),request.scenario());
        if(!valid.valid())return invalid(valid,servletRequest);
        try {
            return responses.map(engine.execute(request.architecture(),request.scenario()),
                    request.architecture(),request.scenario());
        } catch(IllegalArgumentException ex) {
            var violation=new br.com.archlab.simulation.core.validation.Violation(
                    "INVALID_MESSAGE_TEMPLATE","architecture.connections", "Invalid illustrative integration template");
            return invalid(new ValidationResult(false,List.of(violation)),servletRequest);
        }
    }
    private ResponseEntity<Map<String,Object>> invalid(ValidationResult validation,HttpServletRequest request) {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("type","/problems/invalid-simulation");
        body.put("title","Simulação inválida");
        body.put("status",422);
        body.put("detail","A arquitetura, cenário ou representação é inválida.");
        body.put("instance",request.getRequestURI());
        body.put("code","SIMULATION_INVALID");
        body.put("violations",validation.violations());
        return ResponseEntity.status(422).contentType(MediaType.valueOf("application/problem+json")).body(body);
    }
}
