# DevVault 手机版（Android 原生）

DevVault 的 Android 客户端，**Kotlin + XML（ViewBinding）多模块工程**，不使用 Compose。数据层、加密和同步逻辑与此前的 Kotlin Multiplatform 版本一致，界面改为原生 View 实现。模块划分、验证方式和设备验收步骤见 [ANDROID_NATIVE.md](ANDROID_NATIVE.md)。

用于替代此前的 Flutter 版本（`../dev-vault-mobile`），同步协议与数据格式不变，**服务端无需改动**。

桌面端项目位于 `../dev-vault`，下文中的桌面端文件路径均相对于该目录；同步协议见 `../dev-vault/docs/server-api.md`。

## 功能

- **账号密码**：按项目 / 类型 / 收藏 / 即将过期筛选与搜索；与桌面端相同的卡片（点击复制、密码显隐、固定验证码高亮、TOTP 动态码与倒计时、自定义字段与敏感字段、有效期提示）；新建 / 编辑 / 复制为新记录 / 删除；密码生成
- **待办**：筛选（进行中 / 最高优先级 / 今天到期 / 已逾期 / 已完成）；按状态 / 优先级 / 项目分组；快速添加；详情编辑（状态、优先级、开始 / 截止日期、提醒、重复、项目、标签、描述）、子任务、进展记录；重复任务完成后自动生成下一次；清理已完成
- **项目**：底部弹出的项目切换（相当于桌面端侧边栏），新建 / 编辑 / 删除
- **云同步**：登录 / 注册 / 修改主密码 / 退出；修改后 3 秒自动同步、每 2 分钟及回到前台时同步；登录失效自动退出并提示
- **兼容线路**：与桌面端 `cloud-http.ts` 相同，对 `https://vault.749log.com` 先常规探测，若网络中断携带 SNI 的 TLS 握手，自动改用不带 SNI 的连接（仍校验证书链与主机名）
- **账号安全策略**（与桌面端相同，随账号同步到所有设备）：是否允许导出、退出登录（含登录失效）时是否清除本机数据；修改需安全码，安全码无法找回或重置，连续输错 5 次锁定 30 秒。受限账号的数据不会在切换账号时合并到其他账号
- **安全**：密码、TOTP 密钥、敏感字段和登录令牌用 Android Keystore 保护的设备密钥加密存储；可选应用锁（指纹 / 锁屏密码，离开超过 30 秒需重新验证）；防截屏；剪贴板标记为敏感内容
- 跟随系统深色模式（使用桌面端的深色配色）

## 与桌面端的对应关系

| 手机端 | 桌面端（`../dev-vault/`） | 说明 |
| --- | --- | --- |
| `core/security/.../crypto/E2ee.kt`、`Hash.kt`、`Scrypt.kt` | `src/main/e2ee.ts` | scrypt → HKDF → AES-256-GCM，逐字节兼容（含 NFKC 规范化） |
| `core/data/.../data/Schema.kt` | `src/main/db.ts` | 与 v7 结构一致，含同步触发器和内置优先级 / 状态的固定 uid |
| `core/data/.../data/Repository.kt` | `src/main/repo.ts` | 状态与完成标记联动、重复任务、子任务跟随父任务项目等规则 |
| `core/data/.../sync/SyncEngine.kt`、`Cloud.kt` | `src/main/sync.ts`、`cloud.ts` | 拉取 → 合并 → 推送，后写者胜、删除优先；记录明文结构与桌面端相同 |
| `core/security/.../totp/Totp.kt` | `src/renderer/src/totp.ts` | RFC 6238，支持 otpauth:// |
| `core/ui/.../Constants.kt`、`DvIcon.kt`、`res/values*/colors.xml` | `constants.ts`、`Icon.tsx`、`styles.css` | 类型字段名、图标路径、颜色变量 |

## 开发

需要 JDK 17、Android SDK（compileSdk 35）。

```bash
# Android 调试包
./gradlew :app:assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk

# 单元测试（加密互通向量由桌面端同款 Node.js crypto 生成、AES-GCM 两种实现互相校验、TOTP RFC 向量、安全码）
./gradlew testDebugUnitTest

# 额外访问真实服务器的只读接口，验证不带 SNI 的兼容线路
DEVVAULT_NET_TEST=1 ./gradlew :core:data:testDebugUnitTest

# 界面冒烟测试（独立包名 com.devvault.app.uitest，只用虚构数据）
./gradlew :app:connectedDebugAndroidTest -PuiPreview
```

## 暂未支持

- 系统通知提醒（提醒时间会同步，由桌面端弹出通知）
- 附件（桌面端附件本身也不参与同步）
- 看板 / 日历视图（手机上使用列表与分组）
- 在手机上编辑优先级与状态的配置（在桌面端配置后自动同步）
- 数据导入 / 导出
- release 签名配置（`app/build.gradle.kts` 中 release 目前未配置签名）
- iOS：此分支只包含 Android；iOS 版本在 `main` 分支的 Kotlin Multiplatform 工程中
