// 카카오맵 JS SDK 동적 로드(REQ-030). 스크립트는 한 번만 붙이고, 같은 Promise를 돌려준다.
// 키가 비었거나 스크립트·kakao.maps.load가 실패하면 실패로 끝내고 다음 호출에서 다시 시도한다.

const SDK_URL = 'https://dapi.kakao.com/v2/maps/sdk.js';
// 스크립트가 붙은 뒤 kakao.maps.load가 응답하지 않을 때의 상한(UI 조절값).
// 넘으면 상세는 주소 텍스트, 등록·수정은 주소 직접 입력으로 돌아간다
export const KAKAO_MAP_LOAD_TIMEOUT_MS = 10 * 1000;

export class KakaoMapLoadError extends Error {
  constructor(reason) {
    super(`카카오맵을 불러오지 못했어요: ${reason}`);
    this.name = 'KakaoMapLoadError';
    this.reason = reason;
  }
}

export function getKakaoMapKey() {
  return import.meta.env.VITE_KAKAO_MAP_KEY?.trim() ?? '';
}

/**
 * 로더 하나(캐시 포함). 테스트에서는 appKey·doc·win을 바꿔 넣는다.
 * @returns {() => Promise<object>} window.kakao를 돌려주는 함수
 */
export function createKakaoMapLoader({
  getAppKey = getKakaoMapKey,
  doc = globalThis.document,
  win = globalThis.window,
  timeoutMs = KAKAO_MAP_LOAD_TIMEOUT_MS,
} = {}) {
  let pending = null;

  function load() {
    const appKey = getAppKey();
    if (!appKey) {
      return Promise.reject(new KakaoMapLoadError('no-key'));
    }
    if (!doc || !win) {
      return Promise.reject(new KakaoMapLoadError('no-browser'));
    }
    return new Promise((resolve, reject) => {
      let script = null;
      const timer = setTimeout(() => fail('timeout'), timeoutMs);

      function fail(reason) {
        clearTimeout(timer);
        script?.remove();
        reject(new KakaoMapLoadError(reason));
      }

      function init() {
        const kakao = win.kakao;
        if (!kakao?.maps?.load) {
          fail('no-sdk');
          return;
        }
        kakao.maps.load(() => {
          clearTimeout(timer);
          if (kakao.maps.services) {
            resolve(kakao);
          } else {
            fail('no-services');
          }
        });
      }

      // 개발 서버 HMR 등으로 이미 붙어 있으면 다시 붙이지 않는다
      if (win.kakao?.maps?.load) {
        init();
        return;
      }
      script = doc.createElement('script');
      script.src = `${SDK_URL}?appkey=${encodeURIComponent(appKey)}&autoload=false&libraries=services`;
      script.async = true;
      script.onload = init;
      script.onerror = () => fail('script-error');
      doc.head.appendChild(script);
    });
  }

  return function loadKakaoMap() {
    if (!pending) {
      pending = load().catch((error) => {
        pending = null;
        throw error;
      });
    }
    return pending;
  };
}

export const loadKakaoMap = createKakaoMapLoader();
