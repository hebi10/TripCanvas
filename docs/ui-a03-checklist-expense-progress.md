# UI-A03 — 준비물·경비 진행률 시각화

기준: 2026-11-12 / TripCanvas `main=1365465452ede19d992c0ea4f5c434b066815766` (`UI-A02` 병합 후).
참조: [Google Drive 기획 시안·UI-R00 비교](https://drive.google.com/drive/folders/1JLtNkO438c_APpeXcia46-g6lIzz5NJI),
[UI-R04 준비물 QA](https://docs.google.com/document/d/1yCV2RFkGA4_m1Ac2lKXJwzYsfrOQQAEFusHYvkiTOQg/edit),
[UI-R05 경비 QA](https://docs.google.com/document/d/1dVzOEceEm54kmowEKgjqus5bsDZvTdCUue7glFN22_Y/edit).

## 구현 범위

### 준비물
- 기존 `체크된 개수 / 전체 개수`, 남은 수·완료율 텍스트를 유지.
- 체크리스트 제목 바로 아래 중성 회색 배경·단색 직선형 진행 막대(0~100%)를 배치.
- 저장된 Room 체크 여부를 집계한 동일 `completed/total` 값만 사용. 새로운 저장 컬럼·개별 완료율 계산 기준 없음.
- 항목이 0개이면 비교할 대상이 없으므로 진행 막대를 표시하지 않고 기존 '준비물이 없습니다' 상태를 유지.
- 체크/해제/삭제·복구가 발생하면 기존 Flow 갱신에 따라 막대도 즉시 갱신. 완료율 라벨과 TalkBack 진행률 의미 정보 제공.

### 예산·경비
- 기존 `calculateExpenseTotals`의 기본 통화로 환산 가능한 `baseSpent`와 같은 통화로 설정된 총예산을 사용.
- 총예산·지출 합계·잔액 아래에 `예산 사용률`, 퍼센트 값과 가로 진행 막대를 추가.
- 예산 초과 시 **실제 사용률(예: 120%)을 그대로 텍스트로 표시**, 막대 길이만 100%에서 제한하고 기존 경고 토큰을 적용. 잔액 '초과'와 일치.
- 예산 미설정, 예산 기본 통화 불일치, 잘못된 0원/음수 예산이면 진행 막대 대신 '사용률 표시 불가' 상태를 표시. 예산 통화 자동 환산 금지.
- 환율 없는 외화는 기존과 동일하게 `환산 필요` 섹션으로 별도 노출하며 합계·진행률·잔액에 임의로 포함하지 않음.
- 휴대전화/태블릿 요약은 기존 Compact 1열 / Expanded 3열 유지. 퍼센트와 경고 라벨은 160% 글꼴에서도 자동 줄바꿈.

## 디자인 및 접근성
- 기존 `TripPalette`의 `outline`, `primaryAction`, 초과 상황의 `warning`만 사용.
- 사각형 막대, 높이 8dp. 그림자·새 라이브러리·그라데이션·애니메이션 없음.
- `TripProgressIndicator`는 시맨틱스의 `ProgressBarRangeInfo`와 실제 항목 수 또는 사용률 설명을 제공.
- 사용률 숫자는 실제 값을 유지하되 화면에 그리는 막대는 0~100%로 제한.

## 데이터·기능 보존
- Room DB/DAO/Repository, `calculateExpenseTotals`, `calculateSettlement`, 환율 스냅샷, 분담, CRUD, 항목 재정렬, 백업/복원, 지도/예약, 서명/Play 트랙 수정 없음.
- 다른 화면의 정보 구조/공통 색상 변경 없음.

## 자동 검증
1. JVM `TripProgressRulesTest`: 빈 목록, 일부/전체 체크, 범위 외 개수, 정상 예산 4%, 0%, 100%, 초과(120% 텍스트와 꽉 찬 막대), 0/음수 예산 차단.
2. API35 2-C 실제 Android 휴대전화/태블릿: 완료율 1/3 접근성 표시, 기본 KRW 예산 300,000 대비 합산 가능 지출 12,500 사용률 4% 노출, 환율 없는 USD 12.50 별도 경고 유지. 기존 체크·복구·예산·지출 시나리오, 라이트/다크/160% 캡처 보존.
3. Android CI/JVM/AndroidTest 컴파일·Room35, 2-C phone/tablet, 3-A phone/tablet, 3-B phone/tablet, API29·36 호환성 전체 검사 통과 및 실제 원본 PNG 시각 확인.
4. 성공 전 PR Draft·미병합; 최신 HEAD 동일성, main 변경 여부 대조. 사용자는 **Actions 반복 조회 대신 실행 링크**를 요청했으므로 PR 생성 후 링크를 제공하고, 사용자가 완료를 확인한 후 최종 QA/병합.

## 배포 관련 확인
- UI-A02 main SHA `1365465452ede19d992c0ea4f5c434b066815766`에서 [Play internal release #43161425240](https://github.com/hebi10/TripCanvas/actions/runs/43161425240) 성공.
- Google Play API fresh readback: `com.hebi.tripcanvas`, `0.1.0`, `internal`, `versionCode 12`, `completed`, `api_committed_and_verified`. 기존 Alpha 승격은 실행되지 않음.
- UI-A03는 아직 새 Play AAB 배포 승인/등록을 의미하지 않음. `main` 병합 이후 자동 internal 릴리스 동작 가능성은 기존과 같음. Google 심사/테스터 설치는 별도 검증.

다음 작업: `UI-A04 — 지도·장소 선택 UX 개선`.
