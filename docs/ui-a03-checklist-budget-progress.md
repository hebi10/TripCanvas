# UI-A03 — 준비물·경비 진행률 시각화

기준: 2026-11-12 · TripCanvas `main=1365465452ede19d992c0ea4f5c434b066815766` / UI-A02 PR #39 병합 뒤.
설계 참고: [Google Drive UI-R00 디자인·실제 Android 비교](https://drive.google.com/drive/folders/1JLtNkO438c_APpeXcia46-g6lIzz5NJI) 및 [UI-R00~R09 기획서](https://docs.google.com/document/d/1sOjzZKCaVuHxFeCRVQnKlFp7jZd9dc-i0sYFev9uRKk/edit).

## 사용자 목표

- 준비물(S12): '준비 완료 n/N'와 함께 **실제 저장된 모든 준비물 기준**의 완료율 가로 막대 표시. 미완료 필터를 사용해도 분모가 바뀌거나 진행률이 과장되지 않아야 한다.
- 예산·경비(S09): 기본 통화 예산 대비 이미 환산 가능한 지출만 합산하여 '예산 사용률'과 막대를 표시한다. 현재 예산·사용액·잔액은 기존 계산식/표시를 그대로 유지한다.
- 사용률 100% 초과 시 실제 숫자를 유지하면서 바만 100%까지 표시하고, 초과 상태는 별도 경고 문구·색상으로 표시. 잘못된 합계·임의 환산 금지.
- 데이터가 없어 진행률을 계산할 수 없으면 진행 막대를 숨기고 이유 설명: 준비물 0개, 예산 미설정, 예산 통화 불일치 또는 금액 0 이하.
- 미환산 외화는 별도 '환산 필요' 영역으로 유지하고 사용률에도 합산하지 않음. 막대 아래 '미환산 외화 제외'를 안내.

## 구현 파일

- `designsystem/components/TripProgressTrack.kt`: 라이트/다크 기존 중성 팔레트 기반의 직각형 회색 트랙·본문색 막대. 예산 초과에만 기존 warning 색상 사용. 진행 범위 0..1 의미와 한국어 접근성 설명 제공. 신규 라이브러리/그림자/애니메이션 없음.
- `screens/TripProgressPresentation.kt`: 준비물 완료 개수와 BigDecimal 경비 금액의 **표시 전용 비율** 계산. 음수·빈 상태·0 예산 처리 및 바 표시분 최대 100% 제한. 계산된 실제 사용률 수치는 1자리 반올림, 이력 저장/환율 원본은 미수정.
- `ChecklistScreen.kt`: 완료/전체 및 남은 항목 안내 뒤 진행률, 33% 완료 등 표시. 항목 CRUD, 필터, 순서 변경, 삭제 복구 기존 동작 불변.
- `ExpenseListScreen.kt`: 총예산/지출합계/잔액 패널 아래 예산 사용률·막대·범위 안내, 예외 시 막대 대신 설명. 지출 추가, 다중 통화 경고, 참가자/정산 기능·데이터 불변.
- `TripProgressPresentationTest.kt`: 0개/경계값, 1/3, 완료 100%, 예산 4.2%, 미설정·0·음수, 초과 150% 등 단위 테스트.
- `TripCanvas2CScreenshotTest.kt`: API35 실앱 라이트·다크·160% 환경의 `준비 진행률` / `예산 사용률`·4.2%·25% 및 PNG 캡처, 기존 주요 CRUD·백업/복원 시나리오 유지.

## QA 승인 기준

1. PR 정확한 HEAD에서 Android 빌드/JVM/AndroidTest 컴파일/Room35 성공.
2. 2-C phone/tablet, 3-A 지도 phone/tablet, 3-B 경비·정산·예약 phone/tablet, API29·36 런치/Room 모두 성공.
3. 원본 PNG의 라이트·다크·160%에서 진행 막대 폭·위치·숫자/단위/상태 구분 및 버튼 접근 가능 여부 확인. 유효하지 않은 예산/미환산의 오해 유발 표시 방지.
4. GitHub Actions **실행 링크만 안내**하고 새 Actions를 반복 조회하지 않는다. 사용자가 완료를 확인해 다시 요청하면 정확한 HEAD와 원본 캡처 최종 검수 후 안전 병합.
5. main 병합 시 Play internal 자동 배포가 시작될 수 있지만 `versionCode`·API 등록 완료·실제 테스터 설치는 **실제 Play API 재조회 증빙** 없이는 성공으로 표기하지 않는다. Play closed/production 자동 승격은 이번 단계에 없음.

## 변경 금지

Room 스키마·Repository Flow·계산 엔진 `calculateExpenseTotals`·다중 통화 저장/정산/복구 로직, 지도/예약/백업, Play 서명/트랙, 스토어 자산 및 광고/권한 변경 없음. UI-A03은 기존 데이터를 읽어 표시하는 화면 개선이다.

## 다음 작업

`UI-A04 — 지도·장소 선택 UX`: 선택 핀·장소명·상세 접근 구조 개선. MapLibre 실제 기기/태블릿 2패널·오프라인 안내 및 접근성 검증.
