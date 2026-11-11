# TripCanvas | STORE-01 · 한국어 스토어 이미지

2026-11-11 · `com.hebi.tripcanvas` · 기존 앱 0.1.0 (versionCode 3) 기준

## 결과물
- Google Play 스토어 아이콘: `store-icon-512.png` / 512×512 / RGBA PNG
- 대표 그래픽: `feature-graphic-1024x500.png` / 1024×500 / RGB PNG
- 설명 이미지 8장: `01-home.png` ~ `08-bookings.png` / 각 1350×2400 / RGB PNG
- `manifest.json`: 파일별 원본·규격·크기·SHA-256
- `preview-contact-sheet.jpg`: 검수용 미리보기 (Google Play 제출 대상 아님)

## 소스와 정확성
- 기본 캡처 원본 7장은 기존 Google Drive `91_스토어_자료`에서 확인한 Android 실기동 QA 결과입니다.
- 지도 캡처는 2026-11-10 PR #20 3-A 지도 Android 휴대전화 UI QA 압축파일의 `screenshots/qa3a/03-map-all-light.png`를 사용했습니다.
- Android 화면 내 문구, 기능, 정보, 지도는 이미지 내에서 교체·합성하지 않았습니다. 배경·제목·설명은 앱 UI 바깥에서만 추가했습니다.
- `QA 제주 여행` 및 `QA 제주 여행 3A`는 **기존 QA 캡처에 실제 포함된 시험용 여행명**이므로 그대로 남아 있습니다. Play 제출 전 일반 사용자 시나리오로 실제 캡처를 다시 찍어 교체하는 개선이 권장됩니다.
- 실시간 공동 편집, 길찾기, 실시간 자동 환율, 실제 예약·결제 등 미구현 기능을 주장하지 않습니다.

## 업로드 순서
1. 홈 — 여행을 한눈에
2. 작업 공간 — 여행 정보 한곳에
3. 일정 — 날짜별 일정 관리
4. 지도 — 저장한 장소를 지도에서
5. 준비물 — 준비물 체크리스트
6. 경비 — 예산과 지출 관리
7. 정산 — 동행자 비용 정산
8. 예약 — 예약 자료 한곳에

## 배포 경계
- 이 ZIP은 이미지 산출물이며 Google Play 등록정보 업로드·심사·게시가 완료되었다는 의미가 아닙니다.
- 기존 비공개 테스트 심사·AAB·서명키를 변경하지 않습니다.
- 다음 단계 STORE-02: Android 런처 적응형 아이콘과 별도 Play 등록정보 업로드 자동화(검증→`ko-KR` 이미지 한정 적용→재조회)를 별도 PR로 구성합니다.

원본 폴더: https://drive.google.com/drive/folders/1SeA4Qj0JhVW-7NDKM4jdPS5HZzaibbmW
