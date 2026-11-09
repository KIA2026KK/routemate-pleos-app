# LiveKit 음성 통화 PoC

이 단계는 목적지 매칭과 분리된 개발용 실제 음성 통화 실험이다.
제품의 통화방·서버 발급 토큰 연결은 [MVP 계약](mvp-room-calls.md)과 [통합 검증](mvp-testing.md)을 따른다.
디버그 앱의 **음성 통화 테스트** 버튼으로 진입한다. 릴리스 앱에는 진입 버튼과 테스트 Activity가 없다.
도메인 포트 `VoiceCall` → 유스케이스 `JoinVoiceCall` → LiveKit 어댑터로 연결한다.

## Windows 로컬 서버

프로젝트 루트에서 별도 PowerShell 창으로 실행한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\voice-poc\Start-Server.ps1
```

공식 LiveKit Server 1.13.11 Windows x64 파일을 내려받아 SHA-256 확인 후 실행한다.
다운로드 파일은 Git에서 제외된 `app/build/voice-poc`에만 저장한다.
기본 바인딩은 `127.0.0.1`, 시그널링 포트는 7880이다. 종료는 서버 창의 Ctrl+C이다.
개발 모드의 공개된 개발 키만 사용하며 실제 서비스·공용 네트워크에 배포하지 않는다.
스크립트 실행 정책 변경은 해당 PowerShell 프로세스에만 적용된다.

## 참가자별 토큰

아래 명령 결과를 앱의 참가자 토큰 입력란에 붙여 넣는다.
서로 다른 단말은 `driver-a`, `driver-b`, `driver-c`처럼 서로 다른 identity를 사용한다.
같은 identity를 재사용하면 기존 참가자의 연결이 끊길 수 있다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\voice-poc\New-Token.ps1 -Identity driver-a
```

기본 방은 `routemate-poc`, 토큰 유효기간은 15분이다.
`-Room`과 `-Minutes` 옵션으로 변경할 수 있으며 모든 참가자는 같은 방의 토큰을 사용한다.
이 스크립트는 로컬 `--dev` 서버 전용이며 마이크 발행·트랙 구독 권한만 부여한다.
API Secret은 앱 소스·빌드 설정·APK에 포함하지 않는다.
제품 통화 화면에서는 백엔드가 방 참여 권한을 확인하고 토큰을 발급한다. 수동 PoC는 제품 인증 흐름과 분리한다.

## 서버 주소

- PC의 일반 Android 에뮬레이터: `ws://10.0.2.2:7880`
- 실제 Android 단말: 같은 LAN의 PC 주소 사용. 서버를 `Start-Server.ps1 -AllowLan`으로 실행하고
  사설망에서 7880/TCP, 7881/TCP, 7882/UDP 접근을 허용한다. 방화벽 설정은 자동 변경하지 않는다.
- 외부 서버: 유효한 TLS 인증서를 사용하는 `wss://...` 주소 사용.

서버의 ICE 광고 주소가 VPN·WSL·가상 어댑터 주소이면 시그널링은 성공해도 음성이 연결되지 않을 수 있다.
이때 실제 단말이 접근 가능한 네트워크 주소로 LiveKit RTC 설정을 조정해야 한다.
평문 통신 허용은 디버그 Manifest에만 적용되며 입력 검증은 로컬·사설 IP의 `ws://`만 허용한다.

## 수동 2~3인 검증

1. 각 단말에서 마이크 권한을 허용하고 서로 다른 토큰으로 입장한다.
2. 같은 방 이름과 참가자 2~3명이 표시되는지 확인한다. 입장 직후 마이크는 꺼져 있어야 한다.
3. 각각 마이크를 켜고 번갈아 말해 상대의 실제 목소리를 듣는다. 같은 공간에서는 이어폰을 사용한다.
4. 한 참가자의 마이크를 끄고 해당 음성 송출이 멈추는지 확인한다.
5. 한 참가자가 퇴장하면 나머지 단말의 참가자 수가 감소하는지 확인한다.
6. 네트워크 중단·복구 시 재접속 상태와 음성 복구를 확인한다.
7. 앱을 백그라운드로 보내면 통화가 종료되고 마이크 사용이 정리되는지 확인한다.

PoC는 화면이 보일 때만 통화한다. 백그라운드 통화 서비스, 차량 오디오 정책,
운행 세션·방 참여 API·서버 권한 검사는 이번 구현 범위에 포함하지 않는다.
토큰은 메모리에만 보관하며 Activity 저장 상태에 기록하지 않고 테스트 화면 캡처를 차단한다.

## 자동 검증

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

기본 장치 테스트는 목적지 및 PoC 진입 UI를 확인한다.
로컬 서버가 실행 중이고 에뮬레이터가 부팅된 상태에서는 다음 명령으로 토큰 생성·설치·실제 연결 검증을 실행한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\voice-poc\Test-Connection.ps1
```

`LiveKitConnectionTest`는 `livekitUrl`, `livekitTokenA`, `livekitTokenB`, `livekitTokenC`
instrumentation 인자가 있을 때만 로컬 서버에 실제 접속한다. CI 기본 실행에서는 이 테스트를 건너뛴다.
세 참가자의 실제 연결, 마이크 트랙 발행·구독, 마이크 끄기, 퇴장을 검증한다.
동일 에뮬레이터 내 세 SDK 연결은 세 물리 단말의 음질·오디오 라우팅·청취 확인을 대신하지 않는다.

참고: [Android SDK](https://github.com/livekit/client-sdk-android/tree/v2.30.0),
[로컬 서버](https://docs.livekit.io/transport/self-hosting/local/),
[토큰 권한](https://docs.livekit.io/frontends/reference/tokens-grants/).
