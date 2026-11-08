# 초기 Android 검증 — 2026-11-02

- Windows / Java 21 / Android SDK 36에서 `testDebugUnitTest lintDebug assembleDebug` 성공.
- 좌표 경계 및 잘못된 목적지 입력을 검증하는 단위 테스트 2개 통과.
- Lint 오류 없음. 호환성 검증을 위해 고정한 라이브러리의 최신 버전 알림은 남아 있음.
- APK: app/build/outputs/apk/debug/app-debug.apk.
- UI 테스트는 샘플 목적지 버튼을 누르고 서울역 샘플 표시를 확인함.
- 로컬 API 36 에뮬레이터는 디스크 공간 부족으로 실행 실패. 로컬 기기 테스트는 미실행.
- GitHub Actions에 별도 API 36 에뮬레이터 UI 테스트 추가. 실행 결과는 PR checks에서 확인.
- 실제 Pleos 목적지 및 LiveKit 통화는 구현/검증하지 않았음.

## Pleos 목적지 어댑터 검증 — 2026-11-05

- NaviHelper 3.0.5 및 navi-data 3.0.4의 설치된 AAR를 확인하고 실제 초기화·리스너·조회·해제 API로 구현.
- Mock 구현 및 기존 suspend 조회 인터페이스 유지. 목적지 변경 관찰을 위한 기본 Flow 메서드 추가.
- `testDebugUnitTest assembleDebug lintDebug -PdestinationProvider=mock`: 성공.
- `testDebugUnitTest assembleDebug lintDebug -PdestinationProvider=pleos`: 성공.
- 두 모드 각각 단위 테스트 24개 통과, 실패·오류·건너뜀 없음.
- 테스트 범위: SDK 데이터 변환, 좌표 순서/검증, 목적지 없음, 변경/취소 이벤트,
  변경 중의 이전 결과 무효화, 중복 콜백, 연결 실패, 시간 초과, 구독 취소,
  리스너 정리, 재시도, ViewModel 상태 반영, Mock UTF-8 표시.
- `connectedDebugAndroidTest -PdestinationProvider=mock`: API 36 일반 Android 에뮬레이터에서 UI 테스트 1개 통과.
  샘플 안내, 서울역 이름, 위도·경도 및 조회 버튼 동작 확인.
- Pleos 모드 APK도 같은 일반 Android 에뮬레이터에서 실행.
  Navigation 서비스가 없는 환경에서 시간 초과 안내, 다시 가져오기 재시도 및 프로세스 유지 확인.
  이는 실제 Pleos 목적지 수신 성공 검증이 아님.
- Lint: 오류 0개, 경고 7개. 라이브러리 최신 버전 알림 및 기존 백업 설정 경고가 남아 있음.
- 로컬 `local.properties`의 Windows 드라이브 구분자 이스케이프 오류를 수정해 Lint 오류 해결.
  해당 파일은 Git에서 제외됨.
- 검증 APK는 `app/build/outputs/apk/validation/routemate-mock-debug.apk`와
  `app/build/outputs/apk/validation/routemate-pleos-debug.apk`에 구분해 보관.
- 실제 Pleos AVD/기기가 없어 서비스 연결 성공, 실제 목적지 설정·변경·취소 시 이벤트 순서 및
  실제 목적지 미설정 시 경로 상태는 미검증. 다음 단계는 [수동 검증 절차](docs/pleos-destination.md) 실행.
- 작업 브랜치: develop 기반 `feat/pleos-destination`. 기존 README 및 SDK 설정 변경 유지.
- Spring Boot, DB 테이블, REST API, LiveKit 코드는 변경하지 않음.
- 추천 커밋: `feat: Pleos 목적지 조회 및 변경 감지 구현`
- 원격 CI의 초기 에뮬레이터 부팅 실패 원인은 실행 환경 디스크 부족
  (여유 약 6.7GB, 기본 데이터 파티션 필요 약 7.4GB)으로 확인.
  4096M 지정 후에도 이미지의 최소 필요 공간이 유지되는 것을 확인.
  UI 검사 실행기의 미사용 .NET SDK를 정리하고 부팅 직전 여유 공간을 확인하도록 수정.

## Pleos 실행 준비 및 최소 API 호환성 — 2026-11-05

