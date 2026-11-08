package com.team7.rental.rental.entity;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.team7.rental.global.entity.BaseTimeEntity;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인계·반납 기록 (REQ-017·018). 대여당 단계별 1건(UK rental_id, phase)이고 제출 후 수정하지 않는다.
 * 바뀌는 것은 RETURN 기록의 판정 필드(result·resultNote·resultImageUrls)뿐이며 한 번만 기록한다.
 * 사진은 저장소 URL을 저장한다(item_images와 같음).
 * <p>
 * RETURN 기록의 ai_result는 AI 워커가 판정과 따로 쓴다(REQ-040). @DynamicUpdate로 바뀐 컬럼만 UPDATE해, 판정 트랜잭션이 먼저 읽어 둔 기록을
 * 커밋할 때 그 사이에 저장된 AI 결과를 옛 값으로 덮어쓰지 않게 한다(AiCompareIntegrationTest.judgementDoesNotOverwriteAiResult).
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "item_inspections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemInspection extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long rentalId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private InspectionPhase phase;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private List<String> imageUrls;

	/** RETURN만. 항목마다 true/false */
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<ReturnChecklistItem, Boolean> checklist;

	/** 인계 메모 또는 반납 메모 */
	@Column(length = 300)
	private String note;

	/** 미반납 신고(REQ-018·049). 신고 기록은 판정하지 않고 차감 흐름으로만 끝낸다 */
	@Column(nullable = false)
	private boolean lostReported;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private InspectionResult result;

	@Column(length = 500)
	private String resultNote;

	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> resultImageUrls;

	/** AI 상태 비교 (REQ-040). RETURN 기록만, 비교 전이면 null */
	@JdbcTypeCode(SqlTypes.JSON)
	private AiCompareRecord aiResult;

	/** 작성자. 반납 대리 접수면 제공자 */
	@Column(nullable = false)
	private Long writtenBy;

	/** 인계 기록 (REQ-017). 사진 장수·메모 길이 검증은 호출하는 쪽이 한다 */
	public static ItemInspection handover(Long rentalId, Long writtenBy, List<String> imageUrls, String note) {
		return create(rentalId, InspectionPhase.HANDOVER, writtenBy, imageUrls, null, note);
	}

	/**
	 * 반납 기록 (REQ-018). 체크리스트 완전성·메모 필수 여부 검증은 호출하는 쪽이 한다.
	 *
	 * @param writtenBy 대여자, 대리 접수면 제공자
	 */
	public static ItemInspection returned(Long rentalId, Long writtenBy, List<String> imageUrls,
		Map<ReturnChecklistItem, Boolean> checklist, String note) {
		Map<ReturnChecklistItem, Boolean> copy = new EnumMap<>(ReturnChecklistItem.class);
		copy.putAll(Objects.requireNonNull(checklist, "checklist"));
		return create(rentalId, InspectionPhase.RETURN, writtenBy, imageUrls, copy, note);
	}

	/**
	 * 미반납 신고 기록 (REQ-018·049). 반납 사진·체크리스트 없이 RETURN 단계로 남긴다.
	 *
	 * @param writtenBy 신고한 제공자
	 * @param note 정규화된 메모. 없으면 null
	 */
	public static ItemInspection lostReported(Long rentalId, Long writtenBy, String note) {
		ItemInspection inspection = create(rentalId, InspectionPhase.RETURN, writtenBy, List.of(), null, note);
		inspection.lostReported = true;
		return inspection;
	}

	private static ItemInspection create(Long rentalId, InspectionPhase phase, Long writtenBy, List<String> imageUrls,
		Map<ReturnChecklistItem, Boolean> checklist, String note) {
		ItemInspection inspection = new ItemInspection();
		inspection.rentalId = Objects.requireNonNull(rentalId, "rentalId");
		inspection.phase = phase;
		inspection.writtenBy = Objects.requireNonNull(writtenBy, "writtenBy");
		inspection.imageUrls = List.copyOf(imageUrls);
		inspection.checklist = checklist;
		inspection.note = note;
		return inspection;
	}

	public boolean isJudged() {
		return result != null;
	}

	/**
	 * 제공자 판정 또는 자동 정상 처리(REQ-018). 한 번만 기록한다.
	 *
	 * @param resultImageUrls 사진이 없으면 빈 목록
	 * @throws BusinessException RENTAL_INVALID_TRANSITION 이미 판정됐을 때
	 * @throws IllegalStateException RETURN 기록이 아닐 때
	 */
	public void judge(InspectionResult result, String resultNote, List<String> resultImageUrls) {
		if (phase != InspectionPhase.RETURN) {
			throw new IllegalStateException("반납 기록만 판정한다: " + id);
		}
		if (isJudged()) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
		this.result = Objects.requireNonNull(result, "result");
		this.resultNote = resultNote;
		this.resultImageUrls = resultImageUrls.isEmpty() ? null : List.copyOf(resultImageUrls);
	}

	/**
	 * AI 비교를 맡는다(REQ-040). 대여 건당 한 번: 사진이 있는 반납 기록이고 아직 비어 있을 때만 PROCESSING으로 바꾼다.
	 *
	 * @return 맡았으면 true. 인계 기록·미반납 신고·이미 맡은 기록이면 false
	 */
	public boolean startAiCompare(LocalDateTime now) {
		if (phase != InspectionPhase.RETURN || lostReported || aiResult != null) {
			return false;
		}
		this.aiResult = AiCompareRecord.processing(now);
		return true;
	}

	/**
	 * AI 비교 결과를 남긴다. PROCESSING일 때만 바꾼다(같은 대여를 두 번 끝내지 않는다).
	 *
	 * @param finished PROCESSING이 아닌 상태
	 * @return 저장했으면 true
	 */
	public boolean finishAiCompare(AiCompareRecord finished) {
		if (finished.isProcessing()) {
			throw new IllegalArgumentException("끝난 상태만 저장한다");
		}
		if (aiResult == null || !aiResult.isProcessing()) {
			return false;
		}
		this.aiResult = finished;
		return true;
	}
}
