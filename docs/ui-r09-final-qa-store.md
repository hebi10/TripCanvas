# UI-R09 — 전체 통합 QA·순차 병합·Google Play 스토어 이미지 갱신

기준일: 2026-11-11 · 프로젝트 TripCanvas · 패키지 com.hebi.tripcanvas.

## 1. 시작 시 실제 검증된 상태

- main 시작 커밋: 7f418fa7a3e365c2d61776bb4ad1e24f630d51b9. UI-R04 PR #29까지 병합되었고 UI-R05 #30, UI-R06 #31, UI-R07 #32, UI-R08 #33은 아직 Draft/미병합.
- Android CI 정확한 main push run #42985859961 성공.
- 내부 테스트 자동 배포 run #42988310716 성공. Google Play API: com.hebi.tripcanvas, versionName 0.1.0 / versionCode 8, internal releaseStatus completed, API commit + fresh readback 통과. 해당 v8은 UI-R05~R08 반영 버전이 아니고 Console 심사/테스터 설치 가능성은 미확인.
- 최근 실제 QA 실패 원인: UI-R05 2-C 휴대전화 \`지출 저장\` 탐색 실패, UI-R06 태블릿 3-A \`지도 선택됨\` 상태 검사 실패(실제 실패 PNG에는 선택 표시 존재), UI-R08 3-B 휴대전화 \`확인번호 숨기기\` 검사 실패. 취소/뒤로/필터/스크롤/선택자·접근성 트리를 원본 PNG/XML과 함께 대조해 실제 기능 오류와 테스트 불안정을 구분해야 함.
- 기존 Play 스토어 이미지 8장은 \`assets/play-store/ko-KR/\`에 있지만 manifest 원본 소스는 UI-R00~R08 통합 이전(2026-11-11, STORE-02 f41633f). 이를 '최신 통합 화면'이라고 게시하면 안 됨.

## 2. 안전한 순차 병합

1. UI-R05 PR #30: 정확한 HEAD Android build/JVM/Room35·2-C phone/tablet·3-A·3-B·API29/36 결과, 경비 화면 수치 KRW 300,000 / KRW 12,500 / 미환산 USD를 실제 PNG에서 확인하고 실패 해결 후 안전 병합.
2. UI-R06 PR #31: Tablet 지도·목록 좌우 2패널(실제 MapLibre/핀·지도에서 선택/좌표 없는 장소/원본 출처)와 Phone Compact, 160% 글꼴. 태블릿 \`지도 선택됨\` 실패 시 Test hierarchy와 실제 컴포저블 렌더를 대조하여 수정. R05 병합 후 최신 main 기준 정확한 diff·CI 재검증 후 병합.
3. UI-R07 PR #32: 예약 날짜 표시, 실제 첨부 n개, 편집 시 확인번호 기본 가림·보기/숨기기, 파일 연결 제거 시 원본 보존, 3-B 전화/태블릿/SAF 동작 검증. R06 안전 병합 후 재검증/병합.
4. UI-R08 PR #33: 여행 생성·편집, 일정·지출 필드 검증, 설정/백업 복원 취소와 Room 데이터 보존, 폰트160/IME/다이얼로그. R07 안전 병합 후 재검증/병합.
5. UI-R09 자체 PR(통합 QA·스토어 이미지 생성 도구): 선행 R05~R08 병합·main CI 성공 후 base를 최신 main으로 조정. PR의 안전성·스크립트 smoke/이미지 규격·SHA 통과 후 병합.
6. 어느 단계든 실패·누락·원본 이미지 검수 미완료면 Draft 유지, Play 에셋 교체/Alpha/프로덕션 승격을 차단한다.

## 3. 통합 Android QA

- 대상 API29, API35, API36; 전화1080×2400/태블릿1600×2560, 라이트·다크·글꼴 160%. 최소 2-C/3-A/3-B 전 시나리오 및 Room migration/backup restore/JVM/Release Compatibility.
- 주요 사용자 흐름: 홈·여행 생성/편집/삭제 확인 → 여행 작업 공간 요약 → 일정 날짜 필터·복제·정렬·삭제 취소 → 장소·지도 날짜/핀·일정 연계 → 준비물 필터·체크·삭제 복구 → 예산·지출·미환산/환율·정산 → 예약/첨부/확인번호 → 설정·SAF 백업 선택·취소/복원 미리보기.
- 각 워크플로는 정확한 PR HEAD / 실제 최신 main SHA와 소스 연결을 대조한다. Runner 성공만으로 APK 설치 가능성·실기기 조작·Play Console 심사 승인이라고 단정하지 않는다.
- CI 로그·원본 screenshots PNG·UIAutomator XML·각 이미지 SHA-256·테스트 상태·머지 SHA를 보존. 검수한 화면과 검수하지 못한 화면(폴더블·실물/회전·TalkBack·물리 URI 권한 만료)을 구분한다.

## 4. 최종 main 원본 화면 기반 스토어 이미지 8장

**안전 규칙**: 통합 PR 모두 병합된 단일 정확한 main SHA에서 2-C·3-A·3-B 성공한 여섯 환경(phone/tablet)에서 캡처한 실제 Android UI만 사용. 현재 구 스토어 이미지와 기준 미확정 새 앱 UI를 혼합하지 않는다.

- 01-home.png: 2-C \`01-home-light.png\` — 여행을 한눈에
- 02-workspace.png: 2-C \`02a-workspace-overview-light.png\` — 여행 작업 공간
- 03-schedule.png: 2-C \`02d-workspace-schedule-route.png\` — 날짜별 일정 관리
- 04-map.png: 3-A \`03-map-all-light.png\` — 저장 장소와 지도
- 05-checklist.png: 2-C \`03-checklist-light.png\` — 준비물 체크리스트
- 06-expenses.png: 2-C \`07-expenses-light.png\` — 예산과 지출 관리
- 07-settlement.png: 3-B \`05-settlement-light.png\` — 동행자 비용 정산
- 08-bookings.png: 3-B \`08-bookings-light.png\` — 예약 자료 한곳에

- 생성 도구: \`scripts/play/build_from_verified_qa.py\`. Pillow는 스토어 이미지를 오프라인으로 제작하는 도구에만 사용하며 Android 앱 의존성은 아님. 휴대전화 원본 UI 화면은 리터칭·크롭·가짜 UI 합성을 하지 않고 외부에만 흑백 제목/설명·테두리를 배치.
- 정확한 main SHA와 세 개 실제 phone QA 아카이브의 SHA-256 및 GitHub Actions 성공 run ID가 같은 evidence JSON에 있어야 변환 허용. 1350×2400 RGB 8장, 기존 검증된 icon512 RGBA/feature1024×500 RGB 유지.
- \`.github/workflows/ui-r09-verified-screens.yml\`은 진짜 최종 main push의 승인 마커 \`[tripcanvas-ui-r09-capture]\` 또는 main에서 수동 workflow_dispatch인 경우에만 2-C/3-A/3-B phone+tablet 총 6개 QA를 실행. 여섯 잡 모두 성공한 경우에만 phone 원본을 기반으로 새 8장 이미지·증빙·manifest.json을 **검수용 artifact**로 생성. 별도의 PR 검사에서는 합성 테스트 PNG로 렌더러 코드만 smoke 검증하므로 이를 실제 UI로 발표/배포하지 않는다.
- 스토어 최종 유효 이미지 10장(icon/feature/phone8)은 \`scripts/play/verify_listing_assets.py\`와 \`scripts/play/listing_images.py\`의 규격·순서·SHA 검증 통과, 화면별 최종 시각 검수 후 별도 스토어 이미지 전용 PR로 main에 반영.
- 스토어 이미지 전용 main 변경으로 추가 AAB 재배포가 불필요하게 발생하지 않도록 기존 \`scripts/play/source.py\`의 store-only guard 및 Play internal workflow 조건을 검증한다. 이미지 업로드는 **자동 push 아님**. 검수 완료 후 기존 \`TripCanvas Korean Play listing images\` 수동 \`mode=upload\`만 이용하고 Google Play image edit commit 및 새로운 edit readback으로 SHA/order 확인. Play 심사/설치 가능 여부는 별도 상태.

## 5. 동작·저작권·보안 범위

- 실제로 제공하는 화면만 소개. 계정 동기화·실제 송금/예약 구매/실시간 환율·경로 안내 등을 현재 기능처럼 홍보하지 않는다.
- 테스트 데이터/예약 확인번호/비밀번호/민감한 원본 메모가 스토어 이미지에 노출되지 않았는지 원본과 최종 이미지 각각 육안 확인. 자동 OCR은 사용하지 않음.
- Play 이미지 업로드 시 한국어 ko-KR icon·featureGraphic·phoneScreenshots 8개 외 수정 금지. Google Play 기존 심사·closed alpha/production/서명키·app content·판매자 신고 미변경.
- 3-B 예약 편집의 테스트용 예약 확인번호는 목록/스토어 이미지에 표시하지 않는다. 화면 프레임 외부에만 텍스트 추가.

## 6. 자료 및 결과 보관

- Google Drive \`내 드라이브 → 나만의 앱 → 10_TripCanvas_여행관리 → 04_검증_및_보완\`.
- 이전 UI-R00~R09 계획: https://docs.google.com/document/d/1sOjzZKCaVuHxFeCRVQnKlFp7jZd9dc-i0sYFev9uRKk/edit
- UI-R08 PR #33 https://github.com/hebi10/TripCanvas/pull/33
- Google Play listing 기존 workflow: \`.github/workflows/play-listing-images.yml\` / \`scripts/play/listing_images.py\`
- 현재 검증된 internal v8 workflow: https://github.com/hebi10/TripCanvas/actions/runs/42988310716

## 7. 완료 판정과 보고 형식

매 단계마다 GitHub PR 상태(merged SHA), 정확한 CI 전체 5종 및 6환경 최종 main, Android 원본 PNG 위치/시각 결과, 스토어 8 PNG 출처/SHA, 실제 Play ko-KR 이미지 API readback 및 검수하지 못한 영역을 각각 표시한다.

**현재 결론**: UI-R09 제작 도구·안전 워크플로를 개발 중. 선행 UI-R05~R08 미병합이므로 Play 스토어 기존 이미지는 변경하지 않는 것이 안전함.
