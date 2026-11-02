package br.com.archlab.simulation.core.v3;
import java.util.*;import java.nio.charset.StandardCharsets;import java.security.*;
/** Stable record encoding binds a checkpoint to its exact immutable input, including the seed. */
public final class InputFingerprintV3 {
    private InputFingerprintV3(){}
    public static String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String encode(Object value){if(value==null)return "N";if(value instanceof String s)return "S"+s.length()+":"+s;if(value instanceof Boolean b)return b?"B1":"B0";if(value instanceof Number n)return "I"+n+";";if(value instanceof Enum<?> e)return "E"+e.getDeclaringClass().getName()+":"+e.name()+";";
        if(value instanceof Map<?,?> map){var entries=new TreeMap<String,String>();for(var e:map.entrySet())entries.put(encode(e.getKey()),encode(e.getValue()));return "M"+entries;}
        if(value instanceof Iterable<?> list){var out=new StringBuilder("L[");for(var item:list)out.append(encode(item));return out.append(']').toString();}
        if(value.getClass().isRecord()){var out=new StringBuilder("R"+value.getClass().getName()+"{");try{for(var c:value.getClass().getRecordComponents())out.append(c.getName()).append('=').append(encode(c.getAccessor().invoke(value)));}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}return out.append('}').toString();}
        throw new IllegalArgumentException("Not immutable input data: "+value.getClass());}
}
