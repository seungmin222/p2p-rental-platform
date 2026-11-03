import { useEffect, useRef } from 'react';

// 처음 보여 줄 확대 수준(카카오 level, 작을수록 가까이). 골목 단위로 보이게(UI 조절값)
const MAP_LEVEL = 3;

/**
 * 거래 장소 한 점을 마커로 보여 주는 지도(REQ-030). kakao는 useKakaoMap이 준비한 SDK.
 * onPick이 있으면 지도 클릭·마커 끌기로 고른 { lat, lng }를 넘긴다(없으면 끌기·확대도 막은 보기 전용).
 * 마커는 point를 따라가고, onPick이 false로 끝나면(주소를 못 찾음 등) 지금 point로 돌아온다.
 */
export default function KakaoPlaceMap({ kakao, point, onPick, label, className = '' }) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const markerRef = useRef(null);
  const onPickRef = useRef(onPick);
  const pointRef = useRef(point);
  const interactive = Boolean(onPick);

  useEffect(() => {
    onPickRef.current = onPick;
    pointRef.current = point;
  });

  // 지도·마커는 한 번만 만든다. 편집 가능 여부(interactive)가 바뀌어도 다시 만들지 않고 아래 effect가 setter로 바꾼다
  useEffect(() => {
    const { maps } = kakao;
    const center = new maps.LatLng(point.lat, point.lng);
    const map = new maps.Map(containerRef.current, { center, level: MAP_LEVEL });
    const marker = new maps.Marker({ position: center, map });
    mapRef.current = map;
    markerRef.current = marker;
    // 거절되면(onPick이 false) 마커를 지금 point로 되돌린다
    const pick = async (latLng) => {
      const accepted = await onPickRef.current?.({ lat: latLng.getLat(), lng: latLng.getLng() });
      if (accepted === false) {
        const { lat, lng } = pointRef.current;
        marker.setPosition(new maps.LatLng(lat, lng));
      }
    };
    // 보기 전용(onPick 없음)일 때 클릭은 무시한다. 끌기는 setDraggable(false)라 일어나지 않는다
    const handleClick = (mouseEvent) => {
      if (!onPickRef.current) {
        return;
      }
      marker.setPosition(mouseEvent.latLng);
      pick(mouseEvent.latLng);
    };
    const handleDragEnd = () => pick(marker.getPosition());
    maps.event.addListener(map, 'click', handleClick);
    maps.event.addListener(marker, 'dragend', handleDragEnd);
    return () => {
      maps.event.removeListener(map, 'click', handleClick);
      maps.event.removeListener(marker, 'dragend', handleDragEnd);
      marker.setMap(null);
      mapRef.current = null;
      markerRef.current = null;
    };
    // point는 아래 effect가 따라간다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kakao]);

  // 끌기·확대는 편집할 수 있을 때만
  useEffect(() => {
    mapRef.current?.setDraggable(interactive);
    mapRef.current?.setZoomable(interactive);
    markerRef.current?.setDraggable(interactive);
  }, [kakao, interactive]);

  useEffect(() => {
    const position = new kakao.maps.LatLng(point.lat, point.lng);
    markerRef.current?.setPosition(position);
    mapRef.current?.panTo(position);
  }, [kakao, point.lat, point.lng]);

  return (
    <div
      ref={containerRef}
      role="img"
      aria-label={label}
      className={`h-48 w-full overflow-hidden rounded-xl bg-placeholder ${className}`}
    />
  );
}
