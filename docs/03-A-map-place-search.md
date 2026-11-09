# TripCanvas — 3-A 지도·장소 검색 및 일정 연결

- 기준: 2026-11-09
- PR: https://github.com/hebi10/TripCanvas/pull/10
- 선행: `docs/00-A-product-requirements.md`, `docs/00-B-screen-spec.md`, 2-A~2-C 기능
- 화면: S03 작업 공간, S04 날짜별 일정, S06 저장 장소, S07 지도, S08 장소 상세, 외부 검색
- 기존 Travel Editorial UI/UX 및 사용자 승인 원칙 유지

## 1. 지도 공급자 결정 G-02

**지도:** MapLibre Native Android OpenGL ES 13.7.1 + OpenFreeMap Liberty 스타일.
- 공개 스타일: `https://tiles.openfreemap.org/styles/liberty`
- Android 지도 렌더링은 네트워크 필요. 오프라인에서는 Room 저장 장소·일정 목록 제공.
- OpenFreeMap 공개 지도는 무료·키 불필요이며 타일 제공 가용성 보증은 없음.
- 제한 없는 오프라인 캐시를 구현하거나 약속하지 않음. 자동 위치 추적 없음.
- 지도 출처 표기: OpenFreeMap / © OpenStreetMap contributors.

**장소 검색 개발·소규모 테스트:** Photon 공개 데모 `https://photon.komoot.io/api/`.
- 사용자가 버튼을 클릭한 경우에만 요청. 자동완성/백그라운드/일괄 조회 없음.
- 단말 내 최소 1.5초 요청 간격, 10분 메모리 캐시, 최대 8개 결과, 7초 제한.
- 유일한 필수 데이터: 검색어. 현재 위치/앱 권한은 전송하지 않음.
- Photon 공개 서버는 'reasonable limit' 사용만 허용하고 SLA 없음. **대규모 공개 서비스의 API로 의존 금지.**
- 다수 사용자 출시 이전 자체 Photon/지오코더 또는 쿼터·계약이 있는 정식 장소 검색 공급자로 교체해야 함.

공식 근거:
- https://openfreemap.org/quick_start/
- https://openfreemap.org/
- https://github.com/komoot/photon
- https://github.com/maplibre/maplibre-native/releases/tag/android-v13.7.1

## 2. 데이터 및 마이그레이션

- Room 버전 3 → 4 수동 마이그레이션: `places`에 `provider`, `providerPlaceId`, `sourceFetchedAtMillis` 추가.
- `tripId + provider + providerPlaceId` 고유 색인. 수동 장소는 nullable metadata로 기존 위치 유지.
- 외부 장소 저장 시 동일 여행의 동일 공급자 ID가 있으면 기존 장소 ID 반환. 타 여행과는 별도.
- 기존 장소·일정·경비·예산·준비물 및 참조 FK 유지.
- JSON 백업은 v2로 확장하되 v1 파일 읽기 지원. 공급자 원본 ID·조회 시각 보존.
- 복원 전에 형식·여행 소유·장소 참조·공급자 메타데이터 유효성 검증.

## 3. UI·동작

### S07 여행 지도
- 여행별 Room 장소를 기준으로 지도 핀 생성. 좌표 없는 장소는 목록만 표시.
- 전체/날짜별 일정 필터, 후보/확정/저장 상태 텍스트 표기.
- 지도 핀 탭 → S08 장소 상세; 날짜/장소 카드 → 해당 장소·일정 편집 이동.
- Route API가 연결되지 않았으므로 예상 이동 시간·거리 미표시, '조회 불가' 안내.
- 지도 타일 다운로드 중 또는 네트워크 장애 시에도 저장 장소 목록 사용 가능.

### S06 외부 장소 검색
- 저장 장소 목록의 검색은 종전 Room 로컬 이름·주소·메모 필터 유지.
- '외부 장소 검색'에서는 명시적 검색 실행 후 이름·주소·좌표·공급자 확인.
- 선택 저장 → 같은 공급자 ID 중복 발견 시 기존 ID 사용.
- 기존 장소 직접 입력/수정/삭제 및 일정에서 장소 선택 기능 유지.

### S08 장소 상세
- 이름·주소·좌표·공급자·조회 시각 및 연결 일정 표시.
- 장소 편집, 지도에서 보기, 해당 일정 편집 연결.
- 장소 삭제 시 기존 원칙대로 일정은 남고 연결된 장소 ID만 해제.

## 4. 검증 기준

- Android 빌드·JVM 테스트 및 API35 Room v1→4, v2→4, v3→4 마이그레이션.
- Photon 응답 파싱·중복 제거 및 검색어 유효성 검증(네트워크 호출 없음).
- 공급자 ID를 기준으로 한 여행별 중복 방지 테스트.
- JSON 백업 v1 복원·v2 공급자 메타데이터 왕복 및 손상 메타데이터 차단.
- Android API35 휴대폰·태블릿 실제 지도·일정·장소 상세 캡처 검증.
- 지도 스타일/타일의 실기기 네트워크 렌더링, TalkBack, 폴더블, 작은 화면·글꼴 160%, API29/36은 CI와 별도 후속 QA.

## 5. 범위 외·출시 전 기술 게이트

- 이동수단별 실경로·이동 시간/거리 제공은 **Route 공급자 선정 이후** 추가. 추정 거리·시간 임의 표기 금지.
- 현재 위치 사용 권한, GPS 트래킹, 오프라인 대용량 지도 타일, 자동 동선 최적화 미포함.
- Photon 공개 데모는 프로덕션 서비스 공급자가 아니므로 정식 서비스 배포 전 변경 필수.
- 외부 검색 요청 수·예산 상한, 데이터 제공 라이선스 및 국내/해외 실제 품질을 운영 환경에서 평가.
- 테스트가 통과해도 기기/지역별 지도 타일 렌더링과 공급자 장애 시 복구는 별도 검증.
