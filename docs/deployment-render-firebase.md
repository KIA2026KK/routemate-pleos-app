# Render·Firebase 배포 준비와 전달 정보

2026-11-09 공식 문서 기준이다. 아래 파일은 배포 가능한 구성을 검토하도록 준비한 초안이며,
Render/Firebase 프로젝트나 유료 자원은 생성하지 않았다. 실제 서비스 연결 성공은 배포 후 확인해야 한다.

## 현재 설계를 유지하는 구성

| 역할 | 권장 구성 | 현재 코드와의 관계 |
| --- | --- | --- |
| REST 백엔드 | Render Docker Web Service, Java 21 | 기존 Spring Boot·DDD·인증·API 유지 |
| 주 DB | Render Postgres 17 + PostGIS | 목적지 거리 검색, FK, 동시 참여 제약, Flyway 유지 |
| Redis 호환 저장소 | Render Key Value | Spring Data Redis/Lettuce 연결. 신규 인스턴스는 Valkey 8 |
| 음성 미디어 | LiveKit Cloud 또는 UDP를 지원하는 별도 VM | Render의 REST 서비스와 별도 배치 |
| 테스트 APK 배포·크래시 관찰 | Firebase App Distribution, 필요 시 Crashlytics | 선택 사항. MVP 실행에 Firebase는 필수 아님 |

