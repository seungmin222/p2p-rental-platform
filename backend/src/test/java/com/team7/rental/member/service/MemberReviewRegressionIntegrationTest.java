package com.team7.rental.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.member.client.RegionLookupClient;
import com.team7.rental.member.dto.AuthLoginRequest;
import com.team7.rental.member.dto.MemberRegionAddRequest;
import com.team7.rental.member.entity.Member;
import com.team7.rental.member.entity.Region;
import com.team7.rental.member.repository.MemberRegionRepository;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.member.repository.RegionRepository;
import com.team7.rental.support.IntegrationTestSupport;

/** PR #33: 실제 DB·Redis·JWT 필터를 사용하고 카카오 호출 경계만 대체한다. */
class MemberReviewRegressionIntegrationTest extends IntegrationTestSupport {
	@Autowired
	private AuthService authService;
	@Autowired
	private MemberRegionService regionService;
	@Autowired
	private MemberRepository members;
	@Autowired
	private MemberRegionRepository memberRegions;
	@Autowired
	private RegionRepository regions;
	@Autowired
	private PasswordEncoder passwordEncoder;

	// 외부 카카오 키/통신 없이 H 항목이 없는 응답에 대응하는 예외를 재현한다.
	@MockitoBean
	private RegionLookupClient regionLookupClient;

	private Long memberId;
	private final AuthLoginRequest login = new AuthLoginRequest("review@example.com", "Rental123!");

	@BeforeEach
	void createMember() {
		memberId = members.saveAndFlush(Member.createLocal(login.email(),
			passwordEncoder.encode(login.password()), "리뷰회원", new BigDecimal("50"))).getId();
	}

	@Test
	void passwordChangeKeepsCurrentFamilyAndRevokesOtherFamily() throws Exception {
		var currentResponse = new MockHttpServletResponse();
		var current = authService.login(login, currentResponse);
		var otherResponse = new MockHttpServletResponse();
		authService.login(login, otherResponse);

		mockMvc.perform(patch("/api/members/me/password")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"Rental123!\",\"newPassword\":\"Changed123!\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

		assertThat(passwordEncoder.matches("Changed123!", members.findById(memberId).orElseThrow().getPassword()))
			.isTrue();
		assertThatCode(() -> authService.reissue(refreshToken(currentResponse), new MockHttpServletResponse()))
			.doesNotThrowAnyException();
		assertThatThrownBy(() -> authService.reissue(refreshToken(otherResponse), new MockHttpServletResponse()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID));
		assertThatThrownBy(() -> authService.login(login, new MockHttpServletResponse()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
		assertThatCode(() -> authService.login(new AuthLoginRequest(login.email(), "Changed123!"),
			new MockHttpServletResponse())).doesNotThrowAnyException();
	}

	@Test
	void thirdRegionIsRejectedWithoutInsertingIt() throws Exception {
		addRegion("1111051500");
		addRegion("1111053000");
		Long thirdId = createRegion("1111054000");
		mockMvc.perform(post("/api/members/me/regions").header(HttpHeaders.AUTHORIZATION, bearerToken(memberId))
			.contentType(MediaType.APPLICATION_JSON).content("{\"regionId\":" + thirdId + "}"))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REGION_LIMIT_EXCEEDED"));
		assertThat(memberRegions.findByMemberIdOrderByIsPrimaryDescIdAsc(memberId)).hasSize(2);
	}

	@Test
	void deletingPrimaryPromotesRemainingRegion() throws Exception {
		Long first = addRegion("1111051500");
		Long second = addRegion("1111053000");
		assertThat(memberRegions.findById(first).orElseThrow().isPrimary()).isTrue();
		assertThat(memberRegions.findById(second).orElseThrow().isPrimary()).isFalse();
		mockMvc.perform(delete("/api/members/me/regions/{id}", first)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(memberId))).andExpect(status().isOk());
		assertThat(memberRegions.findById(first)).isEmpty();
		var remaining = memberRegions.findByMemberIdOrderByIsPrimaryDescIdAsc(memberId);
		assertThat(remaining).hasSize(1);
		assertThat(remaining.getFirst().getId()).isEqualTo(second);
		assertThat(remaining.getFirst().isPrimary()).isTrue();
	}

	@Test
	void noAdministrativeRegionReturns400ForVerify() throws Exception {
		Long id = addRegion("1111051500");
		when(regionLookupClient.findAdministrativeCode(any()))
			.thenThrow(new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		mockMvc.perform(patch("/api/members/me/regions/{id}/verify", id)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(memberId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"lat\":37.5,\"lng\":127.0}"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("REGION_VERIFY_MISMATCH"));
		assertThat(memberRegions.findById(id).orElseThrow().getVerifiedAt()).isNull();
	}

	@Test
	void noAdministrativeRegionStillReturns404ForCoordinateLookup() throws Exception {
		when(regionLookupClient.findAdministrativeCode(any()))
			.thenThrow(new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		mockMvc.perform(get("/api/regions/by-coord").param("lat", "37.5").param("lng", "127.0"))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
	}

	@Test
	void missingAndOtherMembersRegionKeep404AndSkipKakao() throws Exception {
		Long ownedId = addRegion("1111051500");
		Long otherId = members.saveAndFlush(Member.createLocal("other@example.com",
			passwordEncoder.encode("Rental123!"), "다른회원", new BigDecimal("50"))).getId();
		for (Long id : new Long[] {ownedId, Long.MAX_VALUE}) {
			mockMvc.perform(patch("/api/members/me/regions/{id}/verify", id)
				.header(HttpHeaders.AUTHORIZATION, bearerToken(otherId)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":37.5,\"lng\":127.0}"))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
		}
		verifyNoInteractions(regionLookupClient);
	}

	@Test
	void upstreamFailureIsNotChangedIntoMismatch() throws Exception {
		Long id = addRegion("1111051500");
		when(regionLookupClient.findAdministrativeCode(any()))
			.thenThrow(new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR));
		mockMvc.perform(patch("/api/members/me/regions/{id}/verify", id)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(memberId)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"lat\":37.5,\"lng\":127.0}"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error.code").value("COMMON_INTERNAL_ERROR"));
	}

	private Long createRegion(String code) {
		return regions.saveAndFlush(Region.create(code, "테스트동", "테스트시", "테스트구", 37.5, 127)).getId();
	}

	private Long addRegion(String code) {
		return regionService.add(memberId, new MemberRegionAddRequest(createRegion(code))).memberRegionId();
	}

	private String refreshToken(MockHttpServletResponse response) {
		String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(cookie).startsWith("refreshToken=");
		return cookie.substring("refreshToken=".length(), cookie.indexOf(';'));
	}
}
