import { Controller } from 'react-hook-form';

import { ImageUploader } from '../../../shared/ui';
import { INSPECTION_IMAGES } from '../constants';

/** 모달·시트 안의 오류(토스트는 top layer 아래라 가려진다) */
export function FormError({ message }) {
  if (!message) {
    return null;
  }
  return (
    <p role="alert" className="text-caption text-danger">
      {message}
    </p>
  );
}

/**
 * 인계·반납·판정 사진(INSPECTION_IMAGES, 1장 이상). 값은 ImageUploader value 그대로이고 제출할 때 file만 꺼낸다.
 * 오류 시 shouldFocusError가 이 영역으로 포커스를 옮기도록 감싼 div에 ref를 단다.
 */
export function InspectionImageField({ control, name = 'images', label, hint, disabled }) {
  return (
    <Controller
      name={name}
      control={control}
      rules={{
        validate: (images) =>
          images.length >= INSPECTION_IMAGES.MIN_COUNT ||
          `사진을 ${INSPECTION_IMAGES.MIN_COUNT}장 이상 올려 주세요`,
      }}
      render={({ field, fieldState }) => (
        <div ref={field.ref} tabIndex={-1} className="flex flex-col gap-1 outline-none">
          <fieldset disabled={disabled} className="contents">
            <ImageUploader
              value={field.value}
              onChange={field.onChange}
              maxCount={INSPECTION_IMAGES.MAX_COUNT}
              maxFileSize={INSPECTION_IMAGES.MAX_FILE_SIZE}
              accept={INSPECTION_IMAGES.ACCEPT}
              label={label}
              hint={hint}
            />
          </fieldset>
          <FormError message={fieldState.error?.message} />
        </div>
      )}
    />
  );
}
