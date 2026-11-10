# TripCanvas Play 등록과 자동 배포

| 항목 | 준비 값 |
| --- | --- |
| 저장소 | `hebi10/TripCanvas` · 저장소 ID `1450869093` |
| 패키지 | `com.hebi.tripcanvas` |
| 버전 | `0.1.0` · 최초 코드 `1`, 이후 Play 전체 버전보다 큰 코드 자동 할당 |
| 기본 언어·가격 | 한국어 `ko-KR` · 무료 |
| 카테고리 후보 | 여행 및 지역정보 |
| 연락처 | asdlkj0104@gmail.com |
| 개인정보처리방침 준비 URL | https://hebi10.github.io/TripCanvas/privacy-policy.html |
| 기본 배포 | 내부 테스트 |
| 비공개 테스트 | 명시적 요청 시 완료된 내부 버전만 `alpha`로 승격 |

앱의 실제 현재 기능만 `listing-ko.json`에 담았습니다. 3-A의 여행 지도·날짜 필터·수동 외부 장소 검색과 3-B의 지출별 수동 환율·로컬 동행자 정산·예약 자료 관리는 구현됐으나, 실경로·이동 시간·자동 실시간 환율·실제 송금·예약 결제·원격 공동 편집·여행 리포트는 아직 제공하지 않습니다. 개발자 계정의 판매자 상태·대상 연령·콘텐츠 등급은 사실을 확인하고 Console에서 신고해야 하며, 무료라는 이유만으로 비판매자라고 단정하지 않습니다.

## 인증과 키 설정

`google-play` GitHub 환경을 `main` 브랜치만 사용할 수 있도록 제한합니다. 이 앱 전용 서비스 계정과 저장소 ID·소유자 ID·`refs/heads/main`·환경을 한정한 WIF를 사용합니다. 다른 앱의 키나 계정을 재사용하지 않습니다.

준비 기준: 프로젝트 `hebi-app-release-80847404` · 번호 `973619009476`, 전용 서비스 계정 `play-tripcanvas@hebi-app-release-80847404.iam.gserviceaccount.com`, WIF 풀 `tripcanvas-github` · 공급자 `github-main`, 저장소 ID `1450869093` · 소유자 ID `80847404`입니다. 실제 계정 생성과 권한 등록 완료 여부는 Console에서 별도로 확인합니다.

| 종류 | 이름 |
| --- | --- |
| Secrets | `ANDROID_RELEASE_KEYSTORE_BASE64`, `ANDROID_RELEASE_STORE_PASSWORD`, `ANDROID_RELEASE_KEY_ALIAS`, `ANDROID_RELEASE_KEY_PASSWORD`, `ANDROID_RELEASE_CERT_SHA256` |
| Variables | `PLAY_WORKLOAD_IDENTITY_PROVIDER`, `PLAY_SERVICE_ACCOUNT` |
| 실행 중 가드 | `PLAY_CONSOLE_INITIALIZED` ← 실제 등록 바이너리를 조회하고 편집을 정리한 스텝의 출력만 사용, 영구 변수를 만들지 않음 |

키는 실행 중 생성하거나 디버그 키로 대체하지 않습니다. 사용자가 승인한 앱별 신규 업로드 키를 비공개로 준비하고 고정 SHA-256 인증서와 일치해야만 사용합니다. 업로드 키·암호 백업은 소유자 전용으로 보관하고 코드나 공개 문서에는 넣지 않습니다. Google Play의 설치용 앱 서명 인증서는 업로드 인증서와 별도로 확인합니다.

## Actions 실행

`Play internal release` → `Run workflow` → `main`을 선택합니다.

| 모드 | 동작 |
| --- | --- |
| `check` | 정확한 main 전체 CI 및 Python 검증 후 WIF로 미커밋 편집에서 앱 등록 목록만 확인, 업로드·트랙 변경 없음 |
| `build` | 앱 전용 키로 AAB·APK를 빌드하고 패키지·버전·서명·실제 네이티브·ZIP 정렬 검사 후 산출물 보관, Play 변경 없음 |
| `initialize` | 빌드 전·제출 직전의 AAB·APK·전체 트랙 버전이 비어 있을 때만 코드 `1`을 내부 `draft`로 제출·검증·커밋·새 편집 재조회 |
| `deploy` | 실제 등록 바이너리를 확인하고 Play 전체보다 높은 코드로 서명 빌드·검증 후 내부 테스트 제출·새 편집 재조회 |

