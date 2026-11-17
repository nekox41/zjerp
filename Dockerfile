# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /src
ENV NODE_OPTIONS=--max-old-space-size=1024
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build:prod

FROM nginx:stable-alpine
COPY --from=build /src/dist/ /usr/share/nginx/html/
COPY zjerp.conf /etc/nginx/conf.d/default.conf
RUN chmod -R a+rX /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
