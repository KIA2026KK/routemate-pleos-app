# 제출용 release 서명

현재 release keystore는 없습니다. 기존 `RouteMate-render-test.apk`는 **debug / 0.1.0 (1)**,
`CN=Android Debug` 인증서 APK입니다. 이름 변경으로 release가 되지 않습니다.
이번 소스는 release **0.3.0 (3)**, `com.routemate.app`, debuggable=false, testOnly=false입니다.
product flavor는 없으며 Gradle 속성으로 Pleos/Mock 구현을 선택합니다.

## 로컬 키 생성과 서명

Java 21, Android SDK 36 / Build Tools 35.0.0, 인터넷을 준비합니다.
PowerShell에서 앱 저장소로 이동하여 실행합니다. 비밀번호는 화면에 표시되지 않는 로컬 입력입니다.

```powershell
cd C:\2026KIA\routemate-pleos-app
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
powershell -NoProfile -ExecutionPolicy Bypass -File tools/submission/Build-Submission.ps1 `
  -Keystore C:\RouteMate-signing\routemate-release.p12 -Alias routemate -CreateKey
```

기본 제출 대상은 **Pleos**입니다. 실제 VIN·내비게이션 목적지를 사용하며 일반 휴대폰에서 Mock으로 자동 대체하지 않습니다.
처음에만 `-CreateKey`를 사용합니다. 재빌드할 때는 같은 명령에서 이 옵션을 제거합니다.
키와 비밀번호는 별도로 안전하게 백업하고 Git·메신저·Firebase 설정 파일에 넣지 않습니다.
스크립트는 환경 변수로 비밀번호를 전달하고 종료 시 이전 환경을 복구합니다.

생성 결과:

- `apk/노래말고 사람들과 함께_RouteMate_release.apk`
- 같은 경로의 `.apk.json` 및 `.apk.sha256` 검증 파일
- 서명 검증, package/version, debug/testOnly 검사 통과 후에만 복사
- 자동 Git 커밋·push·Release 게시 없음

휴대폰에서 실제 Render·LiveKit을 확인하려면 같은 키로 별도 빌드합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/submission/Build-Submission.ps1 `
  -Keystore C:\RouteMate-signing\routemate-release.p12 -Alias routemate -Target Phone
```

결과는 `app/build/distributions/RouteMate-phone-test-release.apk`입니다.
Mock VIN·광주송정역 목적지와 **실제 서버·음성 연결**을 사용합니다. 제출 Pleos APK와 혼동하지 않습니다.
휴대폰 테스트용도 같은 applicationId이므로 두 APK를 동시에 설치할 수 없습니다.

## 기존 설치와 업데이트

기존 debug 인증서 SHA-256:
`5ef5821a1b823646ccd8765b6366a781121407efbbe5536df156c0b95788f134`.
새 release 키의 인증서와 다르므로 기존 debug 앱 위에 업데이트 설치할 수 없습니다.
계정 복구키를 안전하게 보관한 뒤 직접 기존 앱을 제거하고 새 APK를 설치해야 합니다.
앱 삭제 시 설치 세션·테스트 VIN이 사라질 수 있습니다. 스크립트는 앱을 자동 삭제하지 않습니다.

## APK가 없는 상태에서의 빌드 확인

서명 없는 release도 소스 컴파일·패키징 확인은 가능하지만 Android에 설치할 수 없습니다.

```powershell
.\gradlew.bat assembleRelease -PdestinationProvider=pleos -PvehicleProvider=pleos `
  -PbackendUrl=https://routemate-test-api.onrender.com --console=plain
```

이번 검증용 unsigned 산출물은 `app/build/submission/unsigned/`에 별도 보관합니다.
최종 서명 APK가 만들어지면 **그 파일 자체를 설치·실행해** [테스트 결과](TEST_RESULTS.md)를 보완합니다.
앱 원격 push 전 APK를 추가하고 같은 소스·APK 조합으로 최종 로컬 커밋을 새로 기록합니다.

[Android 공식 서명 안내](https://developer.android.com/studio/publish/app-signing)
