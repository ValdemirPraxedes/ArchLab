package br.com.archlab.api.architecture;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class LegacyApiMigrationV5Test {
    private final JsonMapper json = new JsonMapper();

    @Test void convertsOnlyLegacyApiAndPreservesTheOriginal() {
        var input = json.readTree("""
            {"architecture":{"components":[
              {"id":"old-1","type":"API","behaviors":[{"id":"b","actions":[{"id":"a"}]}]},
              {"id":"gateway","type":"API_GATEWAY"},
              {"id":"old-2","type":"API","preProcessingMs":7}],
              "connections":[{"id":"edge","sourceId":"old-1","targetId":"old-2"}]}}
            """);
        var original = input.toString();
        var result = LegacyApiMigrationV5.migrate(input);
        assertEquals(2, result.report().convertedCount());
        assertEquals(java.util.List.of("old-1", "old-2"), result.report().componentIds());
        var components = result.document().get("architecture").get("components");
        assertEquals("SERVICE", components.get(0).get("type").asText());
        assertEquals("API_GATEWAY", components.get(1).get("type").asText());
        assertEquals(7, components.get(2).get("preProcessingMs").asInt());
        assertEquals("b", components.get(0).get("behaviors").get(0).get("id").asText());
        assertEquals("edge", result.document().get("architecture").get("connections").get(0).get("id").asText());
        assertEquals(original, input.toString());
    }

    @Test void invalidInputLeavesOriginalUntouched() {
        var input = json.readTree("""
            {"components":[{"id":"a","type":"API"},{"id":"bad"}],"connections":[]}
            """);
        var original = input.toString();
        assertThrows(IllegalArgumentException.class, () -> LegacyApiMigrationV5.migrate(input));
        assertEquals(original, input.toString());
    }
}
