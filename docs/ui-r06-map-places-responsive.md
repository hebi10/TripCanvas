# UI-R06 — 지도·장소 반응형 리디자인

- 기준: 2026-11-11 Google Drive UI-R00~R09 분석서의 S07·S08 지도/장소 개선, TripCanvas Travel Editorial 디자인 시스템.
- 시작 기준: UI-R05 PR #30 HEAD `24199bf245e6e5027f31ba44f4db97d720a64872`. 선행 UI-R02 #27·UI-R03 #28은 main 병합됨. UI-R04 #29 및 UI-R05 #30의 순차 최종 QA/병합이 완료되기 전 UI-R06 main 병합 불가.
- 현재 작업: UI-R06 지도·장소 UI 변경 및 3-A Android 계측 강화. 아직 CI/실기기 성공을 주장하지 않음.

## A. 화면 구조 및 반응형

1. 기존 `TripPageFrame`의 상단 여행 제목·날짜 전체/날짜 필터·핀 상태 범례·한 번의 외부 세로 스크롤을 유지.
2. Compact(<600dp): 지도(340dp) → 선택 장소 요약(지도에서 직접 선택된 항목이 있을 때만) → 외부 장소 검색 → 장소 목록. 좌표 미등록 장소는 목록에서 보이고 핀은 생성하지 않음. 기존 핀 탭은 상세 화면으로 즉시 이동하며 링크/데이터 의미 변경 없음.
3. Medium/Expanded(>=600dp): 좌측 지도(470dp), 우측 장소 검색·목록을 동일한 상위 Row에 병렬 배치. 기존 중첩 내부 수직 스크롤은 만들지 않고 전체 `TripPageFrame` 스크롤만 사용. 지도에 크기 제한과 적절한 항목 간격 제공.
4. 지도와 목록 연동: 좌표가 존재하는 장소 목록의 '지도에서 선택' 버튼을 누르면 해당 ID를 선택하고 MapLibre 카메라가 해당 핀에 맞춰 이동. 선택 상태는 '지도 선택됨'으로 표시. 상세/연결 일정 수정 버튼은 별도로 유지.
5. Compact에서 지도에서 선택한 장소의 이름·주소·상세 진입을 상단 요약에서 노출. 그 외 선택하지 않은 장소를 자동으로 선택된 것처럼 표시하지 않음.
6. 두 환경 모두 '장소 N개'와 상태(확정/후보/저장), 연결 일정 수, 좌표 없음을 명확하게 노출.

## B. 지도·데이터 기능 보존

- 기존 MapLibre 지도 라이프사이클 및 지도 공급자 OpenFreeMap · © OpenStreetMap contributors 표기, 인터넷 연결 필요/오프라인 장소 목록 안내 유지.
- 기본 일정별 필터, 연계된 장소 집합, 확인/후보 상태, 좌표가 있는 핀만 매핑, GeoJSON source/layers 유지.
- 지도 핀 직접 탭 시 `onPlace(placeId)`로 상세 이동, 지도/일정 연결 `onSchedule(item.id)` 유지.
- 장소 직접 등록·편집·삭제, 원본 출처 정보·검색 공급자 명시·개인정보 정책·지도 타일 URL 변경 없음.
- 시간·거리·경로 계산·외부 검색 서버·신규 의존성·애니메이션·그라데이션·장식형 그림자 도입 없음.

## C. 자동 검증 및 원본 캡처

- UI-R06 PR HEAD에서 Android build/JVM/Room API35, 2-C 휴대전화·태블릿, 3-A 지도·장소, 3-B 경비·정산·예약, API29·36 회귀 필수.
- 3-A 실제 Android UIAutomator: 휴대전화/태블릿 `03-map-all-light`; 태블릿일 경우 지도 MapView의 오른쪽에 '장소 2개' 패널 위치를 실제 기기 화면 픽셀 좌표로 검증하고 `03a-tablet-two-pane-map-and-list` PNG 저장.
- 3-A 지도 핀 탭 → 실제 출처 표시되는 장소 상세 확인, 뒤로 복귀 → 선택 날짜 필터/확정 일정 1개, 목록 '지도에서 선택' → '지도 선택됨'으로 초점 변경, 상세 → 일정 편집 확인.
- 날짜 필터 '전체' 재선택 시 좌표 없는 장소가 목록에 나타나고 '좌표 없음 · 지도 핀 제외' 상태가 보이는지 확인. 외부 장소 검색 진입·복귀 유지.
- 라이트/다크/글꼴 160% 휴대전화·태블릿 PNG에서 지도 높이, 우측 목록 폭, 겹침/클리핑, 외부 스크롤 및 장소 상세 버튼 접근성 검수.
- CI 통과와 실제 PNG 육안 검수는 별도 기준. 물리 실기기·TalkBack·폴더블 및 네트워크 단절 시 지도 타일 실동작은 미검증이면 별도 보류.

## D. 순차 통합·릴리스 안전 조건

- UI-R06은 UI-R05 기반 Stacked Draft PR로 생성. UI-R04 PR #29, UI-R05 PR #30가 각각 최신 HEAD의 전체 QA 및 실제 시각 검수로 main에 병합될 때까지 Draft 유지.
- 선행 병합 이후 PR base를 최신 main으로 변경하고 변경 범위·충돌·공통 코드 누락 여부를 재점검. 정확한 PR HEAD CI와 실제 PNG 검증 후에만 Ready → main 병합.
- 기존 main 자동 Play internal 릴리스 실행은 최종 SHA 기반으로만 API 확인. Google Play closed/production 승격이나 스토어 이미지 변경은 UI-R06 범위에 없음.

## E. 참조

- Google Drive 기획 디자인·실제 Android 화면 비교: https://docs.google.com/document/d/1sOjzZKCaVuHxFeCRVQnKlFp7jZd9dc-i0sYFev9uRKk/edit
- UI-R00 목표 시안/실제 화면 비교: https://drive.google.com/drive/folders/1JLtNkO438c_APpeXcia46-g6lIzz5NJI
- UI-R04: https://github.com/hebi10/TripCanvas/pull/29
- UI-R05: https://github.com/hebi10/TripCanvas/pull/30
