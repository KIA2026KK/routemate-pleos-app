# 다음 AI 작업 인계

## 먼저 읽을 문서

앱·백엔드 `AGENTS.md`, 앱 `README.md`, `docs/TEST_RESULTS.md`,
`docs/SUBMISSION_CHECKLIST.md`, `docs/RELEASE_SIGNING.md`, `docs/SUBMISSION_INFO.md` 순서.
기존 소스·설정·사용자 변경을 덮어쓰거나 Git 기록을 재작성하지 않는다.

## 프로젝트

- 팀: **노래말고 사람들과 함께**, 앱: **RouteMate**, 패키지 `com.routemate.app`, 0.3.0 (3).
- 앱: `C:\2026KIA\routemate-pleos-app` / https://github.com/KIA2026KK/routemate-pleos-app
- 백엔드: `C:\2026KIA\routemate-backend` / https://github.com/KIA2026KK/routemate-backend
- API: https://routemate-test-api.onrender.com
- Spring Boot 3.5.16 / Java 21 / PostgreSQL·PostGIS / Redis / Flyway V1~V7 / LiveKit.
- Firebase는 App Distribution 배포 수단. Firebase DB로 이전하지 않는다.
- 별도 두 저장소 유지. 기능별 DDD, 순수 domain·application 포트·infrastructure 어댑터·presentation 구조.

## 구현 내용

3km·5km 목적지 기반 검색, 첫 Mock 목적지 광주송정역 자동 등록, 장소·도로명 검색,
VIN 계정·설치 세션·복구키, 방 생성·정원·단일 방 참여, 초대 코드·원자적 방 이동,
서버 발급 LiveKit 토큰, 전체 통화방·발화 표시·홈 현재 방 배너,
microphone foreground service·통화 알림·heartbeat 재시도·85초 참가 권한 보호·제한된 재접속.
일시 heartbeat 실패로 정상 미디어를 즉시 종료하던 경로와 Activity가 통화를 소유하던 구조 보완.
실제 Samsung 과거 끊김 원인은 기기 로그가 없어 확정하지 않음.

## Git와 게시 권한

- 사용자 지시로 **커밋·push·PR·develop→main 머지 승인** 상태.
- 소스 기능 PR은 앱 #21, 백엔드 제출 문서는 #22→#23으로 main 반영 완료.
- 실제 최종 상태는 반드시 `git status`, 원격 refs, PR/Actions에서 다시 확인.
- 최신 develop에서 기능 브랜치 → develop PR/CI → develop 검증 → main PR/CI 순서. 실패한 검증 우회 금지.
- 커밋·PR 메시지는 간결한 한국어, 문장 끝은 명사형.
- Release 게시·저장소 공개 전환·접수 메일 발송은 **미승인**, 실행하지 않음.
- 두 저장소 private 상태 확인. 사용자 제공 조건은 2026-11-16까지 공개, 2026-11-23까지 유지.
- 최종 커밋 번호는 커밋 이후 사용자에게 별도 보고. 자기 커밋 번호를 파일에 넣고 다시 커밋하지 않음.

## 테스트 상태와 주의점

- 앱 단위 테스트 100개 통과. 기본 장치 UI 6개 통과·9개 조건부 생략 기록.
- Phone release 빌드·debug/release lint 성공. Pleos unsigned release 산출물 생성.
- 원격 API: 반경·권한 등 29개 통과, 초대 코드·단일 방·안전한 이동 13개 통과, 생성 운행 모두 정리.
- 실제 외부 서버에서 목적지·주소 검색, 계정 복구, 통화 UI·음소거·회전·홈 배너·퇴장·운행 종료 통과.
- 장시간 통화 테스트의 자식 coroutine 정리 위치와 JUnit 메서드의 반환형을 수정.
  `runBlocking<Unit>`를 유지. 첫 CI의 `should be void` 오류를 테스트 제거·무시로 해결하지 않음.
- 세 SDK 세션의 일부 오디오 트랙 수 표시 갱신을 `audioTrackPublications.flow` 관찰로 보완.
  **수정 후 3 SDK 세션·120초 백그라운드 시험의 최종 성공 여부는 `TEST_RESULTS.md`와 원시 기록 확인. 미확인 시 재실행.**
- 로컬 AVD에 DNS·부팅 문제가 있었음. 별도 `RouteMate_Submission_API36`도 기존 API 36 이미지로 준비.
  일반 Android이며 Pleos가 아님. 원래 `Medium_Phone_API_36`의 사용자 데이터를 삭제하지 않음.
