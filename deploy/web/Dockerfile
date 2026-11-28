# The React SPA behind nginx, as its own image (EEC-95 `web`). Replaces the SPA embedded in the monolith jar.
# Build from the repository root: docker build -f deploy/web/Dockerfile -t flavors/web:dev .
FROM node:22-alpine AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY deploy/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 8080