`TripCanvas Android CI`가 같은 저장소의 `main` push에서 성공하면 자동 배포합니다. `android-build`와 `room-migration-instrumentation` 두 job이 정확한 커밋에서 성공해야 합니다. 초기 등록을 수행하는 신뢰한 main 커밋 제목의 독립 토큰 `[initialize-play]`는 해당 실행만 `initialize`로 선택합니다. 커밋 본문·PR·포크·태그·다른 브랜치는 최초 제출을 시작하지 않습니다.

업로드 전 main이 바뀌거나 버전 코드가 다른 제출에 쓰이면 중단합니다. 제출한 AAB의 실제 SHA-256과 Google 응답 해시가 같아야 트랙을 변경합니다. 최종 커밋·재조회가 불확실한 경우 Console 확인 없이 자동 반복하지 않습니다.

최초 초안의 알려진 HTTP 400 `changesNotSentForReview must not be set` 응답만 원문을 출력하지 않고 같은 편집에서 보류 파라미터를 빼고 한 번 재시도합니다. `ERROR_IF_IN_REVIEW`는 유지합니다. 이때 Google의 동작에 따라 Console에 대기 중인 다른 변경도 심사에 전송될 수 있습니다. 다른 실패와 일반 배포에는 이 예외를 적용하지 않습니다.

`Play closed promotion`은 수동 `mode=promote`와 정확한 내부 완료 버전 코드를 요구합니다. 이미 제출된 바이너리를 승격하며 새 파일을 업로드하거나 프로덕션을 변경하지 않습니다.

## 개인정보와 검증 범위

- 소스상 로그인·광고·분석·결제·개발자 서버 업로드 기능은 없습니다. 3-A부터 `INTERNET`·`ACCESS_NETWORK_STATE`를 선언하며, 지도 타일은 OpenFreeMap, 사용자가 직접 검색한 검색어는 Photon(komoot) 서버로 요청합니다.
- 여행·일정·장소·준비물·경비·동행자 이름·분담·환율·예약 확인번호 및 파일 참조는 Room, 테마·온보딩은 DataStore에 로컬 저장합니다. Android 자동 백업은 비활성화되어 있습니다.
- 백업·복원은 Android 시스템 파일 선택기에서 사용자가 지정한 JSON을 읽고 씁니다. 파일은 암호화되지 않으며 예약 확인번호·동행자 이름·정산 정보까지 포함될 수 있습니다. 예약 문서 원본·URI는 제외되며 선택한 외부 저장 서비스의 처리는 그 서비스 약관을 따릅니다.
- 로컬 처리라는 사실은 신고 양식의 모든 질문을 자동으로 확정하는 근거가 아닙니다. 외부 지도·장소 검색 요청(접속 IP·표시 지도 영역·검색어 등)을 포함해 Google Play 데이터 보안 양식과 개인정보처리방침을 현재 포함 SDK 및 실제 동작에 맞춰 재검토해야 합니다.
- 실제 AAB와 APK의 package·versionCode·versionName, 고정 업로드 인증서, 모든 `.so`의 ELF·AAB 설정·APK ZIP 정렬을 검사합니다. 두 파일 모두 네이티브 라이브러리가 없으면 명시적인 `--allow-no-native`에서만 해당 검사를 비적용으로 기록하고 ZIP 정렬 검사는 유지합니다.
- API 커밋과 새 편집 재조회 성공은 Console 게시·실제 설치나 16KB 기기 실행 검증을 대신하지 않습니다.

## 현재 상태

2026-11-09 기준 3-A 기능은 `main`에 병합됐고 Android 빌드와 Room 계측 CI를 통과했습니다. 그러나 이후 Play 자동 배포(https://github.com/hebi10/TripCanvas/actions/runs/42588783906)의 초기 API 검사에서 HTTP 403 `PERMISSION_DENIED`가 발생했고 배포 요청은 수행되지 않았습니다. Console 앱 등록/서비스 계정 권한을 확인하기 전에는 스토어 게시가 완료됐다고 주장할 수 없습니다. 공개 개인정보 URL의 배포 상태와 실제 등록 앱·트랙은 계정 설정 후 확인해야 합니다. 기존 저장소에는 앱 아이콘 원본과 manifest 연결이 없으므로 검수한 브랜드 자산이 확인되면 별도로 연결합니다.
