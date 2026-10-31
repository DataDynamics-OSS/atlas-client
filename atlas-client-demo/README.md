# Atlas Client 데모 앱

Atlas Client 라이브러리(`atlas-client-*`)의 기능을 브라우저에서 직접 실행해 보는 Spring Boot 웹 앱입니다.
버튼 하나하나가 라이브러리 메서드를 호출하고, 결과와 함께 **라이브러리가 Atlas에 실제로 보낸 HTTP 요청**을 보여줍니다.
코드를 쓰기 전에 "이 메서드가 Atlas에 무엇을 보내고 무엇을 돌려받는지" 확인하는 용도입니다.

## 시작하기

Docker만 있으면 됩니다. 저장소 루트에서 실행합니다.

```bash
docker compose -f docker/compose.yaml up -d --build --wait
```

1. `--wait`는 Atlas가 ACTIVE가 될 때까지 기다립니다. 첫 기동은 몇 분 걸립니다(내장 HBase·Solr 초기화).
2. 브라우저에서 http://localhost:8090 을 엽니다. 대시보드의 Atlas 상태가 `ACTIVE`면 준비된 것입니다.
3. **샘플 데이터** 메뉴에서 "만들기 / 다시 만들기"를 누릅니다. 다른 메뉴에서 연습할 타입, 테이블, classification, 리니지가 생깁니다.

포트 변경, 다른 장비에서 접속(`docker/.env`), 데이터 초기화는 [docker/README.md](../docker/README.md)를 보세요.

## 화면 구성

```
┌─ 메뉴 ──────────────────────────────────────────────────── ? 도움말 ─┐
│ 본문: 입력 폼과 결과                        │ 도움말 (따로 스크롤)   │
│                                             │ 개념, 입력 방법, 예시, │
│ 이 화면에서 클라이언트가 보낸 요청          │ 같은 일을 하는 Java    │
│  # 메서드 경로 시도 상태 시간 (누르면 본문) │ 코드                   │
└─────────────────────────────────────────────┴────────────────────────┘
```

- **본문**: 각 메뉴의 입력 폼과 결과입니다. 실패하면 화면 위쪽에 클라이언트 예외 타입(`AtlasNotFoundException` 등), HTTP 상태, Atlas 에러코드(`ATLAS-404-00-005` 등)가 나옵니다.
- **요청 표**: 그 화면을 처리하면서 클라이언트가 보낸 요청입니다. 경로를 누르면 요청·응답 본문이 펼쳐집니다. 시도가 2 이상이면 자동 재시도, 빨간 행은 실패한 요청입니다.
- **도움말**: 처음 쓰는 사람을 위한 설명입니다. 본문과 따로 스크롤되고, 위쪽 "? 도움말" 버튼이나 × 로 숨깁니다(브라우저에 기억). 화면 폭이 좁으면 본문 아래로 내려갑니다.
- **코드 표시와 편집기**: JSON·YAML·Java는 Monaco Editor로 보여주고 편집합니다. webjar로 포함하므로 인터넷 연결이 없어도 됩니다.

## 메뉴

| 메뉴 | 확인하는 기능 | 클라이언트 API |
|---|---|---|
| 대시보드 | Atlas 상태·버전, 타입 수, Kafka 리스너 상태 | `admin()`, `types().getHeaders()` |
| 샘플 데이터 | 타입·테이블·classification·label·business metadata·리니지를 한 번에 생성/정리 | 아래 기능 조합 |
| 타입 | 목록과 정의, YAML/JSON → plan(ERROR/WARNING) → apply, 삭제 사전 점검, export | `TypeDefManager`, `TypeDefFiles` |
| 엔티티 | basic/DSL/full-text 검색, 상세(속성·관계·classification·label·business metadata·감사 이력), 수정·삭제·purge, CSV/YAML import | `search()`, `entities()`, `admin().purge()`, `EntityFiles` |
| 리니지 | 그래프(Mermaid), 영향도·원천 분석, process 등록(REST 또는 Kafka hook, 없는 엔티티 정책) | `AtlasLineage` |
| Kafka | `ATLAS_HOOK` 전송(create/partial update/delete), `ATLAS_ENTITIES` 이벤트 실시간 수신 | `AtlasHookPublisher`, `AtlasEntityChangeListener` |
| 정합성 점검 | 고아·불완전 process, 삭제된 엔티티를 가리키는 edge, qualifiedName 중복 | `ConsistencyChecker` |
| 요청 기록 | 클라이언트가 보낸 최근 HTTP 요청(재시도 포함)과 요청·응답 본문 | `RequestListener` |

## 따라 해 보기

샘플 데이터를 만든 뒤 순서대로 해 보면 라이브러리의 주요 기능을 한 번씩 거칩니다.

1. **엔티티 검색**: 모드 `basic`, typeName `console_demo_table`로 검색합니다.
   classification 열을 보면 PII는 `raw.customers`에만 붙였는데 `mart.daily_sales`와 `report.sales_kpi`에도 있습니다. 리니지를 따라 전파됐기 때문입니다.
   속성 필터 `tier` `EQ` `GOLD`(→ `report.sales_kpi`만), DSL `console_demo_table where tier = 'GOLD'`도 해 보세요.
