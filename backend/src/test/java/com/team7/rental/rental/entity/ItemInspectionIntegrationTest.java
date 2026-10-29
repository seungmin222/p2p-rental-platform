package com.team7.rental.rental.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

class ItemInspectionIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 10, 0);

	@Autowired
	private ItemInspectionRepository itemInspectionRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("반납 기록의 사진·체크리스트·판정 사진이 JSON으로 저장되고 다시 읽어도 같다")
	void jsonRoundTrip() {
		// given
		testClock.fixAt(NOW);
		Rental rental = createRental();
		Map<ReturnChecklistItem, Boolean> checklist = new EnumMap<>(ReturnChecklistItem.class);
		for (ReturnChecklistItem item : ReturnChecklistItem.values()) {
			checklist.put(item, item != ReturnChecklistItem.NO_CONTAMINATION);
		}
		ItemInspection inspection = ItemInspection.returned(rental.getId(), rental.getRenterId(),
			List.of("/files/inspections/a.jpg", "/files/inspections/b.jpg"), checklist, "오염 있음");
		inspection.judge(InspectionResult.ABNORMAL, "얼룩", List.of("/files/inspections/c.jpg"));
		Long id = itemInspectionRepository.save(inspection).getId();

		// when
		ItemInspection found = itemInspectionRepository.findById(id).orElseThrow();
		String storedChecklist = jdbcTemplate.queryForObject("SELECT checklist FROM item_inspections WHERE id = ?",
			String.class, id);

		// then
		assertThat(found.getPhase()).isEqualTo(InspectionPhase.RETURN);
		assertThat(found.getImageUrls()).containsExactly("/files/inspections/a.jpg", "/files/inspections/b.jpg");
		assertThat(found.getChecklist()).isEqualTo(checklist);
		assertThat(found.getChecklist().keySet()).allSatisfy(
			key -> assertThat(key).isInstanceOf(ReturnChecklistItem.class));
		assertThat(found.getNote()).isEqualTo("오염 있음");
		assertThat(found.isLostReported()).isFalse();
		assertThat(found.getResult()).isEqualTo(InspectionResult.ABNORMAL);
		assertThat(found.getResultNote()).isEqualTo("얼룩");
		assertThat(found.getResultImageUrls()).containsExactly("/files/inspections/c.jpg");
		assertThat(found.getWrittenBy()).isEqualTo(rental.getRenterId());
		assertThat(found.getCreatedAt()).isEqualTo(NOW);
		assertThat(storedChecklist).contains("\"NO_CONTAMINATION\"");
	}

	@Test
	@DisplayName("인계 기록은 체크리스트·판정이 비어 있다")
	void handoverColumns() {
		// given
		Rental rental = createRental();
		Long id = itemInspectionRepository.save(ItemInspection.handover(rental.getId(), rental.getOwnerId(),
			List.of("/files/inspections/a.jpg"), null)).getId();

		// when
		ItemInspection found = itemInspectionRepository.findById(id).orElseThrow();

		// then
		assertThat(found.getPhase()).isEqualTo(InspectionPhase.HANDOVER);
		assertThat(found.getChecklist()).isNull();
		assertThat(found.getNote()).isNull();
		assertThat(found.getResult()).isNull();
		assertThat(found.getResultImageUrls()).isNull();
	}

	@Test
	@DisplayName("같은 대여·단계 기록을 두 번 저장하면 UK 위반이다")
	void uniqueRentalPhase() {
		// given
		Rental rental = createRental();
		itemInspectionRepository.save(ItemInspection.handover(rental.getId(), rental.getOwnerId(),
			List.of("/files/inspections/a.jpg"), null));

		// then
		assertThatThrownBy(() -> itemInspectionRepository.save(ItemInspection.handover(rental.getId(),
			rental.getOwnerId(), List.of("/files/inspections/b.jpg"), null)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_item_inspections_rental_id_phase");
		assertThat(itemInspectionRepository.findAllByRentalIdOrderById(rental.getId())).hasSize(1);
	}

	@Test
	@DisplayName("같은 대여라도 단계가 다르면 각각 저장된다")
	void differentPhase() {
		// given
		Rental rental = createRental();
		itemInspectionRepository.save(ItemInspection.handover(rental.getId(), rental.getOwnerId(),
			List.of("/files/inspections/a.jpg"), null));

		// when
		itemInspectionRepository.save(ItemInspection.returned(rental.getId(), rental.getRenterId(),
			List.of("/files/inspections/b.jpg"), Map.of(), null));

		// then
		assertThat(itemInspectionRepository.findAllByRentalIdOrderById(rental.getId()))
			.extracting(ItemInspection::getPhase)
			.containsExactly(InspectionPhase.HANDOVER, InspectionPhase.RETURN);
	}

	private Rental createRental() {
		VerifiedMember owner = fixture.createVerifiedMember();
		Member renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		return rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
	}
}
