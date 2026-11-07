package br.com.archlab.api;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V6-22: no silent change for v0.1-v0.5. Replays the corpus frozen from the v0.5 release
 * (docs/api/examples/regression/corpus-v05.json) against every /api/v1 and /api/v2 endpoint and compares
 * status + SHA-256 of the canonical body with golden-v05.json, recorded from the v0.5 release by
 * diff-baseline.cjs. The only tolerated difference is the additive, empty top-level "dataOutcomes".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HistoricalRegressionV6Test {
    private static final Path DIR=Path.of("..","..","docs","api","examples","regression");
    @Value("${local.server.port}") int port;
    private final HttpClient client=HttpClient.newHttpClient();
    private final JsonMapper json=new JsonMapper();

    @TestFactory Stream<DynamicTest> responsesMatchTheV05Release() throws Exception {
        Map<String,JsonNode> bodies=new HashMap<>();
        for (JsonNode entry:json.readTree(Files.readString(DIR.resolve("corpus-v05.json"))).get("entries"))
            bodies.put(entry.get("id").asText(),entry.get("body"));
        JsonNode calls=json.readTree(Files.readString(DIR.resolve("golden-v05.json"))).get("calls");
        assertTrue(calls.size()>=270,"golden calls: "+calls.size());
        List<DynamicTest> tests=new ArrayList<>();
        for (JsonNode call:calls) {
            String entry=call.get("entry").asText(), endpoint=call.get("endpoint").asText();
            tests.add(DynamicTest.dynamicTest(entry+" "+endpoint,() -> {
                var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+endpoint))
                        .header("Content-Type","application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bodies.get(entry).toString())).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(call.get("status").asInt(),response.statusCode(),entry+" "+endpoint+": "+response.body());
                JsonNode body;
                try { body=json.readTree(response.body()); } catch (RuntimeException notJson) { body=json.getNodeFactory().textNode(response.body()); }
                if (body instanceof ObjectNode object && object.path("dataOutcomes").isArray() && object.get("dataOutcomes").isEmpty())
                    object.remove("dataOutcomes");
                assertEquals(call.get("sha256").asText(),sha256(canonical(body)),
                        entry+" "+endpoint+" changed versus the v0.5 release: "+response.body());
            }));
        }
        return tests.stream();
    }

    /** Same canonical form as diff-baseline.cjs: recursively sorted keys, JSON.stringify scalars. */
    static String canonical(JsonNode node) {
        StringBuilder out=new StringBuilder();
        write(node,out);
        return out.toString();
    }
    private static void write(JsonNode node,StringBuilder out) {
        if (node.isObject()) {
            List<String> keys=new ArrayList<>(node.propertyNames());
            Collections.sort(keys);
            out.append('{');
            for (int i=0;i<keys.size();i++) {
                if (i>0) out.append(',');
                string(keys.get(i),out); out.append(':'); write(node.get(keys.get(i)),out);
            }
            out.append('}');
        } else if (node.isArray()) {
            out.append('[');
            for (int i=0;i<node.size();i++) { if (i>0) out.append(','); write(node.get(i),out); }
            out.append(']');
        } else if (node.isString()) string(node.asText(),out);
        else if (node.isNumber()) {
            BigDecimal value=node.decimalValue();
            out.append(value.signum()==0?"0":value.stripTrailingZeros().scale()<=0
                    ?value.toBigInteger().toString():value.stripTrailingZeros().toPlainString());
        }
        else if (node.isBoolean()) out.append(node.asBoolean());
        else out.append("null");
    }
    private static void string(String value,StringBuilder out) {
        out.append('"');
        for (char c:value.toCharArray()) switch (c) {
            case '"' -> out.append("\\\"");
            case '\\' -> out.append("\\\\");
            case '\b' -> out.append("\\b");
            case '\f' -> out.append("\\f");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t");
            default -> { if (c<0x20) out.append(String.format("\\u%04x",(int)c)); else out.append(c); }
        }
        out.append('"');
    }
    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
