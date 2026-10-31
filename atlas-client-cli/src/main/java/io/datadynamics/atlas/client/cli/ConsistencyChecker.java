package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.instance.AtlasRelatedObjectId;
import io.datadynamics.atlas.client.model.instance.EntityStatus;
import io.datadynamics.atlas.client.model.search.SearchParameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Atlas가 막아 주지 않는 메타데이터 문제를 찾습니다.
 * <p>
 * 찾는 문제는 다음 세 가지입니다.
 * <ul>
 *   <li>리니지 연결(inputs/outputs)이 없는 프로세스</li>
 *   <li>삭제된 데이터셋을 가리키는 리니지</li>
 *   <li>같은 qualifiedName을 가진 활성(ACTIVE) 엔티티가 여러 개인 경우</li>
 * </ul>
 * {@code atlas check} 명령이 이 클래스를 쓰지만, public이므로 애플리케이션에서 직접 호출할 수도 있습니다.
 * 검사는 읽기 전용이며 Atlas의 데이터를 바꾸지 않습니다.
 */
public final class ConsistencyChecker {

    /** 발견한 문제의 종류입니다. */
    public enum Kind {
        /** 입력도 출력도 없는 프로세스입니다. 리니지에 아무것도 기여하지 않습니다. */
        ORPHAN_PROCESS,
        /** 입력은 있는데 출력이 없거나, 반대로 출력은 있는데 입력이 없는 프로세스입니다. */
        INCOMPLETE_PROCESS,
        /** 현재 유효한 리니지 연결이 삭제된 엔티티를 가리키는 프로세스입니다. */
        DELETED_REFERENCE,
        /** 한 타입 안에서 같은 qualifiedName을 가진 활성 엔티티가 여러 개인 경우입니다. */
        DUPLICATE
    }

    /**
     * 발견한 문제 하나입니다.
     *
     * @param kind          문제의 종류
     * @param typeName      문제가 있는 엔티티의 타입
     * @param qualifiedName 문제가 있는 엔티티의 qualifiedName
     * @param guid          문제가 있는 엔티티의 guid. 중복인 경우 그룹의 첫 번째 엔티티입니다
     * @param message       사람이 읽을 수 있는 설명(영어)
     */
    public record Finding(Kind kind, String typeName, String qualifiedName, String guid, String message) {

        /** {@code 종류 타입:qualifiedName (guid) 설명} 형식의 한 줄 문자열을 돌려줍니다. 종류는 18자 폭으로 맞춥니다. */
        @Override
        public String toString() {
            return String.format("%-18s %s:%s (%s) %s", kind, typeName, qualifiedName, guid, message);
        }
    }

    /**
     * 프로세스 엔티티를 한 번의 bulk 조회 요청으로 가져올 최대 개수입니다.
     * guid를 쿼리 문자열로 보내는 GET 요청이므로, 나눠서 조회해 요청 하나의 크기를 제한합니다.
     */
    static final int BULK_FETCH_SIZE = 100;

    private final AtlasClient client;

    /**
     * 검사기를 만듭니다.
     *
     * @param client 검사에 쓸 Atlas 클라이언트
     */
    public ConsistencyChecker(AtlasClient client) {
        this.client = client;
    }

    /**
     * {@code processType}과 그 하위 타입의 모든 활성 엔티티를 검사합니다.
     * <p>
     * basic search로 활성 프로세스를 모두 찾은 뒤, 같은 qualifiedName을 가진 프로세스를 중복으로 보고합니다.
     * 이어서 {@link #BULK_FETCH_SIZE}개씩 {@code GET /api/atlas/v2/entity/bulk}로 엔티티를 가져와
     * inputs/outputs 관계를 확인합니다.
     *
     * @param processType 검사할 프로세스 타입(예: {@code Process}). 하위 타입도 포함됩니다
     * @return 발견한 문제 목록. 문제가 없으면 빈 목록
     */
    public List<Finding> checkProcesses(String processType) {
        List<AtlasEntityHeader> processes = activeEntities(processType, true);
        List<Finding> findings = new ArrayList<>(duplicates(processes));
        List<String> guids = processes.stream().map(AtlasEntityHeader::getGuid).collect(Collectors.toList());
        // 관계 상태(relationshipStatus/entityStatus)를 보려면 relationshipAttributes가 필요하므로
        // ignoreRelationships는 false로, 참조된 엔티티 본문은 필요 없으므로 minExtInfo는 true로 요청합니다.
        for (int i = 0; i < guids.size(); i += BULK_FETCH_SIZE) {
            AtlasEntitiesWithExtInfo bulk = client.entities()
                    .getBulk(guids.subList(i, Math.min(i + BULK_FETCH_SIZE, guids.size())), true, false);
            if (bulk != null && bulk.getEntities() != null) {
                bulk.getEntities().forEach(process -> checkProcess(process, findings));
            }
        }
        return findings;
    }

