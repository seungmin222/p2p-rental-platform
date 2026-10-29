package com.team7.rental.rental.entity;

import static org.assertj.core.api.Assertions.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

class ItemInspectionTest {

	private static final List<String> URLS = List.of("/files/inspections/a.jpg");

	@Test
	@DisplayName("반납 기록을 판정하면 결과·내용·사진이 기록된다")
	void judge() {
		// given
		ItemInspection inspection = ItemInspection.returned(1L, 3L, URLS, allTrue(), null);

		// when
		inspection.judge(InspectionResult.ABNORMAL, "모서리 파손", List.of("/files/inspections/b.jpg"));

		// then
		assertThat(inspection.isJudged()).isTrue();
		assertThat(inspection.getResult()).isEqualTo(InspectionResult.ABNORMAL);
		assertThat(inspection.getResultNote()).isEqualTo("모서리 파손");
		assertThat(inspection.getResultImageUrls()).containsExactly("/files/inspections/b.jpg");
	}

	@Test
	@DisplayName("판정 사진이 없으면 판정 사진은 null로 둔다")
	void judgeWithoutImages() {
		// given
		ItemInspection inspection = ItemInspection.returned(1L, 3L, URLS, allTrue(), null);

		// when
		inspection.judge(InspectionResult.NORMAL, null, List.of());

		// then
		assertThat(inspection.getResult()).isEqualTo(InspectionResult.NORMAL);
		assertThat(inspection.getResultImageUrls()).isNull();
	}

	@Test
	@DisplayName("이미 판정한 기록을 다시 판정하면 409 RENTAL_INVALID_TRANSITION이고 첫 판정이 남는다")
	void judgeTwice() {
		// given
		ItemInspection inspection = ItemInspection.returned(1L, 3L, URLS, allTrue(), null);
		inspection.judge(InspectionResult.NORMAL, null, List.of());

		// then
		assertThatThrownBy(() -> inspection.judge(InspectionResult.ABNORMAL, "파손", URLS))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.RENTAL_INVALID_TRANSITION);
		assertThat(inspection.getResult()).isEqualTo(InspectionResult.NORMAL);
	}

	@Test
	@DisplayName("인계 기록은 판정할 수 없고 체크리스트가 없다")
	void judgeHandover() {
		// given
		ItemInspection inspection = ItemInspection.handover(1L, 2L, URLS, "메모");

		// then
		assertThatThrownBy(() -> inspection.judge(InspectionResult.NORMAL, null, List.of()))
			.isInstanceOf(IllegalStateException.class);
		assertThat(inspection.getChecklist()).isNull();
		assertThat(inspection.isLostReported()).isFalse();
	}

	private Map<ReturnChecklistItem, Boolean> allTrue() {
		Map<ReturnChecklistItem, Boolean> checklist = new EnumMap<>(ReturnChecklistItem.class);
		for (ReturnChecklistItem item : ReturnChecklistItem.values()) {
			checklist.put(item, true);
		}
		return checklist;
	}
}
