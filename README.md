### Docker

1. 构建镜像

```bash
git clone git@github.com:nekox41/zjerp_frontend.git
```

```bash
cd zjerp_frontend
```

```bash
docker build -t aliceresia/zjerp-frontend:latest .
```

```bash
docker push aliceresia/zjerp-frontend:latest
```

2. 运行镜像

```bash
docker run -d \
--name front \
-p 80:80 \
--network zjerp \
aliceresia/zjerp-frontend:latest
```
