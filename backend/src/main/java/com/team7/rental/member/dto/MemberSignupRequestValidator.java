package com.team7.rental.member.dto;

import com.team7.rental.global.policy.PolicyProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** {@link ValidMemberText} — 이메일 최대(member.email.max)·비밀번호(member.password)·닉네임(member.nickname) 글자 수 */
@RequiredArgsConstructor
public class MemberSignupRequestValidator implements ConstraintValidator<ValidMemberText, MemberSignupRequest> {

	private final PolicyProperties policyProperties;

	@Override
	public boolean isValid(MemberSignupRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		PolicyProperties.Member policy = policyProperties.member();
		boolean isValid = true;
		if (request.email() != null && MemberTexts.length(request.email()) > policy.emailMax()) {
			isValid = MemberTexts.reject(context, "email", policy.emailMax() + "자 이하로 입력해 주세요.");
		}
		isValid &= MemberTexts.checkLength(request.password(), policy.password(), "password", context);
		isValid &= MemberTexts.checkLength(request.nickname(), policy.nickname(), "nickname", context);
		return isValid;
	}
}
