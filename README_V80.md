# V80 Docker 基础镜像修正版

修复 V79 中 `docker.1ms.run/python:3.12-slim` 提示 not found 的问题。

本版默认使用：

```text
python:3.12-slim
```

如果 NAS 无法访问 Docker Hub，可以在项目根目录新建或修改 `.env`：

```text
BASE_IMAGE=docker.1ms.run/library/python:3.12-slim
```

然后重新构建：

```bash
docker compose down
docker compose build
docker compose up -d
```

继续保留：

```text
data/app.db
static/uploads/
```
