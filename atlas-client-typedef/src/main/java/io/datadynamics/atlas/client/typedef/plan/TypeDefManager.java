package io.datadynamics.atlas.client.typedef.plan;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.model.search.SearchParameters;
import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.model.typedef.TypeCategory;
import io.datadynamics.atlas.client.typedef.TypeDefBuilder;
import io.datadynamics.atlas.client.typedef.TypeDefValidator;
import io.datadynamics.atlas.client.typedef.TypeDefs;
import io.datadynamics.atlas.client.typedef.TypeIssue;
import io.datadynamics.atlas.client.typedef.file.TypeDefFiles;
import io.datadynamics.atlas.client.typedef.plan.TypeChange.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Declarative type management: describe the types you want, review the plan, apply it.
 * <pre>{@code
 * TypeDefManager manager = new TypeDefManager(atlas);
 *
 * TypePlan plan = manager.plan(TypeDefFiles.readAll(Path.of("atlas-types")));
 * System.out.println(plan.render());
 * manager.apply(plan);              // or manager.sync(...) to do both
 * }</pre>
 * A plan reflects the server at the time it was computed; apply it soon after planning.
 */
public final class TypeDefManager {

    private static final Logger LOG = LoggerFactory.getLogger(TypeDefManager.class);

    private final AtlasClient client;

