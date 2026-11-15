# RouteMate

**같은 방향으로 향하는 운전자들을, 음악 대신 사람의 목소리로 연결하는 목적지 기반 그룹 통화 앱.**

팀 **노래말고 사람들과 함께** · Android 패키지 `com.routemate.app` · 버전 **0.3.0 (3)**

RouteMate는 차량의 현재 위치가 아닌 **내비게이션 최종 목적지**를 기준으로 3km·5km 이내의 통화방을 찾습니다.
Pleos 목적지·차량 SDK 어댑터와 일반 Android 검증용 Mock 구현을 분리하며, LiveKit 참가 토큰은 백엔드에서 발급합니다.

> **제출 준비 상태:** 소스와 서명 전 release 빌드 준비. 팀 소유 release 키가 없어 **서명된 최종 APK 생성·설치 검증은 대기 중**입니다.
> 일반 Android 검증 결과를 실제 차량/Pleos 검증으로 표현하지 않습니다.

## 설치와 사용

최종 제출 파일 위치: [`apk/노래말고 사람들과 함께_RouteMate_release.apk`](apk/).
현재 해당 파일은 서명 대기 상태이며, GitHub Release도 아직 게시하지 않았습니다.
완성 절차는 [release 서명 안내](docs/RELEASE_SIGNING.md)를 따릅니다.

| 실행 대상 | 목적지·차량 정보 | 서버·음성 | 현재 검증 |
| --- | --- | --- | --- |
| 제출 Pleos release | NaviHelper 목적지·Vehicle VIN | Render·LiveKit | 빌드 확인; 실제 Pleos 런타임 확인 필요 |
| 휴대폰 테스트 APK | 저장되는 Mock 목적지·설치별 합성 VIN | 실제 Render·LiveKit | 일반 Android 에뮬레이터 검증 |
| 별도 `.prototype` debug 앱 | 명시적 예시·가상 참가자 | 서버 연결·실제 음성 없음 | 화면 흐름 확인용 |

설치 후 다음 순서로 사용합니다. 검색 입력 등 복잡한 조작은 정차 후 수행합니다.

1. **목적지 확인:** Pleos 모드는 내비게이션 목적지를 읽습니다. 휴대폰 테스트 모드는 첫 실행에 광주송정역을 자동 등록합니다.
2. **동행 선택:** 3km·5km 목록에서 방 선택 또는 방 만들기. 기본 공개·4명, 옵션에서 비공개·2~8명 설정.
3. **통화:** 마이크 권한 허용 후 연결. 처음에는 음소거이며 마이크 아이콘으로 켭니다. 초록 테두리가 실제 SDK 발화 상태를 표시합니다.
4. **홈 이동:** 통화방 왼쪽 위 뒤로가기는 통화를 유지합니다. 홈 상단 현재 방을 누르면 돌아옵니다.
5. **다른 방:** 방 이름으로 목록 필터, 초대 코드로 참여. 현재 방이 있다면 이동 확인 후 전환하며 동시에 한 방만 참여합니다.
6. **종료:** 빨간 전화 아이콘으로 퇴장. 홈 통화방 메뉴의 운행 종료는 운행까지 종료합니다.

마이크 알림으로 통화를 확인하고 음소거·퇴장할 수 있습니다. 명시적 퇴장 전 화면 이탈·화면 잠금만으로 통화를 종료하지 않습니다.
운영체제의 프로세스 강제 종료, 권한 회수, 장시간 통신 장애에서는 무중단 통화를 보장하지 않습니다.

## 소스 빌드

Windows PowerShell, **JDK 21**, Android SDK **36**, Build Tools **35.0.0**을 준비합니다.
Gradle **8.14.3** wrapper, AGP **8.13.2**, Kotlin **2.2.21**을 저장소에 고정합니다.
Pleos Maven 저장소는 `settings.gradle.kts`에 설정되어 있습니다.

