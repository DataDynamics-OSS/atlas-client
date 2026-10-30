package io.datadynamics.atlas.client.typedef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Attribute type names understood by Atlas, plus helpers for container types.
 * <pre>{@code
 * attr("tags", array(STRING))
 * attr("properties", map(STRING, STRING))
 * attr("columns", array("my_column"))
 * }</pre>
 */
public final class AttributeTypes {

    public static final String BOOLEAN = "boolean";
    public static final String BYTE = "byte";
    public static final String SHORT = "short";
    public static final String INT = "int";
    public static final String LONG = "long";
    public static final String FLOAT = "float";
    public static final String DOUBLE = "double";
    public static final String BIGINTEGER = "biginteger";
    public static final String BIGDECIMAL = "bigdecimal";
    public static final String STRING = "string";
    public static final String DATE = "date";
    public static final String OBJECT_ID = "object_id";

    /** Built-in types that never appear in the typedef listing of the server. */
    public static final Set<String> BUILT_IN = Set.of(
            BOOLEAN, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BIGINTEGER, BIGDECIMAL, STRING, DATE, OBJECT_ID);

    private static final String ARRAY_PREFIX = "array<";
    private static final String MAP_PREFIX = "map<";

    private AttributeTypes() {
    }

    public static String array(String elementType) {
        return ARRAY_PREFIX + elementType + ">";
    }

    public static String map(String keyType, String valueType) {
        return MAP_PREFIX + keyType + "," + valueType + ">";
    }

    public static boolean isBuiltIn(String typeName) {
        return BUILT_IN.contains(typeName);
    }

    public static boolean isContainer(String typeName) {
        return typeName.startsWith(ARRAY_PREFIX) || typeName.startsWith(MAP_PREFIX);
    }

    /**
     * Names of the non-container types an attribute type refers to:
     * {@code map<string,array<my_column>>} gives {@code [string, my_column]}.
     */
    public static List<String> referencedTypeNames(String typeName) {
        List<String> result = new ArrayList<>();
        collect(typeName.trim(), result);
        return result;
    }

    private static void collect(String typeName, List<String> result) {
        if (typeName.startsWith(ARRAY_PREFIX) && typeName.endsWith(">")) {
            collect(typeName.substring(ARRAY_PREFIX.length(), typeName.length() - 1).trim(), result);
        } else if (typeName.startsWith(MAP_PREFIX) && typeName.endsWith(">")) {
            String inner = typeName.substring(MAP_PREFIX.length(), typeName.length() - 1);
            int split = topLevelComma(inner);
            if (split < 0) {
                result.add(typeName);
                return;
            }
            collect(inner.substring(0, split).trim(), result);
            collect(inner.substring(split + 1).trim(), result);
        } else {
            result.add(typeName);
        }
    }

    private static int topLevelComma(String value) {
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
