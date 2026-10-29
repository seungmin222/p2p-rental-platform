package com.team7.rental.global.config;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.support.IntegrationTestSupport;

/** /v3/api-docs에 회원·물품 API 계약(chore/member-item-api-contract)이 모두 있는지 */
class OpenApiDocsIntegrationTest extends IntegrationTestSupport {

	/** 경로 → 메서드. api.md 1순위 회원·물품 행 + REQ-020 + 수정 화면 조회 */
	private static final Map<String, List<String>> CONTRACT = Map.ofEntries(
		Map.entry("/api/members", List.of("post")),
		Map.entry("/api/auth/login", List.of("post")),
		Map.entry("/api/auth/logout", List.of("post")),
		Map.entry("/api/auth/reissue", List.of("post")),
		Map.entry("/api/members/me", List.of("get", "patch")),
		Map.entry("/api/members/me/password", List.of("patch")),
		Map.entry("/api/regions", List.of("get")),
		Map.entry("/api/regions/by-coord", List.of("get")),
		Map.entry("/api/members/me/regions", List.of("get", "post")),
		Map.entry("/api/members/me/regions/{memberRegionId}/verify", List.of("patch")),
		Map.entry("/api/members/me/regions/{memberRegionId}/primary", List.of("patch")),
		Map.entry("/api/members/me/regions/{memberRegionId}", List.of("delete")),
		Map.entry("/api/categories", List.of("get")),
		Map.entry("/api/items", List.of("get", "post")),
		Map.entry("/api/items/deposit-guide", List.of("get")),
		Map.entry("/api/items/{itemId}", List.of("get", "patch", "delete")),
		Map.entry("/api/items/feed", List.of("get")),
		Map.entry("/api/members/me/items", List.of("get")),
		Map.entry("/api/members/me/items/{itemId}", List.of("get")),
		Map.entry("/api/items/{itemId}/rentals", List.of("get")));

	@Test
	@DisplayName("회원·물품 계약 경로가 모두 있고 summary 끝에 REQ ID가 있다")
	void containsContractPaths() throws Exception {
		// when
		String docs = mockMvc.perform(get("/v3/api-docs").header("Authorization", bearerToken(1L)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		// then
		Map<String, Object> paths = JsonPath.read(docs, "$.paths");
		CONTRACT.forEach((path, methods) -> {
			assertThat(paths).as(path).containsKey(path);
			for (String method : methods) {
				String summary = JsonPath.read(docs, "$.paths['" + path + "']." + method + ".summary");
				assertThat(summary).as(method + " " + path).matches(".*\\(REQ-\\d{3}(, REQ-\\d{3})*\\)$");
			}
		});
	}
}
