package com.team7.rental.item.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.item.entity.Item;

public interface ItemRepository extends JpaRepository<Item, Long>, ItemListingRepository {
	/** 누적 스냅샷을 반영해 재시도·중복 배치에서도 조회수가 중복 증가하지 않는다. */
	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("update Item i set i.viewCount = :count where i.id = :id and i.viewCount < :count")
	int advanceViewCount(@org.springframework.data.repository.query.Param("id")
	Long id,
		@org.springframework.data.repository.query.Param("count")
		int count);

	/**
	 * 채팅방을 새로 만들 때 한 번 (REQ-026). 동시 생성에도 빠지지 않게 원자적으로 올린다. 삭제(DELETED)된 물품은 올리지 않는다(0 반환):
	 * 방 생성 전 삭제 확인과 이 UPDATE 사이에 삭제가 커밋되면 여기서 걸러 방이 생기지 않게 한다
	 */
	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("update Item i set i.chatCount = i.chatCount + 1 where i.id = :id "
		+ "and i.status <> com.team7.rental.item.entity.ItemStatus.DELETED")
	int increaseChatCount(@org.springframework.data.repository.query.Param("id")
	Long id);

	/**
	 * 관심 추가·해제 (REQ-022) 트랜잭션의 첫 문장. 같은 물품의 관심 변경을 물품 행 락에서 줄 세운다. wishes insert(FK 검사로 물품 행 S 락)
	 * 뒤에 카운트를 올리면 동시 요청끼리 교착이 나므로 이 락을 먼저 잡는다(ChatRoomTxService와 같은 이유).
	 */
	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("select i from Item i where i.id = :id")
	java.util.Optional<Item> findWithLockById(@org.springframework.data.repository.query.Param("id")
	Long id);

	/** 관심 추가 (REQ-022). {@link #findWithLockById}로 물품 행을 잠근 트랜잭션에서만 부른다 */
	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("update Item i set i.wishCount = i.wishCount + 1 where i.id = :id")
	int increaseWishCount(@org.springframework.data.repository.query.Param("id")
	Long id);

	/** 관심 해제 (REQ-022). 0 아래로 내려가지 않는다. {@link #findWithLockById}로 물품 행을 잠근 트랜잭션에서만 부른다 */
	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("update Item i set i.wishCount = i.wishCount - 1 where i.id = :id and i.wishCount > 0")
	int decreaseWishCount(@org.springframework.data.repository.query.Param("id")
	Long id);
}
