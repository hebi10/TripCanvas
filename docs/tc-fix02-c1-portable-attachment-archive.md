# TC-FIX-02-C1 — 사진·예약 문서 원본 암호화 내보내기 (2026-11-25)

기준 main: eefab18c808ee80b380fdc46f22a11752fc19ce8. 기존 JSON/.tcb 및 기기 내부 복구본 형식, Room v6 스키마 유지.

## 구현 범위
- 전체 여행의 구조화 JSON, 방문 사진 원본, 예약 PDF/이미지 원본 및 연결 메타데이터를 별도 휴대용 암호화 백업(.tcm)으로 내보낸다. 현재 연결된 원본이 없거나 일부 원본을 읽을 수 없는 경우 전체 내보내기를 중단한다.
- 100개 파일, 개별 10MiB, 원본 합계 20MiB, 구조화 JSON 10MiB, manifest 1MiB, ZIP payload 32MiB로 제한한다. 지원 MIME: JPEG, PNG, WEBP, GIF, HEIC/HEIF, AVIF, 예약 PDF. 파일 이름은 생성된 경로만 사용한다.
- 고정 envelope: ASCII TCMEDIA1(8), version(1), random salt(16), random nonce(12), AES-256-GCM ciphertext, tag(16). PBKDF2-HMAC-SHA256 600,000회, 헤더 인증(AAD), ZIP 무압축 레벨. 구조화 snapshot.json, media/NNN.ext 원본, manifest.json(형식/버전/원본 길이·SHA-256·연결 ID·예약 표시 이름/메타데이터)을 포함한다. 기존 기기 URI는 내보내지 않고 사진 URI는 복원 전용 placeholder로 대체한다.
- 캐시에는 암호화된 임시 파일만 생성, 원본 접근/형식/용량/암호화 성공 뒤 SAF의 새 빈 파일에 복사한다. 기존 자료가 있는 저장 대상과 동일 원본 URI 거부, 파일 길이·SHA-256 재조회 성공 후에만 완료 표시. 원본과 Room 변경 없음. 취소/실패 시 임시 암호화 파일 삭제; 외부 저장 오류로 불완전한 대상 파일이 남을 수 있으므로 UI에 경고한다.

## 검증 및 다음 단계
- JVM: 경로·허용 MIME/크기·원본 복사, 인증 왕복/무작위화/잘못된 비밀번호/변조/절단 거부.
- API35 A06: 실제 MediaStore PNG/PDF 바이트 및 연결 ID/해시, 구조화 JSON의 기기 URI 제외, 기존 저장 파일/원본 덮어쓰기 차단, 누락 원본 시 데이터 불변.
- TC-FIX-02-C2: 엄격한 인증 후 archive 검증·manifest/해시/중복·경로·용량 검증, 앱 내부 파일 복원 및 안전한 content URI, 명시적 승인·기존 복구본 생성 후 Room 반영과 실패 처리. 현재 .tcm의 앱 내 복원은 미구현이다.
- C1 CI를 먼저 확인하고 C2를 같은 PR에 추가한 뒤 전체 검증한다. **복원 구현 및 검증 전 Draft 유지·main 병합 금지.** 물리 기기/다른 기기 파일 제공자 QA 및 개인정보·스토어 문구 현행화는 별도 최종 단계. Google Play/웹/서버 배포 없음.
