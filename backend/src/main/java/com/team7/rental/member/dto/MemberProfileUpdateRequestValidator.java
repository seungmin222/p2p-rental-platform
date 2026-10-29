package com.team7.rental.member.dto;

import com.team7.rental.global.policy.PolicyProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** {@link ValidMemberText} — 보낸 닉네임이 비어 있지 않고 member.nickname 글자 수 안인지 */
@RequiredArgsConstructor
public class MemberProfileUpdateRequestValidator
	implements ConstraintValidator<ValidMemberText, MemberProfileUpdateRequest> {

	private final PolicyProperties policyProperties;

	@Override
	public boolean isValid(MemberProfileUpdateRequest request, ConstraintValidatorContext context) {
		if (request == null || request.nickname() == null) {
			return true;
		}
		if (request.nickname().isBlank()) {
			return MemberTexts.reject(context, "nickname", "공백일 수 없습니다.");
		}
		return MemberTexts.checkLength(request.nickname(), policyProperties.member().nickname(), "nickname", context);
	}
}
