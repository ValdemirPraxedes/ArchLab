package br.com.archlab.api;
import br.com.archlab.api.v3.ArchitectureRequestMapperV3;
import br.com.archlab.simulation.core.v3.*;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ArchitectureRequestMapperV3Test {
    private final JsonMapper json=JsonMapper.builder().build();
    private final ArchitectureRequestMapperV3 mapper=new ArchitectureRequestMapperV3();
    @Test void parsesRepositoryFixtureAndValidatesRealGraph() throws Exception {
        var file=Path.of("../../docs/examples/v3/architecture-simple.json");
        var a=mapper.architecture(json.readTree(Files.readString(file)));
        assertEquals(2,a.components().size());assertEquals(2,a.executionPlans().getFirst().steps().size());
        var result=new ArchitectureValidatorV3(TypeCatalogV3.initial()).validate(a);assertTrue(result.valid(),result.toString());
    }
    @ParameterizedTest @ValueSource(strings={
        "{\"kind\":\"STRING\",\"value\":\"\"}","{\"kind\":\"INTEGER\",\"value\":9223372036854775807}",
        "{\"kind\":\"DECIMAL\",\"value\":\"-1.000\"}","{\"kind\":\"BOOLEAN\",\"value\":false}",
        "{\"kind\":\"ENUM\",\"value\":\"FIFO\"}","{\"kind\":\"REFERENCE\",\"targetId\":\"x\",\"expectedTypeId\":\"API_SERVICE\"}",
        "{\"kind\":\"LIST\",\"values\":[{\"kind\":\"INTEGER\",\"value\":1}]}","{\"kind\":\"OBJECT\",\"fields\":{\"a\":{\"kind\":\"BOOLEAN\",\"value\":true}}}"})
    void parsesAllEightPropertyVariants(String input){assertNotNull(mapper.property(json.readTree(input)));}
    @ParameterizedTest @ValueSource(strings={
        "{\"kind\":\"INTEGER\",\"value\":1.5}","{\"kind\":\"INTEGER\",\"value\":9223372036854775808}",
        "{\"kind\":\"DECIMAL\",\"value\":1.1}","{\"kind\":\"BOOLEAN\",\"value\":\"true\"}",
        "{\"kind\":\"STRING\",\"value\":null}","{\"kind\":\"STRING\",\"value\":\"x\",\"extra\":true}",
        "{\"kind\":\"REFERENCE\",\"targetId\":\"x\"}","{\"kind\":\"OTHER\",\"value\":1}","[]"})
    void rejectsInvalidPropertyShapes(String input){assertThrows(IllegalArgumentException.class,()->mapper.property(json.readTree(input)));}
    @ParameterizedTest @ValueSource(strings={"{\"kind\":\"COMPONENT\",\"componentId\":\"x\"}","{\"kind\":\"RESOURCE\",\"resourceId\":\"x\",\"available\":false}","{\"kind\":\"POLICY\",\"policyInstanceId\":\"x\",\"state\":\"OPEN\"}","{\"kind\":\"QUEUE\",\"queueId\":\"x\",\"initialMessageCount\":0}"})
    void parsesFourInitialStateVariants(String input){assertNotNull(mapper.initialState(json.readTree(input)));}
    @Test void rejectsUnknownArchitectureFieldsAndBadOptionalFields() throws Exception {
        var fixture=Files.readString(Path.of("../../docs/examples/v3/architecture-simple.json"));
        assertThrows(IllegalArgumentException.class,()->mapper.architecture(json.readTree(fixture.replace("\"arch-simple\"","\"arch-simple\",\"extra\":true"))));
        assertThrows(IllegalArgumentException.class,()->mapper.initialState(json.readTree("{\"kind\":\"COMPONENT\",\"componentId\":\"x\",\"available\":null}")));
        assertThrows(IllegalArgumentException.class,()->mapper.initialState(json.readTree("{\"kind\":\"QUEUE\",\"queueId\":\"x\",\"initialMessageCount\":-1}")));
    }
}
