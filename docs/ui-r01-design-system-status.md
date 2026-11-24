# UI-R01 — 공통 디자인·아이콘·상태 안내 보정

기준: `adb5c23af25f23d243d830d7419acbc5e57482cc` / UI-R00 목표 디자인 8종 / 2026-11-11

## 실제 변경

1. `TripChoiceChip`의 비선택 사방 테두리를 라이트 `#D2D2D2`, 다크 `#555555`로 고정. 선택 시 기존 흑백 채움 유지. `enabled` 인자 추가로 테마 저장 중 실제 비활성 표시.
2. `TripCanvasTheme` Material 3 `outlineVariant`·`surfaceTint`를 앱의 중성 팔레트에 매핑해 기본 Material 보라 계열이 유입되지 않도록 제한. 기존 색상 토큰·타이포·사각형 규칙 유지.
3. 홈 설정 버튼은 기존 `설정` 텍스트·동작을 유지하면서 평면 설정 아이콘을 추가하고 48dp 높이를 확보. 앱바 뒤로 버튼도 48dp 확보.
4. 공통 빈 상태 문구 가운데 정렬, 오류·로딩·오프라인 안내에 `liveRegion=Polite` 의미 속성을 부여. 공통 확인 다이얼로그 버튼의 최소 높이를 48dp로 보정.
5. 텍스트 필드 안내가 오류 상태일 때 오류 색상을 사용하고 평상시는 중성 보조 텍스트를 사용. 기존 입력·저장·검증 구조는 불변.

## 제외 및 보존

- 여행 CRUD, 일정/경비/정산/지도/예약·백업 로직/DB 마이그레이션/네트워크 호출: 변경 없음
- 신규 하단 내비게이션, AI 생성 시안에만 있던 기능·브랜드 요소: 반영하지 않음
- 런처·스토어 이미지, Google Play 등록정보: R01에서 변경하지 않음

## 검증

- JVM: `TripDesignTokensTest` (팔레트·간격·터치 타겟)
- Android CI: assembleDebug + testDebugUnitTest + Room API35 계측
- 2-C 휴대전화·태블릿: 라이트 설정 캡처, 다크 설정 캡처, 160% 다크 설정 캡처 추가, 기존 홈/작업공간/준비물/경비/설정/백업 조작 회귀
- 3-A·3-B 휴대전화·태블릿: 지도/경비·정산·예약 전 구간 회귀
- 필요 시 API29/36 및 물리 실기기·TalkBack·폴더블은 별도 확인

## 판정 기준

동일 커밋에서 모든 PR CI가 성공하고, 이미지 원본을 직접 열어 라이트/다크/160%의 강조색·테두리·버튼 가독성을 확인한 후에만 병합. 실제 Play 배포·심사/테스터 노출은 CI와 별도 판정.

원본: https://drive.google.com/drive/folders/1JLtNkO438c_APpeXcia46-g6lIzz5NJI
기존 기획: https://docs.google.com/document/d/1sOjzZKCaVuHxFeCRVQnKlFp7jZd9dc-i0sYFev9uRKk/edit