2. **엔티티 상세**: `daily_sales`를 엽니다. 속성 하나(`description`)를 바꾸고, 레이블과 business metadata(`score`)를 바꿔 봅니다.
   바꿀 때마다 version이 늘고, 아래 감사 이력에 기록이 남습니다.
3. **리니지**: typeName `console_demo_table`, qualifiedName `mart.daily_sales@demo`로 그래프를 그립니다.
   `raw.orders@demo`를 기준으로 하면 영향도(하류)에 `mart.daily_sales`(거리 1)와 `report.sales_kpi`(거리 2)가 나옵니다.
4. **타입 plan**: 아래 정의를 plan하면 `ERROR`가 나옵니다. 이미 엔티티가 있는 타입에 필수 속성을 추가하면 Atlas가 거부하기 때문입니다.
   `isOptional: true`로 바꾸면 ERROR가 사라져 "plan 후 apply"로 적용할 수 있습니다.
   두 경우 모두 나오는 WARNING은 정의에서 빠진 `tier`, `rowCount` 속성이 지워지지 않고 남는다는 안내입니다(Atlas는 속성 삭제를 지원하지 않음).
   apply로 추가한 속성은 마지막 단계의 "정리"가 타입과 함께 지웁니다.

   ```yaml
   entityDefs:
     - name: console_demo_table
       superTypes: [DataSet]
       attributeDefs:
         - { name: region, typeName: string, isOptional: false }
   ```
5. **Kafka**: createOrUpdate 탭에서 기본 예시를 보냅니다. 몇 초 뒤 아래 이벤트 목록에 `ENTITY_CREATE`(이미 있던 엔티티면 `ENTITY_UPDATE`)가 새로고침 없이 나타납니다.
   REST와 달리 전송 결과는 "Kafka가 받았다"는 뜻일 뿐이고, Atlas의 처리 결과는 이벤트로 확인합니다.
6. **정합성 점검**: process 타입 `console_demo_job`, 중복 검사할 타입 `console_demo_table`로 점검하면 "문제 없음"입니다.
   엔티티 상세에서 `raw.orders`와 `raw.customers`를 delete한 뒤 다시 점검하면 `daily_sales_etl@demo`가 `INCOMPLETE_PROCESS`로 나옵니다(입력이 모두 사라짐).
   샘플 데이터 화면에서 "다시 만들기"를 누르면 원래대로 돌아옵니다.
7. **정리**: 샘플 데이터 화면에서 "정리"를 누르면 샘플 엔티티를 delete → purge하고 샘플 타입 다섯 개를 지웁니다.

샘플 데이터는 `console_demo_*` 타입과 `*@demo` 엔티티만 만들고 지웁니다. 다른 메뉴의 예시 입력은 `console_sample_*`, `console.*@demo`를 씁니다.
공용 Atlas에 연결해 직접 입력할 때도 이런 이름을 쓰면 다른 데이터와 섞이지 않습니다.

## 설정

`application.yml`의 `atlas.*` 설정을 환경 변수로 바꿉니다. compose에서는 `docker/compose.yaml`의 `demo` 서비스에 지정되어 있습니다.

| 환경 변수 | 기본값 | 설명 |
|---|---|---|
| `ATLAS_URLS` | `http://localhost:21000` | Atlas 주소. 쉼표로 여러 개를 주면 ACTIVE 서버를 찾아 씁니다(HA) |
| `ATLAS_USERNAME` / `ATLAS_PASSWORD` | `admin` / `admin` | Basic 인증 |
| `ATLAS_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | 비우면 Kafka 메뉴와 이벤트 수신을 끕니다 |
| `ATLAS_KAFKA_USER` | `atlas-client-demo` | hook 메시지의 user |
| `SERVER_PORT` | `8090` | 웹 포트 |

## 호스트에서 실행 (개발)

compose로 Atlas와 Kafka만 띄우고 데모 앱은 호스트에서 실행합니다. 기본 설정이 `localhost:21000`, `localhost:9092`를 가리킵니다.

```bash
docker compose -f docker/compose.yaml up -d --wait kafka atlas
mvn -pl atlas-client-demo -am install -DskipTests
mvn -pl atlas-client-demo spring-boot:run
```

템플릿 캐시를 끄고 있어서(`spring.thymeleaf.cache: false`) `spring-boot:run` 중에는 화면 템플릿 수정이 새로고침만으로 반영됩니다.

## 문제 해결

| 증상 | 확인할 것 |
|---|---|
| 대시보드에 "접속하지 못했습니다" | Atlas가 아직 시작 중입니다. `docker compose -f docker/compose.yaml ps`에서 atlas가 `healthy`가 될 때까지 기다립니다 |
| Kafka 메뉴가 없음 | `ATLAS_KAFKA_BOOTSTRAP_SERVERS`가 비어 있습니다 |
| Kafka로 보냈는데 이벤트가 안 옴 | 메시지 내용이 틀리면(없는 타입, 필수 속성 누락) Atlas가 서버 로그에만 남깁니다. `docker compose -f docker/compose.yaml logs atlas` |
| 방금 만든 엔티티가 검색·점검에 안 나옴 | 검색 인덱스 반영에 몇 초 걸립니다. 잠시 뒤 다시 시도합니다 |
| 요청 응답 본문이 정렬되지 않은 원문으로 보임 | 요청 기록은 본문을 2만 자까지만 저장하므로, 잘린 JSON은 원문 그대로 보여줍니다 |
