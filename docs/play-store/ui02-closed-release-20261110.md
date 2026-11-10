# TripCanvas UI-02 통합 릴리스 · 비공개 alpha 승격

## 범위

- UX 기반: UI-01 PR #17, UI-02-A PR #18, UI-02-B PR #19, UI-02-C PR #20.
- 호환성: QA PR #16의 API 29·36 Room 계측 + 첫 실행 화면 160% 글꼴 보정.
- `main` **한 번의 릴리스 병합**으로 중간 버전별 내부 테스트 자동 배포를 방지합니다. 원본 Draft PR은 검토/추적 목적으로 유지하며 릴리스 통합 PR에서 각 변경을 포함합니다.
- 앱 소스 외 배포 범위는 오직 내부 테스트(`internal`)에서 이미 완료된 정확한 버전의 비공개 테스트(`alpha`) 승격입니다. 프로덕션 배포/다른 앱/다른 트랙은 변경하지 않습니다.

## 릴리스 검증 게이트

1. 통합 PR의 Android 빌드/JVM·Room 계측과 2-C/3-A/3-B 실제 휴대폰·태블릿 QA 및 API 29/36 호환성 테스트 전체 성공.
2. 정확한 `main` SHA에서 Android CI `android-build`·`room-migration-instrumentation` 모두 성공.
3. GitHub `Play internal release`에서 WIF/서비스 계정 및 업로드 키 검증, 현재 Play 전체에서 중복되지 않은 `versionCode` 할당, AAB/APK 패키지·서명·네이티브 검사.
4. 실제 동일 AAB 해시와 내부 `completed` 트랙 상태를 Play API 새 편집으로 읽어 확인한 **후에만** `alpha` 승격.
5. 오직 이 릴리스의 신뢰된 `main` 병합 커밋 제목이 ` [tripcanvas-closed-alpha-once]`로 끝나고 이벤트가 성공한 `main` CI에서 시작된 `workflow_run`인 경우에만 기존 Play 발행기 `promote --version-code ... --track alpha`를 실행합니다. 그 외 커밋·수동 `deploy`는 기존과 같이 내부 테스트만 배포합니다.
6. Play API 커밋 및 `alpha` 새 편집 읽기 성공이 실제 테스터 설치 가능/Play Console 심사 완료를 의미하지는 않습니다. Console 게시 상태와 테스터 등록을 별도 확인합니다.

## 중단 및 보존

- `main` 커밋이 달라지거나 CI·서명·API 인증·기존 바이너리 검사·버전 코드·내부 완료 상태·`alpha` 검증이 실패하면 중단합니다.
- 사용자의 비밀키, 서비스 계정 JSON, API 토큰을 소스·문서·로그에 쓰지 않습니다.
- Release automation이 `alpha` 승격에 실패한 경우 자동 재업로드/재승격 반복하지 않고 마지막 성공 상태 및 실패 단계부터 분리 보고합니다.
- GitHub PR 성공/Play API 릴리스 성공/Console 심사/실제 설치는 서로 다른 상태로 기록합니다.
