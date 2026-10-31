# Apache Atlas Client

Apache Atlas REST API를 쉽게 쓰기 위한 Java 클라이언트입니다. Hadoop, Jersey 의존성 없이 JDK HttpClient와 Jackson만 사용합니다.

## 모듈

| 모듈 | 내용 | 상태 |
|---|---|---|
| `atlas-client-core` | HTTP 전송, 인증(Basic/Bearer/Kerberos), HA, 재시도, 예외 매핑, Atlas v2 모델, 저수준 API | 구현 |
| `atlas-client-typedef` | 타입 정의 DSL, 검증, plan/apply, 안전한 삭제, YAML/JSON 입출력 | 구현 |
| `atlas-client-lineage` | 리니지 등록 DSL, 그래프 탐색, 영향도 분석, Mermaid/DOT 출력 | 구현 |
| `atlas-client-kafka` | `ATLAS_HOOK` 비동기 전송(압축·분할 포함), `ATLAS_ENTITIES` 변경 이벤트 리스너 | 구현 |
| `atlas-client-cli` | `atlas` 명령: 타입 plan/apply/export/delete, CSV·YAML 대량 등록, 리니지 조회, 정합성 점검, 이벤트 tail | 구현 |
| `atlas-client-test` | 단위 테스트용 가짜 Atlas 서버(`FakeAtlasServer`), Testcontainers 기반 `AtlasContainer` | 구현 |
| `atlas-client-it` | 실제 Atlas 대상 통합 테스트 (`-Pit`) | 구현 |
| `atlas-client-demo` | 위 모듈의 기능을 브라우저에서 실행해 보는 Spring Boot 웹 앱. Atlas·Kafka와 함께 docker compose로 기동 | 구현 |

## 빌드

```bash
mvn clean verify                                        # 단위 테스트 (JDK 17+)
mvn verify -Pit                                         # + 통합 테스트: Docker로 Kafka와 Atlas 기동 (약 3분)
mvn verify -Pit -Datlas.url=http://atlas-dev:21000 \
    -Datlas.username=admin -Datlas.password=admin \
    -Datlas.kafka.bootstrap.servers=kafka-dev:9092      # + 통합 테스트: 기존 Atlas 사용 (Kafka 설정이 없으면 Kafka 테스트는 건너뜀)
```

CLI 실행 파일은 `atlas-client-cli/target/atlas-client-cli-<버전>-all.jar`로 만들어집니다.

통합 테스트는 실행할 때마다 고유한 접두어를 붙인 타입과 엔티티를 만들고 끝나면 지웁니다. 그래서 개발용으로 함께 쓰는 Atlas에서 돌려도 됩니다. 기본 이미지는 `sburn/apache-atlas:2.3.0`이고 `-Datlas.image=...`로 바꿀 수 있습니다.

## 사용 예

```java
AtlasClient atlas = AtlasClient.builder()
        .serverUrls("http://atlas1:21000,http://atlas2:21000")   // HA: ACTIVE 서버 자동 탐지
        .basicAuth("admin", "admin")                              // 또는 kerberosKeytab(principal, keytab)
        .build();

// 타입: 존재 여부와 관계없이 생성 또는 수정
AtlasEntityDef table = new AtlasEntityDef("my_table");
table.addSuperType("DataSet");
table.addAttributeDef(new AtlasAttributeDef("owner", "string"));
atlas.types().createOrUpdate(AtlasTypesDef.of(table));

// 엔티티: guid 없이 qualifiedName으로 참조
AtlasEntity db = new AtlasEntity("hive_db", Map.of("qualifiedName", "sales@prod", "name", "sales"));
AtlasEntity t = new AtlasEntity("hive_table", Map.of("qualifiedName", "sales.orders@prod", "name", "orders"));
t.setRelationshipAttribute("db", db.toObjectId());
EntityMutationResponse res = atlas.entities().createOrUpdate(List.of(db, t));

Optional<String> guid = atlas.entities().findGuid("hive_table", "sales.orders@prod");

// 리니지
AtlasLineageInfo lineage = atlas.lineage().getByQualifiedName("hive_table", "sales.orders@prod");

// 검색: 페이징 자동 처리
SearchParameters params = SearchParameters.forType("hive_table");
params.setEntityFilters(FilterCriteria.of("owner", Operator.EQ, "kim"));
atlas.search().basicStream(params).forEach(h -> System.out.println(h.qualifiedName()));
```

## core 동작 방식

