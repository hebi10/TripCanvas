# QA-A06 — 전체 화면·접근성·데이터 복원 통합 QA

검토 기준: 2026-11-12 · 패키지 `com.hebi.tripcanvas` · GitHub main `a86e5d27b167a4a0e04889d5eebb9049889f7e76`.
기획 근거: [Google Drive UI-R00~R09 화면 정합성 보고서](https://docs.google.com/document/d/1sOjzZKCaVuHxFeCRVQnKlFp7jZd9dc-i0sYFev9uRKk/edit), [UI-R09 최종 QA](https://docs.google.com/document/d/1rBQtg4K_o6egU-iseDyrYjSctNreovw9TLk9gdVG0TE/edit).

## 1. QA 기준선과 Play API

- UI-A01 PR #38 / A02 PR #39 / A03 PR #41 / A04 PR #42 / A05 PR #43 **순차 main 병합 완료**. UI-A05 main SHA `a86e5d27b167a4a0e04889d5eebb9049889f7e76`.
- UI-A05 통합 PR HEAD `ac4b272334312507cddf1ca1f1f1b52aa5531670`의 5개 필수 워크플로 **전체 success**: Android/JVM/Room [#43176951460](https://github.com/hebi10/TripCanvas/actions/runs/43176951460), 2-C phone/tablet [#43176951281](https://github.com/hebi10/TripCanvas/actions/runs/43176951281), 3-A MapLibre phone/tablet [#43176951372](https://github.com/hebi10/TripCanvas/actions/runs/43176951372), 3-B 정산·예약 phone/tablet [#43176951243](https://github.com/hebi10/TripCanvas/actions/runs/43176951243), API29/36·정책 [#43176951264](https://github.com/hebi10/TripCanvas/actions/runs/43176951264).
- UI-A05 통합 HEAD의 원본 Android PNG **201장** SHA-256 manifest 대조 성공. 휴대전화·태블릿 라이트/다크·160% 원본 검수, 2-C 계측 XML 실패 0건.
- [main CI #43179301621](https://github.com/hebi10/TripCanvas/actions/runs/43179301621): 2026-11-12 최초 조회 시 빌드 성공, Room API35 계측 진행 중. **이 통합 QA 시작 시 Play internal release의 신규 실행/버전 확인 불가**. 사용자가 완료했다고 안내했더라도 Google Play API 신규 `versionCode`·`internal completed` 및 fresh readback 증거 없이 게시 성공이라 기록하지 않음.
- 이전 **마지막 검증된 Play internal v0.1.0 (14)**: [#43177564452](https://github.com/hebi10/TripCanvas/actions/runs/43177564452), `track=internal`, `status=api_committed_and_verified`; 새 UI-A05 AAB와 구별. 비공개 Alpha·프로덕션 자동 승격 없음.
- Google Play Console의 Google 검토 결과/테스터 opt-in/실제 기기 설치는 API 등록 성공과 별도로 확인.

## 2. 전체 화면·기능 자동 QA 추적

| 영역 | 기존 실제 CI 근거 | 추가 확인 |
|---|---|---|
| 홈/여행 작업 공간 | 2-C phone/tablet; 160% | D-day·상태 필터·상단 빠른 실행, 큰 글꼴 가로 넘침 |
| 여행 일정 | 2-C, JUnit 날짜/중복/재정렬 | 타임라인·날짜 칩·저장/복제/충돌/삭제 |
| 준비물 | 2-C, JUnit 진행률 | 0/25/33/100%, 체크 해제·삭제·복구·순서 |
| 경비·정산 | 2-C, 3-B, Finance Booking Integration | 0/4/100/120%, 외화 미환산 제외, 정산 확인·내역 변경 지문 무효화 |
| 지도·장소 | 3-A MapLibre phone/tablet | 선택 링·핀/카메라 이동·날짜 필터·좌표 없는 장소·실제 네트워크 실패 |
| 동행자·예약·첨부 | 3-B phone/tablet | 송금 방향·합계, 예약번호 마스킹, 삭제 확인/취소, 첨부 링크·파일 선택기 취소 |
| 설정·백업/복원 | 2-C, BackupRestoreTest | UTF-8 파일 왕복, preview 미변경, 전체 교체 확인, 이전 버전 호환 |
| API 호환성 | API29/35/36 CI | 160% 시작 화면·다크·가로 오버플로·OS별 권한 |

자동 검증의 UI는 실제 Android 에뮬레이터 기반. 2-C/3-A/3-B는 UIAutomator 및 실 PNG/파일 해시가 근거이며, **기기별 모든 수동 조작을 인증하지 않음**.

## 3. QA-A06 추가 자동 테스트

- 새 `TripCanvasA06BackupIoTest.kt`는 Android API35의 실제 **MediaStore content:// URI**를 생성하여 `TripBackupService.export(uri)` → 파일 UTF-8 readback → `preview(uri)` → Room 전체 교체 `restore(snapshot)`까지 검증.
- 여행·장소·시간 있는 일정·기본통화 예산·외화+1320 환율·2인 균등 분담·정산 확인 fingerprint·준비물·예약 확인번호를 하나의 synthetic fixture에 포함.
- JSON에는 예약 확인번호가 **포함됨**(백업 암호화 미적용 사실을 드러냄). **첨부 원본/URI/파일명은 JSON에서 제외**되어야 하며 복원 뒤 재첨부가 필요하다는 계약을 검증.
- 손상되거나 지원하지 않는 백업 내용은 `preview`에서 거부되고, 기존 Room 데이터가 변경되지 않는지 검증. 임시 MediaStore 자료는 테스트 종료 시 삭제.
- 전용 CI: [QA-A06 백업 무결성 워크플로](https://github.com/hebi10/TripCanvas/actions/workflows/qa-a06-backup-integrity.yml). 두 개의 테스트를 API35 Android 에뮬레이터에서 수행하고 계측 원본 아티팩트를 저장. 기존 Android CI의 migration + backup 테스트도 계속 유지.
- 실제 Android SAF `OpenDocument`로 **외부 PDF 제공자의 장기 URI 권한 취소·파일 이동·기기 재시작**까지 확인한 것은 아님. MediaStore 내부 테스트 문서는 실제 외부 타사 PDF 제공자를 대체하지 않음.

## 4. 수동 실기기 필수 테스트 (자동 완료로 표시 금지)

1. **폴더블 실기기**: 접힘↔펼침, 1/2패널 전환, 멀티윈도우·회전, 입력 중 상태 보존, 하단 버튼 노출/중첩 없음.
2. **TalkBack·확대**: 홈/일정/지도/경비/예약/백업 화면 포커스 순서, 아이콘/더보기/삭제 버튼의 명확한 음성 이름, 48dp 터치 타깃, 160%·200% 글꼴/고대비 접근성. 경고·오류·확인 상태 읽힘.
3. **실제 SAF PDF·이미지**: 다운로드/외부 제공자 PDF 선택→앱 재시작→열기, 공급자 권한 취소/원본 이동/삭제→'접근 불가·다시 첨부' 안내, 목록에서 예약 번호·파일 URI 비노출, 예약 삭제 후 원본 파일 보존.
4. **JSON 백업 개인정보/복원**: 다른 기기로 UTF-8 JSON 이동, 복원 전 내용 확인/취소/승인, 다른 여행 중복 ID/금액·정산·확인번호 보존, 첨부 링크 재연결, 기존 내용 교체 전 백업 권고. JSON은 **평문이므로 암호·클라우드 공개 링크 없이 보관**.
5. **지도 네트워크**: 비행기 모드/지하철/지도 타일 요청 실패 시 앱 충돌 금지·장소 목록 이용, Photon 공개 데모 서버 한도/운영용 공급자 정책 재검토.
6. **Google Play 테스터 설치**: 최신 internal `versionCode` 실제 opt-in 계정 설치, 설치/업데이트/앱 재시작 후 기존 Room 데이터 보존, 서비스 정책·신고·심사 상태 확인.

미확인 항목은 **실행 필요**로 유지하며 자동 CI 성공을 실기기 인증으로 확대 해석하지 않는다.

## 5. 병합/출시 게이트

- QA-A06 PR에서는 **프로덕션 기능 코드·DB 스키마·결제·지도 공급자·서명·Play 트랙을 변경하지 않음**. 테스트/CI/문서만 추가한다.
- 새 PR HEAD Android/JVM/AndroidTest 컴파일·Room API35, 2-C phone/tablet, 3-A phone/tablet, 3-B phone/tablet, API29/36, 전용 A06 MediaStore 백업·손상 복원 CI 모두 success, 원본 계측/PNG 검증 후 안전 병합.
- QA-A06 병합으로 기존 `main` Android CI → Play internal 자동 배포가 시작될 수 있으므로 새로운 AAB/Play API 결과는 별도 검증. 테스트-only 병합에 불필요한 Play release가 발생하는지 `scripts/play/source.py` 적용 범위도 확인하고 중복 바이너리 배포를 피한다.
- 사용자의 운영 지시: 신규 Actions는 **실행 링크 안내 후 반복 조회하지 않음**. 사용자 보고 후 최신 결과 검수·필요 수정/조건부 병합 순서로 재개.
- Google Play 프로덕션 또는 비공개 Alpha 변경은 QA-A06 범위 밖.

## 다음 단계

`RELEASE-A07 — 최신 internal 테스터 설치·실기기/접근성 최종 확인 및 Google Play 출시 게이트`.
