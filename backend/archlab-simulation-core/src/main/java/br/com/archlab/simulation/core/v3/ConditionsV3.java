package br.com.archlab.simulation.core.v3;
import java.util.*;import java.math.BigDecimal;
import static br.com.archlab.simulation.core.v3.ExecutionPlanV3.*;
/** Evaluates data only; missing references differ from an explicit null literal. */
public final class ConditionsV3 {
    private static final Object MISSING=new Object();private ConditionsV3(){}
    public static boolean evaluate(Expression expression,Map<String,PropertyValueV3> parameters){return switch(expression){
        case Exists e -> value(e.operand(),parameters)!=MISSING;
        case Not n -> !evaluate(n.condition(),parameters);
        case Junction j -> j.op()==Logical.AND?j.conditions().stream().allMatch(e->evaluate(e,parameters)):j.conditions().stream().anyMatch(e->evaluate(e,parameters));
        case Compare c -> {Object a=value(c.left(),parameters),b=value(c.right(),parameters);if(a==MISSING || b==MISSING)throw new IllegalArgumentException("Missing condition operand");
            boolean numeric=a instanceof Number && b instanceof Number;int comparison=numeric?new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())):0;
            if(c.op()==Comparison.EQ || c.op()==Comparison.NE){boolean equal=numeric?comparison==0:Objects.equals(a,b);yield c.op()==Comparison.EQ?equal:!equal;}
            if(!numeric){if(a instanceof String x && b instanceof String y)comparison=x.compareTo(y);else throw new IllegalArgumentException("Incomparable operands");}
            yield switch(c.op()){case GT->comparison>0;case GTE->comparison>=0;case LT->comparison<0;case LTE->comparison<=0;default->throw new IllegalStateException();};}
    };}
    private static Object value(Operand operand,Map<String,PropertyValueV3> parameters){if(operand instanceof Literal literal)return literal.value();String path=((Reference)operand).path();
        if(path.startsWith("parameters."))path=path.substring(11);else if(path.startsWith("input."))path=path.substring(6);
        Object value=parameters;for(String segment:path.split("[.]",-1)){if(value instanceof Map<?,?> map)value=map.getOrDefault(segment,null);else if(value instanceof PropertyValueV3.Fields fields)value=fields.fields().get(segment);else return MISSING;if(value==null)return MISSING;}
        return switch(value){case PropertyValueV3.Text t->t.value();case PropertyValueV3.IntegerValue n->n.value();case PropertyValueV3.Decimal d->new BigDecimal(d.value());case PropertyValueV3.Flag f->f.value();case PropertyValueV3.Choice c->c.value();default->value;};
    }
}
