package io.datadynamics.atlas.client.lineage;

import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Describes one process (job, query, pipeline step) and the datasets it reads and writes.
 * Created with {@link AtlasLineage#process(String, String)}.
 * <pre>{@code
 * lineage.process("etl_job", "daily_sales_agg@prod")
 *     .inputs(EntityRef.of("hive_table", "raw.sales@prod"), EntityRef.of("hive_table", "raw.customer@prod"))
 *     .outputs(EntityRef.of("hive_table", "mart.daily_sales@prod"))
 *     .attribute("queryText", sql)
 *     .register();
 * }</pre>
 * Registering replaces the inputs and outputs of an existing process with the same qualifiedName.
 */
public final class ProcessBuilder {

    /** One column-level lineage edge set: an output column computed from input columns. */
    public record ColumnLineage(EntityRef output, List<EntityRef> inputs, Map<String, Object> attributes) {

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

    /** Display name; defaults to {@link EntityRef#defaultName()} of the process qualified name. */
    public ProcessBuilder name(String name) {
        return attribute("name", name);
    }

    public ProcessBuilder description(String description) {
        return attribute("description", description);
    }

    public ProcessBuilder owner(String owner) {
        return attribute("owner", owner);
    }

    /** Any other attribute of the process type, e.g. {@code queryText}, {@code startTime}. */
    public ProcessBuilder attribute(String name, Object value) {
        if (name.equals("inputs") || name.equals("outputs") || name.equals("qualifiedName")) {
            throw new IllegalArgumentException("'" + name + "' is managed by the builder");
        }
        attributes.put(name, value);
        return this;
    }

    public ProcessBuilder inputs(EntityRef... refs) {
        inputs.addAll(Arrays.asList(refs));
        return this;
    }

    public ProcessBuilder input(String typeName, String qualifiedName) {
        return inputs(EntityRef.of(typeName, qualifiedName));
    }

    public ProcessBuilder outputs(EntityRef... refs) {
        outputs.addAll(Arrays.asList(refs));
        return this;
    }

    public ProcessBuilder output(String typeName, String qualifiedName) {
        return outputs(EntityRef.of(typeName, qualifiedName));
    }

    /** {@code output} column is computed from the {@code inputs} columns. */
    public ProcessBuilder columnLineage(EntityRef output, EntityRef... inputs) {
        return columnLineage(output, Arrays.asList(inputs), Map.of());
    }

    /**
     * @param attributes extra attributes of the column lineage entity, e.g. {@code depenendencyType}
     *                   and {@code expression} for {@code hive_column_lineage}
     */
    public ProcessBuilder columnLineage(EntityRef output, List<EntityRef> inputs, Map<String, Object> attributes) {
        columnLineages.add(new ColumnLineage(output, inputs, attributes));
        return this;
    }

    /** Registers this process alone. Use {@link AtlasLineage#register(List)} to send several in one call. */
    public LineageRegistration register() {
        return lineage.register(List.of(this));
    }

    /** The entities {@link #register()} would send; see {@link AtlasLineage#toEntities(List)}. */
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

    void validate() {
        if (inputs.isEmpty() && outputs.isEmpty()) {
            throw new IllegalStateException("Process " + process + " has neither inputs nor outputs");
        }
    }
}
