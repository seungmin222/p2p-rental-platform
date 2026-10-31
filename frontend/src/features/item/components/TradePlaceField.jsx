import { useEffect, useRef, useState } from 'react';
import { Controller } from 'react-hook-form';

import { Button, TextField } from '../../../shared/ui';
import { getCurrentCoords } from '../../region/geolocation';
import { getErrorMessage } from '../errorMessages';
import { validateAddress } from '../itemForm';

/**
 * 거래 장소(REQ-006·REQ-030 좌표 입력만): 주소 직접 입력 + "현재 위치로 가져오기". 지도·주소 검색은 REQ-030(2순위).
 * 좌표는 폼 값에만 두고 화면에 숫자로 보이지 않는다. 주소와 좌표를 맞춰 보는 수단이 없어 어긋날 수 있다.
 * 권한 거부여도 버튼을 막지 않는다(브라우저 설정에서 다시 켤 수 있어서).
 */
export default function TradePlaceField({ control, register, setValue, error, isEdit, disabled }) {
  const [isLocating, setIsLocating] = useState(false);
  const [isFreshLocation, setIsFreshLocation] = useState(false);
  const [locationError, setLocationError] = useState(null);
  // 화면을 벗어난 뒤 온 응답은 버린다
  const isMounted = useRef(true);
  useEffect(() => {
    isMounted.current = true;
    return () => {
      isMounted.current = false;
    };
  }, []);

  async function handleLocate() {
    setIsLocating(true);
    setLocationError(null);
    try {
      const { lat, lng } = await getCurrentCoords();
      if (isMounted.current) {
        setValue('longitude', lng);
        setValue('latitude', lat, { shouldValidate: true });
        setIsFreshLocation(true);
      }
    } catch (locateError) {
      if (isMounted.current) {
        setLocationError(getErrorMessage(locateError));
      }
    } finally {
      if (isMounted.current) {
        setIsLocating(false);
      }
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <TextField
        label="거래 장소 주소"
        required
        placeholder="예: 인천 연수구 송도동 24-5"
        hint={isEdit ? '장소가 바뀌었다면 현재 위치를 다시 가져와 주세요' : undefined}
        disabled={disabled}
        error={error}
        {...register('address', { validate: validateAddress })}
      />
      <Controller
        name="latitude"
        control={control}
        rules={{ validate: (latitude) => latitude != null || '현재 위치를 가져와 주세요' }}
        render={({ field, fieldState }) => {
          let status = '아직 위치를 가져오지 않았어요';
          if (isFreshLocation) {
            status = '현재 위치를 가져왔어요';
          } else if (field.value != null) {
            status = '저장된 위치를 쓰고 있어요';
          }
          const message = locationError ?? fieldState.error?.message;
          return (
            <div ref={field.ref} tabIndex={-1} className="flex flex-col gap-1.5 outline-none">
              <div className="flex items-center justify-between gap-3">
                <span aria-live="polite" className="text-caption text-ink-muted">
                  {status}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  className="shrink-0"
                  isLoading={isLocating}
                  disabled={disabled}
                  onClick={handleLocate}
                >
                  현재 위치로 가져오기
                </Button>
              </div>
              {!isEdit && (
                <p className="text-xs text-ink-muted">
                  거래할 장소에서 눌러 주세요. 좌표는 화면에 보이지 않아요.
                </p>
              )}
              {message && (
                <p role="alert" className="text-xs text-danger">
                  {message}
                </p>
              )}
            </div>
          );
        }}
      />
    </div>
  );
}
