# TC-AUDIT-01 / TC-SCREEN-01 / TC-UI-REVIEW — 2026-11-16

## 기준
- PRD: Google Docs `1IF7ngaZrorGaQsy_VsjLfB4rtXU8ZKc1W2WkAO9hvmY` 및 `docs/00-A-product-requirements.md`.
- 기준 `main`: `fee725e5a8743aff75c24c408c390a26a1d96280`.
- 이 문서는 **자동 검사·코드 구현과 사람/실기기 검증을 분리**하며 완료를 추정하지 않는다.
- 변경 브랜치의 프로덕션 코드 변경: 좌표 유효성 검증 및 회귀 테스트에 한정.
- [skip-play] — Play 앱 번들 업로드, 스토어 자산 변경 및 트랙 승격 없음.

## PRD 대비 범위·추가 작업

| 단계 | 요구 기능 | 현재 저장소 근거 | 판정 | 다음 작업 |
|---|---|---|---|---|
| P0 | 여행·일정·준비물·경비·로컬 백업 | Room / Compose 화면과 2-C, QA-A06 | 구현 있음, 최종 실기기 검증 제외 | 최신 main의 회귀 QA |
| P1 | 지도/장소·경로 비교 | 3-A, ROUTE-P1 #49 | 이동 구간/모드 UI 있음, 실제 거리/ETA 없음 | ROUTE-02 API 공급자·비용 승인 필요 |
| P1 | 예약·첨부·참가자 정산 | 3-B, RELEASE-A07 #45 | 구현/자동 QA 있음, 외부 URI 수명 미검증 | API29/35/36 자동 QA, 실기기 별도 |
| P2 | 선택 로그인·초대·권한·실시간 동기화 | PRD FR-COL/FR-SYNC, roadmap #48 | 프로덕션 구현 근거 미확인·로드맵 미완료 | COLLAB-00~03; 서버 권한/비용 승인 이후 |
| P3 | 오늘 일정·수동 체크인·사진 기록·리포트 | FR-LIVE/MEM/REP, roadmap #48 | 프로덕션 구현 근거 미확인 | LIVE-01 → MEM-01 → REPORT-01 |
| P4 | 영수증 OCR·AI 일정 승인·여행 일기 | FR-OCR/AI, roadmap #48 | 프로덕션 구현 근거 미확인 | AI-00 개인정보/비용 검토 → OCR-01 → AI-01/02 |
| 최종 | 접근성·폴더블·Play 테스터 설치 | RELEASE-A07 #46 | 수동 검증 BLOCKED | 앱 설치 후 별도 검수 |

`미확인`은 존재하지 않는다고 증명한 것이 아니다. 해당 FR에 대응하는 구현·QA 증빙을 남기기 전에는 완료로 표시하지 않는다.

## TC-SCREEN-01 화면 수집

현재 `.github/workflows/ui-r09-verified-screens.yml`는 `main`에서 수동 `workflow_dispatch`를 수행할 때 Android API35의 phone/tablet × 2-C/3-A/3-B 6개 매트릭스로 실제 화면을 수집한다. 모두 통과한 뒤에만 동일 SHA의 캡처로 스토어 검토용 8장을 생성한다.

1. `main` SHA 고정 후 `TripCanvas UI-R09 verified real screenshots`를 수동 실행한다.
2. `r09-2c-phone`, `r09-2c-tablet`, `r09-3a-phone`, `r09-3a-tablet`, `r09-3b-phone`, `r09-3b-tablet` 6개 아티팩트를 수집한다.
3. 각 PNG가 실제 Android UI인지, 날짜/경로 비교/지도/정산/예약의 최신 변경 내용과 일치하는지 검증한다.
4. 원본 PNG를 보존하고 스토어 검토용 8장과 구분한다. 캡처 생성만으로 시각적 QA 합격을 주장하지 않는다.
5. 캡처를 실제 실행하지 않은 상태에서는 **최신 캡처 미확인**으로 표시한다.

## TC-UI-REVIEW 체크 항목

- 홈: 예정/진행/완료 필터, 긴 여행명, 작은 화면의 카드 밀도
- 일정: 날짜 전환, 시간 미정, 겹침 경고, 드래그 순서와 저장 상태
- 지도: 실제 지도 타일, 잘린 핀/마커, 검색 결과·출처, 경로 비교의 거짓 숫자 방지
- 준비물: 완료율 0/전체 및 항목이 없는 상태, 체크 해제·복원
- 경비: 긴 통화 값, 환산 필요, 초과 예산, 0원 방지
- 정산: 참가자별 구분, 반올림 잔액, 정산 변경 후 확인 무효화
- 예약: 확인번호 마스킹, 큰 글꼴, 문서 접근 권한 만료 상태
- 공통: light/dark, 글꼴 160%/200%, 휴대폰/태블릿, 키보드 가림, TalkBack 초점

## TC-FEATURE-GAP 순서와 릴리스 게이트

1. 외부 계약 없는 기능부터 P3의 로컬 체크인 → 기록 → 요약 리포트를 별도 PR로 구현한다. Room migration/backup 호환성과 데이터 삭제 정책을 함께 검증한다.
2. P2 공동 편집과 P4 AI 및 ROUTE-02 유료/외부 서비스는 공급자·키·과금·권한·개인정보 승인을 받은 뒤에만 운영 API를 연결한다. 허위 데모 API/가짜 ETA 금지.
3. 각 기능 PR에서 Android build, JVM, Room, API29/35/36, 화면 2-C/3-A/3-B, OPS-07/08을 가능한 범위에서 통과시킨다. CI 미완료를 성공으로 기록하지 않는다.
4. 모든 기능/자동 QA 완료 후 릴리스 후보 SHA를 고정한다. `TC-RELEASE-FINAL`은 최종 승인과 별도 수동 GitHub Actions 배포 단계다. 물리 실기기 항목은 자동 PASS로 전환하지 않는다.

## 이번 PR의 실제 코드 변경
- `TripRouteComparison.kt`: 위도 -90..90, 경도 -180..180, 유한수 조건을 만족하지 못하는 경로 구간을 `COORDINATES_MISSING`으로 표시.
- `TripRouteComparisonTest.kt`: 범위 외, NaN, infinity, 유효 경계 테스트 추가.
- GPS 좌표 및 일정 데이터 수정 없음. 기존 route provider 미연동 상태 유지.
