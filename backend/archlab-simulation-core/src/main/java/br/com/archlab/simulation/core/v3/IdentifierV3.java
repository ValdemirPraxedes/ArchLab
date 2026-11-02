package br.com.archlab.simulation.core.v3;
import java.util.Objects;
/** Contract identifier; preserves the caller's exact value. */
public record IdentifierV3(String value) {
    public IdentifierV3 {
        Objects.requireNonNull(value, "identifier");
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > 128 || value.codePoints().allMatch(IdentifierV3::isSchemaWhitespace))
            throw new IllegalArgumentException("Identifier must contain 1..128 characters and a non-whitespace character");
    }
    // JSON Schema uses ECMAScript whitespace, which differs from Java isBlank().
    private static boolean isSchemaWhitespace(int c) {
        return c == 9 || c == 10 || c == 11 || c == 12 || c == 13 || c == 32
                || c == 0xA0 || c == 0x1680 || (c >= 0x2000 && c <= 0x200A)
                || c == 0x2028 || c == 0x2029 || c == 0x202F || c == 0x205F
                || c == 0x3000 || c == 0xFEFF;
    }
}
