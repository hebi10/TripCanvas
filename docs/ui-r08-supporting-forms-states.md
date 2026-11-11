# UI-R08 — 보조 화면·상태 통합 리디자인

기준일: 2026-11-11 · TripCanvas · Jetpack Compose + Room
디자인/QA 기준: UI-R00~R09 기획·실화면 차이 분석 및 S02·S05·S10·S20·S21 상세 명세.

## 선행 관계와 상태
- UI-R08 최초 구현 기반: UI-R07 PR #32, HEAD `4c8988e42d2b5db6285f0a5dc25af99344a79617`.
- UI-R04 #29 → UI-R05 #30 → UI-R06 #31 → UI-R07 #32 → UI-R08의 순차 검증·병합 관계를 유지.
- 현재 작업은 UI-R08 화면/안내·단위 테스트·Android 계측 테스트에 한정한다. 선행 PR 자동화가 수정하는 브랜치를 임의로 변경하거나 병합하지 않는다.

## UI 변경 범위

### 1. 여행 생성·편집 (TripFormScreen)
- 제목 및 기간/목적지, 현지 설정(시간대/기준 통화), 선택 정보(여행 목적/메모)를 별도 논리 섹션으로 구성.
- 필수값(*) 의미와 변경 사항이 저장 시 반영된다는 사실을 설명. 과거의 잘못된 '일정·경비·준비물은 후속 제공' 문구를 현재 구현 기준으로 보정.
- 사용자가 한 필드만 수정할 때 다른 입력의 검증 오류까지 사라지지 않도록 `retainTripFormErrors` 적용. 시작일이나 종료일이 바뀌면 날짜 범위 오류는 함께 해제해 재검증 가능.
- 날짜 선택기에서는 날짜 선택 전 '확인' 동작 비활성화. 기존 `TripDateField`와 선택한 날짜의 ISO 저장 형태 유지.
- 기존 일정을 여행 기간 밖으로 밀어내는 편집이 거부될 때 개수/날짜를 명확히 읽을 수 있도록 경고 문구 정리. 실제 기간 차단 로직과 데이터 보존은 그대로.
- 시간대/통화 입력에는 ASCII 키보드 힌트를 부여하되 입력값 허용/검증 정책 불변.

### 2. 일정·지출 입력 (ScheduleFormScreen, ExpenseFormScreen)
- 일정과 지출 필수 입력 표시 및 현지 날짜/시각 안내 정리.
- 한 필드의 변경으로 다른 필드 오류가 일괄 사라지지 않도록 변경한 항목의 오류만 해제. 시작/종료 시각은 같은 유효성 그룹이므로 함께 재검증.
- 지출은 금액/통화 → 일시 → 분류·장소·메모 순서로 섹션 구분. 금액은 소수 입력 키보드, 시간은 콜론 입력이 가능한 ASCII 키보드 사용.
- 기존 저장/수정/삭제, 유효성 검증, 겹치는 일정 승인, 여행별 Room Flow, 미저장 뒤로가기 확인/중복 제출 방지는 수정하지 않음.

### 3. 설정·개인정보 안내 (SettingsScreen)
- 화면 모드, 여행 기본값, 데이터 관리, 개인정보·앱 정보, 개발용 화면 검수를 명확히 구분.
- 기기의 기본값과 여행별 시간대·통화 편집 차이 설명, 백업의 비암호화 및 JSON에 예약 확인번호/메모 등이 포함될 수 있다는 주의, 첨부 원본/URI 제외 안내.
- 개인정보처리방침 외부 브라우저 연결, 라이트/다크 설정, 기존 개발용 미리보기 버튼 유지. 신규 계정 기능 추가 없음.

