package com.team7.rental.global.lock.experiment;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionTemplate;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.lock.RedissonRentalLockStrategy;
import com.team7.rental.global.lock.RentalLockStrategy;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;

/**
 * 동시성 비교 실험 전용 락 전략(docs/experiments/concurrency.md). {@code app.experiment.rental-lock.mode}를 줄 때만 빈이 생기고,
 * 그때는 {@link Primary}로 기본 {@link RedissonRentalLockStrategy}를 대신한다. 프로퍼티가 없으면(운영·개발·테스트 기본) 기본 경로는 그대로다.
 * <p>
 * 격리 수준: 기존 트랜잭션 메서드(RentalReservationTxService.create)는 READ_COMMITTED로 고정돼 있다. 여기서 바깥 트랜잭션을
 * {@code isolation} 값으로 먼저 열면 안쪽 메서드가 REQUIRED로 합류해 바깥 격리 수준을 쓴다(기존 트랜잭션 검증은 꺼져 있음).
 * <p>
 * 스키마: OPTIMISTIC은 items.version, UNIQUE 계열은 rental_dates 테이블이 필요하다. 둘 다 운영 스키마(Flyway)에 없고 부하 DB에만
 * {@code scripts/load/concurrency/schema.sql}로 만든다. 엔티티에 매핑하지 않으므로 ddl-auto validate와 무관하다.
 * <p>
 * 예약·연장·가능 기간 수정이 같은 인터페이스를 쓰므로 실험 모드에서는 셋 다 이 전략을 탄다. rental_dates INSERT는 결과가
 * {@link Rental}일 때만 한다(예약). 연장·가능 기간 수정은 실험 범위 밖이다.
 */
@Primary
@Component
@ConditionalOnProperty(prefix = "app.experiment.rental-lock", name = "mode")
public class ExperimentRentalLockStrategy implements RentalLockStrategy {

	private static final Logger log = LoggerFactory.getLogger(ExperimentRentalLockStrategy.class);

	// MySQL 오류 번호: 1205 락 대기 초과(innodb_lock_wait_timeout), 1213 교착(deadlock)
	private static final int ER_LOCK_WAIT_TIMEOUT = 1205;
	private static final int ER_LOCK_DEADLOCK = 1213;

	private final ExperimentLockMode mode;
	private final int optimisticRetries;
	private final long lockWaitSeconds;
	private final TransactionTemplate transactionTemplate;
	private final JdbcTemplate jdbcTemplate;
	private final RedissonRentalLockStrategy redissonRentalLockStrategy;

	public ExperimentRentalLockStrategy(@Value("${app.experiment.rental-lock.mode}")
	ExperimentLockMode mode, @Value("${app.experiment.rental-lock.isolation:READ_COMMITTED}")
	Isolation isolation, @Value("${app.experiment.rental-lock.optimistic-retries:0}")
	int optimisticRetries, PlatformTransactionManager transactionManager, JdbcTemplate jdbcTemplate,
		RedissonRentalLockStrategy redissonRentalLockStrategy, PolicyProperties policyProperties) {
		this.mode = mode;
		this.optimisticRetries = optimisticRetries;
		// innodb_lock_wait_timeout은 초 단위다. rental.lock-wait(3초)를 올림해 쓴다
		this.lockWaitSeconds = Math.max(1, (policyProperties.rental().lockWait().toMillis() + 999) / 1000);
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setIsolationLevel(isolation.value());
		this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
		this.jdbcTemplate = jdbcTemplate;
		this.redissonRentalLockStrategy = redissonRentalLockStrategy;
		log.warn("Experiment rental lock strategy enabled: mode={}, isolation={}, optimisticRetries={}", mode,
			isolation, optimisticRetries);
	}

	@Override
	public <T> T executeWithItemLock(Long itemId, Supplier<T> task) {
		try {
			return switch (mode) {
				case NONE -> inTransaction(task::get);
				case OPTIMISTIC -> withOptimisticRetry(itemId, task);
				case PESSIMISTIC_ITEM -> inTransaction(() -> {
					lockItemRow(itemId);
					return task.get();
				});
				case PESSIMISTIC_RANGE -> inTransaction(() -> {
					lockActiveRentalRows(itemId);
					return task.get();
				});
				case REDISSON -> redissonRentalLockStrategy.executeWithItemLock(itemId, () -> inTransaction(task::get));
				case UNIQUE -> withRentalDates(task);
				case REDISSON_UNIQUE -> redissonRentalLockStrategy.executeWithItemLock(itemId,
					() -> withRentalDates(task));
			};
		} catch (PessimisticLockingFailureException e) {
			throw translateLockFailure(itemId, e);
		}
	}

	private <T> T inTransaction(Supplier<T> body) {
		return transactionTemplate.execute(status -> body.get());
	}

