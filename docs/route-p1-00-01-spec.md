# ROUTE-P1 — 이동 구간 비교·경로 공급자 게이트

기준일: 2026-11-12 · 앱 `com.hebi.tripcanvas` · 출처 [TripCanvas 0-A 전체 PRD](https://docs.google.com/document/d/1IF7ngaZrorGaQsy_VsjLfB4rtXU8ZKc1W2WkAO9hvmY/edit).
운영 방침: [RELEASE-FREEZE #47](https://github.com/hebi10/TripCanvas/pull/47) 적용 이후에는 **개발 작업마다 Google Play 업로드하지 않고** P1~P4/QA를 모두 완료한 후 수동 최종 승인 배포.

## 1. PRD 범위와 근거

- `FR-ROUTE-01 [P1]`: 이동 수단별 거리·예상 시간은 **실제 공급자가 제공하는 경우에만** 화면에 표시하며, 미조회·실패·좌표 없음은 '조회 불가'.
- `FR-ROUTE-02 [P4]`: 방문 순서 변경 제안은 기존 순서/제안 순서 차이를 보여 준 뒤 사용자 승인 시에만 적용. 이 ROUTE-P1 PR에는 자동 변경 없음.
- 사용자의 여행과 장소 좌표 및 날짜가 공급자에게 전송될 수 있으므로 요청 순간의 안내·개인정보/약관·비용 동의 설계를 별도로 승인해야 함.

## 2. 공급자 조사 — 2026-11-12 기준 공식 자료

| 공급자 | 확인 내용 | 운영 적용 판단 |
|---|---|---|
| Google Routes API | 실 경로 조회에 청구 계정·API 키가 필요. `Compute Routes`는 요청 단위, Matrix는 원점×목적지 원소 단위 과금. 쿼터/하루 요청 한도 설정 가능. https://developers.google.com/maps/documentation/routes/usage-and-billing | 글로벌 차량/도보/대중교통 등 후보. **과금·프로젝트·약관·서버 프록시 검토 전 직접 호출 금지** |
| GraphHopper Directions API | Free: 하루 500 크레딧, 공식 요금표상 비상업용. Basic: €69/월·5,000 크레딧/일 등. https://www.graphhopper.com/pricing/ | 무료 운영 전제 금지. 유료 상업 사용 계약 검토 필요 |
| FOSSGIS OSRM 공개 데모 | 1 req/sec 제한, 과도한/상업적·고가용 운영 목적에 맞지 않으며 안정성/응답 SLA 없음. https://github-wiki-see.page/m/Project-OSRM/osrm-backend/wiki/Demo-server / https://map.project-osrm.org/about.html | **프로덕션 호출 금지**; 구현용 더미 숫자도 사용자에게 표시하지 않음 |
| OpenRouteService | 공식 계획 안내 https://openrouteservice.org/plans/ | 현재 실제 계정 정책·상업 사용 범위/개별 rate limit 확인 전 공급자 확정 보류 |

**2026년 실제 과금액은 계약/사용량에 따라 달라집니다.** 사용자 동의 없이 새 유료 API 호출·백엔드 배포·API 키 저장 없음.

## 3. ROUTE-00 / ROUTE-01 이번 코드 범위

- `TripRouteComparison.kt`: 날짜별 일정 `sortOrder`와 연결 장소 ID에서 연속 장소 구간 추출. 연속 중복 장소는 한 번만 취급. 날짜 '전체'나 1곳 이하인 경우 거리·시간을 표시하지 않음.
- 구간의 시작/종료 장소에 위경도 중 하나라도 없으면 `COORDINATES_MISSING`; 값이 있어도 공급자 확정 전에는 `PROVIDER_NOT_CONFIGURED`.
- 이동수단 `도보/차량/자전거/대중교통`을 사용자가 선택할 수 있지만, 현재는 실제 경로 공급자가 연결되지 않은 상태임을 분명히 표시. 모든 값의 정확도를 보장하는 수치·예상 ETA·직선 거리/차량 속도 기반 임의 추정 **표시 금지**.
- `TripRouteProvider` 계약은 비동기 공급자 연동을 위한 인터페이스만 정의. 응답은 거리(미터), 시간(초), 공급자 식별, 조회 시각으로 검증하며 잘못된/부정확한 응답은 값 없는 상태로 전환.
- `TripMapScreen.kt`의 기존 단일 문구를 날짜별 구간·수단 선택 패널로 대체. 저장된 일정·정렬·Room·MapLibre 핀·카메라·장소 편집·정산/예약 기능은 불변. 선택 수단은 화면 상태이며 **데이터에 쓰거나 실제 일정 순서를 변경하지 않음**.

## 4. ROUTE-02 — 실제 공급자 연동 전 충족할 게이트

1. 공급자 하나 이상 선택 및 라이선스·운영 국가·교통수단 지원/요금·쿼터·지도 attribution 정책 확인.
2. API 키를 앱 APK/AAB에 직접 포함하지 않는 안전한 프록시/인증·사용량 차단·비용 상한 설정. 해당 백엔드는 사용자의 명시적 동의와 별도 운영 준비 필요.
3. 클릭 시에만 요청하고 좌표 전송 고지; 사용자가 거절·오프라인·쿼터 초과·지도 응답 지연·지원되지 않는 교통수단이면 정확히 '조회 불가' 표시.
4. 실제 API 응답에는 기준 시각·공급자·거리 단위·예상 시간·교통수단 정보를 포함하고 출처를 명시. 허위 실측·추정 ETA/가짜 경로를 삽입하지 않음.
5. 응답 캐시 만료·쿼터/비용 모니터링·자동 재시도/연속 호출 제한·다중 국가 정확성·실기기·폰/태블릿·160% 접근성 검증.
6. 사용자 승인 없이 방문 순서·실제 일정·예약을 변경하지 않음.

**블로커**: 실제 상용 경로 API 공급자, 서비스 운영 계정·쿼터·청구/개인정보 처리 조건은 아직 확정·연결되지 않음. ROUTE-02 완료로 표기하지 않음.

## 5. 품질 게이트

- JUnit `TripRouteComparisonTest`: 날짜와 저장 순서 일치, 연속 동일 장소 축약, 1곳·없는 장소·전체 날짜에서 수치 생성 금지, 좌표 없음, 잘못된 공급자 숫자 차단.
- Android CI/JVM/AndroidTest/Room, 2-C phone/tablet, 3-A 지도 phone/tablet, 3-B 정산/예약 phone/tablet, API29/36·개인정보 정책 통과. 실제 지도·경로 비교 패널의 라이트/다크/160% 캡처 시각 확인.
- 선행 QA-A06 #44·RELEASE-A07 #45 및 동결 main의 안전 병합을 완료한 후 이 브랜치를 최신 main과 다시 정합화·재검증해야 함.
- PR 작성 자체는 Play 업로드가 아니며 `main` 병합 시에도 RELEASE-FREEZE 수동 승인 전 업로드 금지.

후속 P2: `COLLAB-00 — 공동 여행 인증·권한·동기화 아키텍처 및 비용 게이트`.
