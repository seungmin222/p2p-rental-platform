package com.team7.rental.chat.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatRoomMemberTest {

	@Test
	@DisplayName("읽음 위치는 처음이면 설정되고, 이후에는 더 큰 값으로만 바뀐다")
	void markReadOnlyMovesForward() {
		ChatRoomMember member = ChatRoomMember.join(1L, 2L);

		assertThat(member.markRead(10L)).isTrue();
		assertThat(member.getLastReadMessageId()).isEqualTo(10L);

		assertThat(member.markRead(10L)).isFalse();
		assertThat(member.markRead(5L)).isFalse();
		assertThat(member.getLastReadMessageId()).isEqualTo(10L);

		assertThat(member.markRead(11L)).isTrue();
		assertThat(member.getLastReadMessageId()).isEqualTo(11L);
	}
}
