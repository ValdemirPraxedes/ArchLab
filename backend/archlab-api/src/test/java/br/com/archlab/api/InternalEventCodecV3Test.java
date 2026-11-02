package br.com.archlab.api;
import br.com.archlab.api.v3.InternalEventCodecV3;
import br.com.archlab.simulation.core.v3.*;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InternalEventCodecV3Test {
    private final JsonMapper json=JsonMapper.builder().build();private final InternalEventCodecV3 codec=new InternalEventCodecV3();
    @ParameterizedTest @EnumSource(SimulationEventV3.Type.class)
    void roundtripsEveryPayloadAndRejectsUnknownMissingAndInvalidFields(SimulationEventV3.Type type) throws Exception {
        var input=json.readTree(Files.readString(Path.of("../../docs/examples/v3/events/"+type+".json")));var payload=codec.decode(input);
        assertEquals(type,payload.eventType());var serialized=json.readTree(json.writeValueAsString(codec.encode(payload)));
        assertEquals(input,serialized);assertEquals(payload,codec.decode(serialized));
        var fields=new LinkedHashMap<>(codec.encode(payload));fields.put("extra",true);assertThrows(IllegalArgumentException.class,()->codec.decode(json.valueToTree(fields)));fields.remove("extra");
        var key=fields.keySet().stream().filter(k->!k.equals("eventType")).findFirst().orElseThrow();var value=fields.remove(key);
        assertThrows(IllegalArgumentException.class,()->codec.decode(json.valueToTree(fields)));fields.put(key,value);
        fields.put("eventType","UNKNOWN_EVENT");assertThrows(IllegalArgumentException.class,()->codec.decode(json.valueToTree(fields)));fields.put("eventType",type.name());
        for(var entry:new LinkedHashMap<>(fields).entrySet())if(entry.getValue() instanceof Number){fields.put(entry.getKey(),-1L);assertThrows(IllegalArgumentException.class,()->codec.decode(json.valueToTree(fields)));fields.put(entry.getKey(),entry.getValue());}
        assertThrows(UnsupportedOperationException.class,()->codec.encode(payload).clear());
    }
    @Test void catalogContainsExactlyThe32SealedVariants(){assertEquals(32,SimulationEventV3.Type.values().length);assertEquals(32,SimulationEventV3.class.getPermittedSubclasses().length);}
}
