package br.com.archlab.simulation.core.v3;
import java.util.*;
/** Closed property algebra; decimals retain their exact contract representation. */
public sealed interface PropertyValueV3 {
    record Text(String value) implements PropertyValueV3 { public Text { Objects.requireNonNull(value); } }
    record IntegerValue(long value) implements PropertyValueV3 { }
    record Decimal(String value) implements PropertyValueV3 {
        public Decimal { if(value == null || !value.matches("-?(0|[1-9][0-9]*)([.][0-9]+)?"))
            throw new IllegalArgumentException("Invalid decimal"); }
    }
    record Flag(boolean value) implements PropertyValueV3 { }
    record Choice(String value) implements PropertyValueV3 {
        public Choice { if(value == null || value.isEmpty()) throw new IllegalArgumentException("Empty enum"); }
    }
    record Reference(IdentifierV3 targetId, IdentifierV3 expectedTypeId) implements PropertyValueV3 {
        public Reference { Objects.requireNonNull(targetId); Objects.requireNonNull(expectedTypeId); }
    }
    record Values(List<PropertyValueV3> values) implements PropertyValueV3 {
        public Values { values=List.copyOf(values); if(values.size()>1000) throw new IllegalArgumentException("List limit"); }
    }
    record Fields(Map<String,PropertyValueV3> fields) implements PropertyValueV3 {
        public Fields { fields=Map.copyOf(fields); if(fields.size()>200) throw new IllegalArgumentException("Object limit"); }
    }
}
