package com.team7.rental.notification.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Optional<Notification> findByMemberIdAndEventId(Long memberId, String eventId);

	boolean existsByMemberIdAndEventId(Long memberId, String eventId);

	/** 첫 페이지: 최신순(id 내림차순) */
	List<Notification> findByMemberIdOrderByIdDesc(Long memberId, Limit limit);

	/** 다음 페이지: 커서 id보다 오래된 것 */
	List<Notification> findByMemberIdAndIdLessThanOrderByIdDesc(Long memberId, Long id, Limit limit);

	Optional<Notification> findByIdAndMemberId(Long id, Long memberId);

	long countByMemberIdAndIsReadFalse(Long memberId);

	/** 모두 읽음. 벌크 수정이라 updated_at을 직접 넣는다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update Notification n set n.isRead = true, n.updatedAt = :now
		where n.memberId = :memberId and n.isRead = false
		""")
	int markAllRead(@Param("memberId")
	Long memberId, @Param("now")
	LocalDateTime now);
}
