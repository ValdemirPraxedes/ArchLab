package br.com.archlab.api;

import br.com.archlab.api.architecture.ArchitectureRequestMapper;
import br.com.archlab.api.simulation.SimulationResponseMapper;
import br.com.archlab.simulation.core.domain.Architecture;
import br.com.archlab.simulation.core.engine.SimulationEngine;
import br.com.archlab.simulation.core.validation.ArchitectureValidator;
import tools.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping(path = "/api/v1", consumes = MediaType.APPLICATION_JSON_VALUE)
public class ArchitectureController {
    private final ArchitectureRequestMapper mapper;
    private final SimulationResponseMapper responseMapper;
    private final ArchitectureValidator validator = new ArchitectureValidator();
    private final SimulationEngine engine = new SimulationEngine();

    public ArchitectureController(ArchitectureRequestMapper mapper, SimulationResponseMapper responseMapper) {
        this.mapper = mapper;
        this.responseMapper = responseMapper;
    }

    @PostMapping(path = "/architectures/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object validate(@RequestBody JsonNode input) {
        Architecture architecture = mapper.toDomain(input);
        return validator.validate(architecture);
    }

    @PostMapping(path = "/simulations", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> simulate(@RequestBody JsonNode input) {
        Architecture architecture = mapper.toDomain(input);
        return responseMapper.map(engine.execute(architecture));
    }
}
