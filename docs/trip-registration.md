# 목적지 등록 실행 및 검증

[백엔드와 공통 API 계약](trip-api.md)을 사용한다.
목적지를 조회한 뒤 **목적지 등록**을 누르면 VIN 조회 → 설치 세션 발급 → 차량 연결 → 인증된 운행 등록을 수행한다.
차량 계정의 기준과 실제 SDK 속성은 [VIN 설계](vin-vehicle-identity.md)를 따른다.
화면에는 등록 중·완료·오류·재시도가 표시된다. Mock 목적지는 샘플임을 별도로 안내한다.
목적지가 바뀌거나 없어지면 이전 요청과 등록 완료 표시를 취소하고 새 목적지 확인을 기다린다.

## 로컬 실행

백엔드 저장소의 `.env`가 준비된 상태에서 다음을 실행한다.

```powershell
$env:ALLOW_MOCK_VEHICLE='true'
docker compose up -d --wait
.\gradlew.bat bootRun --console=plain
```

일반 Android 에뮬레이터용 앱은 앱 저장소에서 빌드한다.

```powershell
.\gradlew.bat assembleDebug -PbackendUrl=http://10.0.2.2:8080 --console=plain
```

위 실행은 합성 VIN을 사용하는 로컬 개발 전용이다. 실제 차량의 VIN 조회는 다음과 같이 빌드한다.

```powershell
.\gradlew.bat assembleDebug -PdestinationProvider=pleos -PvehicleProvider=pleos -PbackendUrl=https://your-server.example --console=plain
```

`vehicleProvider`를 생략하면 `destinationProvider`를 따른다. Pleos VIN은 `CAR_INFO` 권한과 호환 차량 서비스가 필요하다.
VIN을 읽지 못하면 Mock으로 전환하지 않는다. 실차 서버에서는 `ALLOW_MOCK_VEHICLE`를 기본 false로 유지한다.

실제 단말은 같은 LAN에서 접근 가능한 PC의 사설 IP를 사용한다. 방화벽은 자동 변경하지 않는다.
실제 배포용 빌드는 HTTPS 서버 주소를 사용한다.
`backendUrl`을 생략하면 서버 등록 버튼을 비활성화해 잘못된 서버로 전송하지 않는다.
백엔드 주소는 앱 빌드 시 결정되며 서버를 변경하면 다시 빌드한다.
서버 origin·VIN·출처별 토큰은 서로 분리해 암호화 저장하고 외부 주소로 리다이렉트하지 않는다.

## 테스트

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

기본 빌드는 실제 서버 없이 동작하는 UI·단위 테스트를 수행한다.
실제 백엔드와 API 36 에뮬레이터가 실행 중이면 다음 명령으로 등록 화면 및 암호화 저장을 검증한다.

```powershell
.\gradlew.bat connectedDebugAndroidTest -PbackendUrl=http://10.0.2.2:8080 -Pandroid.testInstrumentationRunnerArguments.tripE2E=true --console=plain
```

`TripBackendConnectionTest`와 등록 성공 UI 테스트는 `tripE2E=true` 및 서버 주소가 있을 때만 실행된다.
기본 CI에서는 외부 서버가 필요한 테스트를 건너뛰며, 서버 미설정 상태의 버튼 비활성화를 확인한다.
기존 LiveKit 실제 서버 테스트는 별도 LiveKit 토큰이 있어야 실행된다.

설치 세션 만료·VIN 충돌 시 사용자 ID를 바꾸어 자동 재등록하지 않는다. 갱신·소유권 확인·복구 흐름은 후속 인증 작업이다.
목적지 취소 전송과 운행 종료 API도 후속 작업이며, 현재 화면의 취소만으로 서버 운행을 종료하지 않는다.
