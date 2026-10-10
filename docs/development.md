# 轻待办 · QingTodo

轻量、离线、原生 Android 待办清单。Java + Android 原生控件，SQLite 保存数据，没有 WebView，也不需要网络权限。

## 使用

1. 安装 `qingtodo-v1.3.0.apk`，适用于 Android 8.0 及以上版本。已安装旧版时直接覆盖安装，事项、完成状态和模板保留。
2. 在「待办」页点「添加事项」，填写名称和可选备注。
3. 点击事项或圆圈切换完成状态；长按事项可编辑。
4. 事项右侧的 `⋮` 可编辑、保存为模板或删除。删除后的提示条通常提供 10 秒撤销窗口；切换页面、筛选或旋转屏幕时保留原来的撤销期限。系统无障碍设置可延长该期限，新的操作提示会替换原提示。
5. 在「模板」页创建、修改、删除预设，点「套用」直接添加一个新的未完成事项。模板支持反复套用，事项的完成状态彼此独立。
6. 添加事项时也可先选模板，再修改内容；勾选「同时保存为模板」可以一起保存。
7. 切到「已完成」筛选后点「清除已完成」，一键移除全部已完成事项；通常有 10 秒可点击「撤销」。未完成事项和模板继续保留。
8. 点击页面右上角的调色盘进入「颜色主题」，点击颜色即可即时预览和应用，点击「完成」返回。主题会自动保存，深浅外观跟随系统。

支持全部／未完成／已完成筛选、完成进度、随系统切换深色模式。首次运行提供三个可以编辑或删除的示例模板，待办列表初始为空。

## 1.3.0 更新

- 在「已完成」筛选里新增「清除已完成」，批量清理已完成事项，支持原样撤销；没有已完成事项时隐藏入口。
- 批量删除和快照写入使用同一 SQLite 事务，撤销同样使用事务。快照留在数据库中，旋转时只保存标记和期限，避免长备注、较大批次塞满页面状态。
- 批量撤销保留原来的期限，支持切页、筛选、换色和页面重建；后续操作提示会替换提示条，到期清理临时快照。
- 名称验证拒绝只有零宽、格式或附加标记的输入，同时保留正常标题内的 emoji 连接符。
- 长标题缩略按完整字符边界截断，避免家庭、国旗等复合 emoji 拆开。
- 编辑草稿在旋转后恢复光标、选区和备注焦点；模板「查看」提示也会恢复原来的跳转操作及到期时间。
- 按钮改用原生文字布局自动决定高度，修正 1.5 倍字体下估算行高与中文实际行高不一致；覆盖筛选、预设、保存、主题完成和关于按钮。
- 数据库由版本 1 升级到 2，新增临时批量撤销表，保留原事项和模板。

## 1.2.0 更新

- 8 种配色：鸢尾紫、晴空蓝、薄荷绿、湖水青、樱花粉、暖杏橙、珊瑚红、雾霭灰蓝，每种都有浅色和深色版本。
- 主题统一应用于按钮、筛选、进度条、勾选、提示条、弹窗、输入光标和选中文字；设置即时生效并保留到下次启动。
- 主题选择器显示当前颜色，旋转时恢复当前弹窗；换色保留页面、滚动位置、数据以及原有撤销期限和「查看」操作。
- 修复全角空格、不可换行空格被当作有效标题的问题，名称和备注只去除首尾空白，保留中间空格和 emoji；旧版记录撤销删除时按原内容恢复。
- 调整浅色界面的次要文字和完成文字，提高可读性；提示条避让左右系统安全区，大字体下的弹窗按钮按文字高度调整。
- 滚动位置恢复改为等待原生布局完成，增加长清单刷新与跨页滚动检查。

## 1.1.0 更新

- 底栏文字和图标水平居中，按钮内部图标与文字垂直居中；底栏随字体实际高度调整，已验证系统 1.5 倍字体。
- 添加按钮放到独立区域，不再覆盖列表底部的编辑、删除或套用操作。
- 页面双向滑动与淡入淡出、筛选淡入、底部弹窗上滑、勾选反馈与提示条出现动画。系统关闭动画时，页面和勾选反馈立即更新。
- 切页和旋转时保留删除撤销；修复重复点保存、旧弹窗回调清空新弹窗引用、长标题截断半个 emoji 的问题。
- 套用模板后，即使先手动回到主页，再点提示中的「查看」，也会正确刷新未完成清单。完成动画期间暂时停用该事项的操作，避免使用过期状态。

数据保存在手机的应用私有数据库中；关闭应用或重启手机后仍保留。卸载应用或清除应用数据会删除数据。本版不提供云同步、提醒、账号或导入导出。

## 源码

```text
app/src/main/java/com/qingtodo/app/MainActivity.java  原生界面与交互
app/src/main/java/com/qingtodo/app/TodoStore.java     SQLite 数据存储
app/src/main/java/com/qingtodo/app/ColorTheme.java    8 种配对明暗主题
app/src/main/res/                                   明暗主题、矢量图标与弹窗动画
build_apk.py                                        SDK 命令行构建与签名
tests/                                             Android 端到端验收、回归与升级检查
```

## 构建 APK

需要 Java 17 JDK、Python 3 和 Android SDK（Platform 36，Build Tools 35.0.0 或更新）。

```bash
export QINGTODO_ANDROID_SDK=/path/to/Android/Sdk
export QINGTODO_KEYSTORE_PASSWORD="你的本地密钥密码"
python3 build_apk.py
```

