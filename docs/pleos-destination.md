# Pleos 목적지 조회

## SDK와 REST API의 역할

NaviHelper는 Pleos Navigation 서비스에 연결하는 Android SDK입니다.
목적지 조회는 SDK 메서드와 콜백으로 처리하고, 조회한 좌표는 별도의 우리 서버 REST API로 전송합니다.
현재 구현 범위는 SDK 어댑터이며 백엔드 목적지 등록 API 연동은 후속 작업입니다.

Pleos의 [Vehicle Data REST API 목록](https://document.pleos.ai/api-reference/vehicle-data-api/intro)에는
차량 위치·상태 조회 등이 안내되어 있지만 내비게이션의 최종 목적지 조회는 확인되지 않았습니다.
차량의 현재 위치를 RouteMate의 목적지 매칭 좌표로 대체하지 않습니다.
별도 REST API를 제공받는 경우에는 해당 명세의 목적지 필드와 권한부터 확인합니다.

에뮬레이터 설치는 실제 SDK 검증 경로 중 하나입니다.
Mock 앱과 백엔드 개발은 에뮬레이터 설치 없이 진행할 수 있습니다.

## 모드 선택

기본값은 `mock`입니다. 일반 Android에서 NaviHelper를 초기화하지 않고 샘플 목적지를 표시합니다.
실제 Pleos 연결을 검사할 때만 `-PdestinationProvider=pleos`를 지정합니다.
Pleos 모드는 공식 요구사항에 맞춰 최소 API 28, Mock 모드는 최소 API 26을 사용합니다.
전용 이미지 설치와 공간 요구사항은 [에뮬레이터 준비 문서](pleos-emulator-setup.md)를 참고합니다.
잘못된 속성 값은 빌드 설정 단계에서 거부합니다. Pleos 연결 실패 시 Mock으로 자동 대체하지 않습니다.

```powershell
# 일반 Android / Mock
.\gradlew.bat assembleDebug testDebugUnitTest -PdestinationProvider=mock --console=plain

# 실제 NaviHelper 3.0.5
.\gradlew.bat assembleDebug testDebugUnitTest -PdestinationProvider=pleos --console=plain
```

두 모드 모두 같은 applicationId와 출력 경로를 사용합니다.
`app/build/outputs/apk/debug/app-debug.apk`는 마지막으로 빌드한 모드의 APK입니다.
이번 검증에서 두 모드의 APK는 `app/build/outputs/apk/validation/`에
`routemate-mock-debug.apk`, `routemate-pleos-debug.apk`로 각각 보관했습니다.

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.routemate.app/.MainActivity
```

실행한 화면의 안내 문구가 Mock인지 Pleos인지 확인합니다.
Mock 화면의 서울역 샘플과 Pleos에서 실제로 수신한 목적지를 구분해야 합니다.

## SDK 확인 근거

설치된 NaviHelper 3.0.5 AAR와 navi-data 3.0.4 AAR의 `classes.jar`를 `javap`로 확인했습니다.

- `NaviHelper(Context)`
- `initialize(onConnected, onFailed)`
- `addListener`, `removeListener`, `release`
- `getRouteStateInfo`, `getDestinationInfo`
- `onRouteStateInfo`, `onDestinationInfo`, `onDestinationChanged`, `onRoutingCompleted`, `onRouteCancelled`, `onError`
- `DestinationInfo.name`, `address`, `latitude`, `longitude`

공식 문서:

- [초기화·해제](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release)
- [목적지 조회](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)
- [이벤트 리스너](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/event-listeners)
- [3.0.5 변경 사항](https://document.pleos.ai/en/docs/pleos-only/release-note/sdk-release-note)

## 상태 및 리소스 처리

- domain의 기존 `currentDestination()`은 유지합니다. 기본 `observeDestinations()`는 1회 조회를 Flow로 제공합니다.
- Pleos 구현은 구독마다 새 SDK 인스턴스를 생성합니다. SDK 접근과 상태 변경은 Main dispatcher에서 직렬화합니다.
- 초기 연결 성공 후 리스너를 등록하고 경로 상태를 조회합니다.
- 경로 탐색 중이면 완료/변경 이벤트를 기다립니다. 경로 안내·모의 주행 모드이면 목적지를 요청합니다.
- 탐색 중이 아니며 `NOT_WORKING` 또는 `SAFE_GUIDING` 상태이면 활성 목적지가 없는 것으로 처리해 null을 반환합니다.
- 명시적인 경로 취소도 null을 전달합니다. 무응답·연결 실패·잘못된 좌표는 빈 목적지로 취급하지 않고 오류로 전달합니다.
- 위도·경도는 최종 목적지의 `latitude`, `longitude`를 사용합니다. 현재 위치나 표시용 좌표로 대체하지 않습니다.
- 목적지 이름이 비어 있으면 주소, 주소도 비어 있으면 '목적지'라는 표시 이름을 사용합니다.
- 목적지 변경 이벤트는 재조회하며, 진행 중인 조회가 변경으로 무효화되면 결과를 버리고 새로 조회합니다.
- 초기 연결과 각 조회에는 각각 10초 제한을 둡니다. 정상 응답 후 다음 변경을 기다리는 동안에는 시간 초과를 적용하지 않습니다.
- 구독 취소·오류·ViewModel 정리 시 동일한 리스너를 제거하고 SDK를 해제합니다.
- `currentDestination()`은 첫 결과를 받으면 해당 구독을 종료해 리소스를 정리합니다.
- 재시도는 새 구독과 새 SDK 인스턴스를 만듭니다.

SDK는 요청 ID를 공개하지 않습니다. 요청을 직렬화하고 변경 중인 결과를 무효화하지만,
실제 이벤트 순서와 목적지 미설정 시 경로 상태는 Pleos 환경에서 확인해야 합니다.
초기 연결 이후의 별도 연결 끊김 콜백은 사용하지 않습니다. 재조회 무응답은 시간 초과로 안내합니다.

## 실제 Pleos 수동 검증

Pleos Connect Emulator/기기와 Navigation 서비스, SDK 접근 권한이 필요합니다.
Manifest에는 `pleos.car.permission.NAVI_ROUTE`를 선언합니다.

1. Pleos 모드 APK를 설치하고 실행합니다. Pleos 안내 문구를 확인합니다.
2. 내비게이션에 목적지가 없는 상태에서 '설정된 목적지가 없습니다'가 표시되는지 확인합니다.
3. Navigation에서 첫 목적지를 설정하고 앱에 실제 이름·위도·경도가 표시되는지 확인합니다.
4. 다른 목적지로 변경하고 앱을 다시 실행하거나 버튼을 누르지 않아도 표시가 바뀌는지 확인합니다.
5. 경로를 취소하고 이전 목적지가 사라지는지 확인합니다.
6. 연결/권한 실패 및 응답 지연 시 오류 안내와 다시 가져오기 동작을 확인합니다.
7. 회전·앱 종료·재실행 후 리스너 중복 등록이나 이전 구독의 결과가 남지 않는지 확인합니다.

일반 Android Mock UI 테스트:

```powershell
.\gradlew.bat connectedDebugAndroidTest -PdestinationProvider=mock --console=plain
```

기존 UI smoke test는 Mock 전용입니다. Pleos 빌드에서는 해당 테스트를 건너뛰며,
이는 실제 목적지 조회 성공을 의미하지 않습니다. 실제 SDK 런타임 검증은 위 절차로 수행합니다.
자동 테스트와 실제 실행 결과는 검증 보고에 따로 기록합니다.
