# TripCanvas — 1-A Android 기반·Travel Editorial 디자인 시스템

- 기준일: 2026-11-09
- 구현 단계: 1-A. 여행 생성/일정/경비/지도/로그인/저장소는 미구현
- 기반: [0-A 기능 명세](./00-A-product-requirements.md), [0-B 화면 명세](./00-B-screen-spec.md)
- 앱 임시 applicationId: `com.hebi.tripcanvas` (Google Play 최초 등록 전에 최종 확정)
- 디자인 방향: Travel Editorial — 차분한 흑백·회색, 정보 위계 중심, 그림자/그라데이션/불필요한 라운드 없음

## 1. 기술 구성

| 구분 | 설정 |
| --- | --- |
| 언어·UI | Kotlin + Jetpack Compose + Material 3 |
| Android Gradle Plugin | 9.3.3, 내장 Kotlin 지원 사용 |
| Compose Compiler | Kotlin 2.3.21 동버전 Compose Gradle 플러그인 |
| Gradle | 9.5.0 (공식 Wrapper 포함, 로컬·CI 공통) |
| Compose BOM | 2026.09.00 |
| Android API | compileSdk 37.1 / targetSdk 36 / minSdk 26 |
| Java | JDK 17 |
| 구조 | 단일 :app 모듈 + designsystem/theme, tokens, components, responsive |
| CI | PR·main push 시 debug assemble 및 로컬 단위 테스트, APK 7일 보관 |

공식 Gradle 9.5.0 Wrapper(`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties`)를 저장소에 포함했다. Wrapper JAR의 Git blob SHA는 Gradle 공식 v9.5.0 저장소의 `b1b8ef56b44f16b14dc800fa8103a6d89abb526f`와 동일하다. 로컬·CI 모두 프로젝트 루트에서 `./gradlew :app:assembleDebug :app:testDebugUnitTest`로 실행한다. Windows에서는 `gradlew.bat`를 사용한다.

## 2. 색상 토큰

### 라이트

| 역할 | 토큰 | 값 |
| --- | --- | --- |
| 기본 배경 | background | #FFFFFF |
| Surface | surface | #FFFFFF |
| 보조 면 | surfaceSubtle | #F5F5F5 |
| 기본 글자 | textPrimary | #171717 |
| 보조 글자 | textSecondary | #626262 |
| 테두리 | outline | #D2D2D2 |
| Primary 버튼 | primaryAction | #1F1F1F |
| 버튼 위 텍스트 | onPrimaryAction | #FFFFFF |
| 상태: 성공 | success | #256B45 |
| 상태: 경고 | warning | #8A5A00 |
| 상태: 오류 | error | #B3261E |

### 다크

| 역할 | 토큰 | 값 |
| --- | --- | --- |
| 기본 배경 | background | #111111 |
| Surface | surface | #191919 |
| 보조 면 | surfaceSubtle | #242424 |
| 기본 글자 | textPrimary | #F3F3F3 |
| 보조 글자 | textSecondary | #B8B8B8 |
| 테두리 | outline | #555555 |
| Primary 버튼 | primaryAction | #EFEFEF |
| 버튼 위 텍스트 | onPrimaryAction | #161616 |
| 상태: 성공 | success | #9BD8B0 |
| 상태: 경고 | warning | #FFD088 |
| 상태: 오류 | error | #FFB4AB |

의미 강조색은 **성공/경고/오류 상태에만** 사용한다. 시스템 배경화면에 의해 임의 브랜드 색상이 바뀌지 않도록 동적 색상(Material You)은 이 단계에서 사용하지 않는다. 테마는 시스템/라이트/다크를 지원하고, 현 1-A 미리보기 선택값은 인메모리 상태다. DataStore 영구 저장은 1-B에서 구현한다.

## 3. 타이포그래피·간격·도형