```powershell
git clone https://github.com/KIA2026KK/routemate-pleos-app.git
cd routemate-pleos-app
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat testDebugUnitTest lintDebug --console=plain

# 일반 Android: 합성 차량·테스트 목적지, 실제 서버·음성
.\gradlew.bat assembleDebug -PdestinationProvider=mock -PvehicleProvider=mock `
  -PbackendUrl=https://routemate-test-api.onrender.com --console=plain

# Pleos: 실제 SDK 사용, 서명 키 없으면 unsigned release 생성
.\gradlew.bat assembleRelease -PdestinationProvider=pleos -PvehicleProvider=pleos `
  -PbackendUrl=https://routemate-test-api.onrender.com --console=plain
```

debug 결과는 `app/build/outputs/apk/debug/app-debug.apk`, unsigned release 결과는
`app/build/outputs/apk/release/app-release-unsigned.apk`입니다. unsigned APK는 설치할 수 없습니다.
서명 키는 저장소 밖에 보관하고 [로컬 서명 스크립트](tools/submission/Build-Submission.ps1)로 최종 APK를 생성합니다.
제품 flavor는 없으며 `destinationProvider`, `vehicleProvider`, `backendUrl` Gradle 속성으로 선택합니다.
서버 주소를 지정하지 않으면 서버 연동이 비활성화됩니다. release는 HTTPS 서버만 허용합니다.

## 테스트 환경과 Pleos 준비

| 항목 | 확인된 환경 |
| --- | --- |
| 개발 OS | Windows / PowerShell |
| 일반 Android | Android 16 / API 36, `Medium_Phone_API_36`, Google Play x86_64 이미지 revision 7 |
| 일반 AVD 화면 | 1080×2400 px, 420 dpi |
| 차량 화면 모의 검증 | 일반 AVD 해상도 override 1920×1080 px / 160 dpi; 실제 Pleos 화면 아님 |
| NaviHelper SDK | 3.0.5 |
| Vehicle SDK | 2.3.2 |
| LiveKit Android SDK | 2.30.0 |
| 개발에 사용한 Pleos Connect 에뮬레이터 버전 | **확인 필요 — 로컬에 Pleos 이미지 설치·실행 기록 없음** |
| Pleos 화면 규격 | **확인 필요 — 실제 Pleos 기기·AVD 없음** |

기존 문서의 Pleos Connect **v2.1.0 / API 34**는 과거 설치 후보 조사값이며 개발·검증에 사용한 버전이 아닙니다.
실제 Pleos 설치 후 버전·화면 규격을 확인해 이 표와 [테스트 결과](docs/TEST_RESULTS.md)를 갱신해야 합니다.
[설치 준비](docs/pleos-emulator-setup.md) · [자동 테스트 방법](docs/mvp-testing.md) · [최종 수동 시나리오](docs/SUBMISSION_CHECKLIST.md)

기본 장치 테스트는 실행 중인 AVD에서 `.\gradlew.bat connectedDebugAndroidTest`로 실행합니다.
실제 서버·LiveKit 검증은 외부 서비스가 필요하므로 별도 opt-in이며, 조건 없이 성공으로 집계하지 않습니다.

## 백엔드와 설정

