# 部署说明

目标：只维护 `deploy/.env` 和 `deploy/docker-compose.yml`。

## 1. 准备配置

```bash
cd deploy
cp .env.example .env
```

只需要先改这几项：

```bash
POSTGRES_PASSWORD=你的数据库密码
MINIO_ROOT_PASSWORD=你的 MinIO 密码
AI_BASE_URL=你的模型 API 地址
AI_API_KEY=你的模型 API Key
AI_MODEL=你的模型名
```

端口统一用 `APP_` 前缀，默认值如下：

```text
APP_FRONTEND_PORT=18000
APP_BACKEND_PORT=18080
APP_POSTGRES_PORT=15432
APP_MINIO_API_PORT=19000
APP_MINIO_CONSOLE_PORT=19001
APP_QDRANT_HTTP_PORT=16333
APP_QDRANT_GRPC_PORT=16334
```

容器之间不要使用这些宿主机端口，已经在 `docker-compose.yml` 里固定好了。

## 2. 启动

```bash
docker compose up -d --build
```

访问地址：

```text
前端：http://服务器IP:APP_FRONTEND_PORT
后端：http://服务器IP:APP_BACKEND_PORT
MinIO Console：http://服务器IP:APP_MINIO_CONSOLE_PORT
Qdrant：http://服务器IP:APP_QDRANT_HTTP_PORT/dashboard
```

## 3. 常用命令

```bash
# 查看状态
docker compose ps

# 查看后端日志
docker compose logs -f backend

# 重启应用
docker compose restart backend frontend

# 更新代码后重新构建
docker compose up -d --build

# 停止服务
docker compose down
```

## 4. 清空数据

```bash
docker compose down -v
```

这会删除数据库、MinIO 文件和 Qdrant 向量数据。正常升级不要执行。

## 5. 备份页面填写

如果使用本部署内置 MinIO：

```text
端点地址：http://minio:9000
存储桶：rag-study-backup
Access Key ID：MINIO_ROOT_USER
Secret Access Key：MINIO_ROOT_PASSWORD
Key 前缀：backups
pg_dump 路径：pg_dump
psql 路径：psql
```
