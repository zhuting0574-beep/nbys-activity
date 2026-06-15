# 国内网络优化版：默认使用 Docker Hub 镜像代理，避免 NAS 直接访问 registry-1.docker.io 失败
# 如果你的网络可以直接访问 Docker Hub，可以把 docker-compose.yml 里的 BASE_IMAGE 改回 python:3.12-slim
ARG BASE_IMAGE=docker.1ms.run/python:3.12-slim
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
