# Android 原生 XML 客户端

分支：`feature/android-kotlin-xml`。

纯 Android 工程：Kotlin + XML + ViewBinding，`AppCompatActivity` 入口，列表使用 RecyclerView，弹窗使用
AppCompat Dialog / AlertDialog。工程中不再有 Kotlin Multiplatform、Compose 编译插件或 Compose 依赖。

## 模块

```
app ──► core:ui ──► core:data ──► core:model
 │         ▲            └──────► core:security
 ├──► feature:entries ──┤
 ├──► feature:todos ────┤
 └──► feature:settings ─┘
```

| 模块                 | 内容                                                                                                                                                                                                                        |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app`              | 应用壳：`MainActivity`（底部导航、项目切换、搜索 / 筛选、生命周期收集、应用锁遮罩）；布局 `activity_main` / `item_nav` / `dialog_project_picker` / `item_project_choice`；清单与图标、界面冒烟测试                                                                         |
| `core:model`       | 数据模型 `Models.kt`、日期工具 `Dates.kt`                                                                                                                                                                                          |
| `core:security`    | 端到端加密（scrypt / HKDF / AES-GCM）、TOTP、Android Keystore 与随机数（`Platform`）、系统身份验证（`DeviceAuth`）                                                                                                                                |
| `core:data`        | `AppStore`、SQLite 数据库与 Schema、Repository、账号安全策略、云同步（含无 SNI 兼容线路）                                                                                                                                                          |
| `core:ui`          | 共用的 XML 资源：图标 `drawable/ic_*.xml`、背景 `drawable/bg_*.xml`、控件样式 `values/styles.xml`、颜色 / 主题 / 字符串；布局 `page_form` / `view_pill` / `view_empty`。Kotlin：`VaultActivity`（宿主契约）、`NativeForm`（页面 / 弹窗外壳）、`NativeUi.kt`（绑定工具）、类型常量 |
| `feature:entries`  | 布局 `card_entry` / `item_entry_field` / `item_entry_totp` / `form_entry` / `item_extra_field`；`EntryScreens` 负责绑定：复制、收藏、TOTP、密码生成、自定义字段                                                                                    |
| `feature:todos`    | 布局 `view_todo_header` / `item_todo_stat` / `item_todo_group_header` / `card_todo_group` / `item_todo` / `view_todo_check` / `page_todo_detail` / `item_todo_comment`；`TodoScreens` 负责筛选、分组、详情即改即存、子任务、进展记录                |
| `feature:settings` | 布局 `page_settings` / `view_login` / `item_project` / `form_project` / `form_password` / `form_policy` / `item_color_swatch`；`SettingsScreens` 负责登录 / 注册、同步、主密码、安全策略、应用锁、项目管理、退出                                           |

功能模块之间互不依赖，只依赖 `core:ui` 暴露的 `VaultActivity`；`app` 负责把三个功能模块组装起来。

### 界面约定

- 页面结构、文字、尺寸、间距、静态颜色全部写在 XML（布局 + `styles.xml` + `strings.xml`）中，Kotlin 通过
  ViewBinding 只做数据绑定和事件处理，不用代码创建视图。
- 列表中重复的行（账号字段、待办、进展、项目等）各有一个 `item_*.xml`，由代码按数据 inflate。
- 只有颜色来自数据（类型、环境、项目、优先级、状态）时，才在代码中用 `shape()` 生成背景。
- 图标为矢量图，路径与桌面端 `Icon.tsx` 相同；用在文字旁的图标使用 `ic_*_small.xml`（14–18dp）。
- 输入框统一通过 `DvInput` 样式排除系统自动填充，lint 的 `Autofill` 检查在根目录 `lint.xml` 中关闭。

### 大屏适配（横屏、折叠屏、平板、分屏）

| 窗口宽度 | 主界面 | 内容 |
| --- | --- | --- |
| < 600dp | `layout/activity_main.xml`：底部导航 | 左右 16dp 留白 |
| ≥ 600dp | `layout-w600dp/activity_main.xml`：左侧导航栏，标题 / 项目 / 搜索合并为一行 | 内容列最宽 720dp，居中 |
| 列表区域 ≥ 900dp | 同上 | 账号卡片两列，总宽最多 1200dp |

- 尺寸集中在 `core/ui/.../values/dimens.xml`（`content_max_width`、`form_max_width`、`dialog_max_width` 等）。
- 全屏表单 / 详情页（`page_form.xml`）的内容列用 `layout_constraintWidth_max` 限宽 640dp 并居中；居中弹窗最宽 520dp，项目切换最宽 560dp。
- 清单保留 `configChanges`（旋转、分屏、折叠时不重建 Activity，避免正在编辑的弹窗丢失），因此 `MainActivity.onConfigurationChanged` 在宽度跨过 600dp 时自行重新加载主界面布局；列表宽度变化时重新计算居中留白和列数。

数据层、加密和同步协议与原 `shared` 模块代码一致（`expect/actual` 已展开为 Android 实现），应用包名
`com.devvault.app` 与数据库路径不变，可以覆盖安装原客户端继续使用本机数据。

## 操作

底部切换账号、待办和设置；顶部选择项目，账号页可按类型 / 收藏 /
即将过期筛选。账号卡片内可直接复制字段、显隐密码；点击卡片进入编辑。点击待办查看详情，长按切换完成状态。

支持浅色 / 深色主题、系统栏和软键盘避让。敏感弹窗不保存明文到 Android 实例状态；旋转时保留当前
Activity，进程退出或深浅色切换重建时未保存的表单草稿会丢弃。应用启用防截屏；剪贴板标记为敏感内容。开启应用锁后，后台超过
30 秒返回会关闭敏感弹窗并要求验证。

## 验证

```powershell
# 构建、lint、单元测试
.\gradlew.bat assembleDebug lintDebug testDebugUnitTest

# 界面冒烟测试（需连接设备或模拟器）。-PuiPreview 使用独立包名 com.devvault.app.uitest，
# 只写入虚构数据，不会碰到真实保险库；测试结束后 Gradle 会自动卸载
.\gradlew.bat :app:connectedDebugAndroidTest -PuiPreview
```

冒烟测试每一步会截图到 `/sdcard/Android/data/com.devvault.app.uitest/files/*.png`。想保留截图时改用手动安装运行：

```powershell
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest -PuiPreview
adb shell am instrument -w -e class com.devvault.android.LayoutSmokeTest com.devvault.app.uitest.test/android.test.InstrumentationTestRunner
adb pull /sdcard/Android/data/com.devvault.app.uitest/files/ shots
adb uninstall com.devvault.app.uitest.test; adb uninstall com.devvault.app.uitest
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。

设备验收：新建项目 → 添加含密码、TOTP、自定义字段的账号 → 编辑 / 复制 / 收藏 / 筛选 →
新建待办和子任务、进展、重复任务 → 登录测试账号并同步 → 应用锁后台超过 30 秒恢复 →
深色模式和横屏。使用专门的测试账号验收云端写入及退出清除策略。

原有客户端暂未实现的通知提醒、附件、导入 / 导出仍未新增。提醒时间仍保存并同步到桌面端。