- 글꼴: Android 기본 SansSerif. 라이선스가 없는 외부 글꼴이나 가짜 여행 브랜드 폰트를 설치하지 않음.
- 크기: Display 40/48, Headline 28/36, Title 22/30·18/26, Body 16/24·14/22, Label 14/20·12/18 (폰트 크기/줄높이, sp).
- 간격: 2·4·8·12·16·24·32·40·48dp. Layout gutter Compact 16dp, 넓은 화면 32dp 적용 계획.
- 터치 영역: 모든 주 CTA 48dp 이상.
- 모서리: RectangleShape. 그림자·그라데이션·불필요한 애니메이션 없음.
- 테두리: 카드/보조 버튼은 전체 사방 테두리 1dp. 단면 border 사용하지 않음.
- 컨트롤: TripPrimaryButton, TripSecondaryButton, TripChoiceChip, TripTextField, TripSurfacePanel, TripSectionHeading, TripDivider, TripStatusTag, TripEmptyState, TripErrorState, TripLoadingState, TripOfflineNotice, TripConfirmDialog.
- 기초 아이콘: TripIcons의 Material 단색 기본 벡터. 전체 화면 기능 아이콘 디자인은 이후 개별 단계에서 검토.

## 4. 반응형 배치

| 가용 콘텐츠 너비 | 분류 | 레이아웃 |
| --- | --- | --- |
| 0~599dp | Compact | 1열 |
| 600~839dp | Medium | 2열 |
| 840dp 이상 | Expanded | 2열 이상 확장 가능 |
| 글꼴 160% | 모든 분류 | FlowRow에 버튼/태그 자연 줄바꿈, 전체 세로 스크롤 1개 |
| 폴더블 힌지 | 추후 기기 QA | 현재는 윈도 너비 기준으로만 재배치; 분할 힌지/자세 감지는 후속 작업 |

`TripAdaptiveContainer`는 실제 가용 너비 변화에 따라 분류를 다시 계산한다. 정보 목록/사진 영역에 중첩된 수직 스크롤은 두지 않는다.

## 5. 앱 실행 시 보이는 상태

1-A는 아직 여행 앱의 홈 화면이 아닌 **디자인 시스템 미리보기**를 실행한다.
- 테마 선택(라이트/다크/시스템, 재실행 간 지속 저장 아님)
- 타이포그래피·테두리·버튼·입력폼·칩·상태 컴포넌트 확인
- 클릭 가능한 확인 대화상자 및 오류 상태 예시
- 빈 여행 상태, 오프라인 상태의 설명 샘플
- Compact 1열, Medium/Expanded 2열 배치

이 미리보기의 입력값은 로컬 DB에 쓰지 않으며, 여행 CRUD·지도 데이터·실제 네트워크 상태와 무관하다. 사용자에게 이를 구현 완료한 여행 기능으로 표현하지 않는다.

## 6. 테스트와 검증

### 자동 검증
- 단위 테스트: 가용 너비 599/600/839/840dp 경계 검증
- PR CI: 공식 Wrapper를 이용한 `assembleDebug`, `testDebugUnitTest`
- 결과물: 디버그 APK 첨부 (Play 배포 아님)

### 후속 실기기 QA
- 라이트/다크/시스템 자동 전환, 상태바 아이콘 대비
- 화면 폭 360/412/600/840dp 및 태블릿/폴더블
- 시스템 글꼴 160%, TalkBack, 회전, IME 열린 상태의 버튼 접근
- 텍스트·상태색 최소 대비 및 터치 영역 수동 확인
- 레퍼런스 시안 ↔ 실제 Android 캡처 비교

## 7. 의도적 미구현 및 다음 단계

- 1-B: S00 첫 실행, S01 홈, S03 여행 작업 공간, S20 설정, 영구 테마 저장, 내비게이션.
- 1-C/2단계: 여행 DB(Room) 및 Repository, CRUD, 여행/일정 상태.
- 추후: 실제 지도 API, 공동 편집, 사진, AI, Play 배포.

구현 결과·테스트 통과 여부는 PR CI 로그로 최종 판정한다. 단순 파일 생성만으로 빌드 성공을 선언하지 않는다.

### 공식 참고
- https://developer.android.com/build/releases/about-agp
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://developer.android.com/develop/ui/compose/bom
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