### 4. 백업·복원 (BackupScreen)
- 파일 내보내기(1단계)와 복원 전 파일 검증·최종 확인(2단계)을 사용자 관점에서 구분.
- 저장/선택 취소 시 오류 상태를 해제하고 '기존 여행 데이터 변경 없음'을 안내. 시스템 SAF 취소는 백업 복원이나 로컬 데이터 삭제로 해석하지 않음.
- 실제 복원 확인 대화상자에서는 작업 중 확인/취소 버튼 비활성화. 취소하면 검증 대기 상태를 닫고 '복원 취소/기존 데이터 유지'를 표시.
- 공통 `TripErrorState`의 액션 라벨을 선택 가능하도록 하여 '오류 닫기'와 '실제 다시 시도'를 혼동하지 않게 함. 공통 빈 상태는 접근성 polite live region.
- 기존 `TripBackupService`, `TripBackupCodec`, Room transaction/백업 버전·최대 10MB·JSON SAF 경로·실제 복원 기능·민감자료 정책·복원 전 확인 다이얼로그를 그대로 보존.

### 5. 공통 UI 호환성
- `TripTextField`에 옵션 `keyboardOptions` 추가; 기본값 `KeyboardOptions.Default`로 나머지 화면 동작 보존. UI-R07 확인번호 가림 `visualTransformation` 지원 유지.
- `TripConfirmDialog`에 옵션 `enabled` 추가; 기본 true로 나머지 모든 다이얼로그 동작 보존.
- `TripErrorState`에 옵션 `actionLabel` 추가; 기본 '다시 시도'로 기존 호출 유지. 백업 오류 해제에만 '닫기' 적용.
- 새로운 색상, 그림자, 둥근 모서리, 불필요한 애니메이션, 신규 패키지/의존성/저장소 구조 변경 없음.

## 자동 검증 게이트
1. Kotlin JVM `TripFormErrorsTest`: 한 필드 수정 시 관련 오류만 해제, 날짜 변경 시 날짜 범위 오류 함께 해제, 동일 입력 오류 상태 유지.
2. API35 2-C 실제 Android: 기존 여행 편집 진입/필수 기간 섹션/시작일 날짜 선택 팝업의 취소/현지 설정, 지출 폼의 일시 섹션과 무효 금액 필드 오류, 설정의 개인정보·앱 정보, JSON 백업 파일 저장 취소 후 기존 여행 행 보존. 휴대전화·태블릿 라이트/다크/160% 실화면 PNG.
3. 기존 Android CI 빌드/JVM/Room API35, 3-A MapLibre 지도, 3-B 정산·예약 및 API29/36 호환성 회귀.
4. SAF 파일 선택·취소, 복원 미리보기·변조 JSON 오류·복원 원자성은 기존 테스트와 별개로 실제 상황을 확인한 범위만 보고. 물리 실기기/폴더블/TalkBack/가로 회전·IME 키보드 실측은 자동 성공과 구분.
5. 정확한 최신 PR HEAD에서 5개 GitHub Actions 전체 성공 + 원본 PNG 시각 검수 + 선행 PR 안전 병합 확인 전 main 병합 금지.

## 병합·배포 조건
- Stacked Draft PR: UI-R08 → UI-R07 #32 → UI-R06 #31 → UI-R05 #30 → UI-R04 #29 → main. 선행 단계가 최종 QA와 main 병합을 마치기 전 UI-R08 main 병합 금지.
- 선행 병합 시 정확한 main SHA 기준으로 base 조정·diff/충돌·코드 최신 상태 확인 후 UI-R08 정확한 HEAD의 빌드/Room/UI 캡처를 재검증.
- Google Play closed/production 승격, 스토어 이미지 교체, 앱 서명/AAB/개인정보 정책 변경은 UI-R08 작업 범위 외. main 자동 Play internal 릴리스가 발생할 경우 결과를 API로 확인한 경우에만 기록.

참조:
- UI-R00~R09 Google Docs: https://docs.google.com/document/d/1sOjzZKCaVuHxFeCRVQnKlFp7jZd9dc-i0sYFev9uRKk/edit
- 디자인/실화면 대조: https://drive.google.com/drive/folders/1JLtNkO438c_APpeXcia46-g6lIzz5NJI
- 선행 UI-R07: https://github.com/hebi10/TripCanvas/pull/32