- [백엔드 소스](https://github.com/KIA2026KK/routemate-backend): Java 21 / Spring Boot 3.5.16, PostgreSQL·PostGIS, Redis, Flyway V1~V7.
- 테스트 API: `https://routemate-test-api.onrender.com` · 상태: [`GET /actuator/health`](https://routemate-test-api.onrender.com/actuator/health).
- LiveKit Cloud 접속 주소·참가 JWT는 서버의 `/api/v1/voice/token` 응답으로 수신합니다. APK에 API Secret을 넣지 않습니다.
- Firebase App Distribution은 APK 배포 수단입니다. 현재 앱 DB는 Firebase가 아닌 **PostgreSQL/PostGIS**입니다. Firebase SDK·`google-services.json` 없이 콘솔 APK 업로드가 가능합니다.

서버 환경 변수는 백엔드의 `.env.example`을 참고합니다.

| 설정 | 용도 |
| --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_SSL_MODE` | PostgreSQL/PostGIS 연결 |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` 또는 `SPRING_DATA_REDIS_URL` | Redis 연결 |
| `LIVEKIT_SERVER_URL`, `LIVEKIT_PUBLIC_URL` | 같은 LiveKit 프로젝트의 HTTPS 관리 주소·WSS 접속 주소 |
| `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | 서버에서만 보관하는 LiveKit 인증정보 |
| `ALLOW_MOCK_VEHICLE` | 합성 VIN 휴대폰 시험 서버에서만 true; 실제 Pleos 전용 운영은 false |
| `PORT` / `SERVER_PORT` | Render 할당 포트 / 로컬 포트 |

비밀번호·참가 토큰·VIN 원문·복구키·서비스 계정 개인키·서명 키는 공개 저장소에 올리지 않습니다.
테스트 서버를 심사 기간에 유지하고 Render 유휴 후 첫 응답 지연·외부 서비스 네트워크 연결을 확인해야 합니다.

## 구현과 제약

구현: 목적지 조회 포트와 Pleos·Mock 어댑터, VIN 기반 계정과 설치 세션·복구키, 목적지 자동 등록,
PostGIS 반경 검색, 방 생성·참여·원자적 이동·초대 코드, 단일 방 참여, 서버 발급 음성 토큰,
전체 통화방·참가자 발화 표시, 홈 현재 방 배너, 마이크 foreground service와 재접속·heartbeat 보호.

| 항목 | 현재 제약 / 남은 검증 |
| --- | --- |
| 실제 Pleos 목적지·VIN | SDK 어댑터·단위 테스트 구현; 실제 차량·Devbox·Pleos AVD 수신 미검증 |
| Mock 목적지 | 첫 실행 광주송정역. 장소·도로명 검색 지원, 두 역은 명시적 예시이며 나머지는 기기 Geocoder 지원·네트워크 필요 |
| 차량 조작 제한 | 정차 확인 UI 사용. 실제 차량 속도·기어 기반 강제 제한 미연동 |
| 통화 유지 | 화면 이탈 시 유지하는 0.3 구현. 약 85초간 서버 참가 권한 갱신 실패 시 안전하게 종료; 기존 문서의 화면 이탈 종료 정책은 0.2 이전 동작 |
| 음성 검증 범위 | SDK 연결·오디오 트랙 발행/구독과 물리 기기의 실제 청취·음질을 구분 |
| 계정 신뢰 | VIN 수신은 OEM 소유권 증명이 아니며 별도 인증 검증이 필요 |
| 방 검색 | 3km·5km 목적지 직선거리; 화면 이름 검색은 가져온 목록의 필터 |
| 서명 | 최종 팀 release 키 생성·서명 및 그 APK 설치 검증 대기 |

향후: 실제 Pleos·차량 오디오·마이크 권한 검증, 두 물리 기기 장시간 청취와 네트워크 전환 시험,
차량 주행 제한 연동 및 운영 모니터링 보완. 문자 채팅·친구·AI 기능은 현재 MVP 범위가 아닙니다.

## 구조와 제출 정보

기능별 DDD: `destination`, `vehicle`, `auth`, `trip`, `room`, `voice` 내부에
`domain / application / infrastructure / presentation`을 둡니다. 도메인은 Android·HTTP·Pleos SDK에 의존하지 않습니다.

제출 대표 저장소는 **이 Android 저장소**이며 백엔드는 별도 관련 저장소로 연결합니다. 두 원본을 합치거나 이동하지 않습니다.
앱·백엔드 모두 현재 비공개입니다. 사용자 제공 공모전 조건에 따라 **2026-11-16까지 공개 전환 후 2026-11-23까지 유지**해야 합니다.

- [제출 체크리스트](docs/SUBMISSION_CHECKLIST.md)
- [실행한 테스트와 미검증 항목](docs/TEST_RESULTS.md)
- [공개 전 보안 점검](docs/PUBLIC_REVIEW.md)
- [게시용 Release 설명 초안](docs/RELEASE_NOTES.md)

최종 커밋 번호는 커밋 후 별도 전달합니다. 이 문서에 자기 자신의 커밋 번호를 반복 기입하지 않습니다.
