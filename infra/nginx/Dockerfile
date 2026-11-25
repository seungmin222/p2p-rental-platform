# check=skip=SecretsUsedInArgOrEnv
# (위 지시문: VITE_* 키는 브라우저에 공개되는 클라이언트 키라 빌드 인자 경고를 끈다. 비밀값을 넣지 않는다)
# 운영 Nginx 이미지 = 프론트 빌드 산출물 + Nginx 설정. 컨텍스트는 저장소 루트(.dockerignore가 frontend/·infra/nginx/만 남김).
# 빌드: docker build -f infra/nginx/Dockerfile -t rental-frontend --build-arg VITE_KAKAO_MAP_KEY=... .
# VITE_* 값은 번들에 그대로 들어가 브라우저에 공개되는 값이다(비밀값을 넣지 않는다). CI는 저장소 secrets·vars에서 넘긴다.
FROM node:24-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
ARG VITE_API_URL=/api
ARG VITE_PAYMENT_ENABLED=false
ARG VITE_KAKAO_MAP_KEY=
ARG VITE_TOSS_CLIENT_KEY=
ENV VITE_API_URL=$VITE_API_URL \
	VITE_PAYMENT_ENABLED=$VITE_PAYMENT_ENABLED \
	VITE_KAKAO_MAP_KEY=$VITE_KAKAO_MAP_KEY \
	VITE_TOSS_CLIENT_KEY=$VITE_TOSS_CLIENT_KEY
RUN npm run build

FROM nginx:1.28-alpine
ENV TZ=Asia/Seoul
COPY infra/nginx/templates/ /etc/nginx/templates/
COPY --chmod=755 infra/nginx/entrypoint/05-require-env.sh /docker-entrypoint.d/05-require-env.sh
COPY --from=build /app/dist /usr/share/nginx/html
