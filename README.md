# NBYS Activity Platform

前后端分离 + 微服务工程：

- 后管前端：`admin-web`，Vue 3 + Vite + Element Plus
- H5 前端：`h5-web`，Vue 3 + Vite，移动优先适配
- 注册中心：`backend/eureka-server`
- 网关：`backend/api-gateway`
- 活动中心：`backend/activity-center`
- 用户中心：`backend/user-center`
- 公共中心：`backend/public-center`
- 共享库：`backend/nbys-common`

## 微服务职责

- `activity-center`：活动、活动策划、报名、签到、阵营小队、出勤统计、发射器租赁。
- `user-center`：后管/H5 登录注册、用户资料、密码、用户管理、正式队员、邀请关系。
- `public-center`：场地管理、模式管理、权限配置、文件上传、数据库增量迁移。
- `api-gateway`：统一 `/api/**` 入口，通过 Eureka 转发到各中心。
- `eureka-server`：服务注册与发现。

## 配置中心

各服务已接入 Apollo Client，并配置：

```yaml
apollo:
  bootstrap:
    enabled: true
    eagerLoad:
      enabled: true
    namespaces: application
  meta: ${APOLLO_META:http://localhost:8080}
```

各模块的 Apollo `app.id` 位于：

- `activity-center/src/main/resources/META-INF/app.properties`
- `user-center/src/main/resources/META-INF/app.properties`
- `public-center/src/main/resources/META-INF/app.properties`
- `api-gateway/src/main/resources/META-INF/app.properties`

## Swagger

业务中心已接入 springdoc-openapi：

- 活动中心：`http://localhost:8081/swagger-ui.html`
- 用户中心：`http://localhost:8082/swagger-ui.html`
- 公共中心：`http://localhost:8083/swagger-ui.html`

## 数据库

基准 SQL：

`/Users/zhuting/Documents/nbyongshi_activity_manager_v56_item_photo_category/sql_bak/nbys-activity-manager_20260621_024324.sql`

增量脚本：

`backend/public-center/src/main/resources/db/migration/V20260621__activity_manager_delta.sql`

公共中心启动时会检查并新增缺失字段/表，只做新增，不删除字段。

冗余字段记录：

`docs/redundant_fields.txt`

## 前端

本仓库已在 `.tools/node` 安装本地 Node.js。使用前先加入 PATH：

```bash
export PATH=/Users/zhuting/Documents/nbys-activity/.tools/node/bin:$PATH
```

开发：

```bash
npm --prefix admin-web run dev
npm --prefix h5-web run dev
```

构建：

```bash
npm run web:build
```

构建产物：

- `admin-web/dist`
- `h5-web/dist`

H5 不再回传或构建到 Spring Boot `static` 目录。

## 后端启动顺序

本仓库已在 `.tools/maven` 安装本地 Maven。当前机器需要显式指定完整 JDK，避免 Maven 使用浏览器插件里的 JRE：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_281.jdk/Contents/Home
export PATH=/Users/zhuting/Documents/nbys-activity/.tools/maven/bin:$JAVA_HOME/bin:$PATH
cd backend
mvn clean package
```

本地 Maven 配置文件：

`/Users/zhuting/Documents/nbys-activity/.tools/maven/conf/settings.xml`

已配置阿里云 Maven 镜像：

`https://maven.aliyun.com/repository/public`

建议启动顺序：

1. `eureka-server`，端口 `8761`
2. `api-gateway`，端口 `8080`
3. `activity-center`，端口 `8081`
4. `user-center`，端口 `8082`
5. `public-center`，端口 `8083`

默认数据库连接会走本机 SSH 隧道：

```text
jdbc:mysql://127.0.0.1:13306/nbys-activity-manager
```

阿里云 MySQL `3306` 不开放外网访问，本地启动后端前需要先开启 SSH 隧道：

```bash
chmod +x scripts/start-db-tunnel.sh
SSH_USER=root SSH_HOST=8.160.183.48 ./scripts/start-db-tunnel.sh
```

执行后输入服务器 SSH 登录密码。密码不要写入仓库配置文件。

如果服务器 MySQL 只监听内网或本机，默认会转发到服务器侧 `127.0.0.1:3306`。如需调整：

```bash
SSH_USER=root SSH_HOST=8.160.183.48 LOCAL_DB_PORT=13306 REMOTE_DB_HOST=127.0.0.1 REMOTE_DB_PORT=3306 ./scripts/start-db-tunnel.sh
```

保持该终端窗口不关闭，然后再启动 `activity-center`、`user-center`、`public-center`。

可用环境变量覆盖：

```bash
DB_HOST=127.0.0.1 DB_PORT=13306 DB_NAME=nbys-activity-manager DB_USER=root DB_PASSWORD=xxx
```
