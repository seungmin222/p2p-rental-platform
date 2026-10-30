// 회원·인증 정책 수치. 값은 docs/policies.md "회원·인증"이 기준이다. 글자 수는 코드 포인트(shared/lib/validators.js).

// policies.md member.email.max
export const EMAIL_MAX = 100;
// policies.md member.password (구성 규칙은 validators.js validatePassword)
export const PASSWORD_LENGTH = { min: 8, max: 20 };
// policies.md member.nickname
export const NICKNAME_LENGTH = { min: 2, max: 10 };
