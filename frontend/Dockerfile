ARG BASE_REGISTRY=docker.io/library
FROM ${BASE_REGISTRY}/nginx:alpine

COPY dist/frontend/browser /usr/share/nginx/html

# Build outputs may inherit a private umask; Nginx workers must read public assets.
RUN find /usr/share/nginx/html -type d -exec chmod 755 {} + \
    && find /usr/share/nginx/html -type f -exec chmod 644 {} +

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
