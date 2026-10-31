package io.datadynamics.atlas.client.lineage;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.lineage.graph.Impact;
import io.datadynamics.atlas.client.lineage.graph.LineageGraph;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo.LineageDirection;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo.LineageRelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 리니지를 등록하고 조회합니다.
 * <p>
 * 프로세스(작업, 쿼리, 파이프라인 단계)가 어떤 데이터셋을 읽고 쓰는지 Atlas에 등록하고, 등록된 리니지를
 * {@link LineageGraph}로 조회하거나 어떤 데이터셋이 바뀌었을 때 영향을 받는 데이터셋({@link #impact(EntityRef, int)}),
 * 어떤 데이터셋의 원천 데이터셋({@link #sources(EntityRef, int)})을 구할 수 있습니다.
 * <pre>{@code
 * AtlasLineage lineage = new AtlasLineage(atlas);
 *
 * lineage.process("etl_job", "daily_sales_agg@prod")
 *     .inputs(EntityRef.of("hive_table", "raw.sales@prod"))
 *     .outputs(EntityRef.of("hive_table", "mart.daily_sales@prod"))
 *     .register();
 *
 * LineageGraph graph = lineage.graph(EntityRef.of("hive_table", "mart.daily_sales@prod"));
 * System.out.println(graph.toMermaid());
 *
 * lineage.impact(EntityRef.of("hive_table", "raw.sales@prod"), 5)
 *     .forEach(i -> System.out.println(i.dataset() + " at distance " + i.distance()));
 * }</pre>
 * 컬럼 수준 리니지는 {@link #columnLineageType(String, String)}로 지정한 타입의 엔티티로 등록합니다.
 * 기본값은 Atlas가 기본으로 제공하는 유일한 컬럼 리니지 타입인 {@code hive_column_lineage}입니다.
 * 각 컬럼 리니지 엔티티는 부모 속성(기본값 {@code query})을 통해 자신을 소유한 프로세스와 연결됩니다.
 * <p>
 * 설정 메서드({@link #missingEntities(MissingEntityPolicy)} 등)는 필드를 동기화 없이 바꾸므로, 설정은
 * 여러 스레드에서 공유하기 전에 한 번만 해 두는 것이 안전합니다.
 */
public final class AtlasLineage {

    /** 컬럼 리니지 엔티티의 기본 타입 이름입니다. */
    public static final String DEFAULT_COLUMN_LINEAGE_TYPE = "hive_column_lineage";
    /** 컬럼 리니지 엔티티가 소유 프로세스를 가리키는 기본 속성 이름입니다. */
    public static final String DEFAULT_COLUMN_LINEAGE_PARENT_ATTRIBUTE = "query";
    /** {@link #graph(EntityRef)}가 각 방향으로 따라가는 기본 프로세스 홉 수입니다. */
    public static final int DEFAULT_DEPTH = 3;
    // 삭제된 relationship을 걸러낼 때 한 번의 bulk 조회로 가져오는 최대 프로세스 엔티티 수입니다.
    static final int BULK_FETCH_SIZE = 100;

    private static final Logger LOG = LoggerFactory.getLogger(AtlasLineage.class);

    private final AtlasClient client;
    // 엔티티 타입 이름을 받아 프로세스 타입인지 판별합니다. 그래프에서 프로세스와 데이터셋을 구분하는 데 씁니다.
    private final Predicate<String> processTypes;
    private MissingEntityPolicy missingEntityPolicy = MissingEntityPolicy.FAIL;
    private String columnLineageType = DEFAULT_COLUMN_LINEAGE_TYPE;
    private String columnLineageParentAttribute = DEFAULT_COLUMN_LINEAGE_PARENT_ATTRIBUTE;
    private boolean includeDeletedRelationships;
    // 기본 컬럼 리니지 qualifiedName은 "프로세스 qualifiedName:출력 컬럼 qualifiedName" 형식입니다.
    private BinaryOperator<String> columnLineageQualifiedName = (processQualifiedName, outputColumnQualifiedName) ->
            processQualifiedName + ":" + outputColumnQualifiedName;

    /**
     * 주어진 클라이언트로 리니지를 다루는 인스턴스를 만듭니다.
     * <p>
     * 어떤 엔티티 타입이 프로세스인지는 {@link ProcessTypeResolver}가 Atlas의 타입 정의를 필요할 때 조회해
     * 판단합니다(결과는 캐시합니다).
     *
     * @param client Atlas REST 클라이언트
     */
    public AtlasLineage(AtlasClient client) {
        this(client, new ProcessTypeResolver(client));
    }

    /**
     * 프로세스 타입 판별 방법을 직접 지정해 인스턴스를 만듭니다.
     *
     * @param client       Atlas REST 클라이언트
     * @param processTypes 어떤 엔티티 타입이 프로세스인지 알려 주는 조건. 기본 생성자는 타입 정의에서 판별합니다.
     * @throws NullPointerException 인자가 {@code null}인 경우
     */
    public AtlasLineage(AtlasClient client, Predicate<String> processTypes) {
        this.client = Objects.requireNonNull(client, "client");
        this.processTypes = Objects.requireNonNull(processTypes, "processTypes");
    }

    // ---------------------------------------------------------------- 설정

    /**
     * 프로세스의 입력·출력으로 참조한 엔티티가 Atlas에 아직 없을 때 어떻게 할지 정합니다.
     * 기본값은 {@link MissingEntityPolicy#FAIL}입니다.
     *
     * @param policy 없는 엔티티를 처리하는 정책
     * @return 메서드 체이닝을 위한 이 인스턴스
     */
    public AtlasLineage missingEntities(MissingEntityPolicy policy) {
        this.missingEntityPolicy = Objects.requireNonNull(policy, "policy");
        return this;
    }

    /**
     * 컬럼 수준 리니지를 등록할 때 쓰는 엔티티 타입과 부모 속성을 정합니다.
     *
     * @param typeName        컬럼 리니지 프로세스의 엔티티 타입({@code Process}를 상속해야 합니다)
     * @param parentAttribute 소유 프로세스를 가리키는 속성 이름. 이런 속성이 없으면 {@code null}
     * @return 메서드 체이닝을 위한 이 인스턴스
     */
    public AtlasLineage columnLineageType(String typeName, String parentAttribute) {
        this.columnLineageType = Objects.requireNonNull(typeName, "typeName");
        this.columnLineageParentAttribute = parentAttribute;
        return this;
    }

    /**
     * 컬럼 리니지 엔티티의 qualifiedName을 짓는 방법을 정합니다. 함수는 프로세스의 qualifiedName과
     * 출력 컬럼의 qualifiedName을 차례로 받아 컬럼 리니지 엔티티의 qualifiedName을 돌려줍니다.
     * 기본값은 {@code 프로세스QN + ":" + 출력컬럼QN}입니다.
     *
     * @param naming 이름을 만드는 함수
     * @return 메서드 체이닝을 위한 이 인스턴스
     */
    public AtlasLineage columnLineageQualifiedName(BinaryOperator<String> naming) {
        this.columnLineageQualifiedName = Objects.requireNonNull(naming, "naming");
        return this;
    }

    /**
     * 그래프에 soft-delete된 relationship의 간선도 남길지 정합니다(기본값 {@code false}).
     * <p>
     * 프로세스를 다른 입력이나 출력으로 다시 등록하면 Atlas는 예전 relationship을 soft-delete하지만,
     * 리니지 API는 여전히 그것들을 돌려줍니다. 기본 설정에서는 이런 간선을 제거하므로 그래프가 현재
     * 리니지만 보여 줍니다. 과거 간선까지 보고 싶으면 이 옵션을 켜십시오. 걸러내는 데에는 그래프 안의
     * 프로세스 {@value #BULK_FETCH_SIZE}개마다 엔티티 bulk 조회 호출이 한 번씩 듭니다.
     *
     * @param include soft-delete된 간선을 포함하려면 {@code true}
     * @return 메서드 체이닝을 위한 이 인스턴스
     */
    public AtlasLineage includeDeletedRelationships(boolean include) {
        this.includeDeletedRelationships = include;
        return this;
    }

    // ---------------------------------------------------------------- 등록

    /**
     * 프로세스 하나의 리니지를 기술하는 빌더를 시작합니다. 빌더에 입력·출력을 지정한 뒤
     * {@link ProcessBuilder#register()}로 등록합니다.
     *
     * @param typeName      프로세스 엔티티 타입(예: {@code etl_job}, {@code hive_process})
     * @param qualifiedName 프로세스의 qualifiedName. 같은 값으로 다시 등록하면 기존 프로세스를 갱신합니다.
     * @return 새 프로세스 빌더
     */
    public ProcessBuilder process(String typeName, String qualifiedName) {
        return new ProcessBuilder(this, typeName, qualifiedName);
    }

    /**
     * 여러 프로세스를 Atlas 트랜잭션 하나로 등록합니다. 모두 성공하거나 모두 실패합니다.
     * <p>
     * 모든 프로세스, 컬럼 리니지 엔티티, (정책이 {@link MissingEntityPolicy#CREATE}인 경우) 없던 엔티티를
     * 한 번의 엔티티 createOrUpdate 요청으로 보냅니다. 정책이 {@link MissingEntityPolicy#NO_CHECK}가 아니면
     * 보내기 전에 참조한 엔티티마다 존재 여부를 조회합니다.
     *
     * @param processes 등록할 프로세스 빌더들
     * @return 등록 결과(생성·갱신된 엔티티의 guid 등)
     * @throws LineageException      참조한 엔티티가 없고 정책이 {@link MissingEntityPolicy#FAIL}인 경우
     * @throws IllegalStateException 입력과 출력이 모두 없는 프로세스가 있는 경우
     */
    public LineageRegistration register(List<ProcessBuilder> processes) {
        Payload payload = payload(processes);
        LOG.debug("Registering lineage: {} processes, {} entities in total", processes.size(), payload.entities().size());
        EntityMutationResponse response = client.entities().createOrUpdate(payload.entities());
        return new LineageRegistration(resolveGuids(payload.placeholders(), response), payload.missing(), response);
    }

    /**
     * {@link #register(List)}가 보낼 엔티티들을 돌려줍니다. 다른 경로로 보내고 싶을 때 씁니다.
     * 예를 들어 {@code AtlasHookPublisher.createOrUpdate(...)}로 {@code ATLAS_HOOK} 토픽에 보낼 수 있습니다.
     * 없는 엔티티 처리 정책은 {@code register}와 똑같이 적용됩니다. Atlas를 호출하지 않고 페이로드만
     * 만들려면 {@link MissingEntityPolicy#NO_CHECK}를 쓰십시오.
     *
     * @param processes 변환할 프로세스 빌더들
     * @return Atlas에 보낼 엔티티 묶음
     * @throws LineageException 참조한 엔티티가 없고 정책이 {@link MissingEntityPolicy#FAIL}인 경우
     */
    public AtlasEntitiesWithExtInfo toEntities(List<ProcessBuilder> processes) {
        return new AtlasEntitiesWithExtInfo(payload(processes).entities());
    }

    /**
     * 등록 요청에 담을 내용입니다.
     *
     * @param entities     보낼 엔티티 전체(없던 엔티티, 프로세스, 컬럼 리니지 순)
     * @param placeholders {@link EntityRef#key()}별로 이번 요청에서 새로 만든 엔티티(임시 guid 보유)
     * @param missing      Atlas에 없어서 새로 만드는 엔티티 참조
     */
    private record Payload(List<AtlasEntity> entities, Map<String, AtlasEntity> placeholders, List<EntityRef> missing) {
    }

    /**
     * 프로세스 빌더들로부터 보낼 엔티티 목록을 만듭니다.
     * <p>
     * 순서: (1) 각 빌더 검증 → (2) 참조한 엔티티를 중복 없이 모음 → (3) 정책에 따라 존재 여부 확인 →
     * (4) 없는 엔티티의 스텁 생성 → (5) 프로세스와 컬럼 리니지 엔티티 생성.
     * 같은 요청 안에서 만드는 엔티티는 qualifiedName이 아니라 임시(placeholder) guid로 참조해야 Atlas가
     * 요청 안의 엔티티와 연결할 수 있으므로, 만든 엔티티를 {@code placeholders}에 모아 두고 이후 참조에 씁니다.
     */
    private Payload payload(List<ProcessBuilder> processes) {
        processes.forEach(ProcessBuilder::validate);

        // 프로세스 입력·출력과 컬럼 리니지의 입력·출력 컬럼을 모두 모읍니다. 처음 나온 참조를 유지합니다.
        Map<String, EntityRef> referenced = new LinkedHashMap<>();
        for (ProcessBuilder process : processes) {
            process.inputs().forEach(r -> referenced.putIfAbsent(r.key(), r));
            process.outputs().forEach(r -> referenced.putIfAbsent(r.key(), r));
            for (ProcessBuilder.ColumnLineage column : process.columnLineages()) {
                referenced.putIfAbsent(column.output().key(), column.output());
                column.inputs().forEach(r -> referenced.putIfAbsent(r.key(), r));
            }
        }

        // NO_CHECK가 아니면 참조마다 typeName과 qualifiedName으로 guid를 조회해 없는 것을 찾습니다.
        List<EntityRef> missing = missingEntityPolicy == MissingEntityPolicy.NO_CHECK ? List.of()
                : referenced.values().stream()
                .filter(r -> client.entities().findGuid(r.typeName(), r.qualifiedName()).isEmpty())
                .collect(Collectors.toList());
        if (!missing.isEmpty() && missingEntityPolicy == MissingEntityPolicy.FAIL) {
            throw new LineageException("Lineage references entities that do not exist in Atlas: " + missing
                    + " (create them first, or use MissingEntityPolicy.CREATE)", missing);
        }

        // 여기까지 왔는데 missing이 비어 있지 않다면 정책은 CREATE이므로 없는 엔티티의 스텁을 만듭니다.
        List<AtlasEntity> entities = new ArrayList<>();
        Map<String, AtlasEntity> placeholders = new LinkedHashMap<>();
        for (EntityRef ref : missing) {
            AtlasEntity stub = newEntity(ref.typeName(), ref.qualifiedName(), ref.defaultName(), ref.attributes());
            placeholders.put(ref.key(), stub);
            entities.add(stub);
        }

        for (ProcessBuilder process : processes) {
            EntityRef processRef = process.process();
            AtlasEntity processEntity = newEntity(processRef.typeName(), processRef.qualifiedName(),
                    processRef.defaultName(), process.attributes());
            processEntity.setAttribute("inputs", references(process.inputs(), placeholders));
            processEntity.setAttribute("outputs", references(process.outputs(), placeholders));
            placeholders.put(processRef.key(), processEntity);
            entities.add(processEntity);

            for (ProcessBuilder.ColumnLineage column : process.columnLineages()) {
                String qualifiedName = columnLineageQualifiedName.apply(processRef.qualifiedName(),
                        column.output().qualifiedName());
                AtlasEntity columnEntity = newEntity(columnLineageType, qualifiedName, qualifiedName, column.attributes());
                columnEntity.setAttribute("inputs", references(column.inputs(), placeholders));
                columnEntity.setAttribute("outputs", references(List.of(column.output()), placeholders));
                // 부모 속성은 같은 요청 안의 프로세스 엔티티를 임시 guid로 가리킵니다.
                if (columnLineageParentAttribute != null) {
                    columnEntity.setAttribute(columnLineageParentAttribute, processEntity.toObjectId());
                }
                placeholders.put(EntityRef.of(columnLineageType, qualifiedName).key(), columnEntity);
                entities.add(columnEntity);
            }
        }
        return new Payload(entities, placeholders, missing);
    }

    /**
     * 프로세스 엔티티를 삭제합니다. 그러면 그 프로세스가 만든 리니지 간선도 사라집니다.
     * typeName과 qualifiedName으로 엔티티를 삭제하는 API를 호출합니다.
     *
     * @param process 삭제할 프로세스 참조
     * @return Atlas의 삭제 응답
     */
    public EntityMutationResponse unregister(EntityRef process) {
        return client.entities().deleteByQualifiedName(process.typeName(), process.qualifiedName());
    }

    /**
     * qualifiedName, name과 추가 속성을 채운 새 엔티티를 만들고 임시 guid를 부여합니다.
     * 추가 속성은 name 다음에 설정되므로 {@code attributes}에 {@code name}이 있으면 기본 이름을 덮어씁니다.
     */
    private static AtlasEntity newEntity(String typeName, String qualifiedName, String defaultName,
                                         Map<String, Object> attributes) {
        AtlasEntity entity = new AtlasEntity(typeName);
        entity.setAttribute(AtlasObjectId.QUALIFIED_NAME, qualifiedName);
        entity.setAttribute("name", defaultName);
        attributes.forEach(entity::setAttribute);
        return entity.assignPlaceholderGuid();
    }

    /**
     * 참조 목록을 {@link AtlasObjectId} 목록으로 바꿉니다. 이번 요청에서 만드는 엔티티는 임시 guid로,
     * 이미 Atlas에 있는 엔티티는 typeName과 qualifiedName(uniqueAttributes)으로 가리킵니다.
     */
    private static List<AtlasObjectId> references(List<EntityRef> refs, Map<String, AtlasEntity> placeholders) {
        return refs.stream()
                .map(r -> placeholders.containsKey(r.key()) ? placeholders.get(r.key()).toObjectId() : r.toObjectId())
                .collect(Collectors.toList());
    }

    /**
     * 요청에 넣은 엔티티들의 실제 guid를 응답에서 찾아 {@link EntityRef#key()}별로 돌려줍니다.
     * <p>
     * 먼저 응답의 guidAssignments(임시 guid → 실제 guid)에서 찾습니다. 그곳에 없으면 응답에 담긴 엔티티
     * 헤더에서 typeName과 qualifiedName으로 찾습니다. 둘 다 없으면 결과에서 빠집니다.
     */
    private static Map<String, String> resolveGuids(Map<String, AtlasEntity> placeholders, EntityMutationResponse response) {
        Map<String, String> assignments = response == null || response.getGuidAssignments() == null
                ? Map.of() : response.getGuidAssignments();
        Map<String, String> guids = new LinkedHashMap<>();
        placeholders.forEach((key, entity) -> {
            String guid = assignments.get(entity.getGuid());
            if (guid == null && response != null) {
                guid = response.findGuid(entity.getTypeName(), String.valueOf(entity.getAttribute(AtlasObjectId.QUALIFIED_NAME)))
                        .orElse(null);
            }
            if (guid != null) {
                guids.put(key, guid);
            }
        });
        return guids;
    }

    // ---------------------------------------------------------------- 조회

    /**
     * 엔티티를 중심으로 양방향({@link LineageDirection#BOTH}) {@value #DEFAULT_DEPTH}홉까지의 리니지 그래프를 조회합니다.
     *
     * @param entity 기준 엔티티
     * @return 리니지 그래프
     */
    public LineageGraph graph(EntityRef entity) {
        return graph(entity, LineageDirection.BOTH, DEFAULT_DEPTH);
    }

    /**
     * typeName과 qualifiedName으로 지정한 엔티티의 리니지 그래프를 조회합니다.
     * <p>
     * Atlas의 uniqueAttributes 기반 리니지 API를 호출합니다. 기준 엔티티에 리니지가 하나도 없어 응답에
     * 엔티티 정보가 없을 때를 대비해, typeName과 qualifiedName만 채운 헤더를 기준 노드의 대체값으로 넘깁니다.
     *
     * @param entity    기준 엔티티
     * @param direction 따라갈 방향(입력 쪽, 출력 쪽, 양쪽)
     * @param depth     각 방향으로 따라갈 프로세스 홉 수
     * @return 리니지 그래프(기본 설정에서는 soft-delete된 relationship을 제외)
     */
    public LineageGraph graph(EntityRef entity, LineageDirection direction, int depth) {
        AtlasLineageInfo info = client.lineage().getByQualifiedName(entity.typeName(), entity.qualifiedName(),
                direction, depth);
        AtlasEntityHeader fallback = new AtlasEntityHeader();
        fallback.setTypeName(entity.typeName());
        fallback.setAttribute(AtlasObjectId.QUALIFIED_NAME, entity.qualifiedName());
        return LineageGraph.from(withoutDeletedRelationships(info), processTypes, fallback);
    }

    /**
     * guid로 지정한 엔티티의 리니지 그래프를 조회합니다.
     *
     * @param guid      기준 엔티티의 guid
     * @param direction 따라갈 방향
     * @param depth     각 방향으로 따라갈 프로세스 홉 수
     * @return 리니지 그래프(기본 설정에서는 soft-delete된 relationship을 제외)
     */
    public LineageGraph graph(String guid, LineageDirection direction, int depth) {
        return LineageGraph.from(withoutDeletedRelationships(client.lineage().get(guid, direction, depth)), processTypes);
    }

    /**
     * relationship이 soft-delete된 관계를 제거하고, 그 결과 어떤 것과도 연결되지 않게 된 엔티티를
     * (기준 엔티티는 제외하고) 제거합니다. 리니지 응답에는 relationship 상태가 들어 있지 않으므로,
     * 프로세스들을 bulk 조회해 그 {@code relationshipAttributes}에서 상태를 읽습니다.
     * 원본 {@code info}는 바꾸지 않고, 걸러낼 것이 있을 때만 깊은 복사본을 만들어 돌려줍니다.
     */
    private AtlasLineageInfo withoutDeletedRelationships(AtlasLineageInfo info) {
        if (includeDeletedRelationships || info.getRelations().isEmpty()) {
            return info;
        }
        List<String> processGuids = info.getGuidEntityMap().values().stream()
                .filter(h -> processTypes.test(h.getTypeName()))
                .map(AtlasEntityHeader::getGuid)
                .collect(Collectors.toList());
        Set<String> deleted = new HashSet<>();
        for (int i = 0; i < processGuids.size(); i += BULK_FETCH_SIZE) {
            List<String> chunk = processGuids.subList(i, Math.min(i + BULK_FETCH_SIZE, processGuids.size()));
            AtlasEntitiesWithExtInfo processes = client.entities().getBulk(chunk, true, false);
            if (processes != null && processes.getEntities() != null) {
                processes.getEntities().forEach(p -> collectDeletedRelationships(p.getRelationshipAttributes(), deleted));
            }
        }
        if (deleted.isEmpty()) {
            return info;
        }

        Set<LineageRelation> relations = info.getRelations().stream()
                .filter(r -> !deleted.contains(r.getRelationshipId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // 남은 관계의 양 끝 엔티티와 기준 엔티티만 남깁니다.
        Set<String> connected = new HashSet<>();
        relations.forEach(r -> {
            connected.add(r.getFromEntityId());
            connected.add(r.getToEntityId());
        });
        connected.add(info.getBaseEntityGuid());
        Map<String, AtlasEntityHeader> entities = new LinkedHashMap<>();
        info.getGuidEntityMap().forEach((guid, header) -> {
            if (connected.contains(guid)) {
                entities.put(guid, header);
            }
        });
        LOG.debug("Removed {} soft-deleted lineage relations", info.getRelations().size() - relations.size());

        AtlasLineageInfo filtered = AtlasJson.deepCopy(info);
        filtered.setRelations(relations);
        filtered.setGuidEntityMap(entities);
        return filtered;
    }

    /**
     * 엔티티의 {@code relationshipAttributes}를 훑어 {@code relationshipStatus}가 {@code DELETED}인 참조의
     * {@code relationshipGuid}를 {@code deleted}에 모읍니다. 속성 값은 참조 하나(맵)일 수도, 참조 목록일 수도
     * 있으므로 둘 다 처리합니다.
     */
    private static void collectDeletedRelationships(Map<String, Object> relationshipAttributes, Set<String> deleted) {
        if (relationshipAttributes == null) {
            return;
        }
        for (Object value : relationshipAttributes.values()) {
            List<?> references = value instanceof List ? (List<?>) value : Collections.singletonList(value);
            for (Object reference : references) {
                if (reference instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) reference;
                    if ("DELETED".equals(map.get("relationshipStatus")) && map.get("relationshipGuid") != null) {
                        deleted.add(map.get("relationshipGuid").toString());
                    }
                }
            }
        }
    }

    /**
     * {@code entity}가 바뀌면 영향을 받을 하위(출력 쪽) 데이터셋을 가까운 것부터 돌려줍니다.
     *
     * @param entity 기준 엔티티
     * @param depth  출력 방향으로 따라갈 프로세스 홉 수
     * @return 영향을 받는 데이터셋과 기준 엔티티로부터의 거리
     */
    public List<Impact> impact(EntityRef entity, int depth) {
        LineageGraph graph = graph(entity, LineageDirection.OUTPUT, depth);
        return graph.impactOf(graph.base());
    }

    /**
     * {@code entity}가 만들어지는 데 쓰인 상위(입력 쪽) 데이터셋을 가까운 것부터 돌려줍니다.
     *
     * @param entity 기준 엔티티
     * @param depth  입력 방향으로 따라갈 프로세스 홉 수
     * @return 원천 데이터셋과 기준 엔티티로부터의 거리
     */
    public List<Impact> sources(EntityRef entity, int depth) {
        LineageGraph graph = graph(entity, LineageDirection.INPUT, depth);
        return graph.sourcesOf(graph.base());
    }
}
