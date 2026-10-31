package io.datadynamics.atlas.client.lineage;

import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 프로세스 하나(작업, 쿼리, 파이프라인 단계)와 그 프로세스가 읽고 쓰는 데이터셋을 기술합니다.
 * {@link AtlasLineage#process(String, String)}로 만듭니다.
 * <pre>{@code
 * lineage.process("etl_job", "daily_sales_agg@prod")
 *     .inputs(EntityRef.of("hive_table", "raw.sales@prod"), EntityRef.of("hive_table", "raw.customer@prod"))
 *     .outputs(EntityRef.of("hive_table", "mart.daily_sales@prod"))
 *     .attribute("queryText", sql)
 *     .register();
 * }</pre>
 * 같은 qualifiedName의 프로세스가 이미 있으면, 등록할 때 그 프로세스의 입력과 출력을 이 빌더의 것으로
 * 교체합니다(추가가 아니라 교체입니다).
 * <p>
 * 이 클래스는 스레드 안전하지 않습니다. 한 스레드에서 만들고 등록하십시오.
 */
public final class ProcessBuilder {

    /**
     * 컬럼 수준 리니지 하나입니다. 출력 컬럼 하나가 어떤 입력 컬럼들로부터 계산되는지 나타냅니다.
     *
     * @param output     계산 결과가 담기는 출력 컬럼
     * @param inputs     출력 컬럼을 계산하는 데 쓰인 입력 컬럼(하나 이상)
     * @param attributes 컬럼 리니지 엔티티에 추가로 넣을 속성. {@code null}이면 빈 맵으로 취급합니다.
     */
    public record ColumnLineage(EntityRef output, List<EntityRef> inputs, Map<String, Object> attributes) {

        /**
         * 인자를 검증하고 목록과 맵을 변경 불가능한 복사본으로 바꿉니다.
         *
         * @throws NullPointerException     {@code output}이 {@code null}인 경우
         * @throws IllegalArgumentException 입력 컬럼이 하나도 없는 경우
         */
        public ColumnLineage {
            Objects.requireNonNull(output, "output");
            inputs = List.copyOf(inputs);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("Column lineage of " + output + " needs at least one input column");
            }
        }
    }

    private final AtlasLineage lineage;
    private final EntityRef process;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final List<EntityRef> inputs = new ArrayList<>();
    private final List<EntityRef> outputs = new ArrayList<>();
    private final List<ColumnLineage> columnLineages = new ArrayList<>();

    ProcessBuilder(AtlasLineage lineage, String typeName, String qualifiedName) {
        this.lineage = lineage;
        this.process = EntityRef.of(typeName, qualifiedName);
    }

    /**
     * 표시 이름을 정합니다. 지정하지 않으면 프로세스 qualifiedName의 {@link EntityRef#defaultName()}을 씁니다.
     *
     * @param name 표시 이름({@code name} 속성)
     * @return 메서드 체이닝을 위한 이 빌더
     */
    public ProcessBuilder name(String name) {
        return attribute("name", name);
    }

    /**
     * 프로세스 설명({@code description} 속성)을 정합니다.
     *
     * @param description 설명
     * @return 메서드 체이닝을 위한 이 빌더
     */
    public ProcessBuilder description(String description) {
        return attribute("description", description);
    }

    /**
     * 프로세스 소유자({@code owner} 속성)를 정합니다.
     *
     * @param owner 소유자
     * @return 메서드 체이닝을 위한 이 빌더
     */
    public ProcessBuilder owner(String owner) {
        return attribute("owner", owner);
    }

    /**
     * 프로세스 타입의 그 밖의 속성을 정합니다. 예: {@code queryText}, {@code startTime}.
     * 같은 이름으로 다시 호출하면 값을 덮어씁니다.
     *
     * @param name  속성 이름
     * @param value 속성 값
     * @return 메서드 체이닝을 위한 이 빌더
     * @throws IllegalArgumentException {@code inputs}, {@code outputs}, {@code qualifiedName}처럼 빌더가
     *                                  직접 관리하는 속성을 지정한 경우
     */
    public ProcessBuilder attribute(String name, Object value) {
        if (name.equals("inputs") || name.equals("outputs") || name.equals("qualifiedName")) {
            throw new IllegalArgumentException("'" + name + "' is managed by the builder");
        }
        attributes.put(name, value);
        return this;
    }

    /**
     * 프로세스가 읽는 데이터셋을 추가합니다. 여러 번 호출하면 누적됩니다.
     *
     * @param refs 입력 데이터셋 참조
     * @return 메서드 체이닝을 위한 이 빌더
     */
    public ProcessBuilder inputs(EntityRef... refs) {
        inputs.addAll(Arrays.asList(refs));
        return this;
    }

    /**
     * 타입과 qualifiedName으로 입력 데이터셋 하나를 추가합니다.
     *
     * @param typeName      데이터셋 타입
     * @param qualifiedName 데이터셋 qualifiedName
     * @return 메서드 체이닝을 위한 이 빌더
     */
    public ProcessBuilder input(String typeName, String qualifiedName) {
        return inputs(EntityRef.of(typeName, qualifiedName));
    }

    /**
     * 프로세스가 쓰는 데이터셋을 추가합니다. 여러 번 호출하면 누적됩니다.
     *
     * @param refs 출력 데이터셋 참조
     * @return 메서드 체이닝을 위한 이 빌더
     */
    public ProcessBuilder outputs(EntityRef... refs) {
        outputs.addAll(Arrays.asList(refs));
        return this;
    }

    /**
     * 타입과 qualifiedName으로 출력 데이터셋 하나를 추가합니다.
     *
     * @param typeName      데이터셋 타입
     * @param qualifiedName 데이터셋 qualifiedName
     * @return 메서드 체이닝을 위한 이 빌더
     */
    public ProcessBuilder output(String typeName, String qualifiedName) {
        return outputs(EntityRef.of(typeName, qualifiedName));
    }

    /**
     * {@code output} 컬럼이 {@code inputs} 컬럼들로부터 계산된다는 컬럼 수준 리니지를 추가합니다.
     *
     * @param output 출력 컬럼
     * @param inputs 입력 컬럼(하나 이상)
     * @return 메서드 체이닝을 위한 이 빌더
     * @throws IllegalArgumentException 입력 컬럼이 없는 경우
     */
    public ProcessBuilder columnLineage(EntityRef output, EntityRef... inputs) {
        return columnLineage(output, Arrays.asList(inputs), Map.of());
    }

    /**
     * 추가 속성과 함께 컬럼 수준 리니지를 추가합니다.
     *
     * @param output     출력 컬럼
     * @param inputs     입력 컬럼(하나 이상)
     * @param attributes 컬럼 리니지 엔티티의 추가 속성. 예를 들어 {@code hive_column_lineage}라면
     *                   {@code depenendencyType}과 {@code expression}
     * @return 메서드 체이닝을 위한 이 빌더
     * @throws IllegalArgumentException 입력 컬럼이 없는 경우
     */
    public ProcessBuilder columnLineage(EntityRef output, List<EntityRef> inputs, Map<String, Object> attributes) {
        columnLineages.add(new ColumnLineage(output, inputs, attributes));
        return this;
    }

    /**
     * 이 프로세스 하나만 등록합니다. 여러 프로세스를 한 번의 호출로 보내려면
     * {@link AtlasLineage#register(List)}를 쓰십시오.
     *
     * @return 등록 결과
     * @throws LineageException      참조한 엔티티가 없고 정책이 {@link MissingEntityPolicy#FAIL}인 경우
     * @throws IllegalStateException 입력과 출력이 모두 없는 경우
     */
    public LineageRegistration register() {
        return lineage.register(List.of(this));
    }

    /**
     * {@link #register()}가 보낼 엔티티들을 돌려줍니다. 자세한 내용은 {@link AtlasLineage#toEntities(List)}를
     * 보십시오.
     *
     * @return Atlas에 보낼 엔티티 묶음
     */
    public AtlasEntitiesWithExtInfo toEntities() {
        return lineage.toEntities(List.of(this));
    }

    EntityRef process() {
        return process;
    }

    Map<String, Object> attributes() {
        return attributes;
    }

    List<EntityRef> inputs() {
        return inputs;
    }

    List<EntityRef> outputs() {
        return outputs;
    }

    List<ColumnLineage> columnLineages() {
        return columnLineages;
    }

    /** 입력도 출력도 없는 프로세스는 리니지로서 의미가 없으므로 등록 전에 거부합니다. */
    void validate() {
        if (inputs.isEmpty() && outputs.isEmpty()) {
            throw new IllegalStateException("Process " + process + " has neither inputs nor outputs");
        }
    }
}