- **예외**: 모두 unchecked입니다. `AtlasNotFoundException`(404), `AtlasConflictException`(409) 등 HTTP 상태별 예외가 있고, Atlas 에러코드(`ATLAS-404-00-005`)는 `getErrorCode()`로 꺼낼 수 있습니다.
- **조회 메서드**: `find*`는 대상이 없으면 `Optional.empty()`를 돌려주고, `get*`은 예외를 던집니다.
- **재시도**: 연결 실패와 503(Passive 서버)은 항상 재시도합니다. 502/504와 읽기 타임아웃은 멱등 메서드(GET/PUT/DELETE)만 재시도합니다.
- **호환성**: 클라이언트가 모르는 JSON 필드도 보존합니다. 조회한 typedef를 수정해서 다시 보내도 필드가 사라지지 않습니다.
- **요청 관찰**: `AtlasClient.builder().requestListener(e -> ...)`로 HTTP 시도마다 메서드, URI, 시도 횟수, 상태 코드, 소요 시간, 요청·응답 본문을 받습니다. 로깅이나 메트릭에 씁니다. 리스너에서 난 예외는 로그만 남기고 무시합니다.

## 타입 관리 (atlas-client-typedef)

```java
import static io.datadynamics.atlas.client.typedef.TypeDefs.*;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.*;

TypeDefManager manager = new TypeDefManager(atlas);

TypePlan plan = manager.plan(
        enumType("table_tier").values("BRONZE", "SILVER", "GOLD"),
        entity("my_table").dataSet()
                .attribute(attr("tier", "table_tier").indexable())
                .attribute(attr("rowCount", LONG)),
        entity("my_column").dataSet()
                .attribute(attr("dataType", STRING).required()),
        relationship("my_table_columns").composition()
                .end1(end("my_table", "columns").set().container())
                .end2(end("my_column", "table")),
        classification("PII").entityTypes("my_column"));

System.out.println(plan.render());
manager.apply(plan);          // plan에 ERROR가 있으면 아무것도 보내지 않고 TypePlanException
```

출력 예:

```
+ create    ENUM              table_tier
~ update    ENTITY            my_table
      + attribute 'tier' (table_tier)

Issues:
  WARNING my_table: attribute 'legacy' exists in Atlas but not in the definition; it is kept ...

Plan: 1 to create, 1 to update, 0 to delete, 0 unchanged; 0 errors, 1 warnings
```

- **속성 기본값**: `attr()`로 만든 속성은 기본이 optional입니다. 필수 속성은 `.required()`를 붙입니다.
- **비교 방식**: 정의에 명시한 필드만 서버와 비교합니다. 수정 요청은 서버 정의 위에 원하는 정의를 덮어쓴 결과를 보내므로, 정의에 없는 기존 속성도 유지됩니다.
- **미리 잡아내는 오류**: Atlas가 거부할 변경은 서버에 보내기 전에 ERROR로 표시합니다.
  - 기존 타입에 필수 속성 추가
  - 속성 타입 변경
  - relationship의 category나 end의 type/name 변경
  - 다른 category로 같은 이름 재정의
  - 존재하지 않는 타입 참조
- **파일**: `TypeDefFiles.read/readAll/write`로 YAML/JSON 파일을 읽고 씁니다. Atlas 배포본의 `models/*.json`을 그대로 읽을 수 있습니다. `manager.plan(Path.of("types/"))`처럼 디렉터리를 바로 넘길 수도 있습니다.
- **삭제**: `manager.planDelete(names)`는 다음을 미리 검사합니다.
  - 남아 있는 하위 타입이나 참조
  - 엔티티 인스턴스(soft-deleted 포함)
  - classification이 붙어 있는 엔티티
- **Export**: `manager.exportByServiceType("hive")`로 정의를 내보냅니다. guid나 시간 같은 서버 필드는 제거됩니다.

## 리니지 (atlas-client-lineage)

```java
AtlasLineage lineage = new AtlasLineage(atlas);

// 등록: process, 컬럼 리니지, (필요 시) 없는 데이터셋까지 한 번의 bulk 요청으로 등록
lineage.process("etl_job", "daily_sales_agg@prod")
        .inputs(EntityRef.of("hive_table", "raw.sales@prod"), EntityRef.of("hive_table", "raw.customer@prod"))
        .outputs(EntityRef.of("hive_table", "mart.daily_sales@prod"))
        .columnLineage(EntityRef.of("hive_column", "mart.daily_sales.amount@prod"),
                EntityRef.of("hive_column", "raw.sales.price@prod"),
                EntityRef.of("hive_column", "raw.sales.qty@prod"))
        .attribute("queryText", sql)
        .register();

// 조회: 그래프로 탐색
LineageGraph graph = lineage.graph(EntityRef.of("hive_table", "mart.daily_sales@prod"));
graph.upstreamDatasets(graph.base());
graph.roots();
System.out.println(graph.toMermaid());   // 또는 toDot()

// 영향도 분석: 이 테이블이 바뀌면 영향받는 데이터셋 (가까운 순, 경로 포함)
for (Impact i : lineage.impact(EntityRef.of("hive_table", "raw.sales@prod"), 5)) {
    System.out.println(i.dataset() + " distance=" + i.distance());
}
```

