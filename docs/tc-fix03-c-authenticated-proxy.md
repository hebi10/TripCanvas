# TC-FIX-03-C — 운영 프록시 인증 준비 및 공급자 검토 (2026-11-25)

기준 main: 66ddcbd2b995f73dfdd744752b19ca866df1dc99. #77 Photon 공개 데모 release 제한 완료 이후, 인증된 운영 API의 연결 경계를 준비한다. 실제 공급자/백엔드 계약 승인, 서비스 생성, 결제, 네트워크 요청 및 배포는 하지 않는다.

## 현재 데이터 모델과 공급자 검토

Room/JSON 백업과 PlaceRepository는 저장 가능한 외부 장소의 provider를 Photon/OSM으로 제한한다. 지도는 MapLibre/OpenFreeMap이다. 따라서 기존 장소 ID와 저장 데이터를 유지하면서 검토할 첫 후보는 자체 운영 Photon(OpenStreetMap) 및 Valhalla 경로 엔진이다. 승인된 공급자 선정이나 무상 운영을 의미하지 않는다.

- Photon 공식 README https://github.com/komoot/photon/blob/master/README.md : 공개 데모는 합리적인 소규모 요청만 허용하며 제한/차단 가능. 자체 인스턴스 운영 가능. 실제 한국/해외 검색 정확도와 데이터 갱신, 서버 용량·운영 비용을 PoC로 측정해야 한다.
- Valhalla 공식 경로 API https://valhalla.github.io/valhalla/api/turn-by-turn/overview/ : 차량·도보·자전거 및 대중교통/복합 이동수단 지원. 대중교통 데이터의 실제 지역 커버리지와 최신성은 별도 검증한다. 미지원 모드는 조회 불가로 처리한다.
- OSM Foundation 지오코딩/출처 지침 https://osmfoundation.org/wiki/Licence/Community_Guidelines/Geocoding_-_Guideline 및 https://osmfoundation.org/wiki/Licence/Attribution_Guidelines : ODbL 출처 표시와 상당량 추출/파생 DB의 라이선스 조건을 검토한다. 개별 사용자 저장 결과 및 원본 데이터의 이용 조건을 운영 전에 확인한다.
- Google Places API 정책 https://developers.google.com/maps/documentation/places/web-service/policies 및 Routes 정책 https://developers.google.com/maps/documentation/routes/policies : 저장/캐시·지도 표시 제한이 존재하므로 현재 MapLibre 및 영구 저장/백업 구조에 단순 치환하지 않는다. 다른 서비스도 같은 기준으로 라이선스와 가격을 검토한다.
- 금액 미확정: 서버/저장소/트래픽/데이터 갱신 비용, 계정별 일일 및 월간 요청 상한, 전역 비용 상한을 실제 예상 이용량과 견적을 바탕으로 승인해야 한다. 무료라고 단정하지 않는다.

## 앱 연결 계약

- MapProxySessionProvider는 승인된 인증 어댑터로부터 호출 시점의 단기 bearer credential을 받는다. credential은 정확한 HTTPS origin에 바인딩하고 만료·빈 값·잘못된 문자·대상 불일치 시 연결 열기 전에 중단. 앱에 키 저장/로그 기록/실제 인증 공급자 추가 없음.
- MapProxyTransport: 고정 HTTPS /v1/places 또는 /v1/routes, redirect/cache 금지, 7초 연결·읽기 제한, 요청 4KiB·응답 8KiB/256KiB 제한, 200 응답만 읽고 429 Retry-After 전달. 서버의 오류 본문을 읽거나 UI에 표시하지 않는다. 자동 재시도 없음.
- 기존 경로 프록시는 URL만으로 활성화되지 않도록 변경한다. 실제 인증 어댑터가 주입될 때까지 비활성화. 인증된 결과의 계정/권한 간 재사용을 피하기 위해 서버 응답 메모리 캐시는 사용하지 않는다. 기존 명시 조회 동의·결과 조회 시각 및 UI 만료·요청 제한은 보존.
- 장소 후보 어댑터는 POST /v1/places {query, language:"ko", limit:8}, 성공 응답 {provider:"Photon/OSM", places:[{providerId, name, address, latitude, longitude}]} 계약. 최대 8개, OSM ID/중복/좌표/문자열/공급자 검증 후 기존 RemotePlace로 반환한다. 아직 TripApp에 설치하지 않는다. DEBUG Photon 호출 및 release 공개 데모 제한 유지.
- 경로는 기존 POST /v1/routes {origin, destination, mode}, {distanceMeters, durationSeconds, provider, queriedAtMillis} 계약 및 지원 불가 상태 유지.
- 서버 측 책임: 실제 인증 토큰 검증 및 만료/권한/앱 식별 확인, 승인된 공급자와 저장/표시 권리 검증, 계정·설치/전체 쿼터와 비용 상한의 동시성 안전한 강제, 키 보호·로그 최소화, 지원 모드와 오류 정책, 개인정보 정책. 클라이언트의 인증 문자열 검사/요청 간격만으로 서버 권한을 대체하지 않는다.

## 검증 및 남은 작업

JVM: endpoint/경로, 인증 부재/만료/대상 불일치/헤더 삽입 방지, 매 요청 인증, redirect/오류 본문/응답 상한 확인, 인증 어댑터 없는 경로 비활성. Android: Photon 호환 후보 응답의 ID/좌표/공급자 검사. 실제 운영 백엔드·인증/공급자 설정과 실기기/지역별 QA는 미완료. 공급자·월 예산·인증 방식 승인 전 운영 API 활성화/Play 게시 금지. 기존 Room/백업 형식 변경 없음.
