# 초기 Android 검증 — 2026-11-02

- Windows / Java 21 / Android SDK 36에서 `testDebugUnitTest lintDebug assembleDebug` 성공.
- 좌표 경계 및 잘못된 목적지 입력을 검증하는 단위 테스트 2개 통과.
- Lint 오류 없음. 호환성 검증을 위해 고정한 라이브러리의 최신 버전 알림은 남아 있음.
- APK: app/build/outputs/apk/debug/app-debug.apk.
- UI 테스트는 샘플 목적지 버튼을 누르고 서울역 샘플 표시를 확인함.
- 로컬 API 36 에뮬레이터는 디스크 공간 부족으로 실행 실패. 로컬 기기 테스트는 미실행.
- GitHub Actions에 별도 API 36 에뮬레이터 UI 테스트 추가. 실행 결과는 PR checks에서 확인.
- 실제 Pleos 목적지 및 LiveKit 통화는 구현/검증하지 않았음.
