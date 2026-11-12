# UI-A04 — 지도·장소 선택 UX 개선

기준: 2026-11-12, 선행 UI-A03 병합 `main=9c35be4b06bdac1e1d2c9a1c83d9f84cfbb509e0`.
목표: 기존 MapLibre 및 Room 데이터를 보존하면서 지도에서 장소를 선택한 뒤 상세를 확인하는 흐름을 휴대전화·태블릿에서 일관되게 제공.

## 기획 근거

- Google Drive: [UI-R00 지도 실제 Android vs 기획 재구성 비교](https://drive.google.com/file/d/1bLSLyY9E_sgBwF4F3gMbeKxoixIrleEA/view)
- [UI-R06 지도 기획·구현 QA](https://docs.google.com/document/d/1Fow-2X87yLSqR7vA16DqvSIFRXF_8OOyfloRCCdS_LM/edit)
- 현행 코드: `TripMapScreen.kt`은 기존 핀 터치 시 즉시 상세로 이동했으나, 목록의 `지도에서 선택`은 선택 상태만 변경했음. 태블릿에는 선택 장소 요약이 없고 휴대전화만 제공했음.

## 실제 변경

1. **공통 선택 모델**
   - 지도 핀 탭과 장소 목록의 `지도에서 선택`은 동일한 `selectedId`를 갱신. 사용자 명시적 상세 진입은 `선택 장소 상세` 버튼으로 분리.
   - 선택한 핀에는 중성 검정 테두리 1개의 구분 링을 추가. 기존 확정·후보·저장 상태 원형 핀과 실제 좌표/MapLibre 카메라 이동은 유지.
   - 날짜 필터에 따라 유효하지 않은 선택 장소 ID는 지우되, 현재 필터에 포함된 선택은 보존. 비어 있는 필터에서 임의의 다른 장소를 자동 선택하지 않음.

2. **휴대전화·태블릿 공통 요약**
   - 지도에서 선택한 장소의 `지도 선택 · 장소명`, 주소, 상세 진입 버튼을 같은 컴포넌트로 노출. 태블릿에서는 지도 옆 장소 패널에 출력.
   - 장소 목록에서 선택된 행은 전체 사각형 테두리 2dp + `선택한 장소` 상태 배지로 구분. 기존 `지도 선택됨: 이름`, `장소 상세`, `일정 편집` 접근성/조작 유지.
   - 날짜별 `장소 N개 · 지도 핀 N개` 요약 추가, 좌표 없는 장소는 지도에서 제외하고 목록에는 보존. 장소 상태, 날짜별 링크, 외부 검색 동선 유지.

3. **기능 보존**
   - Room·DAO·Repository·지도 타일·위치 좌표·저장 장소·날짜/일정 연결·지도 카메라 기능·검색 공급자·백업 변경 없음.
   - 지도에서 좌표 없는 장소를 선택하려면 기존 목록의 `장소 상세`를 이용.
   - 새로운 외부 SDK/의존성/네트워크 호출, 그라데이션/그림자/애니메이션, 금액·정산·Play 트랙/서명 변경 없음.

## QA 및 수용 기준

- JUnit `TripMapSelectionTest`: 선택 장소가 날짜 필터에 보이는 경우 유지, 필터 밖이면 초기화, 빈 선택을 임의의 장소로 치환하지 않음.
- 실제 Android 3-A API35 phone/tablet: 지도 중앙 핀 터치 → `지도 선택 · 제주국제공항` 표시 → `선택 장소 상세` → 공급자·장소 상세 → 연결 일정 편집·복귀. 필터 2026-11-11에서 `장소 1개 · 지도 핀 1개`, 전체에서 `장소 3개 · 지도 핀 2개`. 제주도청 목록 선택 후 MapLibre 카메라 중심 이동.
- 실화면 3-A 원본 캡처의 라이트·다크·글꼴 160%에서 상태 테두리·요약·버튼 배치 및 태블릿 2패널 확인.
- 최신 PR HEAD에 대해 Android CI/JVM/Room API35, 2-C phone/tablet, 3-A phone/tablet, 3-B phone/tablet, API29/36 compatibility **모두 success** 이후에만 Draft 해제 및 main 안전 병합. 신규 Actions 시작 후에는 사용자 요청에 따라 반복 조회 없이 링크 안내.
- 물리 실기기·폴더블 및 TalkBack 직접 조작, Play 심사/테스터 설치 여부는 에뮬레이터 결과와 별도로 취급.

## UI-A03 내부 테스트 선행 상태

UI-A03의 main Android CI 성공 후 [Play internal release](https://github.com/hebi10/TripCanvas/actions/workflows/play-internal.yml)가 2026-11-12 실행 중인 것으로 확인. 이 UI-A04 브랜치는 **신규 Play AAB 게시 또는 closed Alpha 승격을 수행하지 않음**.

다음 작업: `UI-A05 — 동행자 정산·예약 정보 위계 개선`.