- backend 로컬 DB 불필요 테스트 10개·bootJar 통과. 전체 PostGIS 검증은 정상 Docker가 있는 GitHub CI 기준.
- 물리 두 단말의 사람이 들은 음성·음질·실차·실제 Pleos 목적지/VIN은 미검증.
- SDK 트랙 발행/구독 성공을 실제 두 기기 청취 성공으로 표현하지 않음.

## 바로 이어서 할 작업

1. GitHub main에 앱 최신 코드·문서 반영 여부와 모든 필수 CI 확인. 남아 있으면 실패 원인 수정 후 정규 PR 순서로 마무리.
2. release 키가 **없음**. 사용자가 로컬에서 `tools/submission/Build-Submission.ps1` 실행해 생성·서명하도록 안내.
   비밀번호·개인키를 채팅에 요청하지 않음. 저장소 밖에 키 보관·백업.
3. 기본 제출 대상은 실제 SDK의 **Pleos**. 일반 Android용은 `-Target Phone`으로 별도 생성.
   기존 배포 APK는 debug 서명이고 새 release 키와 다르므로 직접 업데이트 불가. 삭제 전에 복구키 보관 안내.
4. 최종 지정 파일 `apk/노래말고 사람들과 함께_RouteMate_release.apk` 생성 후 그 **서명된 동일 파일** 설치·권한·기능 검증.
5. 실제 Pleos 설치 버전·화면 규격 수집. 로컬에는 사용 완료한 Pleos 이미지가 없음.
   문서의 과거 v2.1.0 후보 정보를 개발에 사용한 버전으로 쓰지 않음.
6. 두 물리 단말에서 청취·발화·음소거·3분 이상 화면 잠금·네트워크 전환·초대 코드·방 이동 시험.
7. APK 크기·SHA-256·버전·서명·테스트 결과를 갱신하고 APK 포함 최종 소스를 정상 PR로 main 반영.
8. 사용자가 공개 전환·Release 게시·최종 접수 수행. 원격 main 최종 커밋 번호와 제출 URL 전달.

## 명령과 산출물

```powershell
cd C:\2026KIA\routemate-pleos-app
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat testDebugUnitTest lintDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

외부 서버 시험 인자와 수동 시나리오는 `TEST_RESULTS.md`, `SUBMISSION_CHECKLIST.md` 참고.
기기 없으면 시험 생략을 분명히 보고한다. Pleos 대용량 이미지를 임의로 내려받거나 기존 AVD를 초기화하지 않는다.

- 휴대폰 debug 배포: `app/build/distributions/RouteMate-render-test.apk` (정리 후 최신 0.3 기준).
- 서명 전 release: `app/build/submission/unsigned/RouteMate-Pleos-release-unsigned.apk`, `RouteMate-Phone-release-unsigned.apk`.
- `apk/`의 지정 최종 파일은 **팀 release 키 서명 전에는 없음**. unsigned를 이름만 바꾸어 제출하지 않음.
- Git 제외된 검사 증거: `app/build/submission/`, `app/build/ux-verification/`.
- Gitleaks 8.30.1로 접근 가능한 Git 이력·제출 파일 탐지 0. keystore·.env·개인키는 Git 제외.

## 정리 내역

구버전 0.1 안내·화면·결과 파일, 중복 APK, 임시 UI 자동화 스크립트 등 생성 파일 16개 정리.
최신 Phone debug APK는 기존 표준 경로로 갱신하여 하나만 유지.
재사용할 원격 API 검사 스크립트 두 개는 `tools/submission/`으로 옮기고 결과를 build 아래로 저장하도록 보완.
소스·리소스·wrapper·SDK 설정·마이그레이션·미서명 release·실패 증거는 보존.
정확한 삭제 목록은 로컬 `app/build/submission/cleanup.json` 참고.
원본 AVD·전역 Android/Gradle 설치·사용자 개인 파일은 삭제하지 않음.

## 사용자에게 전달할 원칙

계획으로 끝내지 말고 승인 범위의 실제 작업을 완료한다. 다만 release 서명·실제 기기처럼 입력/환경이 없는 단계만 남긴다.
제출 완료·main 반영·APK 설치·음성 청취·Pleos 검증을 수행하지 않은 상태에서 완료라고 표현하지 않는다.
완료 작업, 정확한 APK 경로·해시, GitHub main 커밋, 남은 사용자 조작을 간결하게 구분한다.
