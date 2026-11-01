package com.team7.rental.member.client;

import com.team7.rental.member.dto.RegionCoordRequest;

/** 테스트가 명시적으로 만드는 Fake. 실제 애플리케이션 빈으로 등록하지 않는다. */
public record FakeRegionLookupClient(String administrativeCode) implements RegionLookupClient {
	@Override
	public String findAdministrativeCode(RegionCoordRequest coord) {
		return administrativeCode;
	}
}
