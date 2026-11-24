# UI-R02 — 홈·여행 작업 공간 정보 구조 개선

기준: UI-R00 8종 목표 시안 + Google Drive UI-R00~R09 기획서, 2026-11-11.
Base main: `f684f38eb83b3c2dc9c1c8574506d1983899debd`.
선행 UI-R01: PR #26 병합 및 API 35·API 29/36 QA 기록 확인.

## 적용 내용

- 홈 여행 카드에 여행명 → 여행 기간 → 국가·도시 → 상태·D-day 순으로 내용 위계를 정리. 카드 전체 열기, 더보기 편집·삭제 확인, 필터·새 여행 생성은 그대로 유지.
- 작업 공간의 목적/메모가 있는 경우 `여행 설명 보기/접기`로 선택적으로 노출. 여행명·목적지·상태·기간·시간대·통화는 기존대로 표시, 목적·메모 내용은 삭제하지 않음. 최소 터치 48dp.
- 모바일 Compact 작업 공간 '여행 한눈에'를 라벨/요약 값을 같은 행에 배치하고 내부 여백을 16dp→12dp로 압축. 예산 비교불가·미환산 외화·시간미정 일정·준비물 집계 안내는 그대로 유지.
- Medium·Expanded 화면의 기존 3열 요약 및 2열 카드 레이아웃 변경 없음.
- 카드 진입 '일정·지도·경비·준비물'은 상단 주요 작업으로 유지, 나머지 4종은 '추가 관리'로 구분. 8종 전체 카드 터치 및 접근성 actionLabel 그대로 유지.

## 기능/데이터 보존

Room 스키마·Repository Flow·일정/경비/예산/준비물 계산·지도/정산/예약/백업·편집/삭제·로그인·Play 등록정보·서명·광고·권한 변경 없음. 신규 라이브러리 없음.

## 검증 계획

- 정확한 PR HEAD 기준 Android CI: Debug 빌드/JVM/Room API 35, 2-C 휴대전화/태블릿 스크린샷, 3-A 지도, 3-B 정산·예약, API 29/36 호환성
- 2-C UIAutomator: 기존 홈 상태 필터/편집/삭제 취소·작업 공간 8 카드/집계·실데이터 CRUD, 추가로 `여행 설명 보기` → 목적/메모 표시 → `여행 설명 접기` 검증 및 원본 PNG
- 실제 Android 휴대전화/태블릿 라이트·다크·글꼴 160%에서 요약 값 잘림/중첩/수평 오버플로 확인
- 병합 전 CI와 원본 PNG 시각 검수. 물리 실기기·TalkBack·폴더블/Play Console 실노출은 별도 미검증으로 구분

## 배포 기준

PR은 검증 전 Draft. CI 및 시각 QA 판정 전 `main` 병합·Google Play closed/production 승격 금지. UI-R02에서 Play 스토어 이미지/AAB 직접 변경 없음. 다만 `main` 병합 이후에는 기존 자동 `play-internal.yml`이 실행될 수 있으므로 릴리스 가드 별도 확인.

참고:
- R00~R09 기획서: https://docs.google.com/document/d/1sOjzZKCaVuHxFeCRVQnKlFp7jZd9dc-i0sYFev9uRKk/edit
- UI-R00 비교: https://drive.google.com/drive/folders/1JLtNkO438c_APpeXcia46-g6lIzz5NJI
- UI-R01 최종 QA: https://docs.google.com/document/d/1_r5-i-DsRYSd1vO-QPOOpSI1MnmBz0QVv-LZ7G683e8/edit
