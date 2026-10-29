package com.team7.rental.member.dto;

import com.team7.rental.global.policy.PolicyProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** {@link ValidMemberText} — 새 비밀번호 글자 수(member.password) */
@RequiredArgsConstructor
public class MemberPasswordChangeRequestValidator
	implements ConstraintValidator<ValidMemberText, MemberPasswordChangeRequest> {

	private final PolicyProperties policyProperties;

	@Override
	public boolean isValid(MemberPasswordChangeRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		return MemberTexts.checkLength(request.newPassword(), policyProperties.member().password(), "newPassword",
			context);
	}
}