	// 물품 행을 트랜잭션 첫 문장으로 잠근다. REPEATABLE READ의 스냅샷은 첫 일반 SELECT 때 만들어지므로 락을 잡은 뒤에 생긴다
	private void lockItemRow(Long itemId) {
		setLockWaitTimeout();
		jdbcTemplate.queryForList("SELECT id FROM items WHERE id = ? FOR UPDATE", Long.class, itemId);
	}

	// 겹침 검사가 읽는 범위(그 물품의 활성 대여)를 잠근다. 행이 없으면 READ COMMITTED에서는 아무것도 잠기지 않는다
	private void lockActiveRentalRows(Long itemId) {
		setLockWaitTimeout();
		List<Object> args = new ArrayList<>();
		args.add(itemId);
		RentalStatus.ACTIVE_FOR_PERIOD.forEach(status -> args.add(status.name()));
		String placeholders = String.join(",", RentalStatus.ACTIVE_FOR_PERIOD.stream().map(s -> "?").toList());
		jdbcTemplate.queryForList(
			"SELECT id FROM rentals WHERE item_id = ? AND status IN (" + placeholders + ") FOR UPDATE", Long.class,
			args.toArray());
	}

	// 세션 변수라 같은 커넥션의 다음 사용에도 남는다. 실험 모드에서는 모든 락 경로가 매번 다시 설정하므로 받아들인다
	private void setLockWaitTimeout() {
		jdbcTemplate.execute("SET SESSION innodb_lock_wait_timeout = " + lockWaitSeconds);
	}

	private <T> T withOptimisticRetry(Long itemId, Supplier<T> task) {
		for (int attempt = 0;; attempt++) {
			try {
				return inTransaction(() -> {
					Long version = jdbcTemplate.queryForObject("SELECT version FROM items WHERE id = ?", Long.class,
						itemId);
					T result = task.get();
					int updated = jdbcTemplate.update(
						"UPDATE items SET version = version + 1 WHERE id = ? AND version = ?", itemId, version);
					if (updated == 0) {
						throw new OptimisticConflict();
					}
					return result;
				});
			} catch (OptimisticConflict | PessimisticLockingFailureException e) {
				// rentals INSERT의 FK 검사가 물품 행에 공유 락을 걸어, 두 트랜잭션이 같은 물품 행을 UPDATE하려 하면 교착(1213)이 난다.
				// 낙관적 락에서는 이것도 충돌로 보고 같은 재시도 규칙을 따른다. 대기 초과 등 다른 락 오류는 그대로 올린다
				if (e instanceof PessimisticLockingFailureException lockFailure
					&& mysqlErrorCode(lockFailure) != ER_LOCK_DEADLOCK) {
					throw lockFailure;
				}
				if (attempt >= optimisticRetries) {
					throw new BusinessException(ErrorCode.COMMON_CONCURRENT_UPDATE);
				}
			}
		}
	}

	private <T> T withRentalDates(Supplier<T> task) {
		try {
			return inTransaction(() -> {
				T result = task.get();
				if (result instanceof Rental rental) {
					insertRentalDates(rental);
				}
				return result;
			});
		} catch (DuplicateKeyException e) {
			throw new BusinessException(ErrorCode.RENTAL_PERIOD_CONFLICT);
		}
	}

	private void insertRentalDates(Rental rental) {
		List<Object[]> rows = new ArrayList<>();
		for (LocalDate date = rental.getStartDate(); !date.isAfter(rental.getEndDate()); date = date.plusDays(1)) {
			rows.add(new Object[] {rental.getItemId(), Date.valueOf(date), rental.getId()});
		}
		jdbcTemplate.batchUpdate("INSERT INTO rental_dates (item_id, rented_date, rental_id) VALUES (?, ?, ?)",
			rows);
	}

	// 1205(대기 초과)는 분산 락 대기 초과와 같은 409로, 1213(교착)은 다시 시도할 수 있는 409로 바꾼다. 교착 수는 DB 지표로 따로 센다
	private RuntimeException translateLockFailure(Long itemId, DataAccessException e) {
		int errorCode = mysqlErrorCode(e);
		if (errorCode == ER_LOCK_WAIT_TIMEOUT) {
			return new BusinessException(ErrorCode.RENTAL_LOCK_TIMEOUT);
		}
		if (errorCode == ER_LOCK_DEADLOCK) {
			log.info("Experiment deadlock: itemId={}, mode={}", itemId, mode);
			return new BusinessException(ErrorCode.COMMON_CONCURRENT_UPDATE);
		}
		return e;
	}

	private static int mysqlErrorCode(Throwable e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sqlException && sqlException.getErrorCode() != 0) {
				return sqlException.getErrorCode();
			}
		}
		return 0;
	}

	/** 조건부 UPDATE가 0행이면 던져 바깥 트랜잭션을 롤백한다 */
	private static final class OptimisticConflict extends RuntimeException {

		private OptimisticConflict() {
			super(null, null, false, false);
		}
	}
}
