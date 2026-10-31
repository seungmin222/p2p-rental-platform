/**
 * multipart 본문: `request` 파트(JSON) + `images` 파트 반복.
 * request 파트는 Content-Type이 application/json이어야 한다(아니면 415 COMMON_UNSUPPORTED_MEDIA_TYPE).
 * images는 ImageUploader value에서 꺼낸 File 배열.
 */
export function toMultipart(request, images = []) {
  const form = new FormData();
  form.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }));
  for (const file of images) {
    form.append('images', file);
  }
  return form;
}
