# DevVault 手机版（Kotlin Multiplatform）

DevVault 的 Android / iOS 客户端，基于 **Kotlin Multiplatform + Compose Multiplatform**：数据层、加密、同步和全部界面都在 `shared` 模块中，两个平台共用同一套代码，界面与桌面端保持一致（设计变量、图标、卡片与列表样式均从桌面端 `styles.css` / `Icon.tsx` 移植）。

用于替代此前的 Flutter 版本（`../dev-vault-mobile`），同步协议与数据格式不变，**服务端无需改动**。

桌面端项目位于 `../dev-vault`，下文中的桌面端文件路径均相对于该目录；同步协议见 `../dev-vault/docs/server-api.md`。

## 功能

- **账号密码**：按项目 / 类型 / 收藏 / 即将过期筛选与搜索；与桌面端相同的卡片（点击复制、密码显隐、固定验证码高亮、TOTP 动态码与倒计时圈、自定义字段与敏感字段、有效期提示）；新建 / 编辑 / 复制为新记录 / 删除；密码生成
- **待办**：统计卡片筛选（进行中 / 最高优先级 / 今天到期 / 已逾期 / 已完成）；按状态 / 优先级 / 项目分组并可折叠；快速添加；详情页逐字段即改即存（状态、优先级、开始 / 截止日期、提醒、重复、项目、标签、描述）、子任务、进展记录；重复任务完成后自动生成下一次；清理已完成
- **项目**：底部弹出的项目切换（相当于桌面端侧边栏），新建 / 编辑 / 删除
- **云同步**：登录 / 注册 / 修改主密码 / 退出；修改后 3 秒自动同步、每 2 分钟及回到前台时同步；登录失效自动退出并提示
- **兼容线路**：与桌面端 `cloud-http.ts` 相同，对 `https://vault.749log.com` 先常规探测，若网络中断携带 SNI 的 TLS 握手，自动改用不带 SNI 的连接（仍校验证书链与主机名）。目前仅 Android 支持，iOS 的 URLSession 无法关闭 SNI
- **安全**：密码、TOTP 密钥、敏感字段和登录令牌用设备密钥（Android Keystore / iOS 钥匙串保护）加密存储；可选应用锁（指纹 / 面容 / 锁屏密码，离开超过 30 秒需重新验证）
- 跟随系统深色模式（使用桌面端的深色配色）

## 与桌面端的对应关系

| 手机端（`shared/src/commonMain/kotlin/com/devvault/`） | 桌面端（`../dev-vault/`） | 说明 |
| --- | --- | --- |
| `crypto/E2ee.kt`、`Hash.kt`、`Scrypt.kt` | `src/main/e2ee.ts` | scrypt → HKDF → AES-256-GCM，逐字节兼容（含 NFKC 规范化） |
| `data/Schema.kt` | `src/main/db.ts` | 与 v7 结构一致，含同步触发器和内置优先级 / 状态的固定 uid |
| `data/Repository.kt` | `src/main/repo.ts` | 状态与完成标记联动、重复任务、子任务跟随父任务项目等规则 |
| `sync/SyncEngine.kt`、`Cloud.kt` | `src/main/sync.ts`、`cloud.ts` | 拉取 → 合并 → 推送，后写者胜、删除优先；记录明文结构与桌面端相同 |
| `totp/Totp.kt` | `src/renderer/src/totp.ts` | RFC 6238，支持 otpauth:// |
| `ui/theme/Theme.kt`、`Icons.kt`、`ui/Constants.kt` | `styles.css`、`Icon.tsx`、`constants.ts` | 颜色变量、图标路径、类型字段名 |

平台相关代码很少：`androidMain` / `iosMain` 中只有随机数、AES-GCM（Android 用 javax.crypto，iOS 用共享代码中的纯 Kotlin 实现）、设备密钥存储、系统身份验证和返回键处理。

## 开发

需要 JDK 17、Android SDK（compileSdk 35）。

```bash
# Android 调试包
./gradlew :androidApp:assembleDebug
# 输出：androidApp/build/outputs/apk/debug/androidApp-debug.apk

# 单元测试（加密互通向量由桌面端同款 Node.js crypto 生成、AES-GCM 两种实现互相校验、TOTP RFC 向量）
./gradlew :shared:testDebugUnitTest

# 额外访问真实服务器的只读接口，验证不带 SNI 的兼容线路
DEVVAULT_NET_TEST=1 ./gradlew :shared:testDebugUnitTest
```

### iOS

iOS 目标只能在 macOS 上编译（Windows / Linux 上 Gradle 会自动跳过）。工程用 [XcodeGen](https://github.com/yonaskolb/XcodeGen) 生成：

```bash
brew install xcodegen
cd iosApp && xcodegen      # 生成 iosApp.xcodeproj
open iosApp.xcodeproj      # 编译时会自动调用 Gradle 构建 Shared.framework
```

最低支持 iOS 15。界面全部由 Compose 绘制，Swift 端只有一个承载视图（`iosApp/iosApp/ContentView.swift`）。

## 暂未支持

- 系统通知提醒（提醒时间会同步，由桌面端弹出通知）
- 附件（桌面端附件本身也不参与同步）
- 看板 / 日历视图（手机上使用列表与分组）
- 在手机上编辑优先级与状态的配置（在桌面端配置后自动同步）
- 数据导入 / 导出
- release 签名配置（`androidApp/build.gradle.kts` 中 release 目前未配置签名）
