// 인증·회원 목의 순수 함수. 저장소(memberSeed.js의 db)를 받아 계산하고 node에서 테스트한다.
// 목은 화면 상수(features)를 가져오지 않는 규칙이라 정책 값을 여기 따로 둔다(서버 흉내라 화면 상수가 틀려도 같이 틀리지 않게).

// policies.md member.email.max · member.password · member.nickname · region.verify-valid-days
export const MOCK_MEMBER_POLICY = {
  emailMax: 100,
  password: { min: 8, max: 20 },
  nickname: { min: 2, max: 10 },
  verifyValidDays: 30,
};

const DAY_MS = 24 * 60 * 60 * 1000;
// 백엔드 MemberTexts.PASSWORD_PATTERN·PASSWORD_PATTERN_MESSAGE
// openapi 문자열과 글자 단위로 같게 두려고 \[ 이스케이프를 남긴다(문자 클래스 안이라 뜻은 같다)
// eslint-disable-next-line no-useless-escape
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!-/:-@\[-`{-~])[!-~]*$/;
const PASSWORD_PATTERN_MESSAGE = '공백·한글 없이 영문, 숫자, 기호를 각각 1개 이상 포함해 주세요.';
// Bean Validation 기본 문구(한국어). @Email은 도메인의 점을 요구하지 않는 수준 + local part 64자 제한으로 흉내
// (Hibernate EmailValidator가 local part를 64자까지만 받아서, 100자 경계를 확인할 때 서버와 결과가 같도록)
const NOT_BLANK_MESSAGE = '공백일 수 없습니다';
const EMAIL_MESSAGE = '올바른 형식의 이메일 주소여야 합니다';
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/;
const EMAIL_LOCAL_PART_MAX = 64;

/** 백엔드 MemberTexts.length와 같이 코드 포인트로 센다 */
function length(text) {
  return [...text].length;
}

function isBlank(value) {
  return typeof value !== 'string' || value.trim() === '';
}

function lengthMessage({ min, max }) {
  return `${min}자 이상 ${max}자 이하로 입력해 주세요.`;
}

/**
 * 로그인 요청 검증(AuthLoginRequest: @NotBlank만). details[] 반환, 통과면 빈 배열.
 */
export function validateLogin(body) {
  return ['email', 'password']
    .filter((field) => isBlank(body?.[field]))
    .map((field) => ({ field, reason: NOT_BLANK_MESSAGE }));
}

/**
 * 가입 요청 검증(MemberSignupRequest + MemberSignupRequestValidator). 필드마다 첫 위반 하나만 담는다(목 단순화).
 */
export function validateSignup(body, policy = MOCK_MEMBER_POLICY) {
  const details = [];
  const { email, password, nickname } = body ?? {};

  if (isBlank(email)) {
    details.push({ field: 'email', reason: NOT_BLANK_MESSAGE });
  } else if (!EMAIL_PATTERN.test(email) || length(email.split('@')[0]) > EMAIL_LOCAL_PART_MAX) {
    details.push({ field: 'email', reason: EMAIL_MESSAGE });
  } else if (length(email) > policy.emailMax) {
    details.push({ field: 'email', reason: `${policy.emailMax}자 이하로 입력해 주세요.` });
  }

  if (isBlank(password)) {
    details.push({ field: 'password', reason: NOT_BLANK_MESSAGE });
  } else if (!PASSWORD_PATTERN.test(password)) {
    details.push({ field: 'password', reason: PASSWORD_PATTERN_MESSAGE });
  } else if (length(password) < policy.password.min || length(password) > policy.password.max) {
    details.push({ field: 'password', reason: lengthMessage(policy.password) });
  }

  if (isBlank(nickname)) {
    details.push({ field: 'nickname', reason: NOT_BLANK_MESSAGE });
  } else if (length(nickname) < policy.nickname.min || length(nickname) > policy.nickname.max) {
    details.push({ field: 'nickname', reason: lengthMessage(policy.nickname) });
  }
  return details;
}

/** 가입 중복 code. 이메일 → 닉네임 순서로 검사한다고 가정(notes "백엔드에 확인할 것"). 없으면 null */
export function findSignupConflict(members, { email, nickname }) {
  if (members.some((member) => member.email === email)) {
    return 'MEMBER_EMAIL_DUPLICATED';
  }
  if (members.some((member) => member.nickname === nickname)) {
    return 'MEMBER_NICKNAME_DUPLICATED';
  }
  return null;
}

/** 이메일·비밀번호가 맞는 회원. 없으면 null(무엇이 틀렸는지 구분하지 않는다) */
export function findAccount(members, { email, password }) {
  return members.find((member) => member.email === email && member.password === password) ?? null;
}

/** 목 AT. 회원과 발급 순번이 들어가 재발급마다 바뀐다(JWT 흉내는 하지 않는다) */
export function toMockAccessToken(memberId, sequence) {
  return `mock-at.${memberId}.${sequence}`;
}

/** "Bearer xxx" → "xxx". 형식이 아니면 null */
export function readBearerToken(authorization) {
  const match = /^Bearer (\S+)$/.exec(authorization ?? '');
  return match ? match[1] : null;
}

// 서버 시각(오프셋 없는 서울 LocalDateTime) ↔ ms
const SEOUL_OFFSET = '+09:00';
function toMs(serverDateTime) {
  return new Date(`${serverDateTime}${SEOUL_OFFSET}`).getTime();
}
function toServerDateTime(ms) {
  return new Date(ms + 9 * 60 * 60 * 1000).toISOString().slice(0, 19);
}

/**
 * 인증 상태: verifyExpiresAt = verifiedAt + verify-valid-days, isVerified = 지금 < 만료 시각.
 * 인증한 적 없으면 { isVerified: false, verifiedAt: null, verifyExpiresAt: null }
 */
export function toVerification(verifiedAt, now = new Date(), policy = MOCK_MEMBER_POLICY) {
  if (!verifiedAt) {
    return { isVerified: false, verifiedAt: null, verifyExpiresAt: null };
  }
  const expiresMs = toMs(verifiedAt) + policy.verifyValidDays * DAY_MS;
  return {
    isVerified: now.getTime() < expiresMs,
    verifiedAt,
    verifyExpiresAt: toServerDateTime(expiresMs),
  };
}

/** GET /api/members/me 응답(MemberResponse). 대표 동네가 없으면 primaryRegion null */
export function toMemberResponse(db, memberId, regionMaster, now = new Date()) {
  const member = db.members.find((m) => m.memberId === memberId);
  const primary = db.memberRegions.find((mr) => mr.memberId === memberId && mr.isPrimary);
  const region = primary && regionMaster.find((r) => r.regionId === primary.regionId);
  return {
    memberId: member.memberId,
    email: member.email,
    nickname: member.nickname,
    profileImageUrl: member.profileImageUrl,
    role: member.role,
    createdAt: member.createdAt,
    primaryRegion: region
      ? {
          memberRegionId: primary.memberRegionId,
          regionId: region.regionId,
          regionName: region.name,
          ...toVerification(primary.verifiedAt, now),
        }
      : null,
  };
}
