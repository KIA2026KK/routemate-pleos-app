# 검증 결과 — 2026-11-15

RouteMate 0.3.0 (3), Android 패키지 `com.routemate.app`.
일반 Android 자동 시험, 실제 외부 서버·SDK 시험, 실제 사람의 청취 시험을 구분합니다.
**최종 팀 서명 release APK는 키 준비 대기 상태이므로 설치 검증 완료로 집계하지 않습니다.**

## 환경

- Windows / PowerShell, JDK 21, Gradle 8.14.3, Android SDK 36 / Build Tools 35.0.0.
- AVD `Medium_Phone_API_36`, Android 16 / API 36, Google Play x86_64 revision 7.
- 기본 화면 1080×2400 px / 420 dpi; 일반 AVD 차량 화면 모의 override 1920×1080 / 160 dpi.
- 실제 서버 `https://routemate-test-api.onrender.com`, LiveKit Cloud. 합성 VIN·독립 테스트 방 사용.
- 실제 Pleos·차량·Devbox 없음. Pleos 버전·화면 규격은 **확인 필요**.

## 확인된 결과

| 항목 | 결과 / 근거 |
| --- | --- |
| 앱 단위 테스트 | **100개 통과**, 실패 0. `app/build/test-results/testDebugUnitTest/` |
| 통화 회귀 테스트 | 일시 heartbeat 오류 유지·지속 오류 85초 보호·갱신 주기 유지·SDK 재접속/마이크 복구·이동 실패 유지·중복 퇴장 직렬화 포함 |
| 기본 UI 시험 | **6개 통과 / 9개 조건부 생략 / 실패 0**, 일반 Android·서버 미설정. `app/build/ux-verification/device-0.3-default.xml` |
| Phone release 빌드·lint | `assembleRelease lintRelease lintDebug` 성공, release/debug lint 오류 0·기존 경고 각 14 |
| 백엔드 로컬 | DB 불필요한 테스트 **10개 통과**, `bootJar` 성공 |
| 백엔드 기존 main CI | `474be96`의 Backend checks 성공. 실제 PostGIS 통합 검증은 GitHub CI 결과와 구분해 확인 |
| 원격 3km·5km API | **29개 통과**, 합성 운행 5개 생성·5개 종료. `remote-api-3-5-precheck.json` |
| 원격 초대 코드·단일 방·원자적 이동 | **13개 통과**, 합성 운행 4개 생성·4개 종료. `invite-switch-result.json` |
| 실제 Geocoder 주소 검색 | 예시 목록 밖 `서울특별시 중구 세종대로 110` 조회 통과 |
| 서버 연동 UI | 광주송정역 자동 등록, 방 생성, 실제 음성 연결, 마이크 켜기/끄기, Activity 재생성, 홈 배너 복귀, 퇴장, 운행 종료 통과 |
| 암호화 세션·차량 계정 복구 | 재로드·목적지 갱신·이전 세션 철회 시험 통과 |
| 비밀정보 검사 | Gitleaks 8.30.1, 접근 가능한 Git 이력·현재 제출 파일에서 탐지 0. [검사 범위](PUBLIC_REVIEW.md) |
| 로컬 서명 스크립트 | PowerShell 문법 파싱 성공. 실제 키 생성·release 서명은 사용자 입력 대기 |

원시 산출물은 Git 제외된 `app/build/ux-verification/`, `app/build/submission/`에 보관합니다.
테스트용 debug APK는 별도 `app/build/distributions/RouteMate-render-test-v0.3.0.apk`이며 제출 release와 다릅니다.

## 실패 기록과 재검증

- 첫 클라우드 장치 시험은 에뮬레이터 DNS 오류로 실패. PC 서버 health는 UP이나 AVD에서 호스트 해석 실패 확인.
  테스트 AVD만 명시적 DNS로 재시작하고 데이터 유지. 복구 후 Geocoder·목적지·계정·통화 UI 시험 통과.
- 120초 백그라운드 시험의 coroutine 자식 수집기를 timeout 밖 finally에서만 정리해 시험 전체 종료가 지연되는 문제 수정.
- 세 SDK 세션 시험에서 일부 트랙 수 표시가 마지막 SDK 이벤트 이후 갱신되지 않는 경우를 확인.
  SDK 2.30.0의 `audioTrackPublications` Flow를 직접 관찰하도록 보완.
- 위 두 항목은 수정 후 장치 재검증 결과를 아래에 기록합니다. 실패 로그는 삭제하지 않습니다.

## 실제 음성과 미검증 범위

SDK 연결·마이크 트랙 발행/구독은 **두 물리 단말에서 사람이 음성을 들었다는 의미가 아닙니다**.
실제 Samsung 기기의 과거 끊김 로그가 없어 그 발생 원인을 확정하지 않았습니다.

- 최종 팀 서명 APK 설치·실행과 서명 호환성: 미검증, release 키 대기.
- 실제 Pleos VIN·목적지·변경 이벤트·차량 마이크/오디오: 미검증.
- 두 실제 단말의 청취·에코·음질·장시간 통화·네트워크 전환·Samsung 절전 정책: 미검증.
- 이번 PC의 Docker 엔진 문제로 전체 PostGIS 테스트 로컬 재실행 미완료. 원격 GitHub CI 통합 테스트와 실제 API 시험을 별도 기록.

## 재현 명령

```powershell
.\gradlew.bat testDebugUnitTest lintDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest -PprototypeDemo=true --console=plain

# 실제 서버·음성: 합성 VIN을 허용하는 테스트 서버와 네트워크 필요
.\gradlew.bat connectedDebugAndroidTest -PbackendUrl=https://routemate-test-api.onrender.com `
  '-Pandroid.testInstrumentationRunnerArguments.mvpE2E=true' `
  '-Pandroid.testInstrumentationRunnerArguments.tripE2E=true' `
  '-Pandroid.testInstrumentationRunnerArguments.geocoderE2E=true' `
  '-Pandroid.testInstrumentationRunnerArguments.sustainedE2E=true' --console=plain
```

[SDK 트랙 관찰 근거](https://github.com/livekit/client-sdk-android/blob/v2.30.0/livekit-android-sdk/src/main/java/io/livekit/android/room/participant/Participant.kt)
