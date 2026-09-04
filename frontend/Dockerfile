# 1단계: Vite 로 정적 파일을 만듭니다.
FROM node:22-alpine AS builder
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# VITE_ 값은 번들에 그대로 박히기 때문에 런타임 환경변수가 아니라 빌드 인자로 받습니다.
ARG VITE_DATA_MODE=api
ARG VITE_API_BASE_URL=http://localhost:8081
ARG VITE_KAKAO_JS_KEY=
ENV VITE_DATA_MODE=$VITE_DATA_MODE \
    VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_KAKAO_JS_KEY=$VITE_KAKAO_JS_KEY
RUN npm run build

# 2단계: 만들어진 dist 를 nginx 가 정적으로 서빙합니다.
FROM nginx:alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80
