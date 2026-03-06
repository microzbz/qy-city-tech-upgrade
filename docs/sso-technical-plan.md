# SSO 单点登录技术方案（评审稿）

## 1. 目标与范围
- 对接东莞市企业综合服务平台，接收回调参数 `ecspcode`，实现本系统免密登录。
- 后端按接口规范调用：
  - `1.2 访问授权接口 /api/access`
  - `1.4 获取外网登录用户信息 getUserInfoByToken`
- 成功后同步（创建或更新）本地 `sys_user` 与 `enterprise_profile`，并签发本系统 JWT。

## 2. 关键前提
- 对接端回调地址形态：`https://{ip}:{port}/sso#?ecspcode=${ssoCode}`
- `#` 后参数不会发送到后端，请由前端页面读取 `ecspcode` 后再请求后端免密登录接口。
- 业务约定：前端可将 `ecspcode` 放入请求体字段 `token` 传给本系统免密接口（这是“本系统接口参数”，不是企服平台 `/access` 的 `token`）。

## 3. 总体流程
1. 用户在企服平台完成登录。
2. 浏览器跳转到前端 `/sso#?ecspcode=...`。
3. 前端 `SsoCallback` 页面提取 `ecspcode`，调用本系统免密接口 `POST /api/auth/sso-login`。
4. 后端免密接口执行：
   1. 调企服平台 `/token` 获取平台访问凭证 `platformToken`。
   2. 调企服平台 `/access`：
      - `token = platformToken`
      - `apiName = getUserInfoByToken`
      - `data = DES("tokenCode={ecspcode}")`
   3. 先对 `/access` 返回 `data` 做 DES 解密，得到密文用户串。
   4. 取 `ecspcode` 前 16 位作为 SM4 密钥，对用户串做 SM4 解密（使用 `src/main/java/com/qy/citytechupgrade/utils/SM4Utils.java`）。
   5. 解析 SM4 解密后的 JSON，得到统一登录用户信息。
   6. 本地用户/企业幂等同步（upsert）。
   7. 签发本系统 JWT 并返回前端。
5. 前端写入 `ctu_token`，拉取 `/api/auth/me`，按角色跳转系统首页。

## 4. 接口设计（本系统）

## 4.1 免密登录接口
- `POST /api/auth/sso-login`
- 认证：`permitAll`（仅该接口放开）
- 请求示例：

```json
{
  "token": "ecspcode_from_callback"
}
```

- 成功响应示例（复用当前登录返回结构）：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "accessToken": "jwt",
    "userInfo": {
      "userId": 123,
      "username": "DG_xxx",
      "displayName": "张三",
      "enterpriseId": 456
    },
    "roles": ["ENTERPRISE_USER"]
  }
}
```

## 4.2 异常码建议
- `ecspcode` 缺失/空：返回业务错误（400）
- `ecspcode` 长度小于 16：返回业务错误（400）
- 企服接口失败：返回业务错误（400）
- SM4 解密失败：返回业务错误（400）
- 用户信息无法解析：返回业务错误（400）
- 本地同步失败：返回系统错误（500）

## 5. 企服平台调用规则
- `/token`：使用 `time + policyName + policyPwd` 的 32 位小写 MD5 作为 `loginSign`。
- `/access`：
  - `token` 必须是 `/token` 返回的平台访问令牌。
  - `apiName = getUserInfoByToken`
  - `data` 为 DES/ECB/PKCS5Padding 加密的参数串：`tokenCode={ecspcode}`
- `/access` 返回 `data` 处理顺序：
  1. DES 解密得到中间字符串；
  2. 取 `ecspcode.substring(0,16)` 作为 SM4 Key；
  3. 调用 `SM4Utils.decryptData_ECB(...)`（如对方联调要求 CBC，再切换 `decryptData_CBC(...)`）；
  4. 对 SM4 解密结果做 JSON 解析。

## 6. 数据同步策略（幂等）

## 6.1 用户同步（sys_user）
- 主键匹配优先级建议：
  1. 外部唯一账号（如 `id`）映射为本地 `username`（建议前缀：`DG_`）
  2. 若历史数据存在同用户名则更新，不重复创建
- 字段建议：
  - `username`: `DG_{id}` 或稳定唯一值
  - `display_name`: `userName`（缺失可回退 `id`）
  - `status`: `ACTIVE`
  - `enterprise_id`: 关联本地企业主键（可空，后续补齐）
  - `password_hash`: 生成随机高强度口令后 BCrypt 存储（仅满足非空约束，不用于实际登录）
- 角色建议：
  - 默认绑定 `ENTERPRISE_USER`

## 6.2 企业同步（enterprise_profile）
- 匹配键建议：
  1. 有统一社会信用代码时：按 `credit_code` 幂等 upsert
  2. 无统一社会信用代码时：使用稳定占位码（示例：`ECSP_{parUserId}`），避免 `NULL`
- 字段建议：
  - `credit_code`: 统一社会信用代码或稳定占位码（建议保持非空）
  - `enterprise_name`: 企业名称（无则回退联系人/账号名）
  - `legal_person`: `legalPerson`
  - `contact_name`: `linkPersonName`
  - `contact_phone`: `telPhone`
  - `data_source`: `SSO`

## 7. 为什么不建议将 enterprise_profile.credit_code 改为可空
- 现有代码与库结构以 `credit_code` 作为企业主数据关键识别字段。
- 可空后会降低幂等能力，增加重复企业和脏数据风险。
- 更稳妥方案是“无证件号时写入稳定占位码”，而非 `NULL`。

## 8. 安全与风控
- 仅放开 `POST /api/auth/sso-login`，其余接口继续 JWT 鉴权。
- `ecspcode` 一次性使用（可选：短期缓存去重，防重放）。
- 全链路记录审计日志（外部登录成功/失败、用户同步、企业同步）。
- 不落库明文 `ecspcode`、不打印敏感字段明文。

## 9. 前端落地建议
- 新增独立页面组件（如 `frontend/src/views/SsoCallbackView.vue`）。
- 页面行为：
  1. 解析 `location.hash` 中 `ecspcode`
  2. 调 `/api/auth/sso-login`
  3. 保存 `ctu_token`
  4. 拉 `/api/auth/me`
  5. 跳转业务首页
- 失败时展示明确错误并提供“返回登录页”。

## 10. 配置项建议
- 复用现有外部企服配置：
  - `app.external.qf.enabled`
  - `app.external.qf.base-url`
  - `app.external.qf.policy-name`
  - `app.external.qf.policy-pwd`
- 可新增：
  - `app.external.qf.sso-api-name=getUserInfoByToken`
  - `app.sso.user.default-role=ENTERPRISE_USER`

## 11. 实施清单（最小改动）
1. 后端新增 `/api/auth/sso-login`（放行）。
2. 复用/扩展 `QfClientService` 增加 `getUserInfoByToken` 调用。
3. 增加用户/企业 upsert 服务。
4. 前端新增 `/sso` 回调页与路由。
5. 联调并补充审计与异常提示。

## 12. 待确认项
- `1.4` 返回字段中企业唯一标识的稳定来源（`idcardnumber`、`parUserInfo`、`parUserId` 的优先级）。
- SSO 登录用户默认角色是否固定为 `ENTERPRISE_USER`。
- 是否需要支持“同一人多企业切换”。
- 是否要求对 `ecspcode` 做服务端一次性校验/缓存防重放。
