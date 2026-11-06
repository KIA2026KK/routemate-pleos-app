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
