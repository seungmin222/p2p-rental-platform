package com.team7.rental.rental.batch;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.InspectionResult;
import com.team7.rental.rental.entity.ItemInspection;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 자동 상태 확인 배치 (REQ-018). return.auto-confirm-days = 3 */
class ReturnAutoConfirmTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);
	private static final LocalDate PERIOD_START = LocalDate.of(2026, 11, 1);

	@Autowired
	private ReturnAutoConfirmJob job;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private ItemInspectionRepository inspectionRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	private Member renter;
	private Item item;
	private int sequence;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(owner, PERIOD_START, PERIOD_START.plusDays(29)).item();
	}

	@Test
	@DisplayName("returned_at + 3일 == now는 정상 처리(COMPLETED, 판정자·changedBy 없음)하고 1초 모자라면 유지하며, 두 번 실행해도 같다")
	void boundaryAndIdempotent() {
		// given
		Rental due = returnedAt(NOW.minusDays(3));
		Rental notYet = returnedAt(NOW.minusDays(3).plusSeconds(1));

		// when
		int first = job.confirmDue(NOW);
		int second = job.confirmDue(NOW);

		// then
		assertThat(first).isEqualTo(1);
		assertThat(second).isZero();
		Rental confirmed = rentalRepository.findById(due.getId()).orElseThrow();
		assertThat(confirmed.getStatus()).isEqualTo(RentalStatus.COMPLETED);
		assertThat(confirmed.getCompletedAt()).isEqualTo(NOW);
		assertThat(returnInspectionOf(due)).satisfies(inspection -> {
			assertThat(inspection.getResult()).isEqualTo(InspectionResult.NORMAL);
			assertThat(inspection.getResultNote()).isNull();
			assertThat(inspection.getResultImageUrls()).isNull();
		});
		assertThat(historyRepository.findAll()).singleElement().satisfies(history -> {
			assertThat(history.getRentalId()).isEqualTo(due.getId());
			assertThat(history.getFromStatus()).isEqualTo(RentalStatus.RETURNED);
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.COMPLETED);
			assertThat(history.getChangedBy()).isNull();
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_RETURN_AUTO_CONFIRM_BATCH);
			assertThat(history.getChangedAt()).isEqualTo(NOW);
		});
		assertThat(rentalRepository.findById(notYet.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RETURNED);
		assertThat(returnInspectionOf(notYet).isJudged()).isFalse();
	}

	@Test
	@DisplayName("1초 뒤 다시 돌면 남은 건도 처리된다")
	void nextRun() {
		// given
		Rental notYet = returnedAt(NOW.minusDays(3).plusSeconds(1));
		job.confirmDue(NOW);

		// when
		int confirmed = job.confirmDue(NOW.plusSeconds(1));

		// then
		assertThat(confirmed).isEqualTo(1);
		assertThat(rentalRepository.findById(notYet.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.COMPLETED);
	}

	@Test
	@DisplayName("이미 판정된 건과 미반납 신고 건은 기한이 지나도 대상이 아니다")
	void excludeJudgedAndLostReported() {
		// given
		Rental judged = returnedAt(NOW.minusDays(10));
		ItemInspection judgedInspection = returnInspectionOf(judged);
		judgedInspection.judge(InspectionResult.ABNORMAL, "파손", List.of("/files/inspections/x.jpg"));
		inspectionRepository.save(judgedInspection);

		Rental lost = returnedAt(NOW.minusDays(10));
		ItemInspection lostInspection = returnInspectionOf(lost);
		// 미반납 신고 API는 2순위라 컬럼만 직접 넣는다
		ReflectionTestUtils.setField(lostInspection, "lostReported", true);
		inspectionRepository.save(lostInspection);

		// when
		int confirmed = job.confirmDue(NOW);

		// then
		assertThat(confirmed).isZero();
		assertThat(rentalRepository.findAllById(List.of(judged.getId(), lost.getId())))
			.extracting(Rental::getStatus)
			.containsOnly(RentalStatus.RETURNED);
		assertThat(historyRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("반납 완료가 아닌 대여는 returned_at이 지나도 대상이 아니다")
	void excludeNotReturned() {
		// given: 정상 확인으로 이미 완료된 대여
		Rental completed = returnedAt(NOW.minusDays(10));
		job.confirmDue(NOW);
		long histories = historyRepository.count();

		// when
		int confirmed = job.confirmDue(NOW.plusDays(1));

		// then
		assertThat(confirmed).isZero();
		assertThat(rentalRepository.findById(completed.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.COMPLETED);
		assertThat(historyRepository.count()).isEqualTo(histories);
	}

	@Test
	@DisplayName("스케줄 진입점 run()도 같은 처리를 한다(시각은 Clock 기준)")
	void runUsesClock() {
		// given
		Rental due = returnedAt(NOW.minusDays(3));

		// when
		job.run();

		// then
		assertThat(rentalRepository.findById(due.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.COMPLETED);
	}

	// 대여마다 기간이 겹치지 않게 이틀씩 띄운다
	private Rental returnedAt(LocalDateTime returnedAt) {
		LocalDate start = PERIOD_START.plusDays(sequence * 2L);
		sequence++;
		return rentalFixture.createReturned(item, renter, start, start.plusDays(1), returnedAt);
	}

	private ItemInspection returnInspectionOf(Rental rental) {
		return inspectionRepository.findByRentalIdAndPhase(rental.getId(), InspectionPhase.RETURN).orElseThrow();
	}
}
