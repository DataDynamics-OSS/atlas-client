package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Base of all type definition builders.
 *
 * @param <B> the concrete builder type, for chaining
 * @param <D> the definition type produced
 */
public abstract class TypeDefBuilder<B extends TypeDefBuilder<B, D>, D extends AtlasBaseTypeDef> {

    protected final D def;

    protected TypeDefBuilder(D def, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Type name must not be blank");
        }
        this.def = def;
        def.setName(name);
    }

    @SuppressWarnings("unchecked")
    protected final B self() {
        return (B) this;
    }

    public B description(String description) {
        def.setDescription(description);
        return self();
    }

    /** Version of the definition, e.g. {@code "1.0"}; useful to track changes in plan output. */
    public B typeVersion(String typeVersion) {
        def.setTypeVersion(typeVersion);
        return self();
    }

    /** Groups related types (e.g. {@code hive}, {@code my_company}); used for export by service type. */
    public B serviceType(String serviceType) {
        def.setServiceType(serviceType);
        return self();
    }

    public B option(String key, String value) {
        def.putOption(key, value);
        return self();
    }

    /** Hook for builders that derive values (e.g. defaults) right before validation. */
    protected void beforeBuild(D definition) {
    }

    /**
     * Returns a new, validated definition. The builder can be reused afterwards.
     *
     * @throws IllegalStateException if the definition is structurally invalid
     */
    public D build() {
        D copy = AtlasJson.deepCopy(def);
        beforeBuild(copy);
        List<TypeIssue> errors = TypeDefValidator.validateStructure(copy).stream()
                .filter(TypeIssue::isError)
                .collect(Collectors.toList());
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid type definition " + def.getName() + ": "
                    + errors.stream().map(TypeIssue::message).collect(Collectors.joining("; ")));
        }
        return copy;
    }
}
