# TripCanvas UI-R09 — 비공개 테스트 Alpha 출시 안전 게이트

- 사용자 승인: 최신 UI-R05~R09 통합본을 Google Play 비공개 테스트(Alpha)까지 배포.
- 통합된 앱 소스 기준 main SHA: `5fc89e73137c7e80ce694b808c4d480f06457452`.
- 이 승인 기록은 **앱 코드·리소스·서명·스토어 이미지 변경 없이 문서만 생성**. 부모 커밋에서 검증된 동일 앱 소스로 서명 AAB를 생성하기 위한 one-time 배포 승인 표식.
- 최종 통합 main Android CI: https://github.com/hebi10/TripCanvas/actions/runs/43001755600 (Android build/JVM/Room35 모두 성공 필요).
- 동일 SHA Android 휴대전화·태블릿, 2-C/3-A/3-B 6환경 실화면 QA: https://github.com/hebi10/TripCanvas/actions/runs/43001755572 (6개 캡처 잡 모두 성공 및 실제 PNG 검수 필요).
- 위 조건이 실패 또는 미완료면 이 PR을 Draft 상태로 두며 본 문서를 main에 병합하거나 Alpha 승격하지 않음.
- 최종 main 통합 빌드와 검증된 소스가 동일하며 머지 이후 Android CI가 성공하면 기존 `.github/workflows/play-internal.yml`의 버전 할당/앱 전용 업로드키·AAB 서명/16KB 검사/API internal completed + fresh readback 순서 수행.
- 이 배포 승인용 merge commit **제목의 마지막**은 정확히 `[tripcanvas-closed-alpha-once]`여야 기존 Play internal 릴리스 워크플로의 일회성 Alpha 승격 가드를 통과함. R09 캡처 트리거는 이미 부모 main에서 사용했으므로 이 승인용 커밋에는 `[tripcanvas-ui-r09-capture]`를 넣지 않음.
- 내부 테스트에 발행된 **바로 그 새 버전 코드만** `alpha` 트랙의 completed 릴리스로 승격. 새로운 Play edit에서 alpha versionCodes·status를 재조회해 검증. 이전 0.1.0 code8·code9는 최신 통합 코드가 아니므로 승격하지 않음.
- Play API commit과 Console 심사 승인·테스터 실제 설치는 서로 별개로 확인. 프로덕션 출시·다른 패키지·서명 키 교체·스토어 등록 정보 변경 금지.
- Google Drive 최종 QA: https://docs.google.com/document/d/1rBQtg4K_o6egU-iseDyrYjSctNreovw9TLk9gdVG0TE/edit
