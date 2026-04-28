# 新型技改城市平台（首版）

后端：Spring Boot 3 + MySQL 8 + JWT + RBAC  
前端：Vue3 + Vite + Element Plus

## 已实现能力

- 登录认证：`/api/auth/login`、`/api/auth/me`、`/api/auth/logout`
- 用户体系：用户、角色、用户角色绑定（企业用户/审批管理员/系统管理员）
- 企业填报：保存草稿、提交审批、我的填报、详情查询
- 行业工序设备联动：使用 `industry_process_bind`、`process_equipment_bind`
- 审批流：可配置模板，支持通过/驳回/退回
- 附件上传下载：本地文件系统
- 站内消息：审批结果通知
- 审计日志：关键操作留痕
- 企业主数据适配：本地优先，外部企服接口兜底

## 目录

- 后端代码：`src/main/java`
- 前端代码：`frontend`
- 数据库脚本：`sql/schema_v1.sql`、`sql/mysql/*.sql`

## 1. 初始化数据库

推荐方式：从达梦现库完整迁移到全新的 MySQL `city_upgrade`

```bash
bash docker/dm8/migrate-dm8-to-mysql.sh
```

说明：
- 该脚本会以达梦 `CITY_UPGRADE` 为源，删除并重建 MySQL `city_upgrade`，不会复用旧 `city_tech_upgrade` 的历史数据。
- 迁移完成后会生成全量初始化脚本 `sql/mysql/city_upgrade_full_from_dm8.sql`，以及结构脚本 `sql/mysql/city_upgrade_schema_from_dm8.sql`。
- 若你只想导入已经生成好的全量脚本，也可以执行：`mysql -h172.17.0.1 -P3306 -uqiyuan -pqiyuan < sql/mysql/city_upgrade_full_from_dm8.sql`
- `sql/schema_v1.sql` 和 `sql/mysql/00_create_database.sql` 仍保留为无外键 MySQL 基础建表脚本，目标库名也已改为 `city_upgrade`。

## 2. 启动后端

```bash
./mvnw spring-boot:run
```

默认端口：`8654`

默认数据库连接：

```properties
spring.datasource.url=jdbc:mysql://172.17.0.1:3306/city_upgrade?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=qiyuan
spring.datasource.password=qiyuan
```

### 默认账号（初始化自动创建）

- `qydevelop / qydevelop!@#123`（系统管理员）
- `approver / Admin@123`（审批管理员）
- `enterprise / Admin@123`（企业用户）

## 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认前端地址：`http://localhost:5174`，已代理 `/api` 到 `http://localhost:8654`。

## 4. 外部企服接口配置（可选）

在 `src/main/resources/application.properties` 里配置：

```properties
app.external.qf.enabled=true
app.external.qf.base-url=你的企服接口地址
app.external.qf.policy-name=你的账号
app.external.qf.policy-pwd=你的密码
app.external.qf.enterprise-api-name=enterprisePortrait
app.external.qf.sso-api-name=getUserInfoByToken
app.external.qf.system-msg-enabled=false
app.external.qf.system-msg-api-name=sendSystemMsg
app.external.qf.system-msg-biz-type=请向企服平台申请
app.external.qf.system-msg-dept-code=业务部门社统码
app.external.qf.system-msg-dept-name=业务部门名称
app.external.qf.system-msg-link=
```

> 当前项目已实现 token + DES 加密调用流程；`system-msg-enabled=true` 后，审批结果外发时会在原通知链路之外，顺带调用企服平台 `sendSystemMsg` 推一份站内信。

### 附件存储目录配置

```properties
# 上传文件根目录（必填，需有写权限）
app.file-storage.base-path=/tmp/city-tech-upgrade/uploads
# 预览缓存目录（可选，留空则默认 base-path/.preview）
app.file-storage.preview-base-path=
# 子目录规则（按日期自动分目录）
app.file-storage.sub-dir-pattern=yyyy/M/d
```

## 5. 主要接口

- 认证：`/api/auth/*`
- 用户：`/api/users`、`/api/roles`
- 填报：`/api/submissions/*`
- 审批：`/api/approvals/*`
- 流程模板：`/api/workflow/templates`
- 行业映射：`/api/industry/processes`、`/api/industry/equipments`
- 附件：`/api/files/upload`、`/api/files/{id}/download`
- 企业信息：`/api/enterprise/profile/by-credit-code/{creditCode}`
- 消息：`/api/notices/*`
- 审计：`/api/audit-logs`
