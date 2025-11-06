FROM nginx

WORKDIR /usr/share/nginx/html

COPY dist/ ./

COPY zjerp.conf /etc/nginx/conf.d/default.conf