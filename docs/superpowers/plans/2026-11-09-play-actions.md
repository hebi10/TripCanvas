# TripCanvas Play Actions Implementation Plan

> **For agentic workers:** Use the existing approved LiveTranscribe release implementation as a reference, then verify each adapted guard and workflow before opening the PR.

**Goal:** 정확한 main CI 검증 이후 TripCanvas의 서명 AAB를 Google Play 내부 테스트로 직접 제출한다.

**Architecture:** 검증된 LiveTranscribe의 Python Publisher·서명·소스·네이티브 검증을 앱별 상수와 코드 1 기준으로 맞춘다. GitHub Actions는 main 전용 `google-play` 환경에서 단기 WIF 인증하며 내부 자동 배포와 명시적 비공개 승격을 분리한다.

**Tech Stack:** Kotlin/AGP 9.3.3, Java 17, Python 3, GitHub Actions, Android Publisher API.

**Spec:** `docs/play-store/automation-spec.md`

## Global Constraints

- 패키지 `com.hebi.tripcanvas`, 초기 `0.1.0 (1)`, minSdk 26·targetSdk 36·compileSdk 37.1 유지.
- `TripCanvas Android CI`의 `android-build`와 `room-migration-instrumentation` 성공을 모두 요구.
- 앱 전용 신규 signer 고정, main 전용 WIF, internal 기본·closed 명시적, 프로덕션 제외.
- 인증 값 미출력, 기존 UI·데이터 처리 방식 불변.

## Review Focus

- 포크·PR·태그·다른 브랜치·오래된 main 또는 일부 CI만 성공한 경우 제출 차단.
- 빈 앱과 이미 등록된 앱의 초기화·일반 배포 경로 혼동 차단.
- 업로드 SHA·버전·패키지·인증서 불일치와 네이티브 정렬 오류 차단.
- 커밋 응답·재조회 불확실 시 자동 반복 업로드 금지.
- 비공개 승격에서 존재하지 않는 버전·다른 트랙·프로덕션 변경 차단.

## Tasks

- [x] `scripts/play/`의 Publisher·소스·사전·네이티브 검사와 기존 회귀 테스트를 복사하고 TripCanvas의 상수와 첫 코드 1에 맞춘다. `scripts/release_signing.py`를 동일 이름의 환경 변수로 연결한다.
- [x] 거부되어야 할 exact-source/전체 CI/초기화/일반 배포·닫힌 승격 입력의 회귀 테스트를 실행한다. 부족한 동작은 실패 테스트로 먼저 고정한다.
- [x] `app/build.gradle.kts`에 fail-closed release signing과 검증된 버전 환경 변수 입력만 추가한다.
- [x] `.github/workflows/play-internal.yml`, `play-promote.yml`에 검증·서명·WIF·전역 동시성·실제 SHA·새 편집 재조회와 비밀 파일 정리를 구성한다. UI 캡처·스토어 변경 자동 실행은 추가하지 않는다.
- [x] `docs/play-store/registration.md`, `listing-ko.json`, `privacy-policy.html`에 실제 현재 기능과 남은 Console 설정을 문서화한다.
- [ ] Python 전체 회귀·actionlint·diff 검사를 실행하고 별도 검토 후 GitHub PR을 만든다. 로컬 SDK가 없으므로 Android 빌드·계측 검증은 PR/main Actions 결과로 확인한다.

## Baseline

- main `85cbb869be8f2fa4f5b30d1e8e8e332bf3ddf856`.
- 기존 main 전체 CI [#42578796171](https://github.com/hebi10/TripCanvas/actions/runs/42578796171) 성공을 확인했다.
- 작업 경로 `/workspace/scratch/0efde9457e84/tripcanvas-play`, 격리 브랜치 `feat/play-actions-tripcanvas-20261109`.
- 로컬 Java 17은 있으나 Android SDK 설정은 없다. 로컬 Android 검증 성공으로 보고하지 않는다.
