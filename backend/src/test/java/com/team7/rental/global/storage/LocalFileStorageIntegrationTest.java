package com.team7.rental.global.storage;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.team7.rental.support.IntegrationTestSupport;

class LocalFileStorageIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private FileStorage fileStorage;

	@Test
	@DisplayName("test 프로필은 LocalFileStorage이고, 저장한 파일을 로그인 없이 /files/**로 받을 수 있다")
	void servesUploadedFile() throws Exception {
		// given
		byte[] content = TestImages.jpeg(10, 10);
		StoredFile stored = fileStorage.upload("test", "jpg", "image/jpeg", content);

		try {
			// when, then
			assertThat(fileStorage).isInstanceOf(LocalFileStorage.class);
			mockMvc.perform(get(stored.url()))
				.andExpect(status().isOk())
				.andExpect(content().bytes(content));
		} finally {
			fileStorage.delete(stored.key());
		}
		mockMvc.perform(get(stored.url())).andExpect(status().isNotFound());
	}
}
