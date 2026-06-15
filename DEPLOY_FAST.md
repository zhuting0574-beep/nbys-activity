# 快速部署说明

这个版本已经配置了：

- Docker 基础镜像默认使用：`docker.1ms.run/python:3.12-slim`
- pip 默认使用阿里云源：`https://mirrors.aliyun.com/pypi/simple/`
- `.dockerignore` 已排除 `data/` 数据库、备份文件、缓存文件，避免每次构建把数据库也打进镜像。

## 推荐更新命令

以后不要每次都用：

```bash
docker compose build --no-cache
```

`--no-cache` 会强制重新拉基础镜像、重新安装 Python 依赖，所以会很慢。

推荐使用：

```bash
docker compose down
docker compose build
docker compose up -d
```

或者一行：

```bash
docker compose up -d --build
```

## 保留数据库

更新时保留服务器上的：

```text
data/app.db
```

不要执行：

```bash
docker compose down -v
```

## 如果 docker.1ms.run 又不可用

可以打开 `docker-compose.yml`，把：

```yaml
BASE_IMAGE: docker.1ms.run/python:3.12-slim
```

改成其他可用源，例如：

```yaml
BASE_IMAGE: python:3.12-slim
```

或者你当前 NAS 可用的 Docker 镜像代理地址。