- **inputs/outputs 교체**: 같은 qualifiedName의 process를 다시 등록하면 inputs/outputs가 새 값으로 바뀝니다. `lineage.register(List.of(...))`로 여러 process를 한 트랜잭션에 묶을 수 있습니다.
- **없는 데이터셋 처리** (`missingEntities(...)`):
  - `FAIL`(기본): 미리 검사해서 없는 목록을 담은 `LineageException`을 던지고, 서버에는 아무것도 보내지 않습니다.
  - `CREATE`: 없는 엔티티를 stub으로 생성합니다. 필수 속성은 `EntityRef.withAttribute()`로 넘깁니다.
  - `NO_CHECK`: 검사 없이 바로 보냅니다.
- **컬럼 리니지**: 기본 타입은 `hive_column_lineage`이고, 부모 process는 `query` 속성으로 연결합니다. `columnLineageType(type, parentAttr)`와 `columnLineageQualifiedName(...)`으로 바꿀 수 있습니다.
- **그래프**: 노드가 process인지는 타입 정의의 superTypes를 따라가 판정하고 결과를 캐시합니다. 같은 테이블을 읽고 쓰는 순환 리니지도 처리합니다.
- **삭제된 relationship**: process를 다른 inputs로 다시 등록하면 Atlas는 이전 relationship을 soft-delete합니다. 그런데 리니지 API는 이 edge도 계속 돌려줍니다. `graph()`는 기본적으로 이런 edge를 걸러내서 현재 리니지만 보여줍니다. 과거 이력까지 보려면 `includeDeletedRelationships(true)`를 씁니다.

## Kafka (atlas-client-kafka)

REST 대신 Atlas hook과 같은 경로(`ATLAS_HOOK` 토픽)로 메타데이터를 보내고, Atlas가 `ATLAS_ENTITIES` 토픽에 발행하는 변경 이벤트를 받습니다. 메시지 본문은 REST와 같은 모델(`AtlasEntity`, `AtlasObjectId` 등)을 씁니다.

```java
try (AtlasHookPublisher hook = AtlasHookPublisher.builder()
        .bootstrapServers("kafka:9092")
        .user("etl")
        .build()) {
    hook.createOrUpdate(table);                                            // ENTITY_CREATE_V2
    hook.partialUpdateByQualifiedName("hive_table", "db.orders@prod", patch); // ENTITY_PARTIAL_UPDATE_V2
    hook.deleteByQualifiedName("hive_table", "db.tmp@prod");                 // ENTITY_DELETE_V2

    // 리니지도 같은 방식으로 보낼 수 있습니다 (lineage 모듈이 payload만 만듦)
    hook.createOrUpdate(lineage.process("etl_job", "load@prod")
            .inputs(EntityRef.of("hive_table", "raw.sales@prod"))
            .outputs(EntityRef.of("hive_table", "mart.sales@prod"))
            .toEntities());
}

AtlasEntityChangeListener listener = AtlasEntityChangeListener.builder()
        .bootstrapServers("kafka:9092")
        .groupId("catalog-sync")
        .typeNames("hive_table")
        .operations(OperationType.ENTITY_CREATE, OperationType.ENTITY_DELETE)
        .handler(event -> System.out.println(event.getOperationType() + " " + event.qualifiedName()))
        .build()
        .start();                 // 백그라운드 스레드, 배치마다 offset commit (at-least-once)
```