    /**
     * 정확히 {@code typeName} 타입인(하위 타입은 제외) 활성 엔티티 중 qualifiedName이 같은 것이 있는지 검사합니다.
     *
     * @param typeName 검사할 엔티티 타입
     * @return 중복 그룹마다 {@link Kind#DUPLICATE} 문제 하나씩. 중복이 없으면 빈 목록
     */
    public List<Finding> checkDuplicates(String typeName) {
        return duplicates(activeEntities(typeName, false));
    }

    /**
     * 프로세스 하나의 inputs/outputs를 검사해 문제를 {@code findings}에 추가합니다.
     * <p>
     * 삭제된 엔티티를 가리키는 연결은 하나하나 {@link Kind#DELETED_REFERENCE}로 보고하고, 그와 별개로 유효한
     * 연결의 개수에 따라 {@link Kind#ORPHAN_PROCESS} 또는 {@link Kind#INCOMPLETE_PROCESS}를 보고합니다.
     */
    private void checkProcess(AtlasEntity process, List<Finding> findings) {
        String qualifiedName = String.valueOf(process.getAttribute("qualifiedName"));
        Edges inputs = edges(process, "inputs");
        Edges outputs = edges(process, "outputs");

        for (AtlasRelatedObjectId deleted : inputs.deletedTargets) {
            findings.add(new Finding(Kind.DELETED_REFERENCE, process.getTypeName(), qualifiedName, process.getGuid(),
                    "input " + describe(deleted) + " is deleted"));
        }
        for (AtlasRelatedObjectId deleted : outputs.deletedTargets) {
            findings.add(new Finding(Kind.DELETED_REFERENCE, process.getTypeName(), qualifiedName, process.getGuid(),
                    "output " + describe(deleted) + " is deleted"));
        }
        if (inputs.active == 0 && outputs.active == 0) {
            findings.add(new Finding(Kind.ORPHAN_PROCESS, process.getTypeName(), qualifiedName, process.getGuid(),
                    "has no inputs and no outputs" + inputs.note("inputs") + outputs.note("outputs")));
        } else if (inputs.active == 0) {
            findings.add(new Finding(Kind.INCOMPLETE_PROCESS, process.getTypeName(), qualifiedName, process.getGuid(),
                    "has no inputs" + inputs.note("inputs")));
        } else if (outputs.active == 0) {
            findings.add(new Finding(Kind.INCOMPLETE_PROCESS, process.getTypeName(), qualifiedName, process.getGuid(),
                    "has no outputs" + outputs.note("outputs")));
        }
    }

    /** 프로세스 한쪽(inputs 또는 outputs)의 연결을 분류한 결과입니다. */
    private static final class Edges {
        /** 활성 엔티티를 가리키는 유효한 연결 수입니다. */
        int active;
        /** 연결과 대상 엔티티가 모두 삭제된 과거 연결 수입니다. 메시지의 참고 정보로만 씁니다. */
        int removed;
        /** 연결은 유효한데 대상 엔티티가 삭제된, 깨진 연결의 대상 목록입니다. */
        final List<AtlasRelatedObjectId> deletedTargets = new ArrayList<>();

        /** 삭제된 과거 연결이 있으면 메시지 끝에 붙일 설명을, 없으면 빈 문자열을 돌려줍니다. */
        String note(String side) {
            return removed == 0 ? "" : " (" + removed + " former " + side + " were deleted)";
        }
    }