输出为 `build/qingtodo.apk`。脚本依次调用 AAPT2、Java 编译器、D8、zipalign 和 apksigner，并校验输出 APK 的签名和对齐。

只有 JRE 时，可安装 Eclipse ECJ 编译器后设置 `ECJ_JAR=/path/to/ecj.jar`。也可以在 Android Studio 中打开此目录，使用 Gradle 8.11.1、AGP 8.10.1 和 JDK 17 构建。Gradle 包装器和 SDK 不包含在源码压缩包中。

发布仓库不包含签名密钥。构建脚本从 `QINGTODO_KEYSTORE_PASSWORD` 读取密码；没有本地密钥时创建新的密钥。新密钥签名的 APK 无法覆盖安装原发布版本。维护者应在本地恢复原签名密钥、保持包名并递增 `versionCode`，密钥和密码均不得提交。

## 运行验收

需要安装 ECJ 并设置 `ECJ_JAR`，主应用构建完成后：

```bash
python3 tests/build_tests.py
adb install -r build/qingtodo.apk
adb install -r build/tests/qingtodo-acceptance.apk
adb shell am instrument -w com.qingtodo.acceptance/com.qingtodo.acceptance.AcceptanceRunner
```

验收会清空测试设备中此应用的数据，然后检查添加、空名称校验、完成切换、筛选、编辑、模板保存和重复套用、删除撤销及关闭重开后的持久化。请在模拟器或独立测试设备中运行。

升级检查先在旧版安装下执行下面的 seed 命令，覆盖安装 1.3.0 后执行 verify 命令。

```bash
adb shell am instrument -w -e phase seed com.qingtodo.acceptance/com.qingtodo.acceptance.UpgradeRunner
# 覆盖安装新版 APK 后：
adb shell am instrument -w -e phase verify com.qingtodo.acceptance/com.qingtodo.acceptance.UpgradeRunner
```

```bash
adb shell am instrument -w com.qingtodo.acceptance/com.qingtodo.acceptance.RegressionRunner
adb shell settings put system font_scale 1.5
adb shell am instrument -w -e large true com.qingtodo.acceptance/com.qingtodo.acceptance.RegressionRunner
adb shell settings put system font_scale 1.0
```

`RegressionRunner` 检查实际文字与图标坐标、按钮和列表是否重叠、保存防重、编辑草稿恢复、弹窗替换、跨页与旋转撤销、模板跳转和快速打断动画。它会添加和清理测试事项，并临时修改模拟器动画设置。`PreviewRunner` 将测量后的原生 View 绘制为预览图，示例内容仅存在于测试设备；APK 首次运行不会包含这些示例事项。测试结果在 `tests/acceptance-results.txt`，界面预览在 `docs/previews/`。

新增检查在 Android 16 模拟器上运行：

```bash
adb shell am instrument -w com.qingtodo.acceptance/com.qingtodo.acceptance.ScrollRunner
adb shell am instrument -w com.qingtodo.acceptance/com.qingtodo.acceptance.ValidationRunner
adb shell am instrument -w -e theme light com.qingtodo.acceptance/com.qingtodo.acceptance.ThemeRunner
adb shell am instrument -w -e phase verify com.qingtodo.acceptance/com.qingtodo.acceptance.ThemeRunner
adb shell am instrument -w -e theme dark com.qingtodo.acceptance/com.qingtodo.acceptance.ThemeRunner
```

`ThemeRunner` 逐一操作 8 个原生主题按钮，检查明暗配色的文字对比度、实际按钮颜色、设置持久化、未知主题回退、旋转恢复、数据不变、换色后的删除撤销与模板跳转。完整运行结束时留下樱花粉供下一次独立进程的 verify 阶段验证；测试会修改模拟器主题设置，并清理其临时事项和模板。大字体检查使用 `-e large true`。`ValidationRunner` 检查 Unicode 空白和旧版记录恢复，`ScrollRunner` 检查长清单在刷新及切页后的实际滚动坐标。


## 1.3.0 专项检查

```bash
adb shell am instrument -w -e expect fixed com.qingtodo.acceptance/com.qingtodo.acceptance.AuditRunner
adb shell am instrument -w com.qingtodo.acceptance/com.qingtodo.acceptance.ClearCompletedRunner
adb shell am instrument -w -e large true com.qingtodo.acceptance/com.qingtodo.acceptance.ClearCompletedRunner
```

`AuditRunner` 有 `expect old`（复现 1.2.0）和 `expect fixed`（核验 1.3.0）两种模式；`ClearCompletedRunner` 在专用测试设备上暂存并替换任务数据，验证原生按钮、批量撤销、页面状态、实际到期清理、事务回滚及 600 条长备注记录，结束后恢复原任务。请只在测试设备上运行这些验收检查。`UpgradeRunner` 当前核验 1.2.0 → 1.3.0 的签名覆盖安装和数据库升级。专项日志和缺陷报告随源码提供。

## 构建环境说明

上述 Python SDK 脚本按 Linux/macOS 命令行工具路径编写；Windows 请使用 Android Studio 的 Gradle 构建。源码未包含 Gradle Wrapper。仓库未包含发布签名，Android Studio 可使用默认 debug 签名构建调试版。Gradle 发布签名从 `QINGTODO_KEYSTORE_PASSWORD` 读取密码；若不配置签名，release 输出为未签名 APK。测试构建还需要 `ECJ_JAR`，与主应用使用同一个本地密钥。
