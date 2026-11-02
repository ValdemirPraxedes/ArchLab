package br.com.archlab.api;
import br.com.archlab.api.v3.ArchitectureRequestMapperV3;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class ExecutionPlanDecoderV3Test {
    private final ArchitectureRequestMapperV3 mapper=new ArchitectureRequestMapperV3();
    private final JsonMapper json=JsonMapper.builder().build();
    @ParameterizedTest @ValueSource(strings={
        "{\"id\":\"s\",\"kind\":\"PROCESS\",\"durationMs\":0,\"transitions\":{\"NEXT\":\"done\"}}",
        "{\"id\":\"s\",\"kind\":\"CALL\",\"connectionId\":\"c\",\"targetOperationId\":\"o\",\"awaitMode\":\"RESPONSE\",\"transitions\":{\"SUCCESS\":\"done\"}}",
        "{\"id\":\"s\",\"kind\":\"PUBLISH\",\"targetResourceId\":\"q\",\"awaitMode\":\"BROKER_ACK\",\"transitions\":{\"ACCEPTED\":\"done\"}}",
        "{\"id\":\"s\",\"kind\":\"CONDITION\",\"expression\":{\"op\":\"EQ\",\"left\":{\"kind\":\"LITERAL\",\"value\":null},\"right\":{\"kind\":\"REFERENCE\",\"path\":\"input.value\"}},\"onTrue\":\"a\",\"onFalse\":\"b\",\"onError\":\"e\"}",
        "{\"id\":\"s\",\"kind\":\"FORK\",\"branches\":[{\"id\":\"a\",\"entryStepId\":\"x\"},{\"id\":\"b\",\"entryStepId\":\"y\"}],\"joinStepId\":\"j\"}",
        "{\"id\":\"s\",\"kind\":\"JOIN\",\"policy\":\"QUORUM\",\"quorum\":1,\"requiredBranchIds\":[\"a\"],\"remainingPolicy\":\"CONTINUE\",\"transitions\":{\"SATISFIED\":\"done\"}}",
        "{\"id\":\"s\",\"kind\":\"DELAY\",\"durationMs\":0,\"nextStepId\":\"done\"}",
        "{\"id\":\"s\",\"kind\":\"COMPLETE\",\"scope\":\"ACTIVATION\",\"resultProfileId\":\"ok\"}",
        "{\"id\":\"s\",\"kind\":\"FAIL\",\"scope\":\"BRANCH\",\"errorProfileId\":\"error\"}"})
    void parsesAllNineStepVariants(String input){assertNotNull(mapper.step(json.readTree(input)));}
    @ParameterizedTest @ValueSource(strings={
        "{\"id\":\"s\",\"kind\":\"PROCESS\",\"durationMs\":-1,\"transitions\":{\"NEXT\":\"done\"}}",
        "{\"id\":\"s\",\"kind\":\"CALL\",\"connectionId\":\"c\",\"targetOperationId\":\"o\",\"awaitMode\":\"BROKER_ACK\",\"transitions\":{\"SUCCESS\":\"done\"}}",
        "{\"id\":\"s\",\"kind\":\"PUBLISH\",\"targetResourceId\":\"q\",\"awaitMode\":\"NONE\",\"transitions\":{}}",
        "{\"id\":\"s\",\"kind\":\"CONDITION\",\"expression\":{\"op\":\"RUN_CODE\"},\"onTrue\":\"a\",\"onFalse\":\"b\",\"onError\":\"e\"}",
        "{\"id\":\"s\",\"kind\":\"FORK\",\"branches\":[{\"id\":\"a\",\"entryStepId\":\"x\"}],\"joinStepId\":\"j\"}",
        "{\"id\":\"s\",\"kind\":\"JOIN\",\"policy\":\"ALL\",\"quorum\":1,\"requiredBranchIds\":[\"a\"],\"remainingPolicy\":\"CONTINUE\",\"transitions\":{\"SATISFIED\":\"done\"}}",
        "{\"id\":\"s\",\"kind\":\"DELAY\",\"durationMs\":0}",
        "{\"id\":\"s\",\"kind\":\"COMPLETE\",\"scope\":\"OTHER\",\"resultProfileId\":\"ok\"}",
        "{\"id\":\"s\",\"kind\":\"FAIL\",\"scope\":\"BRANCH\",\"errorProfileId\":\"error\",\"nextStepId\":\"x\"}"})
    void rejectsAnInvalidShapeForEveryStep(String input){assertThrows(IllegalArgumentException.class,()->mapper.step(json.readTree(input)));}
}
