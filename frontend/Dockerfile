FROM node:26-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
# Runtime: nginx as a non-root user (UID 101), listening on 8080.
# 1.30 is nginx's stable line. `apk upgrade` pulls Alpine security fixes published
# after the base image was built, so the Trivy image gate does not depend on its age.
FROM nginxinc/nginx-unprivileged:1.31.7-alpine3.24
USER root
RUN apk upgrade --no-cache
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
USER 101
EXPOSE 8080
CMD ["nginx","-g","daemon off;"]
