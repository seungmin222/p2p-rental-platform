package com.team7.rental.review.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.notification.entity.Notification;
import com.team7.rental.notification.entity.NotificationTargetType;
import com.team7.rental.notification.entity.NotificationType;
import com.team7.rental.notification.repository.NotificationRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.review.dto.ReviewCreateRequest;
import com.team7.rental.review.entity.ComplimentType;
import com.team7.rental.review.entity.Satisfaction;
import com.team7.rental.review.repository.ReviewRepository;
import com.team7.rental.review.service.ReviewService;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 후기 작성과 받은 후기 목록 (REQ-034) */
class ReviewIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private ReviewService reviewService;

	private Member owner;
	private Member renter;
	private Member stranger;
	private Item item;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		stranger = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)).item();
	}

	@Test
	@DisplayName("대여자가 쓰면 201이고 대상은 제공자다. 칭찬 중복은 하나로, 공백만인 내용은 null로 저장한다")
	void writeByRenter() throws Exception {
		Rental rental = createCompleted(NOW.minusDays(1));

		write(renter, rental, body("BEST", "[\"CLEAN_USE\", \"ON_TIME\", \"ON_TIME\"]", "\"   \""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.reviewId").exists())
			.andExpect(jsonPath("$.data.rentalId").value(rental.getId()))
			.andExpect(jsonPath("$.data.targetId").value(owner.getId()))
			.andExpect(jsonPath("$.data.satisfaction").value("BEST"))
			.andExpect(jsonPath("$.data.compliments.length()").value(2))
			.andExpect(jsonPath("$.data.compliments[0]").value("ON_TIME"))
			.andExpect(jsonPath("$.data.compliments[1]").value("CLEAN_USE"))
			.andExpect(jsonPath("$.data.content").doesNotExist())
			.andExpect(jsonPath("$.data.createdAt").exists());
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM review_compliments", Integer.class))
			.isEqualTo(2);
	}

	@Test
	@DisplayName("제공자도 같은 대여에 따로 1번 쓸 수 있고 대상은 대여자다. 칭찬·내용은 생략할 수 있다")
	void writeByOwner() throws Exception {
		Rental rental = createCompleted(NOW.minusDays(1));
		write(renter, rental, body("GOOD", null, null)).andExpect(status().isCreated());

		write(owner, rental, body("BAD", null, null)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.targetId").value(renter.getId()))
			.andExpect(jsonPath("$.data.compliments.length()").value(0))
			.andExpect(jsonPath("$.data.content").doesNotExist());
		assertThat(reviewRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("저장되면 상대에게 REVIEW_RECEIVED 알림(대상 RENTAL, 키 REVIEW_RECEIVED:{rentalId})을 보낸다")
	void notification() throws Exception {
		Rental rental = createCompleted(NOW.minusDays(1));

		write(renter, rental, body("GOOD", null, "\"좋았어요\"")).andExpect(status().isCreated());

		Notification sent = notificationRepository
			.findByMemberIdAndEventId(owner.getId(), "REVIEW_RECEIVED:" + rental.getId()).orElseThrow();
		assertThat(sent.getType()).isEqualTo(NotificationType.REVIEW_RECEIVED);
		assertThat(sent.getTargetType()).isEqualTo(NotificationTargetType.RENTAL);
		assertThat(sent.getTargetId()).isEqualTo(rental.getId());
		assertThat(notificationRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("당사자가 아니면 403, 없는 대여는 404, 실패하면 알림이 없다")
	void participantAndNotFound() throws Exception {
		Rental rental = createCompleted(NOW.minusDays(1));

		write(stranger, rental, body("GOOD", null, null)).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_PARTICIPANT"));
		mockMvc.perform(post("/api/rentals/{id}/reviews", 999_999L)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON).content(body("GOOD", null, null)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_FOUND"));
		assertThat(reviewRepository.count()).isZero();
		assertThat(notificationRepository.findAll()).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = {"COMPLETED"}, mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("COMPLETED가 아니면 409 RENTAL_INVALID_TRANSITION")
	void notCompleted(RentalStatus status) throws Exception {
		Rental rental = status == RentalStatus.PENDING_PAYMENT
			? rentalFixture.createPendingPayment(item, renter, START, END, NOW.plusMinutes(10))
			: rentalFixture.createRental(item, renter, status, START, END);

		write(renter, rental, body("GOOD", null, null)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));
	}

	@Test
	@DisplayName("기한: completedAt + 7일 정각은 쓸 수 있고, 1초 지나면 400 REVIEW_DEADLINE_PASSED")
	void deadline() throws Exception {
		Rental atDeadline = createCompleted(NOW.minusDays(7));
		Rental pastDeadline = createCompleted(NOW.minusDays(7).minusSeconds(1));

		write(renter, atDeadline, body("GOOD", null, null)).andExpect(status().isCreated());
		write(renter, pastDeadline, body("GOOD", null, null)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("REVIEW_DEADLINE_PASSED"));
	}

	@Test
	@DisplayName("같은 대여에 두 번 쓰면 409 REVIEW_ALREADY_WRITTEN")
	void duplicate() throws Exception {
		Rental rental = createCompleted(NOW.minusDays(1));
		write(renter, rental, body("GOOD", null, null)).andExpect(status().isCreated());

		write(renter, rental, body("BEST", null, null)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("REVIEW_ALREADY_WRITTEN"));
		assertThat(reviewRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("내용은 300자까지(코드 포인트 기준), 301자는 400(details content)")
	void contentLength() throws Exception {
		Rental first = createCompleted(NOW.minusDays(1));
		Rental second = createCompleted(NOW.minusDays(1));
		String max = "😀".repeat(300);

		write(renter, first, body("GOOD", null, "\"" + max + "\"")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.content").value(max));
		write(renter, second, body("GOOD", null, "\"" + "가".repeat(301) + "\"")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("content"));
	}

	@Test
	@DisplayName("만족도 누락·잘못된 칭찬 항목은 400, 비로그인은 401")
	void invalidInput() throws Exception {
		Rental rental = createCompleted(NOW.minusDays(1));

		write(renter, rental, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
		write(renter, rental, body("GOOD", "[\"KIND\"]", null)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
		mockMvc.perform(post("/api/rentals/{id}/reviews", rental.getId())
			.contentType(MediaType.APPLICATION_JSON).content(body("GOOD", null, null)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("같은 회원이 동시에 8번 쓰면 1건만 저장되고 나머지는 REVIEW_ALREADY_WRITTEN, 알림은 1건")
	void concurrentDuplicate() throws Exception {
		Rental rental = createCompleted(NOW.minusDays(1));
		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<ErrorCode>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(executor.submit(() -> {
				start.await();
				try {
					reviewService.write(renter.getId(), rental.getId(),
						new ReviewCreateRequest(Satisfaction.GOOD, List.of(ComplimentType.ON_TIME), null));
					return null;
				} catch (BusinessException e) {
					return e.getErrorCode();
				}
			}));
		}
		start.countDown();
		List<ErrorCode> results = new ArrayList<>();
		for (Future<ErrorCode> future : futures) {
			results.add(future.get());
		}
		executor.shutdown();

		assertThat(results).filteredOn(code -> code == null).hasSize(1);
		assertThat(results).filteredOn(code -> code != null).containsOnly(ErrorCode.REVIEW_ALREADY_WRITTEN);
		assertThat(reviewRepository.count()).isEqualTo(1);
		assertThat(notificationRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("받은 후기: 비회원도 보고, 최신순, writerRole로 방향을 구분하며 칭찬은 선언 순서다")
	void receivedReviews() throws Exception {
		Rental asOwner = createCompleted(NOW.minusDays(1));
		Item renterItem = fixture.createItem(fixture.createVerifiedMember(), LocalDate.of(2026, 10, 1),
			LocalDate.of(2026, 12, 31)).item();
		Rental asRenter = createCompleted(rentalFixture.createRental(renterItem, owner, RentalStatus.COMPLETED,
			START, END), NOW.minusDays(1));
		Long renterItemOwnerId = renterItem.getOwnerId();
		write(renter, asOwner, body("BEST", "[\"CLEAN_USE\", \"AS_DESCRIBED\"]", "\"최고\""))
			.andExpect(status().isCreated());
		writeAs(renterItemOwnerId, asRenter, body("BAD", null, null)).andExpect(status().isCreated());

		mockMvc.perform(get("/api/members/{id}/reviews", owner.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.size").value(20))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			// 최신 작성(제공자가 쓴 후기)이 먼저
			.andExpect(jsonPath("$.data.items[0].writer.memberId").value(renterItemOwnerId))
			.andExpect(jsonPath("$.data.items[0].writerRole").value("OWNER"))
			.andExpect(jsonPath("$.data.items[0].satisfaction").value("BAD"))
			.andExpect(jsonPath("$.data.items[0].compliments.length()").value(0))
			.andExpect(jsonPath("$.data.items[1].writer.memberId").value(renter.getId()))
			.andExpect(jsonPath("$.data.items[1].writer.nickname").value(renter.getNickname()))
			.andExpect(jsonPath("$.data.items[1].writer.isWithdrawn").value(false))
			.andExpect(jsonPath("$.data.items[1].writerRole").value("RENTER"))
			.andExpect(jsonPath("$.data.items[1].compliments[0]").value("AS_DESCRIBED"))
			.andExpect(jsonPath("$.data.items[1].compliments[1]").value("CLEAN_USE"))
			.andExpect(jsonPath("$.data.items[1].content").value("최고"));
	}

	@Test
	@DisplayName("받은 후기: 탈퇴한 작성자는 닉네임·사진 null, isWithdrawn true. 받은 후기가 없으면 빈 목록, 없는 회원은 404")
	void receivedReviewsEdgeCases() throws Exception {
		Rental rental = createCompleted(NOW.minusDays(1));
		write(renter, rental, body("GOOD", null, null)).andExpect(status().isCreated());
		jdbcTemplate.update("UPDATE members SET status = 'WITHDRAWN' WHERE id = ?", renter.getId());

		mockMvc.perform(get("/api/members/{id}/reviews", owner.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].writer.memberId").value(renter.getId()))
			.andExpect(jsonPath("$.data.items[0].writer.nickname").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].writer.profileImageUrl").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].writer.isWithdrawn").value(true));
		mockMvc.perform(get("/api/members/{id}/reviews", stranger.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(0))
			.andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(get("/api/members/{id}/reviews", 999_999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
		mockMvc.perform(get("/api/members/{id}/reviews", owner.getId()).queryParam("page", "-1"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("대여 상세: 기간 안이고 안 썼으면 canWriteReview true, 쓰면 false·reviewWritten true. 상대 쪽은 그대로 true")
	void rentalDetailFlags() throws Exception {
		Rental rental = createCompleted(NOW.minusDays(1));
		detail(renter, rental).andExpect(jsonPath("$.data.canWriteReview").value(true))
			.andExpect(jsonPath("$.data.reviewWritten").value(false));

		write(renter, rental, body("GOOD", null, null)).andExpect(status().isCreated());

		detail(renter, rental).andExpect(jsonPath("$.data.canWriteReview").value(false))
			.andExpect(jsonPath("$.data.reviewWritten").value(true));
		detail(owner, rental).andExpect(jsonPath("$.data.canWriteReview").value(true))
			.andExpect(jsonPath("$.data.reviewWritten").value(false));
		testClock.fixAt(NOW.plusDays(6).plusSeconds(1));
		detail(owner, rental).andExpect(jsonPath("$.data.canWriteReview").value(false));
	}

	private Rental createCompleted(LocalDateTime completedAt) {
		return createCompleted(rentalFixture.createRental(item, renter, RentalStatus.COMPLETED, START, END),
			completedAt);
	}

	private Rental createCompleted(Rental rental, LocalDateTime completedAt) {
		jdbcTemplate.update("UPDATE rentals SET completed_at = ? WHERE id = ?", completedAt, rental.getId());
		return rental;
	}

	private ResultActions write(Member member, Rental rental, String json) throws Exception {
		return writeAs(member.getId(), rental, json);
	}

	private ResultActions writeAs(Long memberId, Rental rental, String json) throws Exception {
		return mockMvc.perform(post("/api/rentals/{id}/reviews", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(memberId))
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions detail(Member member, Rental rental) throws Exception {
		return mockMvc.perform(get("/api/rentals/{id}", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))).andExpect(status().isOk());
	}

	/** @param compliments JSON 배열 문자열, null이면 생략 @param content JSON 문자열(따옴표 포함), null이면 생략 */
	private static String body(String satisfaction, String compliments, String content) {
		StringBuilder json = new StringBuilder("{\"satisfaction\": \"" + satisfaction + "\"");
		if (compliments != null) {
			json.append(", \"compliments\": ").append(compliments);
		}
		if (content != null) {
			json.append(", \"content\": ").append(content);
		}
		return json.append('}').toString();
	}
}
