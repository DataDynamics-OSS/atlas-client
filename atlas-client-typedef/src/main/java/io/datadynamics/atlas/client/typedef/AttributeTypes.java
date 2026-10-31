package io.datadynamics.atlas.client.typedef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Atlas가 이해하는 속성 타입 이름과, 컨테이너 타입({@code array<...>}, {@code map<...>})을 다루는 도우미를 모았습니다.
 * <pre>{@code
 * attr("tags", array(STRING))
 * attr("properties", map(STRING, STRING))
 * attr("columns", array("my_column"))
 * }</pre>
 * 모든 메서드는 상태가 없으므로 여러 스레드에서 동시에 호출해도 안전합니다.
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

    /** 서버의 typedef 목록에는 나타나지 않는 내장 타입들입니다. 참조 검사에서 존재하는 것으로 간주합니다. */
    public static final Set<String> BUILT_IN = Set.of(
            BOOLEAN, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BIGINTEGER, BIGDECIMAL, STRING, DATE, OBJECT_ID);

    private static final String ARRAY_PREFIX = "array<";
    private static final String MAP_PREFIX = "map<";

    /** 인스턴스를 만들지 않는 유틸리티 클래스이므로 생성자를 막아 둡니다. */
    private AttributeTypes() {
    }

    /**
     * 배열 타입 이름을 만듭니다. 예: {@code array("my_column")}은 {@code "array<my_column>"}입니다.
     *
     * @param elementType 원소 타입 이름
     * @return {@code array<elementType>} 형태의 타입 이름
     */
    public static String array(String elementType) {
        return ARRAY_PREFIX + elementType + ">";
    }

    /**
     * 맵 타입 이름을 만듭니다. 예: {@code map(STRING, INT)}는 {@code "map<string,int>"}입니다.
     *
     * @param keyType   키 타입 이름
     * @param valueType 값 타입 이름
     * @return {@code map<keyType,valueType>} 형태의 타입 이름
     */
    public static String map(String keyType, String valueType) {
        return MAP_PREFIX + keyType + "," + valueType + ">";
    }

    /**
     * 주어진 이름이 {@link #BUILT_IN} 내장 타입인지 알려 줍니다. 컨테이너 타입 이름은 내장 타입으로 보지 않습니다.
     *
     * @param typeName 확인할 타입 이름
     * @return 내장 타입이면 {@code true}
     */
    public static boolean isBuiltIn(String typeName) {
        return BUILT_IN.contains(typeName);
    }

    /**
     * 주어진 이름이 {@code array<} 또는 {@code map<}으로 시작하는 컨테이너 타입인지 알려 줍니다.
     *
     * @param typeName 확인할 타입 이름({@code null}이면 안 됩니다)
     * @return 컨테이너 타입이면 {@code true}
     */
    public static boolean isContainer(String typeName) {
        return typeName.startsWith(ARRAY_PREFIX) || typeName.startsWith(MAP_PREFIX);
    }

    /**
     * 속성 타입이 참조하는, 컨테이너가 아닌 타입 이름들을 돌려줍니다.
     * 예를 들어 {@code map<string,array<my_column>>}이면 {@code [string, my_column]}입니다.
     *
     * @param typeName 속성 타입 이름({@code null}이면 안 됩니다)
     * @return 등장 순서대로 나열한 타입 이름 목록. 내장 타입도 포함합니다
     */
    public static List<String> referencedTypeNames(String typeName) {
        List<String> result = new ArrayList<>();
        collect(typeName.trim(), result);
        return result;
    }

    /**
     * 컨테이너 타입을 재귀적으로 벗겨 내며 안쪽 타입 이름을 모읍니다.
     * {@code map<...>} 안에서 최상위 쉼표를 찾지 못하면(형식이 잘못된 경우) 전체 이름을 그대로 결과에 넣어,
     * 이후 참조 검사에서 알 수 없는 타입으로 드러나게 합니다.
     */
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

    /**
     * 꺾쇠 괄호 깊이가 0인 위치의 첫 쉼표를 찾습니다. {@code string,map<string,int>}처럼 값 타입 안에 쉼표가
     * 있어도 키와 값을 올바르게 나누기 위해서입니다.
     *
     * @return 쉼표 위치, 없으면 -1
     */
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
