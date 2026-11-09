# 차량용 UI 프로토타입 실행

일반 Android 기기·에뮬레이터에서 서버 없이 화면과 조작 흐름을 체험하는 별도 디버그 앱이다.
실제 통화 연결 검증은 [MVP 연결 테스트](mvp-testing.md)를 사용한다.

## 빌드와 실행

Java 21, Android SDK 36과 이 저장소의 Gradle Wrapper가 필요하다. PowerShell에서 실행한다.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\tools\prototype\Build-Prototype.ps1 -Install
```

APK는 `app/build/prototype/RouteMate-prototype.apk`에 생성된다. `-Install`은 연결된 기기 한 대에 설치·실행한다.
USB 디버깅을 허용한 기기 또는 일반 Android 에뮬레이터 한 대를 준비한다. SDK가 없는 PC에서는 빌드된 APK를
Android 기기에 복사해 설치할 수 있다. 패키지는 `com.routemate.app.prototype`이며 기존 앱 데이터와 분리한다.

UI 자동 검증과 화면 내보내기:

```powershell
.\tools\prototype\Build-Prototype.ps1 -Verify -Screenshots
```

`ui-verification.txt`, `dashboard-results.png`, `dashboard-call.png`, `dashboard-night.png`를 APK와 같은 폴더에 저장한다.
테스트 APK는 검증 후 삭제하고 체험 앱은 남긴다. 캡처 화면은 실행 기기의 해상도·배율을 따른다.

Gradle만 사용할 때는 `assembleDebug -PprototypeDemo=true`로 빌드한다. 이 모드는 Mock 목적지·Mock VIN과
빈 backendUrl 조합만 허용하며 릴리스의 `PROTOTYPE_DEMO`는 항상 false다.

## 체험 순서

1. 샘플 목적지 확인 → 목적지 등록.
2. 5km 또는 10km 방 검색 → 예시 방 참여.
3. 통화 화면 체험 → 마이크 상태 조작 → 통화방 퇴장.
4. 새 방 만들기는 ‘정차 중 설정’에서 수동 정차 확인 후 이름·정원·공개 여부 입력.
5. 운행 종료와 확인, 주간·야간 전환, 계정 관리 화면 확인.

체험 데이터는 메모리에만 저장하며 프로세스 재시작 시 초기화한다. 예시 참가자·음성 상태는 시뮬레이션이다.
마이크 권한 요청, VIN 조회, HTTP 요청, 실제 음성 송수신은 이 모드에서 수행하지 않는다.
화면 상단과 통화 카드에 체험 모드임을 표시한다. 계정 복구·세션 갱신 기능은 실제 서버 모드에서 검증한다.

## UI·UX 기준과 적용 범위

- Hyundai Blue `#002C5F`, Active Blue `#00AAD2`를 참고한 주간 팔레트와 눈부심을 줄인 야간 팔레트.
  색상 근거는 [현대자동차 CI](https://www.hyundai.com/kr/ko/info/ci/ci-color)이며 공식 제휴 앱을 의미하지 않는다.
- 목적지와 통화방을 넓은 화면에서 두 열로 배치하고, 좁은 화면에서는 세로로 스크롤한다.
  목적지 등록, 검색, 음소거, 퇴장의 주요 행동을 큰 버튼으로 제공한다.
- 주요 조작의 최소 터치 크기는 76×76dp다. [Android Auto 크기 가이드](https://developers.google.com/cars/design/android-auto/design-system/sizing)를
  설계 참고로 사용했으며 Pleos 주행 중 사용 승인이나 인증을 받은 것은 아니다.
- 상세 입력·계정·개발용 PoC는 정차 확인 뒤 표시한다. 현재는 사용자의 수동 확인이며 차량 속도·기어 감지가 아니다.
  화면을 떠나면 정차 확인 상태를 초기화한다. 실제 배포에는 Pleos 주행 제한과 연결한 검증이 필요하다.
- 지도처럼 보이는 배경은 최종 목적지 기준 매칭을 설명하는 연결도다. 실제 지도·현재 위치·길안내가 아님을 표시한다.
- 정확한 좌표·전체 VIN·토큰을 기본 화면에 표시하지 않는다. 방 거리는 서버의 1km 단위 근삿값으로 안내한다.
- 운행 종료는 확인하고, 통화방 퇴장은 즉시 가능하다. 오류는 행동 가능한 안내와 재시도로 제공한다.

현재 통화는 화면이 보이는 동안 동작한다. 다른 화면으로 이동하면 오디오 해제와 서버 퇴장을 시도한다.
실차에서 글자 가독성·햇빛·진동·오디오 라우팅·접근성·주행 제한을 검증하는 단계는 남아 있다.
