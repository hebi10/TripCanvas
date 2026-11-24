# TripCanvas STORE-02 — Android 런처 아이콘·Play 등록정보 자동화

- 프로젝트: `hebi10/TripCanvas` / `com.hebi.tripcanvas`
- 선행: STORE-01 Draft PR #23, 기존 Google Play 비공개 테스트 `0.1.0 (3)` 심사 기록
- 이번 작업은 Play 릴리스 또는 프로덕션 배포 요청이 **아님**.

## Android 런처 아이콘

- 기존 STORE-01 `store-icon-512.png`의 지도+핀 형태에서 선형 검정 로고만 추출한 투명 전경 이미지를 사용합니다.
- Android adaptive icon은 `res/mipmap-anydpi-v26/ic_launcher.xml`과 흰색 배경·투명 전경으로 구성하며 모노크롬 아이콘 리소스를 지정합니다.
- `AndroidManifest.xml`에 `@mipmap/ic_launcher`를 연결합니다. 앱 네트워크, 데이터 저장, 로그인, 광고, 기능 화면, 버전 이름은 변경하지 않습니다.
- 실제 기기 아이콘 표시/런처 마스크 검사는 Android 에뮬레이터와 물리 기기를 통해 별도 확인합니다.

## Google Play 한국어 스토어 이미지

- STORE-01의 `assets/play-store/ko-KR/` 폴더에 있는 승인 대상 10개 PNG와 `manifest.json`의 SHA-256을 검사합니다.
- `scripts/play/listing_images.py`는 기본적으로 **검증만** 수행합니다. Google Play 인증, 이미지 삭제·업로드·수정은 하지 않습니다.
- `.github/workflows/play-listing-images.yml`의 `workflow_dispatch`에서 **main의 `mode=upload`를 명시적으로 선택한 경우에만** `google-play` 환경의 기존 WIF/서비스계정 인증을 사용합니다.
- 업로드 대상은 한국어 `ko-KR`의 `icon`, `featureGraphic`, `phoneScreenshots`(8장) 세 종류로 한정합니다. AAB, 테스트 트랙, 다른 언어, 가격·스토어 텍스트, 광고 신고는 변경하지 않습니다.
- commit은 `changesInReviewBehavior=ERROR_IF_IN_REVIEW`를 사용하여 진행 중인 Google 심사를 취소하지 않고, `changesNotSentForReview=true`로 변경 사항을 임의 제출하지 않습니다.
- 새 편집에서 업로드된 이미지의 SHA-256과 순서를 확인하고, API 커밋 이후 별도 새 편집의 SHA-256과 순서를 확인합니다. Play Console 게시 승인·테스터 노출은 별도입니다.
- 스토어 등록정보에 포함된 테스트 데이터 이름은 실제 Android QA fixture 이름 변경 후 새로운 Android 캡처를 수집하여 **이미지 원본부터** 교체해야 합니다. 합성된 가짜 앱 UI를 만들지 않습니다.

## Play 내부 테스트 배포 방지

- `source.py`는 신뢰된 `main` push에 한해 첫 부모 커밋과 diff를 검사합니다.
- 변경 파일이 `assets/play-store/ko-KR/` 및 허용된 스토어 자료/자동화 파일로만 구성되었다면 `release_mode=skip`으로 안전하게 AAB 빌드와 새로운 내부 테스트 릴리스를 하지 않습니다.
- 앱 소스(Android icon 포함), Play 내부 배포 로직, 서명 키, 기타 코드가 섞여 있으면 **기존 내부 테스트 자동 배포 정책은 그대로 적용**됩니다.
- 이 가드는 main에 포함된 후부터 효력이 있습니다. 먼저 STORE-01을 main에 단독 병합하면 이 가드가 아직 없어 AAB가 생성될 수 있으므로, STORE-01과 STORE-02를 한 번의 안전한 통합 PR로 병합하는 것을 권장합니다.

## QA 단계

1. 로컬 이미지 10개 서명/규격/해시 및 Python 오프라인 테스트 통과
2. PR 커밋의 Android 빌드·Room 계측, 2-C/3-A/3-B 실제 에뮬레이터 화면 캡처 검증
3. 새 스토어용 캡처에서 시험용 여행명·임시 예약 정보가 없는지 육안 검수, 이미지 8장 재패키징 및 해시 명세 갱신
4. STORE-01/02 통합 병합 → `main` 1회 Android CI → 새 앱 바이너리 내부 테스트 배포 결과 확인(런처 아이콘 변경에 따른 합당한 새 버전)
5. 기존 Google Play 심사·게시 상태 확인 후 수동 `mode=upload` 실행 → Google Play API 이미지 재조회 → Play Console 공개 노출 검증

**안전:** 자동 Play 업로드·main 병합·alpha 승격·프로덕션 출시·기존 AAB 덮어쓰기는 수행하지 않습니다.