    public TypeDefManager(AtlasClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    // ---------------------------------------------------------------- create / update

    public TypePlan plan(AtlasTypesDef desired) {
        return TypePlanner.plan(desired, client.types().getAll());
    }

    public TypePlan plan(TypeDefBuilder<?, ?>... builders) {
        return plan(TypeDefs.typesDef(builders));
    }

    /** Plans the definitions of a file, or of all files under a directory. */
    public TypePlan plan(Path fileOrDirectory) {
        return plan(Files.isDirectory(fileOrDirectory)
                ? TypeDefFiles.readAll(fileOrDirectory) : TypeDefFiles.read(fileOrDirectory));
    }

    /**
     * Applies a plan: creates first (in one call, so new types may reference each other), then
     * updates, then deletes.
     *
     * @throws TypePlanException if the plan has errors; nothing is sent in that case
     */
    public TypeApplyResult apply(TypePlan plan) {
        if (plan.hasErrors()) {
            throw new TypePlanException(plan);
        }
        plan.getWarnings().forEach(w -> LOG.warn("{}", w));

        AtlasTypesDef toCreate = plan.payload(Action.CREATE);
        if (!toCreate.isEmpty()) {
            LOG.info("Creating types {}", names(plan, Action.CREATE));
            client.types().create(toCreate);
        }
        AtlasTypesDef toUpdate = plan.payload(Action.UPDATE);
        if (!toUpdate.isEmpty()) {
            LOG.info("Updating types {}", names(plan, Action.UPDATE));
            client.types().update(toUpdate);
        }
        AtlasTypesDef toDelete = plan.payload(Action.DELETE);
        if (!toDelete.isEmpty()) {
            LOG.info("Deleting types {}", names(plan, Action.DELETE));
            client.types().delete(toDelete);
        }
        return new TypeApplyResult(names(plan, Action.CREATE), names(plan, Action.UPDATE),
                names(plan, Action.DELETE), names(plan, Action.UNCHANGED));
    }

    /** {@link #plan(AtlasTypesDef)} followed by {@link #apply(TypePlan)}. */
    public TypeApplyResult sync(AtlasTypesDef desired) {
        TypePlan plan = plan(desired);
        LOG.info("{}", plan.summary());
        return apply(plan);
    }

    public TypeApplyResult sync(TypeDefBuilder<?, ?>... builders) {
        return sync(TypeDefs.typesDef(builders));
    }

    // ---------------------------------------------------------------- delete

    /**
     * Plans the deletion of types, reporting as errors everything Atlas would refuse:
     * remaining sub-types, relationships or attributes of other types referring to them,
     * and existing entities (soft-deleted ones included) or classification assignments.
     * Relationship instances cannot be checked up front.
     */
    public TypePlan planDelete(Collection<String> typeNames) {
        Map<String, AtlasBaseTypeDef> current = client.types().getAll().stream()
                .collect(Collectors.toMap(AtlasBaseTypeDef::getName, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<String> toDelete = new LinkedHashSet<>(typeNames);
        List<TypeChange> changes = new ArrayList<>();
        List<TypeIssue> issues = new ArrayList<>();

        for (String name : toDelete) {
            AtlasBaseTypeDef def = current.get(name);
            if (def == null) {
                issues.add(TypeIssue.warning(name, "does not exist; skipped"));
                continue;
            }
            changes.add(new TypeChange(name, def.getCategory(), Action.DELETE, List.of(), def));
            checkInstances(def, issues);
        }

        for (AtlasBaseTypeDef other : current.values()) {
            if (toDelete.contains(other.getName())) {
                continue;
            }
            for (TypeDefValidator.Reference reference : TypeDefValidator.references(other)) {
                if (toDelete.contains(reference.typeName()) && current.containsKey(reference.typeName())) {
                    issues.add(TypeIssue.error(reference.typeName(), "still referenced by " + other.getCategory()
                            + " " + other.getName() + " (" + reference.via() + "); delete it too or remove the reference"));
                }
            }
        }
        return new TypePlan(changes, issues);
    }

    /** {@link #planDelete(Collection)} followed by {@link #apply(TypePlan)}. */
    public TypeApplyResult delete(Collection<String> typeNames) {
        return apply(planDelete(typeNames));
    }

    private void checkInstances(AtlasBaseTypeDef def, List<TypeIssue> issues) {
        SearchParameters params = new SearchParameters();
        params.setExcludeDeletedEntities(false);
        params.setLimit(1);
        if (def.getCategory() == TypeCategory.ENTITY) {
            params.setTypeName(def.getName());
            params.setIncludeSubTypes(false);
            if (!client.search().basic(params).getEntities().isEmpty()) {
                issues.add(TypeIssue.error(def.getName(), "entities of this type exist (soft-deleted ones count too); "
                        + "delete and purge them first"));
            }
        } else if (def.getCategory() == TypeCategory.CLASSIFICATION) {
            params.setClassification(def.getName());
            params.setIncludeSubClassifications(false);
            if (!client.search().basic(params).getEntities().isEmpty()) {
                issues.add(TypeIssue.error(def.getName(), "classification is still attached to entities; remove it first"));
            }
        } else if (def.getCategory() == TypeCategory.RELATIONSHIP) {
            issues.add(TypeIssue.warning(def.getName(),
                    "relationship instances cannot be checked up front; Atlas refuses the delete if any exist"));
        }
    }

    // ---------------------------------------------------------------- export

    /** Definitions of the given types as stored in Atlas, without server-managed fields. */
    public AtlasTypesDef export(Collection<String> typeNames) {
        Set<String> wanted = new LinkedHashSet<>(typeNames);
        AtlasTypesDef result = new AtlasTypesDef();
        client.types().getAll().stream().filter(d -> wanted.contains(d.getName())).forEach(result::add);
        return TypeDefFiles.stripServerFields(result);
    }

    /** All definitions of one {@code serviceType} (e.g. {@code hive}), without server-managed fields. */
    public AtlasTypesDef exportByServiceType(String serviceType) {
        AtlasTypesDef result = new AtlasTypesDef();
        client.types().getAll().stream().filter(d -> serviceType.equals(d.getServiceType())).forEach(result::add);
        return TypeDefFiles.stripServerFields(result);
    }

    private static List<String> names(TypePlan plan, Action action) {
        return plan.getChanges(action).stream().map(TypeChange::name).collect(Collectors.toList());
    }
}
