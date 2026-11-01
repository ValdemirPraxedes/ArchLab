package br.com.archlab.simulation.core.v2;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class V2DomainModelTest {
    @Test void multipleClientsApisAndOptionalDatabaseAreRepresentable() {
        ArchitectureV2 architecture = new ArchitectureV2(List.of(
                new ClientV2("client-a", "Web"),
                new ClientV2("client-b", "Mobile"),
                new ApiV2("api-a", "Pedidos", 10, 20),
                new ApiV2("api-b", "Estoque", 30, 40)), List.of());
        assertEquals(4, architecture.components().size());
        assertEquals(0, architecture.connections().size());
        assertEquals(2, architecture.components().stream()
                .filter(component -> component.type() == ComponentTypeV2.CLIENT).count());
        assertEquals(0, architecture.components().stream()
                .filter(component -> component.type() == ComponentTypeV2.DATABASE).count());
    }

    @Test void duplicateDisplayNamesDoNotChangeStableIds() {
        var first = new ApiV2("a", "API", 1, 1);
        var second = new ApiV2("b", "API", 1, 1);
        assertEquals(first.displayName(), second.displayName());
        assertNotEquals(first.id(), second.id());
        assertEquals(ComponentTypeV2.API, first.type());
        assertEquals(ComponentTypeV2.DATABASE, new DatabaseV2("d", "DB", 0).type());
        assertEquals(ComponentTypeV2.CLIENT, new ClientV2("c", "Web").type());
    }

    @Test void duplicateIdsRemainRepresentableForValidatorToRejectInBe03() {
        ArchitectureV2 candidate = new ArchitectureV2(List.of(
                new ClientV2("duplicated", "Client"),
                new ApiV2("duplicated", "API", 0, 0)),
                List.of(new ConnectionV2("x", "duplicated", "duplicated", 0,
                        new HttpRestIntegrationV2(null, null))));
        assertEquals(2, candidate.components().size());
        assertEquals(candidate.components().get(0).id(), candidate.components().get(1).id());
    }

    @Test void architectureSnapshotsDoNotShareMutableLists() {
        List<ComponentV2> components = new ArrayList<>(List.of(new ClientV2("c", "Web")));
        List<ConnectionV2> connections = new ArrayList<>();
        ArchitectureV2 architecture = new ArchitectureV2(components, connections);
        components.clear();
        connections.add(new ConnectionV2("x", "c", "a", 1,
                new HttpRestIntegrationV2(null, null)));
        assertEquals(1, architecture.components().size());
        assertTrue(architecture.connections().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> architecture.components().clear());
        assertThrows(UnsupportedOperationException.class, () -> architecture.connections().clear());
    }

    @Test void integrationVariantsAreTypedButPassive() {
        IntegrationV2 rest = new HttpRestIntegrationV2(
                new HttpRequestV2(HttpMethodV2.POST, "/orders", Map.of("Accept", "application/json"), "{}"),
                new HttpResponseV2(500, Map.of(), "illustrative error"));
        IntegrationV2 soap = new SoapHttpIntegrationV2("1.2", "GetOrder", null,
                "<Envelope/>", "<Envelope/>", 200, null, null);
        IntegrationV2 database = new DatabaseQueryIntegrationV2("SELECT 1", "1");
        assertEquals(List.of(IntegrationKindV2.HTTP_REST, IntegrationKindV2.SOAP_HTTP,
                        IntegrationKindV2.DATABASE_QUERY),
                List.of(rest.kind(), soap.kind(), database.kind()));
        assertEquals(500, ((HttpRestIntegrationV2) rest).response().statusCode());
    }

    @Test void integrationHeadersAreDefensivelyCopied() {
        Map<String, String> headers = new HashMap<>(Map.of("x-id", "1"));
        var request = new HttpRequestV2(HttpMethodV2.GET, "/", headers, null);
        var response = new HttpResponseV2(200, headers, null);
        var soap = new SoapHttpIntegrationV2("1.2", null, null, null, null, null, headers, headers);
        headers.put("x-id", "changed");
        assertEquals("1", request.headers().get("x-id"));
        assertEquals("1", response.headers().get("x-id"));
        assertEquals("1", soap.requestHeaders().get("x-id"));
        assertThrows(UnsupportedOperationException.class, () -> request.headers().put("x", "y"));
    }

    @Test void immutableCandidateAllowsOutOfRangeInputForLaterValidation() {
        ArchitectureV2 candidate = new ArchitectureV2(
                List.of(new ApiV2("api", " ", -1, 1_000_000_001L)), List.of());
        assertEquals(-1, ((ApiV2) candidate.components().getFirst()).preProcessingMs());
        assertThrows(NullPointerException.class, () -> new ArchitectureV2(null, List.of()));
    }
}
