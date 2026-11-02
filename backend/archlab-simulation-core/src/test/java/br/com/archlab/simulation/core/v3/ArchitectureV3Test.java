package br.com.archlab.simulation.core.v3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static br.com.archlab.simulation.core.v3.ArchitectureV3.*;
class ArchitectureV3Test {
    static IdentifierV3 id(String x){return new IdentifierV3(x);}
    static ArchitectureV3 architecture(List<Component> components,List<Resource> resources,List<Endpoint> endpoints,List<Operation> operations,List<Relation> relations){
        return new ArchitectureV3(id("a"),"3",components,resources,endpoints,operations,relations,List.of());}
    static Component component(String type,Map<String,PropertyValueV3> properties){return new Component(id("c"),id(type),type,properties);}
    static ValidationResultV3 validate(ArchitectureV3 a){return new ArchitectureValidatorV3(TypeCatalogV3.initial()).validate(a);}
    @ParameterizedTest @ValueSource(strings={"CLIENT_GENERIC","API_SERVICE","DATABASE_GENERIC","BROKER_GENERIC","QUEUE_GENERIC","TELEMETRY_GENERIC"})
    void permitsExactlyDeclaredInitialComponentTypes(String type){assertTrue(validate(architecture(List.of(component(type,Map.of())),List.of(),List.of(),List.of(),List.of())).valid());}
    @ParameterizedTest @ValueSource(strings={"CLIENT","CACHE","LOAD_BALANCER","unknown"})
    void rejectsUnsupportedComponentTypes(String type){assertEquals("UNSUPPORTED_COMPONENT_TYPE",validate(architecture(List.of(component(type,Map.of())),List.of(),List.of(),List.of(),List.of())).violations().getFirst().code());}
    @Test void rejectsInvalidPropertyTypeRangeAndUnknownFieldsInStableOrder(){
        var properties=new LinkedHashMap<String,PropertyValueV3>();properties.put("zzz",new PropertyValueV3.Text("x"));properties.put("available",new PropertyValueV3.Text("true"));properties.put("processingMs",new PropertyValueV3.IntegerValue(-1));
        var a=architecture(List.of(component("DATABASE_GENERIC",properties)),List.of(),List.of(),List.of(),List.of());
        var result=validate(a);assertEquals(List.of("PROPERTY_TYPE","PROPERTY_RANGE","PROPERTY_NOT_SUPPORTED"),result.violations().stream().map(ViolationV3::code).toList());
        var reordered=new TreeMap<>(properties);assertEquals(result,validate(architecture(List.of(component("DATABASE_GENERIC",reordered)),List.of(),List.of(),List.of(),List.of())));
    }
    @Test void checksResourcesOwnersAndGlobalIds(){
        var pool=new Resource(id("pool"),id("PROCESSING_POOL"),id("c"),Map.of("capacity",new PropertyValueV3.IntegerValue(2)));
        assertTrue(validate(architecture(List.of(component("API_SERVICE",Map.of())),List.of(pool),List.of(),List.of(),List.of())).valid());
        assertTrue(validate(architecture(List.of(component("CLIENT_GENERIC",Map.of())),List.of(pool),List.of(),List.of(),List.of())).violations().stream().anyMatch(v->v.code().equals("RESOURCE_OWNER_TYPE")));
        var duplicate=new Resource(id("c"),id("PROCESSING_POOL"),id("missing"),Map.of());
        assertEquals(List.of("DUPLICATE_ID","OWNER_NOT_FOUND"),validate(architecture(List.of(component("API_SERVICE",Map.of())),List.of(duplicate),List.of(),List.of(),List.of())).violations().stream().map(ViolationV3::code).toList());
    }
    @Test void checksEndpointDirectionProtocolAndOperationOwnership(){
        var c=component("API_SERVICE",Map.of());
        var op=new Operation(id("op"),id("c"),"op",null,null,new Binding(BindingKind.NATIVE_BEHAVIOR,id("unknown")),List.of(id("unsupported")));
        var source=new Endpoint(id("source"),id("c"),Direction.INBOUND,id("HTTP_REST"),List.of(),Map.of());
        var target=new Endpoint(id("target"),id("missing"),Direction.OUTBOUND,id("SOAP_HTTP"),List.of(id("op")),Map.of());
        var link=new Communication(id("link"),id("source"),id("target"),id("HTTP_REST"),Map.of());
        var codes=validate(architecture(List.of(c),List.of(),List.of(source,target),List.of(op),List.of(link))).violations().stream().map(ViolationV3::code).toList();
        assertEquals(List.of("CAPABILITY_NOT_SUPPORTED","NATIVE_BEHAVIOR_NOT_SUPPORTED","OWNER_NOT_FOUND","ENDPOINT_OPERATION_OWNER","SOURCE_DIRECTION","TARGET_DIRECTION","PROTOCOL_MISMATCH"),codes);
    }
    @Test void resolvesNestedTypedReferencesThroughAnExplicitCatalog(){
        var base=TypeCatalogV3.initial();var types=new HashMap<>(base.components());
        types.put("API_SERVICE",new TypeCatalogV3.ComponentType(Map.of("target",PropertyValueV3.Fields.class),Set.of(),Set.of()));
        var catalog=new TypeCatalogV3(types,base.resources(),base.protocols());
        var refs=new PropertyValueV3.Fields(Map.of("items",new PropertyValueV3.Values(List.of(new PropertyValueV3.Reference(id("missing"),id("API_SERVICE")),new PropertyValueV3.Reference(id("c"),id("DATABASE_GENERIC"))))));
        var result=new ArchitectureValidatorV3(catalog).validate(architecture(List.of(component("API_SERVICE",Map.of("target",refs))),List.of(),List.of(),List.of(),List.of()));
        assertEquals(List.of("REFERENCE_NOT_FOUND","REFERENCE_TYPE"),result.violations().stream().map(ViolationV3::code).toList());
    }
    @Test void preservesDecimalAndCopiesCollections(){
        assertEquals("-1.2300",new PropertyValueV3.Decimal("-1.2300").value());
        for(var invalid:List.of("01","1e2","+1","NaN",".5","1."))assertThrows(IllegalArgumentException.class,()->new PropertyValueV3.Decimal(invalid));
        var components=new ArrayList<>(List.of(component("API_SERVICE",Map.of())));var a=architecture(components,List.of(),List.of(),List.of(),List.of());components.clear();assertEquals(1,a.components().size());
        assertThrows(UnsupportedOperationException.class,()->a.components().clear());
        assertThrows(IllegalArgumentException.class,()->new PropertyValueV3.Values(Collections.nCopies(1001,new PropertyValueV3.Flag(true))));
    }
    @Test void validatesInitialStateReferencesDuplicatesAndCapacity(){
        var queue=new Resource(id("q"),id("MESSAGE_QUEUE"),id("c"),Map.of("capacity",new PropertyValueV3.IntegerValue(2)));
        var a=architecture(List.of(component("QUEUE_GENERIC",Map.of())),List.of(queue),List.of(),List.of(),List.of());
        var validator=new ArchitectureValidatorV3(TypeCatalogV3.initial());
        assertTrue(validator.validateInitialState(a,List.of(new InitialStateV3.Component(id("c"),false),new InitialStateV3.Resource(id("q"),true),new InitialStateV3.Queue(id("q"),2L))).valid());
        var result=validator.validateInitialState(a,List.of(new InitialStateV3.Component(id("missing"),null),new InitialStateV3.Resource(id("missing"),null),
                new InitialStateV3.Policy(id("missing"),null),new InitialStateV3.Queue(id("q"),3L),new InitialStateV3.Queue(id("q"),null)));
        assertEquals(List.of("COMPONENT_NOT_FOUND","RESOURCE_NOT_FOUND","POLICY_NOT_FOUND","INITIAL_QUEUE_CAPACITY","DUPLICATE_INITIAL_STATE"),result.violations().stream().map(ViolationV3::code).toList());
    }
}
