# Atlas 실습 환경 (docker compose)

Apache Atlas, Kafka, 그리고 Atlas Client의 기능을 화면에서 확인하는 웹 데모 앱(`atlas-client-demo`)을 한 번에 띄웁니다.
Docker만 있으면 됩니다. 데모 앱은 이 checkout의 소스로 컨테이너 안에서 빌드합니다.

```bash
docker compose -f docker/compose.yaml up -d --build
```

| 서비스 | 주소 | 비고 |
|---|---|---|
| 데모 앱 | http://localhost:8090 | Spring Boot 4, 소스에서 빌드 |
| Atlas | http://localhost:21000 | `sburn/apache-atlas:2.3.0`, admin / admin |
| Kafka | localhost:9092 | `apache/kafka:3.9.1` (KRaft). Atlas와 데모 앱은 내부 주소 `kafka:19092` 사용 |

- **첫 기동**: Atlas가 ACTIVE가 될 때까지 몇 분 걸립니다(내장 HBase·Solr 초기화). 그동안 데모 앱 대시보드에 "접속하지 못했습니다"가 표시됩니다. `docker compose -f docker/compose.yaml up -d --build --wait`로 띄우면 모두 healthy가 될 때까지 기다립니다.
- **포트 변경**: `ATLAS_PORT`, `KAFKA_PORT`, `DEMO_PORT` 환경 변수로 바꿉니다. 예: `DEMO_PORT=18090 docker compose -f docker/compose.yaml up -d`
- **다른 장비에서 접속**: `BIND_ADDRESS`(포트를 열 호스트 IP, 기본 `0.0.0.0`)와 `ADVERTISED_HOST`(Kafka가 Docker 밖 클라이언트에게 알려줄 주소, 기본 `localhost`)를 `docker/.env`에 적습니다. 이 파일은 장비마다 다르므로 커밋하지 않습니다(`.gitignore`). 특정 IP에 바인딩하면 `localhost`로는 접속되지 않습니다.

  ```bash
  # docker/.env
  BIND_ADDRESS=192.0.2.10
  ADVERTISED_HOST=192.0.2.10
  ```
- **데이터**: Atlas 데이터는 `atlas-data` 볼륨에 남습니다. 처음 상태로 돌리려면 `docker compose -f docker/compose.yaml down -v`.
- **Kafka 구성**: 이미지의 내장 Kafka는 컨테이너 밖에서 접속할 수 없어서, 별도 Kafka를 띄우고 Atlas 설정(`atlas.notification.embedded=false`, `atlas.kafka.bootstrap.servers`)을 기동 시 바꿉니다. `AtlasContainer.withExternalKafka`와 같은 방식입니다.

## 데모 앱

http://localhost:8090 에서 Atlas Client의 기능을 메뉴별로 실행해 보고, 클라이언트가 보낸 HTTP 요청을 확인합니다.
화면 구성, 메뉴, 따라 해 보기, 설정, 문제 해결은 [atlas-client-demo/README.md](../atlas-client-demo/README.md)를 보세요.

## 개발

Atlas와 Kafka만 띄우고 데모 앱은 호스트에서 실행할 수도 있습니다: `docker compose -f docker/compose.yaml up -d --wait kafka atlas`.
실행 방법과 접속 설정(환경 변수)은 [atlas-client-demo/README.md](../atlas-client-demo/README.md#호스트에서-실행-개발)를 보세요.
