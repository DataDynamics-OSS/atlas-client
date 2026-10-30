package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasAttributeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasBusinessMetadataDef;
import io.datadynamics.atlas.client.model.typedef.AtlasConstraintDef;
import io.datadynamics.atlas.client.model.typedef.Cardinality;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder of one attribute. Created with {@link TypeDefs#attr(String, String)}.
 * <p>
 * Attributes are <b>optional by default</b>, unlike raw Atlas where {@code isOptional} defaults
 * to {@code false}. Most metadata attributes are optional, and Atlas only accepts optional
 * attributes when they are added to an existing type.
 */
public final class AttributeDefBuilder {

    private final AtlasAttributeDef def;

    AttributeDefBuilder(String name, String typeName) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Attribute name must not be blank");
        }
        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("Type of attribute " + name + " must not be blank");
        }
        def = new AtlasAttributeDef(name, typeName);
        def.setOptional(true);
        def.setCardinality(Cardinality.SINGLE);
    }

    /** Makes the attribute mandatory. Only possible when the type is first created. */
    public AttributeDefBuilder required() {
        def.setOptional(false);
        return this;
    }

    public AttributeDefBuilder optional() {
        def.setOptional(true);
        return this;
    }

    /** Values must be unique across all entities of the type (Atlas indexes unique attributes). */
    public AttributeDefBuilder unique() {
        def.setUnique(true);
        return this;
    }

    /** Indexes the attribute so basic search filters on it are fast. */
    public AttributeDefBuilder indexable() {
        def.setIndexable(true);
        return this;
    }

    /** Uses a string index, allowing exact-match/sort on string attributes. */
    public AttributeDefBuilder stringIndex() {
        def.setIndexType(AtlasAttributeDef.IndexType.STRING);
        return this;
    }

    public AttributeDefBuilder cardinality(Cardinality cardinality) {
        def.setCardinality(cardinality);
        return this;
    }

    public AttributeDefBuilder list() {
        return cardinality(Cardinality.LIST);
    }

    public AttributeDefBuilder set() {
        return cardinality(Cardinality.SET);
    }

    public AttributeDefBuilder description(String description) {
        def.setDescription(description);
        return this;
    }

    public AttributeDefBuilder displayName(String displayName) {
        def.setDisplayName(displayName);
        return this;
    }

    public AttributeDefBuilder defaultValue(Object defaultValue) {
        def.setDefaultValue(defaultValue == null ? null : defaultValue.toString());
        return this;
    }

    /** Free-text search weight (1-10); higher values rank matches on this attribute first. */
    public AttributeDefBuilder searchWeight(int searchWeight) {
        if (searchWeight < 1 || searchWeight > 10) {
            throw new IllegalArgumentException("searchWeight must be between 1 and 10");
        }
        def.setSearchWeight(searchWeight);
        return this;
    }

    public AttributeDefBuilder includeInNotification(boolean include) {
        def.setIncludeInNotification(include);
        return this;
    }

    public AttributeDefBuilder valuesCount(int min, int max) {
        def.setValuesMinCount(min);
        def.setValuesMaxCount(max);
        return this;
    }

    public AttributeDefBuilder option(String key, String value) {
        Map<String, String> options = def.getOptions() == null ? new LinkedHashMap<>() : def.getOptions();
        options.put(key, value);
        def.setOptions(options);
        return this;
    }

    /** Business metadata only: entity types this attribute may be set on. */
    public AttributeDefBuilder applicableEntityTypes(String... entityTypes) {
        return option(AtlasBusinessMetadataDef.OPT_APPLICABLE_ENTITY_TYPES,
                AtlasJson.toJson(Arrays.asList(entityTypes)));
    }

    /** Business metadata only: maximum length of a string value. */
    public AttributeDefBuilder maxStringLength(int maxLength) {
        return option(AtlasBusinessMetadataDef.OPT_MAX_STRING_LENGTH, Integer.toString(maxLength));
    }

    /**
     * Legacy (pre-relationship) modelling: the referenced entities are owned by this one.
     * Prefer {@link TypeDefs#relationship(String)} with composition for new models.
     */
    public AttributeDefBuilder ownedRef() {
        def.addConstraint(new AtlasConstraintDef(AtlasConstraintDef.CONSTRAINT_TYPE_OWNED_REF, null));
        return this;
    }

    /** Legacy (pre-relationship) modelling: inverse of the given attribute of the referenced type. */
    public AttributeDefBuilder inverseRef(String attributeName) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(AtlasConstraintDef.CONSTRAINT_PARAM_ATTRIBUTE, attributeName);
        def.addConstraint(new AtlasConstraintDef(AtlasConstraintDef.CONSTRAINT_TYPE_INVERSE_REF, params));
        return this;
    }

    public String getName() {
        return def.getName();
    }

    public AtlasAttributeDef build() {
        return AtlasJson.deepCopy(def);
    }
}
