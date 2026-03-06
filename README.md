# 新型技改城市平台（首版）

后端：Spring Boot 3 + MySQL + JWT + RBAC  
前端：Vue3 + Vite + Element Plus

## 已实现能力

- 登录认证：`/api/auth/login`、`/api/auth/me`、`/api/auth/logout`
- 用户体系：用户、角色、用户角色绑定（企业用户/审批管理员/系统管理员）
- 企业填报：保存草稿、提交审批、我的填报、详情查询
- 行业工序设备联动：复用 `industry_code_process_map`、`process_equipment_map`
- 审批流：可配置模板，支持通过/驳回/退回
- 附件上传下载：本地文件系统
- 站内消息：审批结果通知
- 审计日志：关键操作留痕
- 企业主数据适配：本地优先，外部企服接口兜底

## 目录

- 后端代码：`src/main/java`
- 前端代码：`frontend`
- 数据库脚本：`sql/schema_v1.sql`

## 1. 初始化数据库

```bash
mysql -uqiyuan -pqiyuan city_tech_upgrade < sql/schema_v1.sql
```

说明：
- 行业工序设备映射表 `industry_code_process_map`、`process_equipment_map` 已保留并复用。
- 若你未导入映射数据，可先执行你已有的 Excel 导入脚本。

## 2. 启动后端

```bash
./mvnw spring-boot:run
```

默认端口：`8654`

### 默认账号（初始化自动创建）

- `admin / Admin@123`（系统管理员）
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
```

> 当前项目已实现 token + DES 加密调用流程；具体 `enterprise-api-name` 需要按你联调接口名确认。

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
