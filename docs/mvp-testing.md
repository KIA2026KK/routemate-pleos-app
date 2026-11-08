# MVP 반복 연결 검증

기본 CI는 외부 서비스 없이 단위 테스트·lint·빌드·일반 Android UI 테스트를 수행한다.
백엔드는 Testcontainers의 실제 PostGIS에서 동시 입장·중복 요청·정원·거리·만료·권한을 검증한다.
LiveKit 관리 API의 오류·JWT 서명·권한·미디어 정리는 로컬 HTTP 대역 서버로 결정적으로 검증한다.
실제 통화 테스트는 아래 로컬 스택에서 따로 실행한다. 건너뛴 테스트는 성공으로 집계하지 않는다.

## 준비

1. 두 저장소를 같은 상위 폴더에 배치한다.
2. Java 21, Android SDK 및 일반 API 36 에뮬레이터 한 대를 실행한다.
3. Docker Desktop의 Linux 엔진을 실행한다. 기본 검증은 임시 DB·Redis를 사용하므로 개발 DB 실행은 필요하지 않다.
4. [LiveKit PoC 가이드](livekit-voice-poc.md)의 스크립트로 검증된 로컬 서버 바이너리를 준비한다.
   다운로드 후 서버를 종료해 7880 포트를 비워 둔다.

앱 저장소에서 실행한다.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
powershell -NoProfile -ExecutionPolicy Bypass -File tools/mvp/Test-Mvp.ps1 -Runs 2
```

스크립트는 백엔드 전체 테스트/JAR 빌드 후 독립 Compose 프로젝트의 임시 PostGIS(15432)·Redis(16379),
localhost 테스트 서버(18080)·LiveKit(7880)을 시작하고,
앱 단위 테스트·lint·빌드를 수행한다. 첫 회는 서버 연동·기본 화면을 포함한 전체 장치 테스트,
이후에는 실제 MVP 통화·화면 시나리오를 반복한다. 제한 시간 내 건강 상태를 확인하며 실패 즉시 중단한다.
이미 실행 중인 포트를 임의로 종료하지 않고, 자신이 시작한 서버만 마지막에 종료한다.
전용 DB·Redis는 실행 시 임의 비밀번호를 사용하며 종료 시 해당 Compose 프로젝트만 정리한다.
임시 DB는 tmpfs이므로 개발 DB·볼륨을 재사용하거나 삭제하지 않는다. 기존 에뮬레이터와 개발 인프라는 유지한다.
실행 로그·회차별 장치 결과는 Git 제외된 `app/build/mvp/`에 보관한다.
회차마다 합성 VIN·방 이름·참가 세션을 새로 만들고 `finally`에서 통화·운행을 정리한다.
실행마다 별도 `com.routemate.app.mvpverify<임의값>` 테스트 앱을 설치하고 종료 시 해당 패키지만 제거한다.
기존 앱의 설치·VIN·암호화 세션을 지우지 않으며, 새 테스트 DB와 이전 설치 토큰이 섞이는 문제도 방지한다.
개발용 공개 키는 서버 프로세스에만 전달하며 APK나 Git에 실제 비밀키를 넣지 않는다.

기존 테스트 스택이 동일 설정으로 실행 중일 때만 다음 옵션을 사용한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/mvp/Test-Mvp.ps1 -UseRunningStack -Runs 2
```

기존 서버 설정은 다음과 같아야 한다.

```properties
ALLOW_MOCK_VEHICLE=true
LIVEKIT_SERVER_URL=http://127.0.0.1:7880
LIVEKIT_PUBLIC_URL=ws://10.0.2.2:7880
LIVEKIT_API_KEY=devkey
LIVEKIT_API_SECRET=secret
```

위 값은 localhost `--dev` 전용이다. 실서비스는 전용 LiveKit 및 HTTPS/WSS를 사용한다.

## 실제 검증 흐름

- `MvpConnectionTest`: 세 합성 차량의 목적지 등록 → 5km 검색 → 방 생성·참여 → 서버 발급 토큰으로
  세 LiveKit 연결 → 마이크 발행·각 두 트랙 구독 → 음소거 → 백엔드 퇴장에 따른 실제 연결 종료 →
  재참여·새 참가 토큰 연결 → 운행 종료·서버 방 정리 확인.
- `MvpUiConnectionTest`: 앱 화면에서 목적지 등록 → 방 생성 → 현재 방 표시 → 실제 음성 연결 →
  마이크 켜기/끄기 → 퇴장 → 운행 종료 확인.
- 기존 계정 복구 테스트: 같은 VIN의 빈 자격증명 저장소 복구 → 기존 토큰 차단 → 차량·운행 ID 유지 확인.

3개 SDK 세션은 같은 일반 에뮬레이터에서 실행한다. 세 물리 단말의 실제 청취·음질·차량 오디오 라우팅,
실제 Pleos 목적지·VIN 수신, 실제 통신망 전환의 미디어 복구를 대신하지 않는다.
HTTP 단절·heartbeat 실패는 단위 테스트에서 확인하며 장비·실망 검증은 별도 수행해야 한다.

## 수동 확인

목적지 등록 후 **5km/10km 방 검색**, **통화방 생성**, **방 ID로 참여**를 사용한다.
현재 방에서 **그룹 음성 연결**을 누르고 마이크 권한을 허용한 뒤 **마이크 켜기**를 선택한다.
다른 단말에서도 같은 서버·방으로 참여한다. **통화방 퇴장** 또는 **운행 종료** 후 상대 인원 감소를 확인한다.
응답을 받지 못했을 때는 **통화방 상태 확인**으로 서버 상태부터 확인한다.
화면을 떠나면 통화를 종료하며, 연결 실패 시 사용자에게 상태와 재시도를 제공한다.

## 에뮬레이터 오디오

API 36 반복 시험 중 AudioRecordJava에서 네이티브 span assertion이 발생한 회차가 있었다.
로그에서 8kHz 초기화를 확인했고, 사용 중인 WebRTC가 과거 이미지 호환을 위해 에뮬레이터를 8kHz로
강제하는 코드를 확인했다. 앱 어댑터는 goldfish/ranchu에서 공식 AudioOptions customizer의
`JavaAudioDeviceModule.Builder.setSampleRate(48000)`을 적용한다. 녹음·마이크 발행·구독을 생략하지 않는다.
실기기의 오디오 기본 설정은 그대로 사용하며 실제 차량 라우팅은 별도 확인이 필요하다.

근거: [LiveKit 2.30.0 AudioOptions](https://github.com/livekit/client-sdk-android/blob/v2.30.0/livekit-android-sdk/src/main/java/io/livekit/android/LiveKitOverrides.kt),
[WebRTC 에뮬레이터 샘플레이트](https://github.com/webrtc-sdk/webrtc/blob/m150_release/sdk/android/src/java/org/webrtc/audio/WebRtcAudioManager.java),
[공식 샘플레이트 설정 API](https://github.com/webrtc-sdk/webrtc/blob/m150_release/sdk/android/api/org/webrtc/audio/JavaAudioDeviceModule.java).
