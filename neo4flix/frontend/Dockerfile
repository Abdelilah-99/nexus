# syntax=docker/dockerfile:1
# Stage 1: build the Angular app. Stage 2: serve it with nginx over HTTPS.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
# Certificates are mounted at runtime in /etc/nginx/certs (never baked into the image).
EXPOSE 80 443
