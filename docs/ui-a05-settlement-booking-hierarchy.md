# UI-A05 — 동행자 정산·예약 정보 위계 개선

- 기준: 2026-11-12, TripCanvas `main=9c35be4b06bdac1e1d2c9a1c83d9f84cfbb509e0`.
- 선행 UI-A04: [PR #42](https://github.com/hebi10/TripCanvas/pull/42) — 이 문서 작성 시 Draft·미병합. UI-A05는 정산·예약 변경만 독립적으로 반영한 브랜치이며, UI-A04 검증/병합 후 main 최신 상태를 확인하고 병합한다.
- 설계 기준: [Google Drive 동행자 정산 기획 비교](https://drive.google.com/file/d/129cklQiXtXrlvmxU71d1nAEOD7UjkAfn/view), [예약·문서 자료 기획 비교](https://drive.google.com/file/d/1pJLzknpFH-9pSDv0aDOmpJLfksPVasbh/view), UI-R05 정산 QA·UI-R07 예약 QA 문서.

## 동행자 정산

- 기존 계산 결과 `calculateSettlement`의 `transfers`를 기반으로 송금 **보내는 사람 → 받는 사람**을 큰 제목으로 표시, 실제 `displayMinor` 금액은 더 큰 `headlineMedium`으로 분리.
- 송금 요약/금액을 정산 완료 확인 상태보다 먼저 배치하여 누구에게 얼마를 정산할지 먼저 읽음.
- `실제 송금은 진행되지 않습니다.`를 각 제안에 명시. 이 화면이 실제 송금을 대신하지 않음을 유지.
- 정산 보류(미환산·분담 미완료), 확인·철회, 참가자별 지불/부담/받을·낼 금액, 지출별 환율·분담 설정과 계산·fingerprint 로직 변경 없음.

## 예약·첨부 자료

- 예약 유형을 중성 사각형 `TripStatusTag`로 표시하고 예약명을 한 단계 큰 제목으로 유지. 기존 현지 날짜·시간 `bookingDateLabel`, 첨부 n개 및 확인번호 등록 상태 노출 순서를 명확화.
- 목록에서 확인번호 원문·첨부 파일 URI/파일명은 계속 비공개.
- `예약 상세·편집`은 카드 내 전체 폭의 명시적 진입 버튼. `예약 삭제`를 목록 카드의 고정 두 번째 버튼에서 제거하고 48dp 더보기(`예약 관리: 제목`) 메뉴로 이동.
- 더보기 삭제는 기존 `TripConfirmDialog`로만 진입하며 사용자 취소 시 예약·첨부 연결 보존. 삭제 시에도 외부 원본 PDF·이미지 파일은 지우지 않음.
- 목록 아래에 `예약 추가`를 배치하여 카드 탐색 흐름이 끊기지 않도록 함. 비어 있는 화면에서는 기존 빈 상태 안내와 예약 추가를 유지.
- 예약 편집 화면의 입력, 확인번호 PasswordVisualTransformation/보기·숨기기, 저장·파일 연결, Android SAF, JSON 백업은 변경하지 않음.

## Android QA 및 안전 기준

- UIAutomator 3-B phone/tablet: 정산 제안 1건, `현지 → 민재` 및 `KRW 6,600`이 분리되어 표시되는지 확인. 정산 확인·철회·환율 분담 진입 유지.
- 예약 목록에서 삭제 버튼이 상시 노출되지 않는지 확인. `예약 관리: 숙소 예약` → `예약 삭제` → `예약 자료 삭제` 경고 → 취소 후 목록 및 편집 진입 확인. 기존 확인번호 기본 비공개, 보기·숨기기, 첨부 1개, 권한 문구와 문서 선택기 동작 유지.
- 실제 Android 원본 phone/tablet 라이트·다크·글꼴 160% 캡처에서 송금 방향·금액 위계, 예약 목록 상단 유형·날짜·첨부 상태, 더보기 메뉴 잘림/가로 오버플로/터치 가능 영역 확인.
- 정확한 최종 PR HEAD에서 **Android 빌드/JVM/AndroidTest 컴파일·Room API35, 2-C phone/tablet, 3-A MapLibre phone/tablet, 3-B phone/tablet, API29/36** CI 전체 성공 후에만 안전 병합.
- 물리 실기기·폴더블·TalkBack 수동 검사와 Play 심사 승인/테스터 설치는 자동 QA와 별도.
- Room·DAO·Repository·정산 공식·환율·지도·예약 저장·Play 트랙·서명·스토어 이미지·신규 의존성 변경 없음.

## 배포 및 다음 단계

- UI-A05는 PR 생성 시 **Draft·미병합** 상태 유지. 선행 [UI-A04 PR #42](https://github.com/hebi10/TripCanvas/pull/42)가 최신 main에 반영되었는지 병합 직전에 검증. 병합 시 GitHub Actions에 따라 내부 테스트 AAB가 자동 게시될 수 있음.
- 사용자 요청: Actions 작업 시작 후 반복 조회하지 않고 PR Checks 링크 제공. 사용자가 성공을 확인해 다시 대화하면 최신 HEAD 전체 QA 검토→순차 병합→내부 테스트 API 결과 확인.
- 다음: `QA-A06 — 전체 화면·실기기·접근성 및 데이터 복원 통합 QA`.