- **메시지 형식**: Atlas와 같은 envelope을 씁니다. 1MB(`atlas.notification.message.max.length.bytes` 기본값)를 넘는 메시지는 GZIP으로 압축하고, 그래도 크면 여러 Kafka 메시지로 나눕니다. 나눈 조각은 같은 key로 보내 같은 파티션에 순서대로 들어갑니다. 리스너도 압축·분할된 메시지를 다시 조립합니다.
- **검증**: Atlas는 hook 메시지를 처리하다 실패해도 서버 로그에만 남깁니다. 그래서 partial update와 delete에 typeName과 비어 있지 않은 uniqueAttributes가 있는지 같은 조건을 보내기 전에 검사합니다. Atlas는 이 두 메시지의 엔티티를 unique attribute로 찾습니다.
- **비동기**: `createOrUpdate` 등은 Kafka가 메시지를 받았을 때 반환합니다. Atlas에 반영되는 것은 그 뒤입니다. `sendAsync`는 기다리지 않습니다.
- **ENTITY_UPDATE 이벤트**: Atlas 2.3은 전체 수정(`POST /v2/entity`, `/bulk`)일 때만 `ENTITY_UPDATE`를 발행합니다. 부분 수정(`PUT .../guid/{guid}?name=`, `PUT .../uniqueAttribute/...`)은 이벤트 없이 반영됩니다.

## CLI (atlas-client-cli)

```bash
alias atlas='java -jar atlas-client-cli-0.1.0-all.jar'
export ATLAS_URL=http://atlas:21000 ATLAS_USER=admin ATLAS_PASSWORD=admin

atlas status                                         # Atlas 2.3.0 (ACTIVE)
atlas types plan ./types                             # 변경 사항만 출력, 오류가 있으면 exit 1
atlas types apply ./types                            # plan 출력 후 'yes' 입력 시 적용 (--yes로 생략)
atlas types export --service-type hive -o hive.yaml
atlas types delete my_old_type                       # 쓰는 곳이 없을 때만 삭제

atlas entities import tables.csv --type hive_table   # qualifiedName 기준 upsert, 100건씩
atlas entities get hive_table db.orders@prod

atlas lineage graph hive_table mart.sales@prod --format mermaid
atlas lineage impact hive_table raw.sales@prod       # 영향받는 데이터셋과 경로
atlas check --duplicates hive_table                  # 문제가 있으면 exit 1
atlas events tail --bootstrap-servers kafka:9092 --type hive_table --qualified-name 'sales.*@prod'
```

- **접속 설정**: 옵션, 환경 변수(`ATLAS_URL`, `ATLAS_USER`, `ATLAS_PASSWORD`, `ATLAS_TOKEN`, `ATLAS_PRINCIPAL`/`ATLAS_KEYTAB`), `~/.atlas/client.properties`(`url=`, `user=` 등) 순서로 찾습니다.
- **CSV 헤더**:

  | 헤더 | 의미 |
  |---|---|
  | `name` | 문자열 속성 |
  | `tags[]` | 배열 (값은 `\|`로 구분) |
  | `db@hive_db` | qualifiedName으로 다른 엔티티 참조 |
  | `columns[]@hive_column` | 참조 배열 |
  | `typeName`, `classifications`, `labels` | 행별 타입, classification, 레이블 |

  숫자나 boolean도 문자열로 적으면 Atlas가 속성 타입에 맞게 변환합니다. 같은 파일 안의 엔티티를 참조해도 됩니다. Atlas는 같은 요청 안에 있는 엔티티를 qualifiedName 참조로 찾지 못하므로(`ATLAS-404-00-00A`), 같은 배치 안의 참조는 CLI가 placeholder guid로 바꿉니다. 배치가 다르면 참조되는 엔티티가 파일에서 먼저 나와야 합니다. YAML/JSON은 엔티티 목록이나 `{entities: [...]}` 형식을 받습니다.
- **파일 읽기 API**: `EntityFiles.parse(fileName, text, defaultType)`와 `EntityFiles.linkWithinBatch(batch)`로 애플리케이션에서도 `entities import`와 같은 형식을 읽을 수 있습니다.
- **check**: process 중 inputs와 outputs가 모두 없는 것, 한쪽이 없는 것(삭제된 데이터셋 때문인 경우 포함), 삭제된 엔티티를 가리키는 활성 edge, qualifiedName 중복을 찾습니다. 검색 인덱스를 쓰므로 방금 만든 엔티티는 잠시 뒤에 잡힐 수 있습니다.
- **종료 코드**: 0 성공, 1 실패·문제 발견·확인 거부, 2 잘못된 명령. `ATLAS_CLI_DEBUG=1`이면 stack trace도 출력합니다.

## 데모 앱 (atlas-client-demo)

라이브러리의 기능을 브라우저에서 직접 실행해 보는 웹 앱입니다. 각 화면은 라이브러리 메서드를 호출하고, 결과와 함께 클라이언트가 Atlas에 실제로 보낸 HTTP 요청을 보여줍니다.
화면마다 처음 쓰는 사람을 위한 도움말이 함께 나옵니다. Docker만 있으면 Atlas, Kafka와 함께 띄울 수 있습니다.

