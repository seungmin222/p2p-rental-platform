// 폼 입력 검증(react-hook-form rules.validate 모양: 통과 true, 실패 문구). 정책 수치는 받지 않고 호출부가
// features/*/constants.js 값을 넘긴다(shared에 정책 기본값을 두지 않는다).

/**
 * 글자 수. 백엔드와 같이 코드 포인트로 센다(MemberTexts.length = codePointCount).
 * 문자열 반복자가 코드 포인트 단위라 이모지(서로게이트 쌍)도 1자다. 브라우저 maxLength(UTF-16)와 다르다.
 */
export function countCodePoints(text) {
  return [...(text ?? '')].length;
}

function isBlank(value) {
  return (value ?? '').trim() === '';
}

function checkLength(value, { min, max }) {
  const length = countCodePoints(value);
  return length >= min && length <= max ? true : `${min}자 이상 ${max}자 이하로 입력해 주세요`;
}

/** 빈 값만 검사한다(로그인 폼: 형식이 바뀌기 전 계정도 로그인할 수 있어야 해서) */
export function validateRequired(value, message) {
  return isBlank(value) ? message : true;
}

// 백엔드 @Email(Hibernate)은 도메인에 점이 없어도 통과시킨다. 서버가 받는 값을 막지 않게 같은 수준으로만 본다.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/;

/** 이메일: 필수 · 최대 글자 수(policies.md member.email.max) · 형식 */
export function validateEmail(value, { max }) {
  if (isBlank(value)) {
    return '이메일을 입력해 주세요';
  }
  if (countCodePoints(value) > max) {
    return `${max}자 이하로 입력해 주세요`;
  }
  return EMAIL_PATTERN.test(value) ? true : '이메일 형식을 확인해 주세요';
}

// docs/openapi.json MemberSignupRequest.password pattern 그대로(백엔드 MemberTexts.PASSWORD_PATTERN).
// 공백 없는 ASCII 출력 문자(! ~ ~)만, 영문·숫자·ASCII 기호(!-/ :-@ [-` {-~)를 각각 1개 이상
// openapi 문자열과 글자 단위로 같게 두려고 \[ 이스케이프를 남긴다(문자 클래스 안이라 뜻은 같다)
// eslint-disable-next-line no-useless-escape
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!-/:-@\[-`{-~])[!-~]*$/;
// 백엔드 MemberTexts.PASSWORD_PATTERN_MESSAGE와 같은 뜻
export const PASSWORD_RULE_MESSAGE =
  '공백·한글 없이 영문, 숫자, 기호를 각각 1개 이상 포함해 주세요';

/** 비밀번호: 필수 · 글자 수 · 구성(policies.md member.password) */
export function validatePassword(value, range) {
  if (!value) {
    return '비밀번호를 입력해 주세요';
  }
  const length = checkLength(value, range);
  if (length !== true) {
    return length;
  }
  return PASSWORD_PATTERN.test(value) ? true : PASSWORD_RULE_MESSAGE;
}

/** 닉네임: 필수 · 글자 수(policies.md member.nickname) */
export function validateNickname(value, range) {
  if (isBlank(value)) {
    return '닉네임을 입력해 주세요';
  }
  return checkLength(value, range);
}
