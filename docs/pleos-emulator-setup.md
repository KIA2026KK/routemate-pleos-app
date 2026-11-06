# Pleos 에뮬레이터 설치 및 검증 준비

## 설치 전 공간 확보

2026-11-05 공식 Windows/Linux 저장소의 Pleos Connect v2.1.0 이미지 정보를 확인했습니다.
대용량 ZIP 전체를 받지 않고 HTTP Range로 ZIP 중앙 디렉터리만 조회해 해제 용량을 계산했습니다.

| 항목 | 확인값 |
| --- | --- |
| 패키지 | `system-images;android-34;pleos_car_emulator;x86_64` |
| 압축파일 | 4,087,751,964 bytes, 약 4.1GB |
| 압축 해제 파일 합계 | 19,643,095,958 bytes, 약 19.6GB |
| 다운로드·압축 해제·실행 여유 합계 | 약 32.3GB |

공간 계획에는 압축파일, 압축 해제 파일, AVD 및 작업 여유 8GiB를 합산했습니다.
약 33GB 이상, 가능하면 40GB 이상의 여유 공간을 확보한 뒤 설치합니다.
이는 이 프로젝트의 준비 기준이며 제조사가 명시한 최소 디스크 사양을 뜻하지 않습니다.
저장소 이미지가 갱신되면 용량을 다시 확인해야 합니다.

## Android Studio 설치 절차

1. **Tools → SDK Manager → SDK Update Sites**에서 다음 저장소를 추가합니다.
   - 이름: `Pleos Connect System Image`
   - URL: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
2. **SDK Platforms → Show Package Details**에서 API 34의 Pleos Connect 이미지를 선택해 설치합니다.
3. Android Studio를 재시작하고 **Device Manager → Create device → Automotive**에서 Pleos Connect 장치를 생성합니다.
4. API 34 / Pleos Connect v2.1.0 이미지를 선택하고 RAM 4GB, Cold boot로 설정합니다.
5. 에뮬레이터 부팅 후 ADB 연결 및 Navigation 앱 실행 상태를 확인합니다.

[공식 설치 가이드](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)

## 앱 호환성과 실행

Pleos의 공식 최소 API 요구사항에 따라 실제 Pleos 빌드는 `minSdk 28`을 사용합니다.
일반 Android용 Mock 빌드는 기존 `minSdk 26`을 유지합니다.
`compileSdk`와 `targetSdk`는 36으로 유지하며 실행할 에뮬레이터는 API 34입니다.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug -PdestinationProvider=pleos --console=plain
adb devices
adb -s <SERIAL> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <SERIAL> shell am start -n com.routemate.app/.MainActivity
```

`<SERIAL>`은 Pleos 장치의 실제 ADB 식별자로 바꿉니다.
일반 Android AVD의 실행 성공이나 시간 초과 안내를 실제 Pleos 목적지 수신 성공으로 기록하지 않습니다.

Virtual Console이 필요한 경우:

```powershell
adb -s <SERIAL> forward tcp:8088 tcp:8088
```

브라우저에서 `http://127.0.0.1:8088/`로 접속합니다.
Virtual Console 연결과 실제 Navigation 목적지 조회 검증은 구분합니다.

## 실제 검증 순서

1. 목적지 없음 상태에서 빈 목적지 안내 확인.
2. Navigation에서 목적지 설정 후 앱의 이름·위도·경도 확인.
3. 목적지 변경 후 앱 재실행이나 조회 버튼 조작 없이 표시 갱신 확인.
4. 경로 취소 후 이전 목적지 제거 확인.
5. 앱 종료·재실행, 연결 실패·재시도 및 리스너 중복 여부 확인.

각 단계에서 장치 버전, 설정한 목적지, 기대 결과와 실제 결과를 기록합니다.
SDK 콜백 어댑터의 세부 동작과 오류 처리는 [목적지 조회 문서](pleos-destination.md)를 참고합니다.

## 준비 상태

실제 Pleos 이미지 설치와 목적지 수신 검증은 디스크 공간 확보 후 진행합니다.
일반 Android AVD 및 기존 SDK 환경을 지우는 방식의 공간 확보는 수행하지 않았습니다.
