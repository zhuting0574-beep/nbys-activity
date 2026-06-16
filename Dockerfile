# 基础镜像可通过 .env 或 docker-compose.yml 的 BASE_IMAGE 修改
# 推荐优先使用官方源：python:3.12-slim
# 如果 NAS 无法访问 Docker Hub，可尝试：docker.1ms.run/library/python:3.12-slim
ARG BASE_IMAGE=python:3.12-slim
FROM ${BASE_IMAGE}

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TZ=Asia/Shanghai \
    PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/ \
    PIP_TRUSTED_HOST=mirrors.aliyun.com

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY . .
RUN mkdir -p /app/data

EXPOSE 5000
CMD ["gunicorn", "-w", "1", "--threads", "4", "-b", "0.0.0.0:5000", "app:app"]
