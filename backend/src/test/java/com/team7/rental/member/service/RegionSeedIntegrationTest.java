package com.team7.rental.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.team7.rental.member.repository.MemberRegionRepository;
import com.team7.rental.member.repository.RegionRepository;
import com.team7.rental.support.IntegrationTestSupport;

class RegionSeedIntegrationTest extends IntegrationTestSupport {
	@Autowired
	private DataSource dataSource;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private RegionRepository regions;
	@Autowired
	private MemberRegionRepository memberRegions;

	@BeforeEach
	void loadSeed() {
		// Base class clears fixture tables before this hook. Exercise the real migration SQL.
		applySeed();
	}

	@Test
	void loadsUniqueVerifiedCodesAndFiniteCoordinates() {
		assertThat(regions.count()).isEqualTo(3565);
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT code) FROM regions", Integer.class)).isEqualTo(3565);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM regions WHERE center_lat NOT BETWEEN 33 AND 39 "
			+ "OR center_lng NOT BETWEEN 124 AND 132", Integer.class)).isZero();
		var seoul = regions.findByCode("1111051500").orElseThrow();
		assertThat(seoul.getName()).isEqualTo("청운효자동");
		assertThat(seoul.getCenterLat()).isCloseTo(37.58411617384134, within(0.000000001));
		assertThat(regions.findByCode("1174052500").orElseThrow().getName()).isEqualTo("상일제1동");
		assertThat(regions.findByCode("1174052600").orElseThrow().getName()).isEqualTo("상일제2동");
	}

	@Test
	void publicSearchReturnsSeedAndSupportsSejongWithoutDistrict() throws Exception {
		mockMvc.perform(get("/api/regions").param("keyword", "상일제"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
		var sejong = regions.findByCode("3611025000").orElseThrow();
		assertThat(sejong.getSido()).isEqualTo("세종특별자치시");
		assertThat(sejong.getSigungu()).isEmpty();
	}

	@Test
	void repeatedSeedPreservesIdsReferencesAndCreationTime() {
		var region = regions.findByCode("1111051500").orElseThrow();
		var member = fixture.createMember();
		var link = fixture.addPrimaryRegion(member, region, LocalDateTime.of(2026, 11, 2, 12, 0));
		var extra = fixture.createRegion();
		Long id = region.getId();
		var created = region.getCreatedAt();
		jdbc.update("UPDATE regions SET name = '이전 이름', center_lat = 35 WHERE id = ?", id);
		applySeed();
		var updated = regions.findByCode("1111051500").orElseThrow();
		assertThat(updated.getId()).isEqualTo(id);
		assertThat(updated.getCreatedAt()).isEqualTo(created);
		assertThat(updated.getName()).isEqualTo("청운효자동");
		assertThat(updated.getCenterLat()).isCloseTo(region.getCenterLat(), within(0.000000001));
		assertThat(memberRegions.findById(link.getId()).orElseThrow().getRegionId()).isEqualTo(id);
		assertThat(regions.findById(extra.getId())).isPresent();
		assertThat(regions.count()).isEqualTo(3566);
	}

	@Test
	void unmatchedBranchOfficeIsNotAssignedInventedCoordinates() {
		assertThat(regions.findByCode("1213025100")).isEmpty();
		assertThat(regions.findByCode("4480025700")).isEmpty();
	}

	private void applySeed() {
		var populator = new ResourceDatabasePopulator(new ClassPathResource("db/migration/V9__insert_regions.sql"));
		populator.setSqlScriptEncoding("UTF-8");
		populator.execute(dataSource);
	}
}