- 공식 요구사항에 맞춰 Pleos 빌드의 minSdk를 28로 설정. Mock 빌드는 기존 26 유지.
- 두 모드 각각 `testDebugUnitTest assembleDebug` 성공, 단위 테스트 24개 통과.
- 공식 Pleos 이미지 메타데이터와 ZIP 디렉터리 확인: 압축 약 4.1GB, 해제 약 19.6GB.
- AVD 및 작업 여유를 포함한 준비 공간 약 33GB, 권장 40GB 산정.
- 로컬 C 드라이브 공간 부족으로 실제 이미지 설치 및 Navigation 목적지 수신 검증 미실시.
- SDK 조회와 우리 서버 REST 전송의 역할, Mock으로 병행 가능한 개발 범위 문서화.
- 설치 절차는 [에뮬레이터 준비 문서](docs/pleos-emulator-setup.md) 참고.

## LiveKit 음성 통화 PoC — 2026-11-06

- LiveKit Android SDK 2.30.0 및 공식 Windows Server 1.13.11 개발 모드 사용.
- `voice` 컨텍스트에 SDK 독립 도메인 포트, 유스케이스, 실제 SDK 어댑터, ViewModel 추가.
- 디버그 전용 통화 화면: 토큰 입력, 권한 요청, 입장, 참가자/수신 트랙 수, 마이크 제어, 퇴장.
- 기본 음소거 입장, 20초 연결/마이크 변경 제한, 재접속 상태, 서버 종료 및 구독 취소 시 해제 처리.
- 백그라운드 전환 시 통화 종료. 토큰 저장·출력 및 API Secret의 APK 포함 없음.
- Mock 모드 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 성공.
- Pleos 모드 `testDebugUnitTest assembleDebug assembleRelease` 성공, 단위 테스트 34개 통과.
- 릴리스 Manifest에서 테스트 Activity 및 평문 통신 허용 설정 제외 확인.
- 단위 테스트 34개 통과(기존 목적지 24개 + 음성 10개), Lint 오류 0개·경고 7개.
- 일반 API 36 에뮬레이터 UI 테스트 2개 통과(기존 Mock 목적지 표시, 개발용 통화 화면 진입·토큰 입력).
- 일반 API 36 에뮬레이터와 로컬 실제 LiveKit 서버의 선택 실행 테스트 1개 통과(26초).
  세 SDK 세션의 참가자 3명, 마이크 트랙 발행 및 각 2개 구독, 음소거, 퇴장 후 인원 감소 확인.
- 동일 에뮬레이터 내 세 세션 검증이며, 2~3개 물리 단말의 실제 음성 청취·음질·오디오 라우팅은 미검증.
- 실제 Pleos 차량의 오디오 정책 및 네트워크 단절 후 실제 미디어 복구는 미검증.
- 로컬 개발 서버·토큰 발급·자동 연결 테스트 절차: [음성 PoC 가이드](docs/livekit-voice-poc.md).
- 원격 UI 검사에서 Activity 전환 직후 Compose 루트가 없는 순간의 조회 실패 확인.
  테스트에 새 화면 표시 대기·Activity 정리 및 실패 시 장치 테스트 리포트 보관 추가.

## 백엔드 목적지 등록 연동 — 2026-11-08

- `trip` 컨텍스트에 등록 포트·유스케이스·HTTP 어댑터·ViewModel·등록 UI 추가.
- 서버별 익명 토큰을 Android Keystore AES-GCM으로 암호화 저장, 인증 헤더 전송 및 리다이렉트 차단.
- 등록 중 중복 요청 방지, 목적지 변경 시 이전 요청 취소 및 오래된 결과 무효화.
- HTTP 및 ViewModel 테스트 포함 단위 테스트 43개 통과, Mock 빌드 성공.
- Pleos 모드 단위 테스트 43개·Lint·디버그 및 HTTPS 설정 릴리스 빌드 성공.
- Lint 오류 0개·경고 11개(라이브러리 버전 알림, 기존 백업 설정, 의도적인 동기 저장 권고).
- 실제 로컬 백엔드·PostGIS와 API 36 에뮬레이터의 연동 테스트 2개 통과.
  화면의 샘플 목적지 등록 완료, 암호화 저장/재로드, 재등록 시 운행 ID 유지 및 목적지 변경 확인.
