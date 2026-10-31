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
 * 선언적 타입 관리를 제공합니다. 원하는 타입을 기술하고, plan을 검토한 뒤, 적용합니다.
 * <pre>{@code
 * TypeDefManager manager = new TypeDefManager(atlas);
 *
 * TypePlan plan = manager.plan(TypeDefFiles.readAll(Path.of("atlas-types")));
 * System.out.println(plan.render());
 * manager.apply(plan);              // 또는 manager.sync(...)로 두 단계를 한 번에 합니다
 * }</pre>
 * plan은 계산한 시점의 서버 상태를 반영합니다. 그사이 다른 쪽에서 타입을 바꿀 수 있으므로 plan을 세운 뒤
 * 곧바로 적용하십시오.
 * <p>
 * 이 클래스는 생성 후 바뀌는 상태가 없고 주어진 {@link AtlasClient}만 사용합니다.
 */
public final class TypeDefManager {

    private static final Logger LOG = LoggerFactory.getLogger(TypeDefManager.class);

    private final AtlasClient client;

    /**
     * 주어진 클라이언트로 Atlas와 통신하는 관리자를 만듭니다.
     *
     * @param client 사용할 Atlas 클라이언트
     * @throws NullPointerException {@code client}가 {@code null}일 때
     */
    public TypeDefManager(AtlasClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    // ---------------------------------------------------------------- 생성 / 수정

    /**
     * 서버의 모든 타입 정의를 가져와({@code GET /api/atlas/v2/types/typedefs}) 원하는 정의와 비교한 plan을 만듭니다.
     * Atlas에 아무것도 바꾸지 않습니다.
     *
     * @param desired 있어야 할 모습의 정의
     * @return 계산된 plan({@link TypePlanner#plan(AtlasTypesDef, AtlasTypesDef)} 참고)
     */
    public TypePlan plan(AtlasTypesDef desired) {
        return TypePlanner.plan(desired, client.types().getAll());
    }

    /**
     * 빌더들을 {@code build()}하여 묶은 뒤 {@link #plan(AtlasTypesDef)}를 호출합니다.
     *
     * @param builders 타입 정의 빌더들
     * @return 계산된 plan
     * @throws IllegalStateException 어느 빌더의 정의가 구조적으로 잘못되었을 때
     */
    public TypePlan plan(TypeDefBuilder<?, ?>... builders) {
        return plan(TypeDefs.typesDef(builders));
    }

    /**
     * 파일 하나, 또는 디렉터리 아래 모든 정의 파일의 정의로 plan을 만듭니다.
     * 디렉터리이면 {@link TypeDefFiles#readAll(Path)}, 파일이면 {@link TypeDefFiles#read(Path)}로 읽습니다.
     *
     * @param fileOrDirectory 정의 파일 또는 디렉터리
     * @return 계산된 plan
     */
    public TypePlan plan(Path fileOrDirectory) {
        return plan(Files.isDirectory(fileOrDirectory)
                ? TypeDefFiles.readAll(fileOrDirectory) : TypeDefFiles.read(fileOrDirectory));
    }

    /**
     * plan을 적용합니다. 생성(POST)을 먼저, 그다음 수정(PUT), 마지막으로 삭제(DELETE) 순서로
     * {@code /api/atlas/v2/types/typedefs}를 호출합니다. 생성은 한 번의 호출로 보내므로 새 타입끼리 서로 참조할 수
     * 있습니다. 해당 동작의 변경이 없으면 그 호출은 건너뜁니다. 경고는 WARN 로그로 남깁니다.
     * <p>
     * 단계 사이에 실패하면 앞 단계는 이미 반영된 상태로 남습니다.
     *
     * @param plan 적용할 plan
     * @return 동작별로 처리한 타입 이름
     * @throws TypePlanException plan에 오류가 있을 때. 이 경우 아무것도 보내지 않습니다
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

    /**
     * {@link #plan(AtlasTypesDef)}를 수행하고 요약을 INFO 로그로 남긴 뒤 {@link #apply(TypePlan)}를 수행합니다.
     *
     * @param desired 있어야 할 모습의 정의
     * @return 동작별로 처리한 타입 이름
     * @throws TypePlanException plan에 오류가 있을 때. 이 경우 아무것도 보내지 않습니다
     */
    public TypeApplyResult sync(AtlasTypesDef desired) {
        TypePlan plan = plan(desired);
        LOG.info("{}", plan.summary());
        return apply(plan);
    }

    /**
     * 빌더들을 {@code build()}하여 묶은 뒤 {@link #sync(AtlasTypesDef)}를 호출합니다.
     *
     * @param builders 타입 정의 빌더들
     * @return 동작별로 처리한 타입 이름
     * @throws IllegalStateException 어느 빌더의 정의가 구조적으로 잘못되었을 때
     * @throws TypePlanException     plan에 오류가 있을 때
     */
    public TypeApplyResult sync(TypeDefBuilder<?, ?>... builders) {
        return sync(TypeDefs.typesDef(builders));
    }

    // ---------------------------------------------------------------- 삭제

    /**
     * 타입 삭제 plan을 만들고, Atlas가 거부할 상황을 모두 오류로 보고합니다.
     * <ul>
     *   <li>삭제 대상이 아닌 다른 타입(남아 있는 하위 타입, relationship, 속성)이 삭제할 타입을 참조하는 경우</li>
     *   <li>엔티티 타입에 엔티티가 남아 있는 경우(soft delete된 엔티티 포함)</li>
     *   <li>classification이 아직 엔티티에 붙어 있는 경우</li>
     * </ul>
     * 엔티티와 classification 확인은 타입마다 basic search를 한 번씩(결과 1건 제한) 호출합니다.
     * relationship 인스턴스는 미리 확인할 수 없으므로 경고만 남깁니다. 서버에 없는 타입은 경고와 함께 건너뜁니다.
     *
     * @param typeNames 삭제할 타입 이름들(중복은 무시합니다)
     * @return DELETE 변경과 발견된 문제를 담은 plan
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

    /**
     * {@link #planDelete(Collection)}를 수행한 뒤 {@link #apply(TypePlan)}를 수행합니다.
     *
     * @param typeNames 삭제할 타입 이름들
     * @return 동작별로 처리한 타입 이름
     * @throws TypePlanException 삭제 plan에 오류가 있을 때. 이 경우 아무것도 삭제하지 않습니다
     */
    public TypeApplyResult delete(Collection<String> typeNames) {
        return apply(planDelete(typeNames));
    }

    /**
     * 삭제하려는 타입의 인스턴스가 남아 있는지 확인합니다. Atlas는 인스턴스가 있는 타입의 삭제를 거부하므로
     * 미리 오류로 알립니다. 하위 타입의 인스턴스는 세지 않고, soft delete된 엔티티는 셉니다(Atlas도 이를 막기 때문입니다).
     */
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

    // ---------------------------------------------------------------- 내보내기

    /**
     * 주어진 타입들의 정의를 Atlas에 저장된 그대로, 서버가 관리하는 필드만 빼고 돌려줍니다.
     * 서버에 없는 이름은 조용히 무시합니다.
     *
     * @param typeNames 내보낼 타입 이름들
     * @return {@link TypeDefFiles#stripServerFields(AtlasTypesDef)}를 거친 정의
     */
    public AtlasTypesDef export(Collection<String> typeNames) {
        Set<String> wanted = new LinkedHashSet<>(typeNames);
        AtlasTypesDef result = new AtlasTypesDef();
        client.types().getAll().stream().filter(d -> wanted.contains(d.getName())).forEach(result::add);
        return TypeDefFiles.stripServerFields(result);
    }

    /**
     * 한 {@code serviceType}(예: {@code hive})에 속한 모든 정의를 서버가 관리하는 필드 없이 돌려줍니다.
     *
     * @param serviceType 서비스 타입 이름
     * @return {@link TypeDefFiles#stripServerFields(AtlasTypesDef)}를 거친 정의
     */
    public AtlasTypesDef exportByServiceType(String serviceType) {
        AtlasTypesDef result = new AtlasTypesDef();
        client.types().getAll().stream().filter(d -> serviceType.equals(d.getServiceType())).forEach(result::add);
        return TypeDefFiles.stripServerFields(result);
    }

    /** plan에서 주어진 동작의 타입 이름만 순서대로 뽑습니다. */
    private static List<String> names(TypePlan plan, Action action) {
        return plan.getChanges(action).stream().map(TypeChange::name).collect(Collectors.toList());
    }
}
