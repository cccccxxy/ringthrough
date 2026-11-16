# 响铃白名单 · RingThrough

> 手机静音 / 振动时，**只有你指定的人**打进来会响，其他人照样安静。

一个装在 Android 手机上的小工具，为「全天静音，但家里人的电话必须接」这种场景做的。
不联网、不上传数据、不要账号。

```
静音中 ──┬── 白名单来电 ──> 响铃（走闹钟通道，静音也出声）
         └── 其他来电   ──> 保持静音
```

**[⬇ 下载 APK v1.1](https://github.com/cccccxxy/ringthrough/releases/latest)** · Android 9+ · 37 KB

## 特性

- **静音也能响**：铃声通过 `USAGE_ALARM` / `STREAM_ALARM` 通道播放。静音模式只静音铃声和通知通道，闹钟通道默认仍然出声。
- **只响指定号码**：白名单支持从通讯录选人，也支持手动输入；按号码后 11 位匹配，`+86` 前缀、空格横线都能对上。
- **音量可调**：响铃时临时把闹钟音量设成设定百分比（10%–100%），响完恢复原音量，不改系统设置。
- **响完自动停**：接听、挂断、45 秒超时、或点通知里的「停止响铃」都会立刻停。
- **两种来电检测**：系统原生「来电筛选」优先，拿不到角色时用后台监听兜底；两条路径同时开也不会重复响。
- **零常驻开销（默认）**：来电筛选模式下不需要常驻通知，也不会耗电。
- **无网络权限**：`AndroidManifest.xml` 里没有 `INTERNET`，想传数据也传不出去。

## 安装

从 [Releases](https://github.com/cccccxxy/ringthrough/releases/latest) 下载 `ringthrough.apk` 安装，或者自己构建（见下文）。

首次打开按提示做三件事：

1. **授予「来电筛选」**：点「去授权」，系统弹窗里把角色交给本 App。这是 Android 原生的来电筛选接口，用来在振铃前拿到来电号码。
2. **添加白名单**：从通讯录选人，或手动输入号码。
3. **试听 6 秒**：确认能听到声音，同时验证音量是否合适。

> 小米 / 红米（HyperOS、MIUI）还需要：设置 → 应用设置 → 本 App → **允许自启动**，省电策略设为 **无限制**。否则后台被清理后不会响。

### 「来电筛选」授权不了怎么办

部分 ROM 把「来电筛选 / 来电识别」这个角色藏起来了，`RoleManager` 会返回角色不可用。
这时回到 App 打开 **后台监听**，按提示授予「电话」和「通话记录」权限即可 —— 效果一样，代价是多一条常驻通知（可在通知上一键关闭）。

## 它是怎么做到的

核心只有一句话：**绕开被静音的铃声通道，改用闹钟通道发声。**

Android 的静音 / 振动只作用于 `STREAM_RING`、`STREAM_NOTIFICATION` 这类通道；闹钟、媒体通道不受影响。
所以来电时可以：

1. 临时把 `STREAM_ALARM` 音量设成用户设定值（`MODIFY_AUDIO_SETTINGS`，普通权限）；
2. 用 `MediaPlayer` + `AudioAttributes.USAGE_ALARM` 循环播放选定的铃声；
3. 播完 / 接听 / 挂断后停掉，并恢复原音量。

系统自己的来电界面照常弹出（它只是没声音），所以接听、挂断、震动都不用我们接管。

### 两种检测路径

| 路径 | 实现 | 代价 | 何时用 |
| --- | --- | --- | --- |
| 来电筛选（默认） | `CallScreeningService` + `RoleManager.ROLE_CALL_SCREENING` | 无常驻开销 | 系统开放了这个角色 |
| 后台监听（兜底） | 前台服务（`specialUse`）+ `PhoneStateListener` | 常驻通知，需要电话 / 通话记录权限 | 角色不可用，或想更保险 |

两条路径都会把来电交给同一套 `RingEngine`，8 秒内的重复通知只响一次。

## 权限说明

| 权限 | 用途 | 是否必需 |
| --- | --- | --- |
| 来电筛选角色 | 拿到来电号码 | 二者至少其一 |
| `READ_PHONE_STATE` / `READ_CALL_LOG` | 后台监听模式拿号码、接听后停铃声 | 仅后台监听 |
| `READ_CONTACTS` | 从通讯录选人 | 可选 |
| `POST_NOTIFICATIONS` | 响铃通知 + 「停止响铃」按钮 | 建议授予 |
| `MODIFY_AUDIO_SETTINGS` | 临时调整闹钟音量 | 必需 |
| `FOREGROUND_SERVICE(_SPECIAL_USE)` | 后台监听常驻 | 仅后台监听 |

## 构建

不需要 Gradle，`aapt` → `javac` → `d8` → `zipalign` → `apksigner` 一条龙：

```bash
sudo apt-get install -y openjdk-17-jdk-headless aapt zipalign apksigner unzip zip
# android.jar 来自 Android SDK:
#   sdkmanager "platforms;android-34" "platforms;android-36" "build-tools;36.0.0"
./build.sh          # 产物 dist/ringthrough.apk
```

几个踩过的坑，写在这里省得别人再踩：

- **arm64 Linux 上 Android SDK 自带的 `aapt2` / `zipalign` 是 x86_64 二进制**，直接跑会报 `cannot execute: required file not found`。这里改用 Ubuntu 仓库里的 arm64 版 `aapt`（v1）、`zipalign`、`apksigner`；`d8` 用 build-tools 里的 jar（Java 跨架构）。
- **Ubuntu 的 `aapt`（Android 14 时期）读不了 API 36 的 `android.jar` 资源表**，会报 `No resource found ... attr 'android:layout_width'`。所以链接资源用 `platforms/android-34/android.jar`，`javac` / `d8` 仍用 API 36，`targetSdkVersion=36`。
- Debian/Ubuntu 的 `apksigner` 在 `minSdkVersion >= 28` 时只产出 v3 签名，这是正常的，Android 9+ 都能装。

## 目录结构

```
AndroidManifest.xml              权限、服务、广播声明
res/
  layout/activity_main.xml       单页界面
  drawable/                      卡片、按钮、矢量图标
  values/                        颜色、样式、文案
java/com/dsh/ringthrough/
  MainActivity.java              白名单管理、权限引导、音量滑杆、试听
  ScreeningService.java          来电筛选（主路径）
  WatchService.java              后台监听（兜底路径）
  RingEngine.java                出声核心：闹钟通道播放、音量、计时、通知
  Prefs.java                     白名单与设置持久化
  Numbers.java                   号码归一化与后 11 位匹配
  StopReceiver.java              通知上的「停止响铃」「关闭监听」
  BootReceiver.java              开机恢复后台监听
build.sh                         无 Gradle 打包脚本
```

## 已知限制

- **系统「勿扰 → 完全静音」下谁都放不出声**，那是系统级策略，本 App 也不例外。普通静音、振动没问题。
- 接听、挂断、震动仍由系统来电界面负责，本 App 不替代拨号应用（要替代需要默认拨号器角色，代价大得多）。
- 部分 ROM 的「来电筛选」角色不可用，只能退到后台监听；后台监听在激进省电策略下可能被系统杀掉。
- 只在 Android 9+（API 29+）上适配，目标 SDK 36。

## 常见问题

**试听有声音，真来电不响？**
大概率是来电检测没生效：检查「来电筛选」是否真的被授予，或改用后台监听；小米还要看自启动和省电策略。

**响完音量变了？**
不会。响铃时改的是临时值，停止时恢复原闹钟音量；只有进程被强杀才会留在临时值上。

**能拦两个、三个号码吗？**
可以，白名单是列表，加多少都行。

**会偷偷上传通话记录吗？**
不会，App 没有 `INTERNET` 权限，代码里也没有任何网络调用。

## License

[MIT](LICENSE)
