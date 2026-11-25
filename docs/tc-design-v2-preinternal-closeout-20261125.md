# TC-DESIGN-V2 — 내부 테스트 직전 마감 검증

기준일: 2026-11-25 KST. 패키지: com.hebi.tripcanvas.
기준 main: 956c1988fdd358c21179eb97c3b0f60519d3aec6 (#86 병합).
이 문서는 검증 계획 및 현재 확인 상태이다. 후보 PR 검사 전에는 완료 증명으로 사용하지 않는다. Play 변경·배포 승인이 아니다.

## 1. 기존 디자인 개선 증빙

| 범위 | 병합 PR | 검증 근거 |
|---|---|---|
| S00~S04, S07-A, S09, S11~S13-A 핵심 | #80 | [실제 화면 QA](https://github.com/hebi10/TripCanvas/actions/runs/45553090563) |
| S06-A/B, S05/S06-C/S08 기존 동작 | #82 | [장소 QA](https://github.com/hebi10/TripCanvas/actions/runs/45556109787) |
| S05/S06-C 반응형 입력 | #83 | [폼 QA](https://github.com/hebi10/TripCanvas/actions/runs/45561439555) |
| S10-A/B/C | #84 | [지출/실제 OCR QA](https://github.com/hebi10/TripCanvas/actions/runs/45563011358) |
| S13-B/C, S14-A/B | #85 | [예약/동행자 QA](https://github.com/hebi10/TripCanvas/actions/runs/45564477060) |
| S15~S18 | #86 | [여행 기록 QA](https://github.com/hebi10/TripCanvas/actions/runs/45568609550), 12 화면 및 진단 1장, 모두 1080×2400/420dpi |

기획 [v2](https://docs.google.com/document/d/1VuiBjt8A_m3Bn89tPI-ZMIklx4O4US-VtKN5Ll7u4ys/edit)의 44개 기본 프레임은 37개 구현 기반과 7개 후속 개념으로 분류한다. 기존 S02-B, S06-D 제한 상태, S07-B 제한 상태, S21-B 공통 오류/빈 화면은 기존 화면 및 최종 회귀 범위다. 화면군을 독립 라우트 44개로 간주하지 않는다. 생성 원본 시안 미확보로 실제 이미지와의 직접 1:1 일치 판정은 미완료.

## 2. 최종 후보 구현·검증 대상

- S20-A/B 설정: .tcb/.tcm/.json 구분, 실제 빌드 versionName/versionCode, 조회된 월/연 구독만 표시, 실제 가격은 Play 응답값. 운영 상품/결제 서버 확인을 대신하지 않는다.
- S20-C/D/E/F: 암호화 우선 및 평문 경고, 비밀번호 길이/일치 안내, 실제 .tcm 후보 검증·복원 취소 후 기존 DB 보존, 안전 백업 및 정리 취소, 다크 160%. 테스트 자료만 사용.
- S07-A: #81의 독립 잔여 작업(배경 타일 파싱 확인, 지도 수동 재조회, 중복 destroy 방지)을 최신 main에 반영. 기존 핀·선택·카메라·공급자 보존. 실제 지도 재조회 후 선택/카메라 회귀. #81의 오래된 중복 코드를 그대로 병합하지 않는다.
- S17/S18 사진 후속: 테스트 소유 실제 이미지 content URI 미리보기, 실제 ZIP 생성·원본 바이트와 DB 보존·접근 URI 미포함 검증. 선택 결과를 제공하는 테스트용 ActivityMonitor와 실제 외부 파일 선택기 조작은 구분한다.
- 후보 QA: API35 Pixel6, 1080×2400/420dpi, 설정/백업/사진 필수 PNG 22장 및 지도 재조회 증빙. QA-A06 실제 content URI 백업 복원/원본 ZIP 검증. 기본 Android CI, OPS-07/09, 호환성 필수. 후보 성공 전 병합 금지.

## 3. 최종 main QA 및 AAB 절차 (아직 미실행)

1. 후보 최신 HEAD의 모든 필수 검사와 원본 증빙 확인 → 안전 병합 → 병합 main의 Android CI/OPS-08 성공 확인. 기존 #81을 대체 종료하고 작업 기록 갱신.
2. [TripCanvas final Android QA](https://github.com/hebi10/TripCanvas/actions/workflows/final-device-qa.yml)를 main에서 정확한 최신 40자 expected_main_sha로 수동 실행. Room/migration, API29/35/36, 폰/태블릿 실제 화면·핀·정산·예약·백업 A06, 새 설치 첫 여행, OCR 포함. 화면 해상도 및 밀도 실제 측정. 실패 또는 SHA 변경 시 빌드 차단.
3. 실제 Play 전체 versionCode 인벤토리 읽기 및 서명 상태 확인 후 중복되지 않는 version_code 선정. [OPS-03 Signed AAB build request](https://github.com/hebi10/TripCanvas/actions/workflows/release-build.yml)에서 정확한 main SHA, KST batch_id, version_code, 최종 범위 및 QA 성공이 확인된 경우에만 final_batch_approved=true. 하위 build-only 실행의 AAB/해시/패키지/서명/버전 검증. 승인 조건이 충족되지 않으면 실행 금지.
4. 위 단계의 성공 후 **Play 업로드 직전 중지**. 이번 요청은 internal 업로드·initialize·deploy·promote·Play 등록정보 수정 승인이 아니다. internal-release-once.yml은 내부 업로드까지 포함하므로 실행하지 않는다.

## 4. 별도 미해결 게이트

- [#48 전체 로드맵](https://github.com/hebi10/TripCanvas/issues/48)의 공동 여행/계정/동기화/AI 등 운영 기능 및 [#50 실제 경로 공급자](https://github.com/hebi10/TripCanvas/issues/50)는 공급자·인증·사용자 동의·비용/보안 운영 승인 없이 구현 완료로 판정하지 않는다. v2의 S14-C/D/E/F/G, S19, S21-A는 후속 개념이다. 실제 거리·시간 또는 연결 상태를 가짜 데이터로 표시하지 않는다. 기존 전체 범위의 제외가 확정되지 않았으므로 전체 P1~P4 완료 판정은 보류.
- [#62 광고·구독](https://github.com/hebi10/TripCanvas/issues/62)의 실제 AdMob ID 설정·상품 활성화·서버 구매 검증·테스터 결제는 현재 확인되지 않았다. 비밀 값 접근/출력 및 상품 변경/실결제 금지. 운영 수익화 완료로 표시하지 않는다.
- [#46 실기기 QA](https://github.com/hebi10/TripCanvas/issues/46)의 물리 폴더블/TalkBack/200%/실제 외부 SAF 권한 지속·Play 설치/업데이트는 에뮬레이터 테스트로 대체하지 않는다. 실제 내부 테스트에서 검증할 범위와 사전 검증 범위를 분리하고, 비공개·정식 출시 전 확인한다.
- 원본 시안 직접 대조·최종 main QA·서명 AAB·Play 현황 확인은 완료 증거가 확보되기 전에는 각각 미완료다. 현재 GitHub 연결은 workflow_dispatch 신규 실행 기능을 제공하지 않는다. 수동 실행 화면 또는 권한 있는 Work 환경에서 동일 승인 게이트를 유지하여 실행한다.
