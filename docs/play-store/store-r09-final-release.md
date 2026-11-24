# STORE-R09-FINAL — 승인된 일회성 Google Play 비공개 테스트 승격

- 기준 Android 앱: `com.hebi.tripcanvas`, `0.1.0`, `versionCode 10`
- 기존 Google Play 내부 테스트: `internal`, `completed` (API commit 및 fresh readback)
- UI 소스 기준: `5fc89e73137c7e80ce694b808c4d480f06457452`
- 스토어 이미지 검수 및 최신 Git 기준: `c86f33690f55b5ec31e06cdcf9a725ce39702a89`
- 기존 AAB SHA-256: `d874c62378b7d2c2bf004e8ca89a5c48b3bd6a9565f05ae784b0ff7927639a60`
- 승인: 2026-11-12 사용자가 TripCanvas를 GalliPick처럼 GitHub Actions로 비공개 테스트까지 직접 배포하도록 요청.
- 최종 승인 대상: 현재 저장소의 검증된 ko-KR 이미지 10개와 이미 internal completed인 versionCode 10을 동일 Google Play edit에서 Alpha completed로 커밋.
- 워크플로 실행 조건: 정확한 일회성 squash subject와 위 기준 main의 직계 부모·허용된 4개 파일 변경·동일 GitHub main 커밋 검증.
- 안전 원칙: 새 AAB/APK 빌드·업로드 금지, 프로덕션 트랙 금지, 기존 심사 취소·우회 금지. API가 in-review 또는 bundle/hash/CI 오류를 반환하면 실패 처리.
- 결과 판정: Google Play edit commit 후 새 edit에서 ko-KR 이미지 순서·SHA 10장, Alpha v10 completed를 재조회한 경우에만 API 배포 완료. Console 심사 승인과 테스터 설치 여부는 별도.
- 원본 GitHub Actions QA: https://github.com/hebi10/TripCanvas/actions/runs/43001755572
- 내부 v10: https://github.com/hebi10/TripCanvas/actions/runs/43002624854
- 일회성 실행 대상: .github/workflows/play-listing-images.yml 의 r09-final job
