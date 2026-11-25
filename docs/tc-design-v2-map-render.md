# TC-DESIGN-V2 지도 렌더링 보완

- 기준: #80 HEAD 29de90902967fcd0666b7b5b691eb14580081fd3, 기존 실제 캡처 96d61a2c.
- 실제 비교: 지도에 배경 타일이 없고, 저장된 두 핀 중 하나가 하단 경계에 잘린 캡처 확인. 이전 3-A 로그 및 아티팩트에는 타일 실패 원인 상세 없음. 실제 네트워크/공급자 장애 원인 확정 불가. OpenFreeMap 공식 Liberty URL은 현행 코드와 일치(https://openfreemap.org/quick_start/). 공급자 교체/새 인증/과금 없음.
- 첫 장소 12배율 고정 확대 대신 선택된 장소는 기존 포커스 유지, 미선택 다중 좌표는 MapLibre LatLngBounds로 실제 뷰포트 측정 후 40dp(작은 뷰포트는 20% 이하) 여백 맞춤. 좌표 중복 단일 처리.
- MapLibre 13.7.1 타일 파싱/지도 전체 렌더 이벤트 및 스타일/타일 실패 이벤트 분리 관찰. 12초 내 배경 미확인 또는 타일 오류면 연결 안내/수동 다시 불러오기, 저장 장소 목록 유지. 이벤트 성공은 실제 지도 배경 시각 검증을 대체하지 않음. 원시 URL, 토큰, 타일 좌표 로깅 금지. 
- 3-A 실제 QA: 전체 핀의 화면 내 20dp 여백 확인 추가, 타일 이벤트 증거 저장. 기존 핀 선택·날짜 필터·상세·카메라 포커스·다크/큰 글꼴 검사 유지. 실제 지도 타일 품질은 신규 캡처를 직접 확인해야 함. #80 검증 완료 전 종속 변경 분리, 배포 없음.
- 공식 API: https://maplibre.org/maplibre-native/android/examples/camera/lat-lng-bounds/ 및 https://maplibre.org/maplibre-native/android/examples/observability/observe-map-events/ .
