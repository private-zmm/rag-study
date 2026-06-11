# Docker Compose 部署

## 1. 准备配置

进入部署目录：

```bash
cd deploy
cp .env.example .env
```

至少修改下面几个值：

```bash
POSTGRES_PASSWORD=你的数据库密码
MINIO_ROOT_PASSWORD=你的MinIO密码
AI_BASE_URL=你的模型API地址
AI_API_KEY=你的模型API Key
AI_MODEL=你的模型名
```

## 2. 启动

```bash
docker compose up -d --build
```

默认访问地址：

```text
前端：http://服务器IP:18000
后端：http://服务器IP:18080
MinIO Console：http://服务器IP:19001
```

## 3. S3 备份页面填写

如果使用本 compose 里的 MinIO 作为备份存储：

```text
端点地址：http://minio:9000
存储桶：rag-study-backup
Access Key ID：.env 里的 MINIO_ROOT_USER
Secret Access Key：.env 里的 MINIO_ROOT_PASSWORD
Key 前缀：backups
pg_dump 路径：pg_dump
psql 路径：psql
```

`backend` 镜像内已安装 PostgreSQL 客户端，所以 `pg_dump` 和 `psql` 保持默认即可。

## 4. 常用命令

查看日志：

```bash
docker compose logs -f backend
```

重启：

```bash
docker compose restart backend frontend
```

停止：

```bash
docker compose down
```

停止并删除数据卷：

```bash
docker compose down -v
```

删除数据卷会清空数据库、对象存储和向量库数据，请谨慎使用。