Render는 PG 17과 PostGIS 확장을 지원한다. 서비스·DB·Key Value는 같은 리전에 배치한다.
정확한 PostgreSQL 마이너 버전과 PostGIS 버전은 관리형 제공자가 관리하므로 로컬 17.5/3.5와 완전히
같다고 가정하지 않고, 배포 DB에서 `SELECT version(); SELECT PostGIS_Full_Version();`를 확인한다.
[Postgres 연결](https://render.com/docs/postgresql-creating-connecting),
[지원 확장](https://render.com/docs/postgresql-extensions), [Key Value](https://render.com/docs/key-value).

### Firebase DB에 대한 결정

사용자는 우선 PostgreSQL·PostGIS 유지 기준을 선택했다. 현재 `ST_DWithin`, `geography`, 복합 FK,
부분 UNIQUE 인덱스, 트랜잭션/advisory lock을 Firestore 설정만 바꿔 사용할 수는 없다.
Firestore의 공식 거리 검색 예시는 geohash 후보 조회 후 거리 재검증을 수행한다.
Firestore를 주 DB로 선택하려면 매칭·동시 입장·저장소·마이그레이션 설계를 별도로 바꿔야 한다.
[Firestore 거리 검색](https://firebase.google.com/docs/firestore/solutions/geoqueries).

과거 Data Connect 문서는 현재 **Firebase SQL Connect**로 연결되며 Cloud SQL PostgreSQL와
GraphQL·생성 SDK를 제공한다. 이를 선택해도 현재 Spring JDBC/Flyway와 SQL Connect의 스키마 소유권,
네트워크·DB 권한·PostGIS 지원 범위를 먼저 조율해야 한다. 지금 SQL 저장소를 자동 전환하지 않았다.
[Firebase SQL Connect](https://firebase.google.com/docs/sql-connect).

## 준비한 배포 파일

- `Dockerfile`: Java 21 다단계 빌드와 비루트 실행. 이미지 빌드에는 DB가 필요하지 않으며 테스트는 CI에서 수행한다.
- `.dockerignore`: `.env`, 빌드 캐시, Git 정보, 로그가 이미지에 들어가지 않도록 제외한다.
- `render.yaml`: main CI 통과 후 배포, HTTP 건강 확인, 내부 PG·Key Value 연결 및 LiveKit 환경 변수 입력 초안이다.
  PostgreSQL은 17, 외부 DB/Key Value 접근은 차단한 구성이다.
- `application.yaml`: Render의 `PORT`를 우선 적용하고 로컬 `SERVER_PORT`/8080을 지원한다.
  `DB_SSL_MODE`, `SPRING_DATA_REDIS_URL`도 지원한다. HTTP 종료는 graceful 방식이다.

Blueprint의 서비스 `1c-2g`, Postgres `0.5c-1g`, Key Value `256mb`, Singapore 리전은 **유료 예시**다.
적용 전에 예산·리전·인스턴스 이름을 결정한다. 무료 웹 서비스의 휴면과 DB 보존 제약은
연속 음성/heartbeat 검증에 적합한지 확인해야 한다. 현재 파일을 저장한 것만으로 과금되지 않는다.
[Docker 배포](https://render.com/docs/docker), [Blueprint](https://render.com/docs/blueprint-spec),
[플랜 ID](https://render.com/docs/compute-plans), [무료 서비스 제약](https://render.com/docs/free).

## Render에 설정할 환경 변수

| 키 | 입력 값/원천 | 주의 |
| --- | --- | --- |
| `PORT` | Render가 자동 제공 | `0.0.0.0`의 해당 HTTP 포트로 바인딩. 앱 기본 바인딩 사용 |
| `DB_HOST` | Postgres 내부 hostname | 동일 리전의 내부 연결 사용 |
| `DB_PORT` | `5432` | DB 연결 정보와 일치 |
| `DB_NAME` | 생성된 database 이름 | Render 서비스 이름과 다른 값일 수 있음 |
| `DB_USER`, `DB_PASSWORD` | Render Postgres 자격증명 | Blueprint가 직접 연결하며 채팅에 붙이지 않음 |
| `DB_SSL_MODE` | Render에서는 `require` | 내부 인증서는 자체 서명이라 `verify-full`을 임의로 적용하지 않음 |
| `SPRING_DATA_REDIS_URL` | Key Value 내부 connectionString | `redis://` 또는 `rediss://`; URL의 인증정보·TLS 설정 사용 |
| `ALLOW_MOCK_VEHICLE` | 운영 `false` | 별도 테스트 서버에서만 `true` |
| `LIVEKIT_SERVER_URL` | 관리 API의 `https://…` 주소 | 백엔드에서 접근 가능해야 함 |
| `LIVEKIT_PUBLIC_URL` | 앱이 접속할 `wss://…` 주소 | 유효한 인증서 필요 |
| `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | LiveKit 프로젝트의 서버 자격증명 | Render 비밀 환경 변수에만 저장 |

현재 앱은 `DB_HOST/PORT/NAME`으로 JDBC URL을 구성하므로 Render의 `postgresql://user:password@…`를
그대로 `SPRING_DATASOURCE_URL`에 붙이면 JDBC URL 형식과 맞지 않는다. Blueprint는 각각의 필드를
연결해 이 문제를 피한다. 직접 `SPRING_DATASOURCE_URL`을 설정하려면 `jdbc:postgresql://host:5432/db?sslmode=require`
형식을 쓰고 사용자·비밀번호는 별도 속성으로 둔다. URL 전체를 로그나 채팅에 출력하지 않는다.

Render 내부 PG는 `sslmode=require`로 암호화를 요구할 수 있다. 외부 연결도 TLS가 필요하다.
`SPRING_DATA_REDIS_URL`이 있으면 호스트·포트·비밀번호 개별 설정보다 우선하며 `rediss://`는 TLS를 사용한다.
Key Value 외부 접속을 열기보다 같은 리전의 내부 URL을 사용한다.
[PG TLS](https://render.com/docs/postgresql-creating-connecting#ssl-modes-for-internal-connections),
[Redis 연결 속성](https://docs.spring.io/spring-boot/appendix/application-properties/index.html).

### LiveKit을 별도로 두는 이유

Render Web Service는 공개 인바운드를 하나의 HTTP 포트로 전달한다. LiveKit은 WebSocket 이외에
ICE/UDP·TCP 및 필요 시 TURN 포트가 필요하다. 따라서 Render의 웹 서비스 한 개에
Spring과 LiveKit을 함께 넣는 구성은 이 배포안에 포함하지 않는다.
LiveKit Cloud 또는 해당 포트를 직접 열 수 있는 VM을 사용하고, 비밀키를 앱에 넣지 않는다.
[Render 포트](https://render.com/docs/web-services#port-binding),
[LiveKit 방화벽](https://docs.livekit.io/transport/self-hosting/ports-firewall/).

## 사용자에게 필요한 결정과 전달 정보

다음 항목은 일반 설정 정보로 전달할 수 있다. DB 비밀번호·서비스 계정 개인키·LiveKit Secret은 보내지 않는다.

| 항목 | 전달할 정보 |
| --- | --- |
| Render | workspace/서비스 이름 또는 대시보드 링크, 사용할 리전, 월 예산/플랜 범위, 운영·테스트 분리 여부 |
| 백엔드 | 배포 GitHub 저장소·main 브랜치, 배포 후 `https://…` 기본 주소, 원하는 도메인이 있으면 도메인 |
| DB | 기존 DB 재사용 또는 새 생성 여부, PG major 17/PostGIS 유지 여부, 기존 데이터 이전 필요 여부 |
| Key Value | 사용 여부와 서비스 이름/리전, 내부 연결을 설정했는지 여부 |
| LiveKit | Cloud/자체 VM 선택, 서버 관리 URL·앱 WSS URL, 별도 테스트 프로젝트 여부 |
| Firebase 선택 기능 | 프로젝트 ID, 사용할 서비스(App Distribution/Crashlytics 등), Android 앱 등록 여부 |
| Android 배포 | 최종 applicationId, 테스트 참가자/그룹, APK 배포 방식, 릴리스 서명 담당자 |

Firebase Android 등록에는 최종 applicationId와 설정 파일이 필요하다. Firebase 전용 앱 식별용 키는
조건에 맞게 제한하면 공개 구성일 수 있지만, Admin SDK 서비스 계정 JSON/개인키는 별도의 서버 비밀이다.
서명 지문 SHA-1/SHA-256은 선택한 Auth/App Check 등의 요구에 맞춰 등록한다.
Google Play services 의존성도 사용하는 Firebase SDK별로 Pleos 환경에서 확인한다.
단순 APK 테스트 배포는 App Distribution 콘솔로 시작할 수 있으며, 현재 VIN 인증을 Firebase Auth로 바꾸지 않는다.
[Firebase Android 설정](https://firebase.google.com/docs/android/setup),
[Firebase 키 구분](https://firebase.google.com/docs/projects/api-keys),
[App Distribution](https://firebase.google.com/docs/app-distribution).

## 배포 후 순서

1. 비용·리전·운영/테스트 환경을 결정하고 별도 스테이징부터 생성한다. 운영 DB를 로컬 테스트에 연결하지 않는다.
2. Render에 GitHub 접근을 연결하고 Docker/Blueprint 초안을 검토한다. 실제 배포는 사용자가 계정에서 실행한다.
3. Postgres·Key Value 내부 연결 및 LiveKit 환경 변수를 저장한다. 서버 비밀은 Render UI/보안 환경 변수로만 설정한다.
4. Flyway 적용 로그, DB/PostGIS 버전, `/actuator/health`의 200/UP을 확인한다. 스키마를 콘솔에서 수동 재작성하지 않는다.
5. 스테이징에만 Mock 차량을 허용하고 앱을 `-PbackendUrl=https://<스테이징주소>`로 빌드한다.
   운영은 실제 VIN 제공자와 `ALLOW_MOCK_VEHICLE=false`를 사용한다.
6. 목적지 → 3/5km 검색 → 방 생성/참여 → 서버 발급 토큰 → 음성 발행/구독 → 퇴장/종료를 원격 환경에서도 확인한다.
7. DB 백업·복원 절차, 비용 알림, 에러 로그·헬스 모니터링, 비밀키 교체 담당자를 정한다.
8. 릴리스 APK/AAB 서명을 준비하고, 필요하면 Firebase App Distribution으로 테스트 APK를 배포한다.
9. 실제 Pleos의 VIN·목적지·권한·주행 중 제한·오디오 라우팅과 물리 단말 2~3대의 청취/재접속을 검증한다.

Firebase 프로젝트나 클라우드 계정 정보가 아직 없어도 현재 로컬 프로토타입과 독립 MVP 테스트는 실행할 수 있다.
