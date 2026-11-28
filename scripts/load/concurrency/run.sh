#!/usr/bin/env bash
# 락 전략 비교 구성 하나를 잰다(docs/experiments/concurrency.md). 저장소 루트에서 Git Bash로 실행한다.
#   scripts/load/concurrency/run.sh <라벨> <모드|DEFAULT> <READ_COMMITTED|REPEATABLE_READ> <시나리오...> [-- 재시도 횟수]
#   예) scripts/load/concurrency/run.sh RR-P1 PESSIMISTIC_ITEM REPEATABLE_READ same same same partial disjoint distinct
# 모드 DEFAULT는 덮어쓰기 파일 없이 기본 경로(Redisson + READ COMMITTED)로 띄운다(격리 인자는 무시).
# 시나리오: same·partial·disjoint(reserve-contention.js PATTERN), distinct(reserve-distinct.js, 쉼 0·30초 램프·2분 유지).
# 시나리오마다 reset.sql → k6 → check.sql 순서이며, 출력은 scripts/load/results/concurrency-<라벨>.log에 덧붙인다.
set -euo pipefail
export MSYS_NO_PATHCONV=1

LABEL=$1; MODE=$2; ISOLATION=$3; shift 3
RETRIES=0
SCENARIOS=()
while [ $# -gt 0 ]; do
	if [ "$1" = "--" ]; then RETRIES=$2; shift 2; else SCENARIOS+=("$1"); shift; fi
done

BASE=(-f infra/docker-compose.load.yml)
LOG=scripts/load/results/concurrency-${LABEL}.log
mysql_load() { docker compose "${BASE[@]}" exec -T mysql mysql -uroot -proot rental_load < "$1"; }

if [ "$MODE" = "DEFAULT" ]; then
	COMPOSE=("${BASE[@]}")
else
	COMPOSE=("${BASE[@]}" -f infra/docker-compose.load-lock.yml)
	export LOCK_MODE=$MODE LOCK_ISOLATION=$ISOLATION LOCK_RETRIES=$RETRIES
fi

# 앱 2개를 이 구성으로 다시 띄우고 Nginx를 다시 시작한다(컨테이너 IP가 바뀌므로)
since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
docker compose "${COMPOSE[@]}" up -d --no-deps --force-recreate backend1 backend2
for i in $(seq 1 90); do
	n=$(docker compose "${BASE[@]}" logs --since "$since" backend1 backend2 2>/dev/null | grep -c "Started RentalApplication" || true)
	[ "$n" -ge 2 ] && break
	sleep 2
done
[ "$n" -ge 2 ] || { echo "backend did not start"; exit 1; }
docker compose "${BASE[@]}" restart nginx >/dev/null
docker compose "${BASE[@]}" logs --since "$since" backend1 | grep -m1 "Experiment rental lock strategy" || echo "default strategy (no experiment bean)"

for s in "${SCENARIOS[@]}"; do
	{
		echo "### $LABEL mode=$MODE isolation=$ISOLATION retries=$RETRIES scenario=$s $(date -Iseconds)"
		mysql_load scripts/load/concurrency/reset.sql
		case $s in
			same|partial) docker compose "${BASE[@]}" run --rm k6 run -q -e VUS=100 -e PATTERN=$s -e LABEL=$LABEL /scripts/reserve-contention.js || true ;;
			disjoint) docker compose "${BASE[@]}" run --rm k6 run -q -e VUS=40 -e PATTERN=disjoint -e LABEL=$LABEL /scripts/reserve-contention.js || true ;;
			distinct) docker compose "${BASE[@]}" run --rm k6 run -q -e VUS=100 -e THINK=0 -e RAMP=30s -e HOLD=2m -e LABEL=$LABEL /scripts/reserve-distinct.js || true ;;
			*) echo "unknown scenario $s"; exit 1 ;;
		esac
		mysql_load scripts/load/concurrency/check.sql
	} 2>&1 | grep -v "Using a password" | tee -a "$LOG"
done
