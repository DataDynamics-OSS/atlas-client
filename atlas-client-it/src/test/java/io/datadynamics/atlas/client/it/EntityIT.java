package io.datadynamics.atlas.client.it;

import io.datadynamics.atlas.client.exception.AtlasServerException;
import io.datadynamics.atlas.client.model.instance.AtlasClassification;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityWithExtInfo;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;
import io.datadynamics.atlas.client.model.instance.EntityStatus;
import io.datadynamics.atlas.client.model.search.FilterCriteria;
import io.datadynamics.atlas.client.model.search.FilterCriteria.Operator;
import io.datadynamics.atlas.client.model.search.SearchParameters;
import io.datadynamics.atlas.client.typedef.plan.TypeDefManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.datadynamics.atlas.client.typedef.AttributeTypes.LONG;
import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.classification;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entity operations against a real Atlas, addressed by qualifiedName.
 */
class EntityIT extends AtlasIT {

    private static String table;
    private static String sensitive;

    @BeforeAll
    static void createTypes() {
        table = name("table");
        sensitive = name("sensitive");
        cleanupTypes(sensitive, table);
        new TypeDefManager(atlas).sync(
                entity(table).dataSet().attribute(attr("rowCount", LONG).indexable()),
                classification(sensitive));
    }

    private static AtlasEntity table(String qualifiedName) {
        return new AtlasEntity(table, Map.of("qualifiedName", qualifiedName, "name", qualifiedName));
    }

    @Test
    void upsertByQualifiedName() {
        String qualifiedName = name("orders@it");
        EntityMutationResponse created = atlas.entities().createOrUpdate(table(qualifiedName));
        String guid = created.findGuid(table, qualifiedName).orElseThrow();
        cleanupEntity(guid);
        assertThat(created.created()).hasSize(1);

        AtlasEntity again = table(qualifiedName);
        again.setAttribute("rowCount", 42L);
        EntityMutationResponse updated = atlas.entities().createOrUpdate(again);
        assertThat(updated.created()).isEmpty();
        assertThat(updated.firstGuid()).contains(guid);

        assertThat(atlas.entities().findGuid(table, qualifiedName)).contains(guid);
        assertThat(atlas.entities().getByQualifiedName(table, qualifiedName).getEntity().getAttribute("rowCount"))
                .isEqualTo(42);
    }

    @Test
    void partialUpdatesLeaveOtherAttributesAlone() {
        String qualifiedName = name("customers@it");
        AtlasEntity entity = table(qualifiedName);
        entity.setAttribute("description", "customers");
        String guid = atlas.entities().createOrUpdate(entity).firstGuid().orElseThrow();
        cleanupEntity(guid);

        AtlasEntity patch = new AtlasEntity(table);
        patch.setAttribute("rowCount", 7L);
        atlas.entities().partialUpdateByQualifiedName(table, qualifiedName, patch);
        atlas.entities().updateAttribute(guid, "owner", "it-team");

        AtlasEntity stored = atlas.entities().get(guid).getEntity();
        assertThat(stored.getAttribute("rowCount")).isEqualTo(7);
        assertThat(stored.getAttribute("owner")).isEqualTo("it-team");
        assertThat(stored.getAttribute("description")).isEqualTo("customers");
    }

    @Test
    void classificationsAndLabels() {
        String qualifiedName = name("payments@it");
        String guid = atlas.entities().createOrUpdate(table(qualifiedName)).firstGuid().orElseThrow();
        cleanupEntity(guid);

        atlas.entities().addClassification(guid, new AtlasClassification(sensitive));
        assertThat(atlas.entities().getClassifications(guid))
                .extracting(AtlasClassification::getTypeName).containsExactly(sensitive);
        // Atlas 2.x propagates classifications in a background task and answers 406
        // (ATLAS-406-00-001) to a delete while that task runs; retry until it has finished.
        boolean removed = awaitValue(() -> {
            try {
                atlas.entities().removeClassification(guid, sensitive);
                return true;
            } catch (AtlasServerException e) {
                assertThat(e.getStatusCode()).isEqualTo(406);
                return false;
            }
        }, done -> done, Duration.ofSeconds(30));
        assertThat(removed).isTrue();
        assertThat(atlas.entities().getClassifications(guid)).isEmpty();

        atlas.entities().setLabels(guid, Set.of("gold", "finance"));
        atlas.entities().addLabels(guid, Set.of("audited"));
        assertThat(atlas.entities().get(guid).getEntity().getLabels())
                .containsExactlyInAnyOrder("gold", "finance", "audited");
    }

    @Test
    void searchStreamsAllPages() {
        List<AtlasEntity> entities = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            AtlasEntity entity = table(name("paged_" + i + "@it"));
            entity.setAttribute("rowCount", 1000L + i);
            entities.add(entity);
        }
        EntityMutationResponse response = atlas.entities().createOrUpdate(entities);
        response.created().forEach(h -> cleanupEntity(h.getGuid()));

        SearchParameters params = SearchParameters.forType(table);
        params.setEntityFilters(FilterCriteria.of("rowCount", Operator.GTE, 1000));

        // The index is updated asynchronously; wait until everything is searchable.
        List<String> found = awaitValue(() -> atlas.search().basicStream(params, 10)
                .map(h -> h.qualifiedName())
                .collect(Collectors.toList()), list -> list.size() == 25, Duration.ofSeconds(60));
        assertThat(found).hasSize(25).doesNotHaveDuplicates();
    }

    @Test
    void softDeleteThenPurge() {
        String qualifiedName = name("tmp@it");
        String guid = atlas.entities().createOrUpdate(table(qualifiedName)).firstGuid().orElseThrow();

        atlas.entities().deleteByQualifiedName(table, qualifiedName);
        AtlasEntityWithExtInfo deleted = atlas.entities().get(guid);
        assertThat(deleted.getEntity().getStatus()).isEqualTo(EntityStatus.DELETED);

        atlas.admin().purge(List.of(guid));
        assertThat(atlas.entities().find(guid)).isEmpty();
    }
}
