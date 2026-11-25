#!/bin/sh
# 템플릿 치환(20-envsubst-on-templates.sh) 전에 실행된다. 값이 비면 검사가 무력화되므로 기동을 멈춘다.
set -eu

if ! printf '%s' "${ORIGIN_VERIFY_SECRET:-}" | grep -Eq '^[A-Za-z0-9]{32,100}$'; then
    echo "ORIGIN_VERIFY_SECRET: 영문·숫자 32~100자여야 한다 (예: openssl rand -hex 32)" >&2
    exit 1
fi

if ! printf '%s' "${SERVER2_PRIVATE_IP:-}" | grep -Eq '^[0-9]{1,3}(\.[0-9]{1,3}){3}$'; then
    echo "SERVER2_PRIVATE_IP: IPv4 주소가 필요하다" >&2
    exit 1
fi