    /**
     * {@code relationshipAttributes}에 있는 연결을 분류합니다.
     * <p>
     * 활성 엔티티를 가리키는 활성 연결은 유효한 연결로 셉니다. 삭제된 엔티티를 가리키는 활성 연결은 깨진
     * 연결입니다. 삭제된 연결은 과거 기록입니다. 이런 연결은 프로세스가 다른 데이터셋으로 다시 등록되었거나
     * 데이터셋이 삭제되었을 때 남습니다. 그중 대상도 삭제된 것만 {@link Edges#removed}로 세고,
     * 대상이 아직 활성인 삭제된 연결은 문제가 아니므로 무시합니다.
     *
     * @param side {@code "inputs"} 또는 {@code "outputs"}
     */
    private static Edges edges(AtlasEntity process, String side) {
        Edges edges = new Edges();
        Map<String, Object> relationships = process.getRelationshipAttributes();
        Object value = relationships == null ? null : relationships.get(side);
        if (value == null) {
            // 관계 정보가 없으면 상태를 알 수 없으므로 일반 속성의 원소 수를 모두 유효한 연결로 셉니다.
            Object attribute = process.getAttribute(side);
            edges.active = attribute instanceof Collection ? ((Collection<?>) attribute).size() : 0;
            return edges;
        }
        for (Object item : value instanceof Collection ? (Collection<?>) value : List.of(value)) {
            AtlasRelatedObjectId related = AtlasJson.convert(item, AtlasRelatedObjectId.class);
            boolean edgeActive = related.getRelationshipStatus() != EntityStatus.DELETED;
            boolean targetActive = related.getEntityStatus() != EntityStatus.DELETED;
            if (edgeActive && targetActive) {
                edges.active++;
            } else if (edgeActive) {
                edges.deletedTargets.add(related);
            } else if (!targetActive) {
                edges.removed++;
            }
        }
        return edges;
    }

    /** 메시지에 쓸 {@code 타입:이름} 문자열을 만듭니다. displayText가 없으면 guid를 씁니다. */
    private static String describe(AtlasRelatedObjectId related) {
        String name = related.getDisplayText() != null ? related.getDisplayText() : related.getGuid();
        return related.getTypeName() + ":" + name;
    }

    /**
     * basic search로 삭제되지 않은 엔티티를 모두 가져옵니다. 결과는 페이지 단위로 끝까지 읽어 목록으로 모읍니다.
     *
     * @param includeSubTypes 하위 타입의 엔티티도 포함할지 여부
     */
    private List<AtlasEntityHeader> activeEntities(String typeName, boolean includeSubTypes) {
        SearchParameters parameters = SearchParameters.forType(typeName);
        parameters.setIncludeSubTypes(includeSubTypes);
        parameters.setExcludeDeletedEntities(true);
        return client.search().basicStream(parameters).collect(Collectors.toList());
    }

    /**
     * 타입과 qualifiedName이 모두 같은 엔티티를 묶어, 두 개 이상인 그룹마다 {@link Kind#DUPLICATE}를 하나 만듭니다.
     * <p>
     * 하위 타입을 포함해 검색한 경우에도 타입이 다르면 중복으로 보지 않습니다. 키는 타입과 qualifiedName 사이에
     * NUL 문자를 넣어 만들어 두 값의 경계가 섞이지 않게 합니다. {@link LinkedHashMap}을 써서 검색 결과 순서대로
     * 보고합니다. qualifiedName이 없는 엔티티는 건너뜁니다.
     */
    private static List<Finding> duplicates(List<AtlasEntityHeader> entities) {
        Map<String, List<AtlasEntityHeader>> byName = new LinkedHashMap<>();
        for (AtlasEntityHeader entity : entities) {
            if (entity.qualifiedName() != null) {
                byName.computeIfAbsent(entity.getTypeName() + "\u0000" + entity.qualifiedName(), k -> new ArrayList<>())
                        .add(entity);
            }
        }
        List<Finding> findings = new ArrayList<>();
        byName.values().stream().filter(group -> group.size() > 1).forEach(group -> {
            AtlasEntityHeader first = group.get(0);
            String others = group.stream().skip(1).map(AtlasEntityHeader::getGuid).collect(Collectors.joining(", "));
            findings.add(new Finding(Kind.DUPLICATE, first.getTypeName(), first.qualifiedName(), first.getGuid(),
                    group.size() + " active entities share this qualifiedName; others: " + others));
        });
        return findings;
    }
}
