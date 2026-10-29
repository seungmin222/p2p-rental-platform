package com.team7.rental.global.response;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

	@Test
	@DisplayName("Page의 번호·크기·전체 수·다음 여부를 그대로 옮긴다")
	void of() {
		// given: 21건 중 첫 페이지 20건
		List<Integer> content = IntStream.range(0, 20).boxed().toList();

		// when
		PageResponse<Integer> response = PageResponse.of(new PageImpl<>(content, PageRequest.of(0, 20), 21));

		// then
		assertThat(response.items()).hasSize(20);
		assertThat(response.page()).isZero();
		assertThat(response.size()).isEqualTo(20);
		assertThat(response.totalElements()).isEqualTo(21);
		assertThat(response.hasNext()).isTrue();
	}

	@Test
	@DisplayName("mapper로 항목을 바꾸고, 마지막 페이지면 hasNext=false다")
	void ofWithMapper() {
		// when
		PageResponse<String> response = PageResponse.of(new PageImpl<>(List.of(1), PageRequest.of(1, 20), 21),
			n -> "item" + n);

		// then
		assertThat(response.items()).containsExactly("item1");
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.hasNext()).isFalse();
	}
}
