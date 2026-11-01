package com.team7.rental.member.client;

import com.team7.rental.member.dto.RegionCoordRequest;

public interface RegionLookupClient {
	String findAdministrativeCode(RegionCoordRequest coord);
}
