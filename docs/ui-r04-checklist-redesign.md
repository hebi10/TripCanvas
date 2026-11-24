# UI-R04 — 준비물 체크리스트 리디자인

기준: UI-R00 8종 재구성 시안 및 UI-R00~R09 개선 계획(2026-11-11), TripCanvas S12 체크리스트.
선행 UI-R01 PR #26 main 병합, UI-R02 PR #27, UI-R03 PR #28(검증 완료 전 Draft) 순서.

## 코드 반영

- 상단: 원래 '준비 완료 n / 전체 n' 표기 유지. 빈 목록을 제외하고 미완료 건수 및 완료율을 함께 안내.
- 필터: 기존 '전체/미완료' 유지. 완료 상태에 따른 표시/숨김만 적용하며 저장·삭제하지 않음.
- 새 준비물 추가: 목록을 오래 스크롤하지 않고 접근하도록 필터 아래 상단에 배치. 기존의 하단 중복 버튼 제거.
- 행 레이아웃: 사방 테두리 패널 하나에 체크박스(48dp)·최대 두 줄 항목명·더보기(48dp)를 한 줄로 구성. 개별 카드 사이 간격 8dp, 클릭 영역 48dp, 흑백/중성 컬러 유지.
- 더보기 메뉴: 기존 수정·위로·아래로·삭제 네 가지 동작을 통합. 메뉴에는 기존 기능만 포함. 첫 행/마지막 행에서 불필요한 이동 비활성화; '미완료' 필터에서는 위/아래 이동을 비활성화해 숨김 항목의 순서가 임의로 섞이지 않도록 함.
- 체크·삭제·복구: Repository의 `setChecked`, `delete`, `undoDelete`를 그대로 호출. 기존 삭제 확인과 '방금 삭제한 준비물 복구' 버튼 유지. 저장 중 중복 조작 방지 유지.

## 제외·보존

- Room DB, DAO/Repository 저장/정렬 규칙, 백업/복원, 여행별 항목 분리, 다른 화면 구조, Play 트랙·이미지·서명 설정: 변경 없음.
- 48dp 접근성과 기존 상태/오류 안내를 유지. 새 의존성, 장식형 그림자, 일방향 테두리, 그라데이션, 임의 애니메이션 추가 없음.

## 검증

- JVM `ChecklistRepositoryTest`: 항목 생성/수정/체크/재정렬/복구 기존 테스트에 경계 순서 이동·삭제/복원 후 순서·체크 상태 보존 테스트 추가.
- API 35 2-C UIAutomator: 전체/미완료 필터에서 재정렬 비활성, 준비물 추가/수정 메뉴, 위/아래 정렬의 Room 순서 확인, 체크 토글 즉시 저장, 삭제 확인/실제 삭제/복구, 휴대전화·태블릿 라이트/다크/160% 실제 PNG.
- Android CI, Room API35, 3-A 지도, 3-B 경비·정산·예약, API29/36 호환성, 개인정보 정책 전체 회귀 필수.
- 실제 PNG 육안 검수에서 긴 이름·160% 폰트 겹침/잘림, 목록 스크롤, 체크박스/메뉴 48dp 및 대비 확인.
- 실기기·TalkBack·폴더블 검증은 실행 사실 없으면 미완료로 분리 기록.

## 안전한 통합 순서

- UI-R04 PR은 UI-R03 브랜치 기반 stacked Draft PR. UI-R02 PR #27 및 UI-R03 PR #28이 각각 동일 HEAD의 QA·이미지 검수로 main 병합되기 전에는 **UI-R04를 main에 병합하지 않음**.
- 선행 PR 병합 시 최신 main 및 충돌 확인, PR base 재지정, UI-R04의 정확한 HEAD 검증을 다시 진행. 자동 Play internal 배포/심사 상태는 별도 조회하고 closed/production 승격은 범위 외.
- PR 검증 단계에서는 Google Play 스토어 이미지 및 AAB 업로드 수정 없음.

참고:
- 기존 기획: https://docs.google.com/document/d/1sOjzZKCaVuHxFeCRVQnKlFp7jZd9dc-i0sYFev9uRKk/edit
- UI-R00 비교: https://drive.google.com/drive/folders/1JLtNkO438c_APpeXcia46-g6lIzz5NJI
- UI-R02: https://github.com/hebi10/TripCanvas/pull/27
- UI-R03: https://github.com/hebi10/TripCanvas/pull/28
