package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** 좌표 (WGS84). 좌표→행정동 조회 쿼리 파라미터와 동네 인증 바디가 같이 쓴다 (REQ-004). 좌표는 저장하지 않는다 */
public record RegionCoordRequest(
	@Schema(description = "위도", example = "37.3925") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
	Double lat,

	@Schema(description = "경도", example = "126.6390") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
	Double lng) {
}
