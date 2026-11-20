// 약관 목(REQ-037). 응답 형태는 openapi.json TermsDocumentResponse·TermsAgreementStatusResponse와 같다.
// 본문은 화면 확인용 짧은 견본이다(실제 본문은 backend/src/main/resources/terms/*.md, 서버에서 받는다).
import { delay, http } from 'msw';

import { agreeTermsInDb } from './memberLogic';
import { memberDb } from './memberSeed';
import { fail, ok } from './response';
import { authenticateMockRequest } from './session';

const VERSION = '2026-11-20';

// 재동의 결과는 새로고침해도 남게 localStorage에 둔다(로그인 세션 mock.session과 같이). 메모리 db만 고치면
// 새로고침 → 재발급 응답이 다시 true가 되어 모달이 또 뜬다.
const TERMS_AGREED_KEY = 'mock.termsAgreedMemberIds';

/** 저장된 재동의 결과를 memberDb에 반영한다(auth.js가 로그인·재발급 응답 전에 부른다) */
export function applySavedTermsAgreements() {
  try {
    const agreed = JSON.parse(localStorage.getItem(TERMS_AGREED_KEY)) ?? [];
    memberDb.termsOutdatedMemberIds = memberDb.termsOutdatedMemberIds.filter(
      (memberId) => !agreed.includes(memberId),
    );
  } catch {
    // 저장된 값이 없으면 시드 그대로
  }
}

function saveTermsAgreement(memberId) {
  try {
    const agreed = JSON.parse(localStorage.getItem(TERMS_AGREED_KEY)) ?? [];
    localStorage.setItem(TERMS_AGREED_KEY, JSON.stringify([...new Set([...agreed, memberId])]));
  } catch {
    // 저장소를 못 쓰면 새로고침 때 다시 묻는다
  }
}

const DOCUMENTS = [
  {
    type: 'TERMS_OF_SERVICE',
    title: '이용약관',
    content: [
      '> 목 견본입니다. 실제 약관은 서버에서 받습니다.',
      '',
      '# 이용약관',
      '',
      '## 제1조 (목적)',
      '이 약관은 동네 기반 개인 간 물품 대여 서비스의 이용 조건을 정합니다.',
      '',
      '## 제2조 (회원가입과 계정)',
      '1. 만 14세 이상인 사람이 약관과 개인정보 수집·이용에 동의하면 가입할 수 있습니다.',
      '2. 회원은 계정 정보를 직접 관리합니다.',
    ].join('\n'),
  },
  {
    type: 'PRIVACY_POLICY',
    title: '개인정보처리방침',
    content: [
      '> 목 견본입니다.',
      '',
      '# 개인정보처리방침',
      '',
      '## 수집하는 항목',
      '- 이메일, 비밀번호(암호화), 닉네임',
      '- 동네 인증 결과(위치 좌표는 저장하지 않음)',
    ].join('\n'),
  },
  {
    type: 'PROHIBITED_ITEMS',
    title: '대여 금지 품목',
    content: [
      '# 대여 금지 품목',
      '',
      '- 법령으로 거래가 금지된 물품',
      '- 의약품, 주류, 담배',
      '- 위험물',
    ].join('\n'),
  },
];

export const termsHandlers = [
  http.get('/api/terms', async () => {
    await delay(300);
    return ok(DOCUMENTS.map((document) => ({ ...document, version: VERSION })));
  }),

  http.post('/api/members/me/terms-agreements', async ({ request }) => {
    await delay(400);
    const member = authenticateMockRequest(request);
    if (!member) {
      return fail(401, 'AUTH_UNAUTHORIZED', '로그인이 필요합니다.');
    }
    const body = await request.json().catch(() => null);
    const result = agreeTermsInDb(memberDb, member.memberId, body?.types);
    if (result.details) {
      return fail(400, 'COMMON_INVALID_INPUT', '입력값이 올바르지 않습니다.', result.details);
    }
    if (!result.requiresTermsAgreement) {
      saveTermsAgreement(member.memberId);
    }
    return ok({ requiresTermsAgreement: result.requiresTermsAgreement });
  }),
];
