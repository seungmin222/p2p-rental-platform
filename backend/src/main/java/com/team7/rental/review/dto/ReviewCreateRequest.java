package com.team7.rental.review.dto;

import java.util.List;

import com.team7.rental.review.entity.ComplimentType;
import com.team7.rental.review.entity.Satisfaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 후기 작성 (REQ-034). 내용 최대 글자 수는 서비스가 policies.md review.content.max로 검사한다.
 *
 * @param compliments 생략하거나 빈 배열이면 칭찬 항목 없음. 같은 항목을 여러 번 보내면 하나로 저장한다
 * @param content 생략·빈 문자열·공백만이면 내용 없음(null)으로 저장한다
 */
public record ReviewCreateRequest(
	@Schema(description = "만족도: BAD 별로예요, GOOD 좋아요, BEST 최고예요") @NotNull
	Satisfaction satisfaction,

	@Schema(description = "칭찬 항목(다중 선택, 선택 안 해도 됨). ON_TIME 시간 약속을 잘 지켜요, AS_DESCRIBED 물품 상태가 설명과 같아요, "
		+ "CLEAN_USE 깨끗하게 사용했어요. 대여자·제공자 모두 같은 목록이다")
	List<@NotNull ComplimentType> compliments,

	@Schema(description = "후기 내용(선택). policies.md review.content.max(300자) 이하. 공백만이면 내용 없음으로 저장", example = "깨끗하게 잘 썼어요.")
	String content) {

	public ReviewCreateRequest {
		if (compliments == null) {
			compliments = List.of();
		}
	}
}
