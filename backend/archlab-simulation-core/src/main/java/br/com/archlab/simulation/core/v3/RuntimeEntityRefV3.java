package br.com.archlab.simulation.core.v3;
import java.util.Objects;
public record RuntimeEntityRefV3(String kind,IdentifierV3 id) {
    public RuntimeEntityRefV3 {Objects.requireNonNull(id);if(kind==null || kind.isEmpty())throw new IllegalArgumentException("Entity kind required");}
}
