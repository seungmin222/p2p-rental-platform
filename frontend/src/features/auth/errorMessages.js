// 로그인·가입·로그아웃 화면의 오류 문구는 이 파일 한 곳에서 code로 고른다.
// 키는 docs/openapi.json 인증·가입·내 정보 API 응답 예시의 코드 전부.
import { createErrorMessageGetter } from '../../shared/lib/formErrors';

const MESSAGES = {
  COMMON_INVALID_INPUT: '입력한 내용을 확인해 주세요',
  // 이메일·비밀번호 중 무엇이 틀렸는지 구분하지 않는다(member.md REQ-002)
  AUTH_INVALID_CREDENTIALS: '이메일 또는 비밀번호가 맞지 않아요',
  AUTH_UNAUTHORIZED: '로그인이 필요해요',
  AUTH_REFRESH_TOKEN_INVALID: '로그인이 만료됐어요. 다시 로그인해 주세요',
  MEMBER_EMAIL_DUPLICATED: '이미 가입된 이메일이에요',
  MEMBER_NICKNAME_DUPLICATED: '이미 사용 중인 닉네임이에요',
  // 비밀번호 재설정(REQ-036): 만료·재사용·위조 모두 같은 코드 → 메일을 다시 요청하게 안내
  AUTH_RESET_TOKEN_INVALID: '링크가 만료됐거나 이미 사용됐어요. 재설정 메일을 다시 요청해 주세요',
};

export const getErrorMessage = createErrorMessageGetter(MESSAGES);
