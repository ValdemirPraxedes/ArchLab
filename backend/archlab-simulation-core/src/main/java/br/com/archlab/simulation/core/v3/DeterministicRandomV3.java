package br.com.archlab.simulation.core.v3;
/** SplitMix64, with explicit state for full-runtime restart. Overflow is intentional. */
public final class DeterministicRandomV3 {
    private long state;
    public DeterministicRandomV3(long seed){state=seed;}
    public long state(){return state;}
    public long nextLong(){long z=(state+=0x9E3779B97F4A7C15L);z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;return z^(z>>>31);}
    public long nextLong(long bound){if(bound<1)throw new IllegalArgumentException("Positive bound required");
        long bits,value;do{bits=nextLong()>>>1;value=bits%bound;}while(bits-value+(bound-1)<0);return value;}
}
