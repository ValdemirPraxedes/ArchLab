package br.com.archlab.api;

import br.com.archlab.api.architecture.LegacyApiMigrationV5;
import br.com.archlab.api.architecture.LegacyDatabaseMigrationV6;
import br.com.archlab.api.v2.V2RequestMapper;
import br.com.archlab.simulation.core.v2.ArchitectureGraphValidatorV2;
import br.com.archlab.simulation.core.validation.Violation;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.util.List;

/** Explicit migration preview; ordinary validation and simulation never invoke it. */
@Service
public final class ArchitectureMigrationServiceV6 {
    private final V2RequestMapper mapper;
    private final ArchitectureGraphValidatorV2 graph=new ArchitectureGraphValidatorV2();
    public ArchitectureMigrationServiceV6(V2RequestMapper mapper) {this.mapper=mapper;}

    public record Report(List<String> apiIds,List<String> databaseIds,List<String> notes) {
        public Report {apiIds=List.copyOf(apiIds);databaseIds=List.copyOf(databaseIds);notes=List.copyOf(notes);}
    }
    public record Result(JsonNode document,Report report) {}
    public static final class MigrationException extends IllegalArgumentException {
        private final List<Violation> violations;
        public MigrationException(List<Violation> violations) {
            super(violations.isEmpty()?"Invalid migrated architecture":violations.get(0).path());
            this.violations=List.copyOf(violations);
        }
        public List<Violation> violations() {return violations;}
    }

    public Result migrate(JsonNode input) {
        LegacyApiMigrationV5.Result api=LegacyApiMigrationV5.migrate(input);
        LegacyDatabaseMigrationV6.Result database=LegacyDatabaseMigrationV6.migrate(api.document());
        JsonNode architectureNode=database.document().has("architecture")
                ?database.document().get("architecture"):database.document();
        var architecture=mapper.canonicalArchitecture(architectureNode);
        var graphResult=graph.validate(architecture);
        if (!graphResult.valid()) throw new MigrationException(graphResult.violations());
        var behaviorResult=UnifiedPlanV41.parseArchitecture(architectureNode,architecture).validateArchitecture();
        if (!behaviorResult.valid()) throw new MigrationException(behaviorResult.violations());
        return new Result(database.document(),new Report(api.report().componentIds(),
                database.report().componentIds(),database.report().notes()));
    }
}
