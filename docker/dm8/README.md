# DM8.1 迁移说明

1. 启动达梦容器

```bash
cd /home/bz/workspace/city-tech-upgrade/docker/dm8
docker-compose up -d
```

2. 初始化 DM8 表结构和基础选项数据

```bash
cd /home/bz/workspace/city-tech-upgrade
bash docker/dm8/init-dm8.sh
```

3. 从旧 MySQL 迁移历史数据到 DM8

```bash
cd /home/bz/workspace/city-tech-upgrade
MYSQL_URL='jdbc:mysql://127.0.0.1:3306/city_tech_upgrade?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai' \
MYSQL_USERNAME='qiyuan' \
MYSQL_PASSWORD='qiyuan' \
DM_URL='jdbc:dm://127.0.0.1:5236' \
DM_USERNAME='SYSDBA' \
DM_PASSWORD='SysdbA123' \
bash docker/dm8/migrate-mysql-to-dm8.sh
```

4. 启动应用

应用默认已经切到 DM8，连接参数来自环境变量：

- `DM_JDBC_URL`
- `DM_JDBC_USERNAME`
- `DM_JDBC_PASSWORD`
