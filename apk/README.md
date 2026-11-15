# 제출 APK

최종 파일명: `노래말고 사람들과 함께_RouteMate_release.apk`

현재 release 키가 없어 **최종 서명 APK 생성 대기** 상태입니다. debug APK를 이름만 바꾸어 제출하지 않습니다.
서명 전 release 빌드는 `app/build/submission/unsigned/`에 보관하며 설치·제출용이 아닙니다.

키를 로컬에서 준비하고 [제출 서명 절차](../docs/RELEASE_SIGNING.md)를 실행하면 이 폴더에 최종 APK와 SHA-256 검증 정보가 생성됩니다.
`apk/*.apk`는 Git 추적 허용 대상이며 keystore와 비밀번호는 제외 대상입니다.

APK가 100 MiB를 초과하면 Git에 추가하지 않고 GitHub Releases에 등록합니다.
최종 Release는 아직 게시하지 않았습니다. [GitHub 파일 제한](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)
