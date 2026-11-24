# RELEASE-FREEZE — 개발·QA 전체 완료 후 Google Play 일괄 배포

기준일: 2026-11-12 · 패키지 `com.hebi.tripcanvas` · 시작 main `a86e5d27b167a4a0e04889d5eebb9049889f7e76`.
사용자 확정 운영 지시: **기능/화면/QA 작업 PR마다 Google Play 내부·비공개 테스트를 업로드하지 않고, 기획한 작업을 완료·검증한 뒤 최종 승인을 받아 한 번에 배포**한다.

## 즉시 적용할 자동 배포 동결

- `.github/workflows/play-internal.yml`: `workflow_run` 자동 배포 트리거 **제거**. `workflow_dispatch` 수동 실행만 유지. `mode=check`(읽기)·`build`(AAB 빌드만) 가능, `mode=deploy` 또는 `initialize`는 `final_release_confirmed=true` 승인 필요(기본 false).
- `scripts/play/source.py`: 오래된 자동 `workflow_run` 이벤트가 혹시 들어와도 Play 업로드 전 차단. 읽기/빌드 외 최종 배포는 수동 승인 flag 검증. 이전처럼 동일 main SHA·Android CI 2개 필수 job 성공 가드 유지.
- `.github/workflows/play-listing-images.yml`: main push에서 스토어 이미지 자동 업로드하는 트리거 제거. `mode=upload`는 `workflow_dispatch` 및 `final_release_confirmed=true`일 때만 허용. 역사적 R09 1회 자동 릴리스 작업은 비활성.
- `.github/workflows/play-promote.yml`: 기존의 별도 수동 비공개 alpha 승격도 `mode=promote`와 `final_release_confirmed=true`를 둘 다 요구.
- `scripts/play/tests/test_release_freeze.py`: 해당 조건이 향후 실수로 되돌아가지 않도록 오프라인 회귀 검증. 기존 소스/초기 업로드 단위 테스트는 새 정책에 맞춰 조정.
- **이 PR이 main 병합되기 전까지는 자동 배포 동결이 실제 적용되지 않는다.** 먼저 이 PR QA 성공 및 안전 병합 → 이후 기능/QA PR 순차 병합.

## 프로젝트 작업·검증 게이트

| 단계 | 현재 상태 | 다음 처리 |
|---|---|---|
| UI-A01~A05 | 코드 main 병합 완료, 마지막 Google Play internal 0.1.0(15) 등록 완료 | 유지; 이후 UI 수정은 별도 PR |
| RELEASE-FREEZE | 본 PR Draft, 배포 동결 적용 전 | CI 통과·메인 안전 병합 우선 |
| QA-A06 PR #44 | Android 실제 content URI·손상 파일·Room 복원 CI 전부 success (HEAD 36ecc588) | 최신 main 정합화·최종 캡처/테스트 확인·병합; **Play 업로드 금지** |
| RELEASE-A07 PR #45 | 오프라인 가드 검사·Android CI success (HEAD af254b85) | A06 통합·최종 CI 재검증·수동 실기기 승인표 관리; **Play 업로드 금지** |
| 실기기 체크리스트 #46 | Test 계정 설치·폴더블·TalkBack·외부 SAF 장기 권한 실제 증빙 대기 | 각 항목 미검증은 BLOCKED로 기록 |
| 이후 기획 기능 백로그 | 기존 요구사항 중 P2 공동 편집·클라우드, P3 여행 기록·체크인, P4 AI·동선 최적화는 현재 별도 미구현 | 기존 범위·의존성에 따라 단계별 PR/QA. 기획에서 제외로 변경하지 않는 한 '전체 기능 완료'로 표시하지 않음 |
| 최종 통합 출시 | 대기 | 전체 기능·디자인·개인정보·Google Play 정책 검증 완료 시 사용자 명시적 최종 승인 후 1회 배포 |

## 최종 일괄 배포 절차

1. 현재 `main`의 필수 GitHub Actions Android/JVM/Room, 2-C phone/tablet, 3-A MapLibre phone/tablet, 3-B finance/booking phone/tablet, API29·36, A06 실제 content URI 및 A07 오프라인 승인 가드가 모두 동일 HEAD에서 성공해야 한다.
2. 승인된 테스트 계정으로 Play Console/기기의 실제 설치·업데이트, 폴더블·TalkBack·외부 PDF/PNG 장기 권한·복원 테스트를 별도로 수행. 공적 심사/테스터 설치는 API 등록 성공만으로 인증하지 않음.
3. **최종 사용자 승인 후에만** `Play internal release` → Run workflow (`main`, `mode=deploy`, `final_release_confirmed=true`) 수동으로 실행한다. 새 versionCode는 Play 전체 인벤토리보다 크게 자동 할당하고 기존 릴리스 서명/패키지/AAB SHA를 검증한다.
4. Play API에서 `internal completed` 및 동일 버전/해시 fresh readback을 확인한 후 테스터 실제 설치를 검증한다. 테스트 이미지의 API 업로드 및 비공개 alpha 승격이 필요하면 **각각 별도 수동 승인/검사**로 실행한다.
5. Google Play 프로덕션 승격은 이 정책에서 자동 실행하지 않는다. 출시 준비·심사·12명/14일 등 현행 Play 계정 요건은 실제 콘솔 결과에 따라 별도 검증해야 한다.

## 보호 조항

- 자동/예약/반복된 점검은 **코드 리뷰·CI·스크린샷 생성·결과 기록까지만** 허용한다.
- `main` 병합, GitHub Actions 성공, 사용자의 “다음 작업 진행” 발언을 Play 업로드 또는 비공개 승격 승인으로 해석하지 않는다.
- 최종 배포 전 기존 Play internal v15·Alpha 프로덕션 트랙/스토어 에셋은 그대로 유지한다.
- 새 GitHub Actions는 사용자가 요청한 대로 반복 조회하지 않고 최초 실행 링크만 전달. CI 완료를 사용자가 확인하여 재요청한 시점에만 후속 검증한다.
