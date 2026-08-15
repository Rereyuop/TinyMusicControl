# 音量控制

这是一个面向 Android 7 及以上版本的媒体音量控制应用。界面显示当前媒体音量、播放信息、本机局域网 IPv4 地址和网络连接状态。

## 首个发布版本

`0.0.15` 是项目首个用于对外发布的版本，支持 Android 7.0（API 24）及以上系统。它提供媒体音量加减、静音和环形拖动调节，支持显示当前媒体信息与播放控制，并能展示本机局域网 IPv4 地址、监听网络连接与断开状态。波浪音量环已针对项目使用的旧版 Material3 和 Android 7 设备完成兼容处理。项目目前主要在魅族 15（Android 7.1.1）与华为 STF-AL00（Android 9）真机上调试；向其他平台分发前，应使用可长期保存的同一正式签名证书签署 APK，以保证后续版本能够覆盖升级。

## 调试设备背景

项目目前使用魅族 15 和华为 STF-AL00 两台真机进行兼容性与安装调试。设备信息来自 ADB 实机读取。

### 魅族 15

| 项目 | 信息 |
| --- | --- |
| 厂商与型号 | Meizu 15 |
| Android 版本 | Android 7.1.1 |
| API Level | 25 |
| 系统版本 | Flyme 7.0.2.2A |
| SoC 平台 | Qualcomm Snapdragon 660（`sdm660`） |
| CPU ABI | arm64-v8a |
| 屏幕 | 1080 × 1920，480 dpi |
| 出厂 API Level | 25（Android 7.1） |
| Vendor / VNDK | 未报告独立 Vendor 版本，也未报告 VNDK 版本 |
| Project Treble | 未启用或不支持（系统属性未报告） |
| Linux 内核 | 4.4.21 |
| 安全补丁 | 2018-04-05 |
| 调试方式 | 同一局域网内通过无线 ADB 连接；当前地址为 `192.168.1.9:5555`，地址可能随 DHCP 变化 |

### 华为 STF-AL00

| 项目 | 信息 |
| --- | --- |
| 厂商与型号 | HUAWEI STF-AL00 |
| Android 版本 | Android 9 |
| API Level | 28 |
| 系统版本 | EMUI 9.1.0；`STF-AL00 9.1.0.225(C00E125R1P9)` |
| SoC 平台 | HiSilicon Kirin 960（`hi3660`） |
| CPU ABI | arm64-v8a |
| 屏幕 | 1080 × 1920，物理 480 dpi，当前覆盖为 408 dpi |
| 出厂 API Level | 24（Android 7.0） |
| Vendor / VNDK | VNDK 28（Android 9 代际） |
| Project Treble | 已启用 |
| Linux 内核 | 4.9.148 |
| 安全补丁 | 2020-05-01 |
| USB ADB 序列号 | `8BN0217B22001788` |
| 调试方式 | 已通过 USB 开启 TCP/IP ADB；当前无线地址为 `192.168.1.11:5555`，地址可能随 DHCP 变化 |

### 系统底层与升级判断

- **华为 STF-AL00：** 官方完整适配的上限按 Android 9 / EMUI 9.1 计算，当前系统已经到达该代际。设备虽已启用 Project Treble，理论上可能尝试更高 Android 版本的 GSI 或第三方系统，但驱动、相机、指纹、通信和 Bootloader 均不保证兼容，不能作为稳定调试环境。华为已停止对 [EMUI 9.1 软件平台](https://consumer.huawei.com/cn/support/content/zh-cn15990457/)提供升级和更新服务。
- **魅族 15：** 稳定底层按 Android 7.1.1 / API 25 计算。该机没有可识别的 Treble/VNDK 分层，内核为 4.4，升级更高 Android 大版本需要整套厂商驱动适配，不能仅靠通用 GSI 完成。魅族官网提供 [Flyme 8.0.5.0A 固件](https://www.flyme.cn/firmwarelist-171.html)，但 Flyme 大版本不等同于 Android 大版本；本机当前实测仍为 Android 7.1.1 / Flyme 7.0.2.2A。
- **项目兼容性基准：** 魅族 15 用于验证 Android 7.1/API 25 的低版本兼容性；华为 STF-AL00 用于验证 Android 9/API 28。若要验证 Android 10 及以上行为，需要增加相应版本的设备或模拟器，不能由这两台设备代替。

这是一台较老 Android 版本并带有 Flyme 深度定制系统的设备，因此项目把 Android 7 兼容性作为实际约束，而不只是 Gradle 配置。当前 `minSdk` 为 24，魅族 15 运行 API 25。涉及 Compose、Material3、网络回调或系统媒体接口的改动，都需要避免依赖较新 Android API；依赖升级也应先确认仍支持该设备。

Flyme 上的“通知使用权”需要由用户在系统设置中手动授予，应用才能读取当前媒体会话并显示歌曲信息和播放控制。每次向该设备安装调试包后，应立即启动 `com.chayu.volumecontrol/.MainActivity`，方便直接确认应用是否能正常进入。

## 本机 IP 实现

应用通过 Android `ConnectivityManager` 获取当前默认网络，再从该网络的 `LinkProperties.linkAddresses` 中查找 IPv4 地址。查询时会排除回环地址和链路本地地址；没有可用默认网络或读取失败时，返回“未连接网络”。

所需权限为：

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

该功能只读取网络状态和局域网地址，不会建立服务端口或向互联网发送数据，因此不需要 `INTERNET` 权限。

## 网络变化监听

页面显示期间会通过 `ConnectivityManager.registerDefaultNetworkCallback` 监听默认网络，兼容项目最低版本 Android 7（API 24）。以下变化会重新读取 IP：

- 默认网络变为可用；
- 默认网络丢失，例如关闭 Wi-Fi 或移动数据；
- 网络的链路属性变化，例如 DHCP 重新分配 IP。

关闭网络后，页面底部会立即从 IP 地址切换为红色的“网络已断开”；网络恢复后会重新显示当前 IPv4 地址。

监听器由 Compose `DisposableEffect` 管理。页面进入组合时注册，离开组合时移除回调和待处理任务，避免 Activity 销毁后继续监听或持有页面状态。
