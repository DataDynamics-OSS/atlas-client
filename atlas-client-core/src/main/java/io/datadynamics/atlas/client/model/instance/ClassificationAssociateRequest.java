package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Payload of {@code POST /v2/entity/bulk/classification}: one classification applied to many entities.
 */
public class ClassificationAssociateRequest extends AtlasModel {

    private AtlasClassification classification;
    private List<String> entityGuids;

    public ClassificationAssociateRequest() {
    }

    public ClassificationAssociateRequest(AtlasClassification classification, Collection<String> entityGuids) {
        this.classification = classification;
        this.entityGuids = new ArrayList<>(entityGuids);
    }

    public AtlasClassification getClassification() {
        return classification;
    }

    public void setClassification(AtlasClassification classification) {
        this.classification = classification;
    }

    public List<String> getEntityGuids() {
        return entityGuids;
    }

    public void setEntityGuids(List<String> entityGuids) {
        this.entityGuids = entityGuids;
    }
}