```bash
docker compose -f docker/compose.yaml up -d --build --wait   # 첫 기동은 Atlas 초기화로 몇 분 걸림
# http://localhost:8090 → "샘플 데이터"에서 만들기
```

사용법은 [atlas-client-demo/README.md](atlas-client-demo/README.md), 실습 환경 구성(포트, 다른 장비에서 접속, 데이터 초기화)은 [docker/README.md](docker/README.md)를 보세요.

## 테스트 지원 (atlas-client-test)

core에 의존하지 않으므로 이 클라이언트를 쓰는 애플리케이션의 테스트에서도 쓸 수 있습니다.

```java
// 단위 테스트: Docker 없이 바로 뜨는 가짜 서버
try (FakeAtlasServer fake = new FakeAtlasServer()) {
    fake.on("GET", "/api/atlas/v2/entity/uniqueAttribute/type/hive_table",
            req -> req.queryParam("attr:qualifiedName").contains("missing")
                    ? FakeAtlasServer.error(404, "ATLAS-404-00-009", "not found")
                    : FakeAtlasServer.respond(200, "{\"entity\":{...}}"));
    ...
    assertThat(fake.requests("POST", "/api/atlas/v2/entity/bulk")).hasSize(1);
}

// 통합 테스트: JVM당 한 번만 기동해서 공유 (-Datlas.url이 있으면 그 서버 사용)
AtlasTestServer atlas = AtlasTestServer.shared();
atlas.kafkaBootstrapServers();   // Atlas가 쓰는 Kafka (컨테이너 모드에서는 Kafka 컨테이너)
```

Atlas 이미지의 내장 Kafka는 컨테이너 안에서만 접근할 수 있습니다. 그래서 컨테이너 모드에서는 Kafka 컨테이너(`apache/kafka:3.9.1`, `-Dkafka.image`로 변경)를 같은 Docker 네트워크에 띄우고 `AtlasContainer.withExternalKafka(...)`로 Atlas가 그 Kafka를 쓰게 합니다.

## Atlas 2.3.0에서 검증한 동작

`atlas-client-it`의 `AtlasBehaviorIT`가 아래 동작을 고정해 둡니다. Atlas 버전을 올릴 때 이 테스트를 돌리면 동작이 바뀌었는지 알 수 있습니다.

| 변경 | Atlas 2.3.0 | planner |
|---|---|---|
| 기존 타입에 필수 속성 추가 | 거부 | ERROR |
| 속성 타입 변경 | 거부 | ERROR |
| relationship end의 type/name/cardinality 변경 | 거부 (`ATLAS-400-00-038`) | ERROR |
| relationship end의 description 변경 | 수용 | 변경 |
| business metadata 문자열 속성에 `maxStrLength` 없음 | 거부 (`ATLAS-400-00-02B`) | ERROR (builder가 기본값 50을 자동으로 채움) |
| superType 추가 | 수용 | WARNING |
| optional 속성을 필수로 변경 (인스턴스 없음) | 수용 | WARNING |
| 쓰이지 않는 enum 값 제거 | 수용 | WARNING |
| 이미 있는 process를 placeholder guid로 다시 보냄 | 기존 guid로 수정됨 | - |
| bulk uniqueAttribute 조회 중 일부가 없음 | 없는 것은 건너뜀 | - |
| classification 전파 작업 중에 삭제 | 406 `ATLAS-406-00-001` (잠시 뒤 재시도하면 성공) | - |

`KafkaIT`와 `CliIT`에서 확인한 엔티티·Kafka 동작:

| 상황 | Atlas 2.3.0 | 클라이언트 |
|---|---|---|
| 같은 요청 안의 엔티티를 qualifiedName으로 참조 | 404 `ATLAS-404-00-00A` | CLI import가 배치 안의 참조를 placeholder guid로 연결 |
| 부분 수정 (`PUT .../guid/{guid}?name=`, `PUT .../uniqueAttribute/...`) | `ENTITY_UPDATE` 이벤트 없음 | 리스너 문서에 명시 |
| 전체 수정 (`POST /v2/entity`) | `ENTITY_UPDATE` 이벤트 발행 | - |
| GZIP 압축 또는 분할된 hook 메시지 | 조립해서 처리 | 1MB 초과 시 자동 압축·분할 |
| 리니지에 쓰인 데이터셋 삭제 | 해당 relationship도 soft-delete | `check`가 process를 INCOMPLETE로 보고 |
| hook 메시지의 partial update / delete | unique attribute로 엔티티를 찾음 | 보내기 전에 uniqueAttributes 검사 |
