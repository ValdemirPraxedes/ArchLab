package br.com.archlab.api;

import br.com.archlab.api.architecture.LegacyApiMigrationV5;
import br.com.archlab.api.v2.V2RequestMapper;
import br.com.archlab.simulation.core.validation.Violation;
import br.com.archlab.simulation.core.v2.ArchitectureGraphValidatorV2;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.util.List;

/** Explicit import operation; no conversion occurs during ordinary validation or simulation. */
@Service
public final class ArchitectureMigrationServiceV5 {
    private final V2RequestMapper mapper;
    private final ArchitectureGraphValidatorV2 graph=new ArchitectureGraphValidatorV2();

    public ArchitectureMigrationServiceV5(V2RequestMapper mapper) {this.mapper=mapper;}

    public static final class MigrationException extends IllegalArgumentException {
        private final List<Violation> violations;
        public MigrationException(List<Violation> violations) {
            super(violations.isEmpty()?"Invalid migrated architecture":violations.get(0).path());
            this.violations=List.copyOf(violations);
        }
        public List<Violation> violations() {return violations;}
    }

    public LegacyApiMigrationV5.Result migrate(JsonNode input) {
        LegacyApiMigrationV5.Result result=LegacyApiMigrationV5.migrate(input);
        JsonNode architectureNode=result.document().has("architecture")
                ?result.document().get("architecture"):result.document();
        var architecture=mapper.canonicalArchitecture(architectureNode);
        var graphResult=graph.validate(architecture);
        if (!graphResult.valid()) throw new MigrationException(graphResult.violations());
        var behaviorResult=UnifiedPlanV41.parseArchitecture(architectureNode,architecture).validateArchitecture();
        if (!behaviorResult.valid()) throw new MigrationException(behaviorResult.violations());
        return result;
    }
}
