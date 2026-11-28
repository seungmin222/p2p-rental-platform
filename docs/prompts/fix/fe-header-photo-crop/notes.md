# fix/fe-header-photo-crop 결정 기록

## 결정

- **대여 상세 인계·반납 대표 사진은 4:3 상자 + `object-contain` + `bg-surface` 여백.** 넓고 낮은 상자(h-27)에 cover로 넣으면 물품 대부분이 잘려 상태를 확인할 수 없었다. 작은 썸네일 줄(44)·판정 사진(64)은 정사각형 cover 그대로 둔다(작은 크기에서 contain은 여백만 커진다). 누르면 원본을 새 탭으로 여는 동작은 유지. 이 `Photo`는 `InspectionRecords` 안에서만 쓰인다(HandoverSheet 미리보기는 `ImageUploader`라 영향 없음).
