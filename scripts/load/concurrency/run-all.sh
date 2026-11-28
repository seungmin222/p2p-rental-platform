#!/usr/bin/env bash
# 락 전략 비교 전체(14개 구성 + 낙관적 락 재시도 2회 구성)를 순서대로 잰다. 저장소 루트에서 Git Bash로 실행, 약 4시간.
# 앞 단계(compose 기동·seed.sql·concurrency/schema.sql)는 scripts/load/README.md "락 전략 비교" 참고.
set -euo pipefail
RUN=scripts/load/concurrency/run.sh
ALL=(same same same partial disjoint distinct)

$RUN RC-R   DEFAULT           READ_COMMITTED  "${ALL[@]}"
for iso in READ_COMMITTED REPEATABLE_READ; do
	p=$([ "$iso" = READ_COMMITTED ] && echo RC || echo RR)
	$RUN "$p-N0"  NONE              "$iso" "${ALL[@]}"
	$RUN "$p-O"   OPTIMISTIC        "$iso" "${ALL[@]}"
	$RUN "$p-O2"  OPTIMISTIC        "$iso" disjoint -- 2
	$RUN "$p-P1"  PESSIMISTIC_ITEM  "$iso" "${ALL[@]}"
	$RUN "$p-P2"  PESSIMISTIC_RANGE "$iso" "${ALL[@]}"
	[ "$p" = RR ] && $RUN "$p-R" REDISSON "$iso" "${ALL[@]}"
	$RUN "$p-U"   UNIQUE            "$iso" "${ALL[@]}"
	$RUN "$p-RU"  REDISSON_UNIQUE   "$iso" "${ALL[@]}"
done
# 기본 경로로 돌려 놓는다
$RUN RC-R-END DEFAULT READ_COMMITTED same
