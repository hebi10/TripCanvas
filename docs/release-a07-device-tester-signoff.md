# RELEASE-A07 — 실기기·접근성·파일 권한·Google Play 테스터 최종 검증

**기준일**: 2026-11-12 · 패키지 `com.hebi.tripcanvas` · 기준 `main=a86e5d27b167a4a0e04889d5eebb9049889f7e76` (UI-A05).
**실제 Google Play API 검증**: [UI-A05 Play internal release #43180204475](https://github.com/hebi10/TripCanvas/actions/runs/43180204475) = `versionName=0.1.0`, `versionCode=15`, `track=internal`, `releaseStatus=completed`, `status=api_committed_and_verified`, AAB SHA-256 `3a25d3c4bf9c9e8963c976f814f66ca6bcc65706a8c07da1e139af18e0b247dc`.

이 문서는 **실기기에서 실행한 항목에만 PASS를 기록**합니다. GitHub 에뮬레이터 CI 성공, Play API 등록, 테스트 참여 링크 생성은 물리 기기 설치 성공·Google Console 게시/심사 승인·TalkBack 사용 가능을 자동으로 입증하지 않습니다. 개인정보·계정 이메일·예약 확인번호·민감한 백업 JSON은 공개 GitHub 아티팩트에 업로드하지 않습니다.

## 1. 선행 CI/Play 승인 기준

| 항목 | 출처 | 상태(2026-11-12 작성 시점) |
|---|---|---|
| UI-A05 병합·실제 Android 화면·통합 HEAD CI | PR #43, main `a86e5d27...` | 완료 |
| UI-A05 최신 Play internal AAB API 등록·fresh readback | Actions #43180204475 | v15 완료 |
| QA-A06 실제 Android content URI 백업·복원 | [PR #44](https://github.com/hebi10/TripCanvas/pull/44) | **재검증 필요**: 첫 실행에서 줄 연결 오류, 수정 커밋 `36ecc588` |
| RELEASE-A07 기존 internal v15·AAB 해시 read-only 재조회 | [RELEASE-A07 workflow](https://github.com/hebi10/TripCanvas/actions/workflows/release-a07-verification.yml) | main 워크플로 실행·검증 필요 |
| 물리 기기 설치·접근성·파일 제공자 QA | 아래 실기기 시나리오 | **미검증** |

**주의:** RELEASE-A07은 Play internal 상태 검증용입니다. `alpha` 승격·프로덕션 출시·테스터 이메일 등록·기존 review 취소는 승인/실행하지 않습니다.

## 2. Google Play 실제 테스터 설치·업데이트

테스터로 승인된 Google 계정이 기기의 Play 스토어에 로그인되어 있는지 확인합니다. 테스트 이메일이나 계정 식별자를 공개 저장소에 적지 않습니다.

1. 기기의 브라우저에서 **[TripCanvas 내부 테스트 참여 링크](https://play.google.com/apps/internaltest/4701279386864949903)** 열기.
2. 테스트 참여 화면에서 `테스터 참여` 또는 `Become a tester` 동의를 진행하고, Google Play의 앱 설치 링크로 이동. 계정 승인/Play Console 트랙 노출 상태에 따라 UI가 다를 수 있습니다.
3. Play 스토어를 통해 `com.hebi.tripcanvas`을 설치하고 직접 실행. **APK 파일 직접 설치는 Google Play 테스터 경로 검증의 대체가 아닙니다.**
4. Play Console 내부 테스트 화면 또는 선택된 앱의 설치 정보에서 대상 릴리스가 **0.1.0 (15)**인지 대조. 기기 앱 정보가 versionName만 보여 주면 versionCode까지 검증했다고 표시하지 않습니다.
5. **업데이트 시나리오**: 기존 v14 설치본의 여행·준비물·경비·예약 테스트 데이터를 남긴 상태로 Play 스토어에서 v15 업데이트 → 데이터 잔존·Room 마이그레이션·앱 실행 확인. 신규 설치 성공만으로 업데이트/데이터 보존 성공을 주장하지 않습니다.
6. 네트워크 연결 없이 재실행하여 로컬 여행 목록·편집 기능이 접근 가능한지 확인. 온라인 지도/장소 검색의 연결 오류는 로컬 기능 장애와 구분합니다.

**Windows/ADB 선택형 확인:** Android Studio는 필요하지 않으며 Android platform-tools의 `adb`가 있는 경우에만 다음 읽기 전용 명령을 사용할 수 있습니다. GitHub 저장소의 `scripts/release-a07-device-probe.ps1`를 받아 USB 디버깅 기기 한 대를 연결한 뒤 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\release-a07-device-probe.ps1 -ExpectedVersionCode 15
```

`versionCode`와 `installer=com.android.vending` 여부만 진단하며, 결과 JSON에는 테스트 계정/기기 시리얼/앱 내용이 들어가지 않습니다. Play 설치 경로를 최종 인증하거나 테스터 가입/Google 심사 승인까지 확인하는 도구는 아닙니다. `adb`가 없다면 Play Console 화면과 실제 기기 설치 화면을 기준으로 수동 검증하고 해당 코드번호 확인 가능 여부를 구분합니다.

- [ ] A07-PLAY-01 테스터 계정 참여/권한 확인
- [ ] A07-PLAY-02 Play 스토어 경유 v15 실제 설치/실행
- [ ] A07-PLAY-03 v14→v15 업데이트 후 로컬 데이터 보존
- [ ] A07-PLAY-04 Play Console의 릴리스 상태와 테스터 접근 확인

## 3. 폴더블·화면 밀도·접근성

**물리 기기**: 모델/Android 버전만 기록하고 IMEI·시리얼·계정 정보 등은 기재하지 않습니다. 실제 폴더블이 없으면 `BLOCKED — physical foldable unavailable`을 기록합니다.

- [ ] A07-FOLD-01 접힘/펼침 중 홈·여행 목록·일정·지도에서 현재 여행과 날짜 선택 유지
- [ ] A07-FOLD-02 지도의 1열↔2패널 전환 시 장소 선택 링/요약/상세 버튼 동작, 터치 가능 영역 정상
- [ ] A07-FOLD-03 가로 회전·분할 화면·멀티윈도우에서 폼 입력/저장·화면 복원, 오버플로 없음
- [ ] A07-TYPE-01 라이트·다크, 글꼴 100%·160%·200%에서 제목/금액/버튼/메뉴 잘림·겹침 확인
- [ ] A07-A11Y-01 실제 TalkBack으로 홈→여행→일정→준비물→경비→정산→예약→백업 화면 순서대로 포커스 이동
- [ ] A07-A11Y-02 더보기·선택 핀·삭제·다이얼로그 버튼의 음성 이름, 선택/해제/경고·진행률 상태 읽힘
- [ ] A07-A11Y-03 터치 타깃 크기(48dp 권장), 스와이프 없이 키보드·접근성 탐색 가능한 경로, 뒤로가기 일관성
- [ ] A07-A11Y-04 예약 확인번호는 목록·백업 UI 캡처에 원문 노출 금지, 편집 화면에서 명시적 보기/숨기기 동작

자동 UIAutomator는 TalkBack 음성 출력·포커스 순서 전체를 인증하지 않습니다. 실제 TalkBack 시연과 결과를 별도 기록합니다.

## 4. Android SAF·PDF·이미지 권한 검증

실제 `OpenDocument`를 지원하는 외부 파일 앱(기기 기본 파일 선택기·문서 제공자)에서 **민감하지 않은 테스트용 PDF 및 PNG**를 선택합니다.

- [ ] A07-SAF-01 예약 생성 → PDF·이미지 각각 첨부 → 미리보기 또는 외부 뷰어로 열기
- [ ] A07-SAF-02 앱 종료/재시작 후 기존 파일을 다시 열 수 있음(지속적 읽기 권한 검증)
- [ ] A07-SAF-03 원본 이동/삭제 또는 문서 제공자 접근 제한 시 사용자에게 다시 연결 안내, 앱 크래시·예약 메타데이터 삭제 없음
- [ ] A07-SAF-04 첨부 연결 제거/예약 삭제 시 **외부 PDF·이미지 원본은 삭제되지 않음**
- [ ] A07-SAF-05 JSON 백업 후 다른 기기에서 복원하면 첨부 URI·파일명·실물 문서 제외, 새 문서 연결 안내
- [ ] A07-SAF-06 외부 PDF 뷰어 부재, 파일 접근 권한 만료, 문서 이동/삭제 각각 오류 안내 확인

MediaStore 테스트는 파일 I/O/Room 복원을 검증하지만 제삼자 문서 제공자 URI 권한 만료·취소와 동일하지 않습니다.

## 5. 개인정보·백업 복원·데이터 보존

**백업 JSON은 평문**이며 예약 확인번호·동행자 이름·메모가 포함될 수 있습니다. 시험용 임의 데이터만 사용하고 실제 사용자 백업을 공개 저장소로 전송하지 않습니다.

- [ ] A07-RESTORE-01 UTF-8 JSON 내보내기 → 재열기/미리보기 → 취소하면 기존 데이터 불변
- [ ] A07-RESTORE-02 손상 JSON/지원 외 버전 거절, 기존 여행 데이터 불변
- [ ] A07-RESTORE-03 전체 복원 승인 후 일정·준비물 체크 상태·예산·외화·정산 지문·예약 확인번호 유지
- [ ] A07-RESTORE-04 원본 PDF/이미지·접근 URI는 백업/복원되지 않음(첨부 재연결 안내)
- [ ] A07-RESTORE-05 백업 암호화 미적용 사실을 확인하고 파일 공유·클라우드 공개 업로드를 차단하는 운영 문구 확인

## 6. 릴리스 승인표 (빈 항목은 통과 아님)

| 검사 | 환경 | 현재 판정 | 증빙 |
|---|---|---|---|
| Play internal v15 AAB API 등록 | GitHub/Play API | PASS (2026-11-12) | #43180204475 |
| RELEASE-A07 재조회/해시 일치 | main, Play WIF | PENDING | A07 workflow JSON |
| QA-A06 실제 content URI·복원 | API35 에뮬레이터 | PENDING | PR #44 checks |
| Play 테스터 실제 설치·업데이트 | 물리 Android | NOT TESTED | 테스트 계정 비노출 화면/기록 |
| TalkBack·폴더블·200% | 물리 Android | NOT TESTED | 화면/사용자 수동 결과 |
| 외부 PDF 권한·재시작·만료 | 물리 Android + 파일 제공자 | NOT TESTED | 수동 결과 |
| 전체 릴리스 판정 | 통합 | **BLOCKED** | 위 필수 항목 모두 PASS 후 갱신 |

증빙 기록 권장 필드: `검사ID | 날짜(KST) | 기기 모델 | Android 버전 | 앱 versionCode | PASS/FAIL/BLOCKED | 원인·재현 경로 | 비민감 스크린샷 파일명`. 개인 이메일·확인번호·IMEI·JSON 내용은 기록하지 않습니다.

## 7. 실패 처리·후속 작업

- 실제 Crash/데이터 손실/예약번호 노출/테스터 설치 불가가 발생하면 **RELEASE-A07-FIX PR**로 분리하고, 원인·검증 완료 이전 `alpha`/production 승격 금지.
- QA-A06와 A07은 각각 독립 Draft PR에서 자동 검증하며, **QA-A06 최신 HEAD 전체 성공→병합→A07 브랜치 최신 main 정합화→A07 Checks 성공→PR 병합** 순서.
- A07 PR 병합 후 Google Play 재조회 워크플로는 `workflow_dispatch`에서 **최신 main만** 대상으로 실행. 업로드·배포·테스터 등록 작업이 아니며, 실행 성공도 실제 설치 완료를 뜻하지 않음.
- 새 Actions는 사용자가 요청한 대로 반복 조회 없이 링크만 제공. 실기기 미검증이 남아 있는 동안 최종 릴리스 통과를 주장하지 않습니다.
