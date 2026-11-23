package com.team7.rental.item.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.item.entity.ItemViewLog;

/** 최근 본 상품 기록 (REQ-035). Redis 조회수 집계(ItemViewRepository)와는 별개다 */
public interface ItemViewLogRepository extends JpaRepository<ItemViewLog, Long> {

	/**
	 * (회원, 물품) 기록을 넣거나 viewed_at을 갱신한다. 같은 키의 동시 조회도 UK 충돌 없이 한 행에 모인다. 늦게 도착한 요청이 viewed_at을 되돌리지
	 * 않게 GREATEST로 앞으로만 옮긴다. 저장 여부를 알 필요가 없어 ON DUPLICATE KEY UPDATE를 쓴다(MySQL 8.0.19+ 행 별칭 문법).
	 */
	@Modifying
	@Query(value = """
		INSERT INTO item_view_logs (member_id, item_id, viewed_at, created_at, updated_at)
		VALUES (:memberId, :itemId, :viewedAt, :viewedAt, :viewedAt) AS new
		ON DUPLICATE KEY UPDATE viewed_at = GREATEST(item_view_logs.viewed_at, new.viewed_at),
			updated_at = new.updated_at
		""", nativeQuery = true)
	void upsert(@Param("memberId")
	Long memberId, @Param("itemId")
	Long itemId, @Param("viewedAt")
	LocalDateTime viewedAt);

	/** 회원의 기록 중 최신 keep건을 넘는 것들의 id (최근 본 순으로 keep번째 뒤). 정리 대상이다 */
	@Query(value = """
		SELECT id FROM item_view_logs WHERE member_id = :memberId
		ORDER BY viewed_at DESC, id DESC LIMIT 1000 OFFSET :keep
		""", nativeQuery = true)
	List<Long> findIdsBeyond(@Param("memberId")
	Long memberId, @Param("keep")
	int keep);

	@Modifying
	@Query("delete from ItemViewLog l where l.id in :ids")
	int deleteAllByIdIn(@Param("ids")
	List<Long> ids);
}
