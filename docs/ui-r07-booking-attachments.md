# UI-R07 — 예약 자료·첨부 화면 리디자인

기준: TripCanvas Google Drive 0-B 상세 화면 기획 S13, UI-R00~R09 단계별 개선 계획(2026-11-11), 기존 3-B 예약 구현과 Android 3-B 실화면.
시작 기준: UI-R06 PR #31 HEAD `9bf669cf6114c41e4a73ccaf0f423b066fcb9da7`. PR #31은 Draft이며 선행 UI-R04 #29, UI-R05 #30 최종 QA·순차 main 병합 대기.

## 구현 변경

1. 예약 목록(TripBookingsScreen)
   - 상단 예약 개수·기기 저장 안내는 유지하고 '예약 추가' 1차 CTA를 위로 배치.
   - 카드 정보 위계: **예약 종류 → 예약명 → 예약 일정 → 첨부 상태 → 확인번호 등록 여부 → 편집/삭제**.
   - 기존 `BookingType`과 실제 `BookingRepository.attachments(id)` Flow를 재사용하여 파일 연결 개수를 표시(0개는 '첨부 없음'). 목록에 예약 확인번호 원문이나 첨부 URI를 노출하지 않음.
   - Android 로컬 시각 문자열을 UTC 등으로 재해석하지 않고 `yyyy.MM.dd (요일) HH:mm`로 표시. 날짜 누락/잘못된 기존 데이터는 '일시 미설정/확인 필요'로 안전하게 안내.
   - 160% 글꼴에서 제목을 최대 두 줄로 정리. 기존 예약 상세·편집 및 삭제 확인은 보존.
2. 예약 추가·편집(BookingFormScreen)
   - 예약 날짜 ISO 입력 필드는 유지하고, 입력된 문자열의 사용자용 날짜 미리보기만 제공. 저장시 기존 검증(`LocalDateTime.parse`)을 그대로 적용.
   - 예약 확인번호는 편집 화면에서도 **기본 가림**(PasswordVisualTransformation), '확인번호 보기/숨기기' 수동 토글로 노출을 제어. 입력 데이터/저장 로직은 변경하지 않음.
   - 확인번호는 목록에 표시되지 않지만 JSON 백업에는 예약 메타데이터가 포함될 수 있다는 주의 문구를 제공.
   - 예약 첨부 영역에서 '첨부 n개', 파일명(최대 2줄), PDF/이미지 유형 및 열기·연결 제거를 카드로 구분. 원본 삭제가 아닌 참조 제거임을 기존 확인 팝업에서 안내.
   - ActivityNotFoundException(뷰어 부재), SecurityException(권한 소실), 기타 파일 오픈 오류 각각 구체적 안내. 실패 시 예약/파일 메타데이터 임의 삭제 없음.
   - 시스템 SAF 파일 선택기/OpenDocument 및 persistableUriPermission 유지. 원본/URI는 JSON 백업 제외 정책 유지.
3. 공통 입력 필드
   - `TripTextField`에 선택형 `visualTransformation` 매개변수(기본 VisualTransformation.None)를 추가. 다른 모든 입력 필드의 기존 모양·동작은 변경하지 않음.
4. 표시 유틸리티 + JVM 테스트
   - `BookingPresentation.kt`: 여행 현지 날짜 표시와 첨부 개수 텍스트. `BookingPresentationTest`: 0시각 또는 날짜 누락/잘못된 입력, 한국어 날짜, 첨부 0/1/다수.
5. Android 3-B 회귀·실화면 증빙
   - 기존 제주 예약 QA 데이터에 DB 첨부 1개(연결만 존재하는 콘텐츠 URI) 생성; 예약 목록 유형·가독성 날짜·첨부 1개 확인과 확인번호 비노출 검증.
   - 편집에서 확인번호 보기/숨기기 토글, 첨부 파일명·개수, 파일 연결 제거 다이얼로그 취소 후 DB 첨부 1개 보존.
   - 기존 Android 파일 선택기 열기/취소, 예약·정산·환율 흐름 보존.
   - 라이트·다크·160% 글꼴, 휴대전화/태블릿 실제 PNG 추가.

## 범위 외 및 보존

- 새 예약 생성·편집·삭제, 확인번호 저장/검증, 첨부 URI·권한/DB Room(v5)·DAO/Repository, 기존 공유·백업 정책 변경 없음.
- 신규 MIME/미리보기 엔진, 원본 파일 복사·자동 삭제, 신규 API·권한·라이브러리, 장식형 그림자·라운드·그라데이션·애니메이션 추가 없음.
- 실제 안드로이드 외부 뷰어·권한 만료·물리 기기/폴더블/TalkBack 테스트는 CI와 구분하여 별도 실행했을 때만 완료라고 기재.

## QA/병합 안전 게이트

- 정확한 PR HEAD에서 Android Build/JVM·Room API35 + 2-C 휴대전화/태블릿 + 3-A 지도 + 3-B 정산/예약 + Release Compatibility API29/36 확인.
- 3-B 실제 원본 PNG 확인: 예약 카드에서 종류·한글 날짜·첨부 개수·확인번호 비공개 표시, 편집에서 첨부 파일 카드 및 160% 버튼/필드, 외부 파일 선택기.
- 명시적으로 사용자 승인이 있는 본 UI-R07 범위만 수정. 재설계 코드가 미완성 원본 목업 기능을 임의 구현하지 않음.
- **Stacked Draft PR**: UI-R07 → UI-R06 #31 → UI-R05 #30 → UI-R04 #29 → main. 선행 PR 모두 정확한 QA·시각 검수 후 병합되기 전까지 R07 main 병합 금지.
- 선행 병합 후 최신 main 기준으로 PR base·충돌·누락·최신 HEAD CI와 Android PNG를 재검증한 뒤 Ready/병합 판정. Google Play 스토어 자산·AAB 설정·closed/production 승격은 범위 외.
- 기존 main 자동 Play internal 릴리스가 실행될 경우 실제 커밋/서명/트랙 API 응답으로만 구분.

## 원본 링크

- UI-R00~R09 기획: https://docs.google.com/document/d/1sOjzZKCaVuHxFeCRVQnKlFp7jZd9dc-i0sYFev9uRKk/edit
- UI-R00 재구성 시안/실화면 비교: https://drive.google.com/drive/folders/1JLtNkO438c_APpeXcia46-g6lIzz5NJI
- UI-R06 PR #31: https://github.com/hebi10/TripCanvas/pull/31
