# RouteMate Android PoC

Kotlin + Jetpack Compose 초기 프로젝트입니다. 샘플 목적지 가져오기만 구현했으며
실제 Pleos 내비게이션과 LiveKit 통화는 아직 연결하지 않았습니다.

## 로컬 빌드

Java 21(컴파일 대상 17), Android SDK 36, Build Tools 35.0.0 이상이 필요합니다.
Android Studio에서 프로젝트 폴더를 열어 SDK 경로를 설정하거나 ANDROID_HOME을 지정하세요.
local.properties와 키/토큰은 커밋하지 않습니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
# 에뮬레이터/기기 연결 후
.\gradlew.bat connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
기기에 설치하고 앱에서 '샘플 목적지 가져오기'를 누르면 '서울역 (샘플)'이 표시됩니다.

## DDD 구조

`destination/domain`: 좌표 검증을 담당하는 Destination 값 객체와 DestinationProvider 포트.
`destination/application`: 목적지 조회 유스케이스.
`destination/infrastructure`: 명시적인 Mock 구현. 향후 확인된 Pleos SDK 어댑터 추가.
`destination/presentation`: ViewModel/StateFlow와 Compose 화면.
MainActivity에서 의존성을 조립합니다. 도메인은 Android 및 SDK에 의존하지 않습니다.
현재 규모에서는 단일 app 모듈의 패키지 경계로 유지합니다.

## 브랜치와 PR

```powershell
git switch develop
git pull --ff-only origin develop
git switch -c feat/livekit-voice-poc
# 구현 및 테스트 후 커밋/푸시 → PR base: develop
```

기능/API 하나마다 develop에서 새 브랜치를 만듭니다.
기능 PR의 Android 검사 통과 후 develop에 머지하고, develop 검증이 통과하면
별도 `develop → main` PR로 승격합니다. main 대상 PR은 develop만 허용하는 CI 검사가 있습니다.
필수 검사 강제 적용은 GitHub 브랜치 보호 설정에서 별도로 지정해야 합니다.

## 다음 검증

LiveKit 연결/음소거/퇴장/재접속과 3개 단말 음성 송수신은 별도 기능 PR에서 검증합니다.
Pleos 목적지 조회와 변경 이벤트도 별도 PR로 검증합니다.
일반 Android 성공은 실제 차량 통화 정책 검증을 대신하지 않습니다.

버전 호환성 근거:
- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://developer.android.com/develop/ui/compose/bom
