import { http } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { fail, ok } from '../../mocks/response';
import { ApiError, CLIENT_NETWORK_ERROR } from './ApiError';
import { useAuthStore } from './authStore';
import { apiClient, setUnauthorizedHandler } from './client';

const API = 'http://localhost/api';
const server = setupServer();
const onUnauthorized = vi.fn();

let reissueCount;
let reissueSucceeds;

/** Bearer new-token만 통과시키는 보호 API */
function protectedHandler(path) {
  return http.get(`${API}${path}`, ({ request }) =>
    request.headers.get('Authorization') === 'Bearer new-token'
      ? ok({ path })
      : fail(401, 'AUTH_UNAUTHORIZED', '로그인이 필요합니다.'),
  );
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterAll(() => server.close());

beforeEach(() => {
  reissueCount = 0;
  reissueSucceeds = true;
  useAuthStore.getState().setAccessToken('old-token');
  setUnauthorizedHandler(onUnauthorized);
  server.use(
    http.post(`${API}/auth/reissue`, async () => {
      reissueCount += 1;
      await new Promise((resolve) => setTimeout(resolve, 20));
      return reissueSucceeds
        ? ok({ accessToken: 'new-token' })
        : fail(401, 'AUTH_UNAUTHORIZED', '로그인이 필요합니다.');
    }),
  );
});

afterEach(() => {
  server.resetHandlers();
  onUnauthorized.mockReset();
});

describe('응답 정규화', () => {
  it('성공 응답은 data만 반환한다', async () => {
    server.use(http.get(`${API}/regions`, () => ok([{ id: 1 }])));

    await expect(apiClient.get('/regions')).resolves.toEqual([{ id: 1 }]);
  });

  it('실패 응답은 code·message·details·status를 가진 ApiError로 throw한다', async () => {
    const details = [{ field: 'email', reason: '형식 오류' }];
    server.use(
      http.post(`${API}/members`, () => fail(400, 'COMMON_INVALID_INPUT', '입력값 오류', details)),
    );

    const error = await apiClient.post('/members', {}).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      code: 'COMMON_INVALID_INPUT',
      message: '입력값 오류',
      details,
      status: 400,
    });
  });

  it('서버 응답이 없으면 CLIENT_NETWORK_ERROR, status 0', async () => {
    server.use(http.get(`${API}/regions`, () => Response.error()));

    await expect(apiClient.get('/regions')).rejects.toMatchObject({
      code: CLIENT_NETWORK_ERROR,
      status: 0,
    });
  });
});

describe('401 재발급', () => {
  it('동시 401 3건이면 재발급은 1회, 3건 모두 새 토큰으로 재시도해 성공한다', async () => {
    server.use(protectedHandler('/a'), protectedHandler('/b'), protectedHandler('/c'));

    const results = await Promise.all([
      apiClient.get('/a'),
      apiClient.get('/b'),
      apiClient.get('/c'),
    ]);

    expect(reissueCount).toBe(1);
    expect(results).toEqual([{ path: '/a' }, { path: '/b' }, { path: '/c' }]);
    expect(useAuthStore.getState().accessToken).toBe('new-token');
  });

  it('재발급에 실패하면 AT를 지우고 로그인 이동 핸들러를 부른다', async () => {
    reissueSucceeds = false;
    server.use(protectedHandler('/a'));

    await expect(apiClient.get('/a')).rejects.toMatchObject({
      code: 'AUTH_UNAUTHORIZED',
      status: 401,
    });

    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('재시도한 요청이 또 401이면 다시 재발급하지 않는다', async () => {
    server.use(http.get(`${API}/a`, () => fail(401, 'AUTH_UNAUTHORIZED', '로그인이 필요합니다.')));

    await expect(apiClient.get('/a')).rejects.toMatchObject({ status: 401 });

    expect(reissueCount).toBe(1);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it.each(['/auth/login', '/auth/logout'])('%s의 401은 재발급을 시도하지 않는다', async (path) => {
    server.use(http.post(`${API}${path}`, () => fail(401, 'AUTH_INVALID_CREDENTIALS', '불일치')));

    await expect(apiClient.post(path)).rejects.toMatchObject({ code: 'AUTH_INVALID_CREDENTIALS' });

    expect(reissueCount).toBe(0);
  });
});

describe('Authorization 헤더', () => {
  function captureAuthorization(method, path) {
    const captured = { value: undefined };
    server.use(
      http[method](`${API}${path}`, ({ request }) => {
        captured.value = request.headers.get('Authorization');
        return ok();
      }),
    );
    return captured;
  }

  it.each([
    ['post', '/auth/login'],
    ['post', '/auth/reissue'],
    ['post', '/members'],
    ['get', '/regions'],
    ['get', '/regions/by-coord'],
    ['get', '/items/feed'],
    ['get', '/items'],
    ['get', '/categories'],
  ])('공개 API %s %s에는 붙이지 않는다', async (method, path) => {
    const captured = captureAuthorization(method, path);

    await apiClient[method](path);

    expect(captured.value).toBeNull();
  });

  it('동네 검색은 쿼리가 있어도 공개 API로 본다', async () => {
    const captured = captureAuthorization('get', '/regions');

    await apiClient.get('/regions?keyword=송도');

    expect(captured.value).toBeNull();
  });

  it('피드·검색은 쿼리가 있어도 공개 API로 본다', async () => {
    const feed = captureAuthorization('get', '/items/feed');
    const search = captureAuthorization('get', '/items');

    await apiClient.get('/items/feed', { params: { regionId: 1101 } });
    await apiClient.get('/items?keyword=텐트');

    expect(feed.value).toBeNull();
    expect(search.value).toBeNull();
  });

  it('물품 상세(GET /items/{id})에는 Bearer AT를 붙인다 — isOwner가 로그인 사용자 기준이어야 해서', async () => {
    const captured = captureAuthorization('get', '/items/101');

    await apiClient.get('/items/101');

    expect(captured.value).toBe('Bearer old-token');
  });

  it('물품 등록·수정·삭제와 내 물품 API에는 Bearer AT를 붙인다', async () => {
    const post = captureAuthorization('post', '/items');
    const mine = captureAuthorization('get', '/members/me/items');

    await apiClient.post('/items');
    await apiClient.get('/members/me/items');

    expect(post.value).toBe('Bearer old-token');
    expect(mine.value).toBe('Bearer old-token');
  });

  it('내 동네 API(GET /members/me/regions)에는 Bearer AT를 붙인다', async () => {
    const captured = captureAuthorization('get', '/members/me/regions');

    await apiClient.get('/members/me/regions');

    expect(captured.value).toBe('Bearer old-token');
  });

  it('회원 API(GET /members/me)에는 Bearer AT를 붙인다', async () => {
    const captured = captureAuthorization('get', '/members/me');

    await apiClient.get('/members/me');

    expect(captured.value).toBe('Bearer old-token');
  });
});
