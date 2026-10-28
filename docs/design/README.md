# 디자인 시안

프론트 화면의 디자인 기준. **정확한 값(색·크기·간격·모서리)은 `mockups/*.html`의 인라인 스타일**이 기준이고, PNG는 전체 모양 참고용이다. 시안과 명세(`docs/state-machines.md`, `docs/domains/*.md`, `docs/policies.md`)가 다르면 명세가 우선이며, 차이는 사용자에게 확인한 뒤 맞춘다.

| 파일 | 내용 |
| --- | --- |
| `design-system.png` | 디자인 시스템 v0.1: 색상·타이포그래피·간격·모서리·버튼·입력·칩·배지·물품 카드·하단 탭 |
| `wireframes-core5.png` | 핵심 5개 화면 와이어프레임 |
| `flow-board.png` | 핵심 대여 흐름 보드: SCR-03 홈 → 05 상세 → 07 예약·결제 → 10 채팅방 → 08 대여 상세(상태 확인) |
| `screen-flow.png` | 전체 화면(SCR-01~14) 이동 흐름도 |
| `screens/SCR-xx_*.png` | 화면별 시안: 03 홈, 05 물품 상세, 07 예약, 08 대여 상세(제공자), 10 채팅방 |
| `mockups/Main.html` | 디자인 시스템(`design-system.png`의 원본) |
| `mockups/Home.html`, `Detail.html`, `Booking.html`, `RentalDetail.html`, `Chat.html` | 화면별 시안 원본(390 × 844) |
| `mockups/fonts/` | 시안이 쓰는 IBM Plex Sans KR woff2(400·500·600·700, 한글·라틴 서브셋)와 `OFL.txt` |

## 글꼴 라이선스

IBM Plex Sans KR — Copyright © 2017 IBM Corp. with Reserved Font Name "Plex". SIL Open Font License 1.1로 배포된다(원문: `mockups/fonts/OFL.txt`, 출처 [IBM/plex](https://github.com/IBM/plex)). 앱에서 서빙하는 사본은 `frontend/src/styles/fonts/`에 같은 `OFL.txt`와 함께 둔다.
