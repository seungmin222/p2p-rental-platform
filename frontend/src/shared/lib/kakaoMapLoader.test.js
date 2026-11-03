import { afterEach, describe, expect, it, vi } from 'vitest';

import { createKakaoMapLoader, KakaoMapLoadError } from './kakaoMapLoader';

// 스크립트 태그를 붙이기만 하는 가짜 document. 붙은 스크립트는 scripts에 쌓이고 onload·onerror를 직접 부른다
function createFakeDocument() {
  const scripts = [];
  return {
    scripts,
    createElement: () => ({
      remove() {
        this.removed = true;
      },
    }),
    head: { appendChild: (script) => scripts.push(script) },
  };
}

function createFakeKakao({ withServices = true } = {}) {
  return {
    maps: {
      load: (callback) => callback(),
      services: withServices ? {} : undefined,
    },
  };
}

afterEach(() => {
  vi.useRealTimers();
});

describe('createKakaoMapLoader', () => {
  it('키가 비어 있으면 스크립트를 붙이지 않고 실패한다', async () => {
    const doc = createFakeDocument();
    const load = createKakaoMapLoader({ getAppKey: () => '', doc, win: {} });
    await expect(load()).rejects.toMatchObject({ reason: 'no-key' });
    expect(doc.scripts).toHaveLength(0);
  });

  it('브라우저가 아니면(document 없음) 실패한다', async () => {
    const load = createKakaoMapLoader({ getAppKey: () => 'k', doc: undefined, win: undefined });
    await expect(load()).rejects.toBeInstanceOf(KakaoMapLoadError);
  });

  it('autoload=false·services로 한 번만 붙이고 같은 kakao를 돌려준다', async () => {
    const doc = createFakeDocument();
    const win = {};
    const load = createKakaoMapLoader({ getAppKey: () => 'test-key', doc, win });
    const first = load();
    const second = load();
    expect(doc.scripts).toHaveLength(1);
    const src = new URL(doc.scripts[0].src);
    expect(src.searchParams.get('appkey')).toBe('test-key');
    expect(src.searchParams.get('autoload')).toBe('false');
    expect(src.searchParams.get('libraries')).toBe('services');
    win.kakao = createFakeKakao();
    doc.scripts[0].onload();
    await expect(first).resolves.toBe(win.kakao);
    await expect(second).resolves.toBe(win.kakao);
    await expect(load()).resolves.toBe(win.kakao);
    expect(doc.scripts).toHaveLength(1);
  });

  it('스크립트 로드 실패는 실패로 끝내고, 다음 호출에서 다시 붙인다', async () => {
    const doc = createFakeDocument();
    const load = createKakaoMapLoader({ getAppKey: () => 'k', doc, win: {} });
    const first = load();
    doc.scripts[0].onerror();
    await expect(first).rejects.toMatchObject({ reason: 'script-error' });
    expect(doc.scripts[0].removed).toBe(true);
    load();
    expect(doc.scripts).toHaveLength(2);
  });

  it('스크립트는 왔지만 kakao.maps가 없거나 services가 없으면 실패한다', async () => {
    const doc = createFakeDocument();
    const win = {};
    const load = createKakaoMapLoader({ getAppKey: () => 'k', doc, win });
    const noSdk = load();
    doc.scripts[0].onload();
    await expect(noSdk).rejects.toMatchObject({ reason: 'no-sdk' });

    const noServices = load();
    win.kakao = createFakeKakao({ withServices: false });
    doc.scripts[1].onload();
    await expect(noServices).rejects.toMatchObject({ reason: 'no-services' });
  });

  it('kakao.maps.load가 응답하지 않으면 상한 뒤 실패한다', async () => {
    vi.useFakeTimers();
    const doc = createFakeDocument();
    const win = { kakao: { maps: { load: () => {} } } };
    const load = createKakaoMapLoader({ getAppKey: () => 'k', doc, win, timeoutMs: 1000 });
    const pending = load();
    // 이미 SDK가 있으면 스크립트를 다시 붙이지 않는다
    expect(doc.scripts).toHaveLength(0);
    vi.advanceTimersByTime(1000);
    await expect(pending).rejects.toMatchObject({ reason: 'timeout' });
  });

  it('시간 초과 뒤에 load가 늦게 와도 실패 그대로이고, 붙인 스크립트는 지운다', async () => {
    vi.useFakeTimers();
    const doc = createFakeDocument();
    const win = {};
    const load = createKakaoMapLoader({ getAppKey: () => 'k', doc, win, timeoutMs: 1000 });
    const pending = load();
    const outcome = pending.then(
      () => 'resolved',
      (error) => error.reason,
    );
    let lateCallback = null;
    win.kakao = { maps: { load: (callback) => (lateCallback = callback), services: {} } };
    doc.scripts[0].onload();
    vi.advanceTimersByTime(1000);
    expect(doc.scripts[0].removed).toBe(true);
    lateCallback();
    await expect(outcome).resolves.toBe('timeout');
  });
});
