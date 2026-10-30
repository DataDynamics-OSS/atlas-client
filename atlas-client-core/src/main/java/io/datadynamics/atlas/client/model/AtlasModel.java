package io.datadynamics.atlas.client.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.datadynamics.atlas.client.json.AtlasJson;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class of all Atlas wire models.
 * <p>
 * Properties not mapped by the subclass are kept in {@link #getAdditionalProperties()} and
 * written back on serialization. This keeps read-modify-write cycles lossless across Atlas
 * versions that add new fields (a typedef fetched from Atlas 2.3 can be updated without
 * dropping fields this client does not know about).
 */
public abstract class AtlasModel {

    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + AtlasJson.toJson(this);
    }
}
