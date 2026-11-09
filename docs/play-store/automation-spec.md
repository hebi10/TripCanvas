# TripCanvas Google Play 자동 배포 기준

- 대상: `hebi10/TripCanvas`, 패키지 `com.hebi.tripcanvas`, 초기 버전 `0.1.0 (1)`.
- 현재 `TripCanvas Android CI`의 정확한 `main` 커밋에 대해 `android-build`와 `room-migration-instrumentation`이 모두 성공해야 서명·업로드한다.
- 정상 `main` push 검증 성공 후 내부 테스트에 자동 제출한다. 수동 실행은 `check`, `build`, `initialize`, `deploy`를 지원한다.
- 최초 `initialize`는 실제 번들·APK·모든 트랙 버전이 비어 있을 때 코드 1을 `internal draft`로만 제출한다. 알려진 Google 심사 보류 파라미터 오류만 같은 편집에서 한 번 좁게 처리한다.
- 일반 배포는 실제 등록 바이너리 조회를 통과한 뒤 Play 전체보다 큰 버전 코드를 할당한다. 영구 초기화 변수로 조회를 대체하지 않는다.
- 앱 전용 업로드 키와 `main`·`google-play` 환경에 한정된 WIF를 사용한다. 키 불일치, 소스 변경, 패키지·해시·16KB 검사 실패 시 중단한다.
- 비공개 테스트는 명시적 실행에서 내부 테스트의 기존 정확한 버전만 `alpha`로 승격한다. 프로덕션은 지원하지 않는다.
- 키·토큰·비밀번호·원문 인증 응답은 출력하거나 저장소에 저장하지 않는다. 인증·Secrets 등록·소유자 전용 키 백업은 계정 설정 단계에서 처리한다.
- API 커밋 및 새 편집 재조회, Console 게시 상태, 실제 설치 가능 여부는 각각 구분한다.
- 사용자 기능과 디자인을 변경하지 않는다. 스토어 문구와 개인정보 안내는 구현된 로컬 여행·일정·준비물·경비·JSON 백업만 설명한다.
