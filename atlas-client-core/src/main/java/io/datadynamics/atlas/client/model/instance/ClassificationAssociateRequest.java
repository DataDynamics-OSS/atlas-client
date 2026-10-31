package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@code POST /v2/entity/bulk/classification}의 요청 본문입니다. classification 하나를 여러 엔티티에 붙입니다.
 */
public class ClassificationAssociateRequest extends AtlasModel {

    private AtlasClassification classification;
    private List<String> entityGuids;

    /** 빈 요청을 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public ClassificationAssociateRequest() {
    }

    /**
     * 요청을 만듭니다. guid 목록은 복사해서 보관합니다.
     *
     * @param classification 붙일 classification
     * @param entityGuids    classification을 붙일 엔티티들의 guid({@code null}이면 안 됩니다)
     */
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