- 해당 연동 실행에서 서버 미설정 UI 테스트 1개는 설정 조건에 따라 건너뜀.
- 기존 목적지·음성 화면 UI 회귀 테스트 2개 통과.
- 실제 Pleos 목적지의 서버 등록 및 차량 환경 검증은 미실시.
- 세션 갱신·복구 및 서버 운행 종료는 후속 범위. [실행 가이드](docs/trip-registration.md) 참고.

## Pleos VIN 조회 및 차량 계정 연동 — 2026-11-08

- Vehicle SDK 2.3.2 AAR에서 Vehicle.initialize/release, CarInfo.getVINInfo 및 checkVINInfoCapabilities 확인.
- 실제 Kotlin 콜백은 nullable String이므로 VIN null·잘못된 형식·권한·미지원·시간 초과를 구분해 등록 중단.
- Main dispatcher SDK 호출, 중복·늦은 콜백 무시, 10초 제한 및 정상/실패/취소 시 release 처리.
- CAR_INFO 권한과 vehicleProvider=pleos|mock 빌드 선택 추가. 실차 조회 실패 시 Mock/익명 대체 없음.
- 설치 세션 → VIN 차량 연결 → 목적지 등록 흐름 및 VIN/source별 Keystore 자격증명 분리.
- 같은 서버 origin의 표기 차이는 정규화하고 다른 차량·다른 서버·Mock/실차 간 토큰 재사용 차단.
- Mock 모드 단위 테스트 51개·Lint·앱 및 장치 테스트 APK 빌드 통과. Lint 오류 0개·경고 12개.
- 실제 로컬 백엔드와 일반 API 36 에뮬레이터의 VIN/운행 연동 테스트 2개 및 기존 화면 2개 통과.
  합성 VIN, 마스킹 표시, 암호화 토큰 재로드, 동일 차량/운행 ID 유지 및 목적지 갱신 확인.
- 실제 Pleos Vehicle Service에서 실제 VIN 수신 성공은 미검증. SDK 신고 값은 서버 소유권 증명과 구분.
- 일반 Android에서 실제 SDK의 Vehicle Service 부재 시 등록 중단·오류 표시 테스트 1개 통과.
- Pleos 목적지·VIN 모드 단위 테스트 51개·Lint·디버그 및 HTTPS 릴리스 빌드 통과.
- 설계·DB 속성 근거·제약은 [VIN 문서](docs/vin-vehicle-identity.md) 참고.

## 차량 계정 복구 및 세션 갱신 — 2026-11-09

- auth 컨텍스트에 갱신·복구키 발급·계정 복구 포트, 유스케이스, HTTP 어댑터, ViewModel 및 계정 관리 화면 추가.
- 목적지 등록에 유효 세션 자동 갱신 추가. 공통 HTTP 어댑터로 HTTPS 검증·리다이렉트 차단·취소 처리를 공유.
- 복구키는 365일 유효, 만료·재발급까지 재사용 가능. 복구마다 이전 세션 폐기, 새 토큰은 기존 Keystore 저장소 사용.
- FLAG_SECURE, 입력 마스킹, 민감 클립 표시, 화면 이탈 시 입력·표시·진행 요청 정리. 키 원문 파일 저장 없음.
- Mock 구성 단위 테스트 58개·lintDebug·assembleDebug·장치 테스트 APK 빌드 성공. Lint 오류 0개·경고 12개.
- API 36 기본 장치 테스트 4개 통과·선택 테스트 5개 조건부 건너뜀. 계정 화면 진입·복구 입력 요구·캡처 제한 확인.
- 실제 로컬 백엔드와 API 36 장치 테스트 구성은 6개 통과·3개 조건부 건너뜀.
  등록·암호화 저장·차량 연결 회귀와 새 저장소 복구 후 차량/운행 ID 유지, 이전 토큰 401, 같은 키 재시도 검증.
- 새 저장소는 앱 재설치 후 자격증명 부재를 흉내 낸 테스트이며 실제 앱 삭제·재설치 검증과 구분.
- 실제 Pleos 차량·Devbox·에뮬레이터 사용 불가. 실제 VIN 수신 성공과 OEM 소유권 확인은 미검증.
- Pleos 목적지·VIN 구성의 단위 테스트 58개 및 디버그·HTTPS 릴리스 APK 빌드 성공.
