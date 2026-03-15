# 背单词 - Anki 旗标单词弹窗复习

从 Anki 导出的 .colpkg 词库中筛选橙/红旗标单词，每 5–7 分钟随机全屏弹窗展示一个单词，30 秒后自动关闭。

## 功能

- 解析 Anki .colpkg 文件
- 筛选红色旗标 (flag=1) 和橙色旗标 (flag=2) 的单词
- 每 5–7 分钟随机弹窗显示一个单词及词义
- 全屏展示，30 秒倒计时后自动关闭
- 禁用返回键，尽量阻止用户提前关闭

## 环境要求

- Android 7.0 (API 24) 及以上

## 构建 APK（三种方式，任选其一）

### 方式一：GitHub Actions（推荐，无需本地环境）

1. 将本项目推送到 GitHub 仓库
2. 打开仓库 → **Actions** → 选择 **Build APK** 工作流
3. 点击 **Run workflow** 手动触发，或推送到 `main`/`master` 分支自动触发
4. 构建完成后，在 **Artifacts** 中下载 `app-debug-apk.zip`，解压得到 `app-debug.apk`

### 方式二：命令行构建（无 Android Studio）

1. **安装 JDK 17**  
   从 [Adoptium](https://adoptium.net/) 或 [Oracle](https://www.oracle.com/java/technologies/downloads/) 下载安装。

2. **安装 Android SDK 命令行工具（无需 Android Studio）**
   - 打开 [Android 命令行工具下载页](https://developer.android.com/studio#command-line-tools-only)
   - 下载 Windows 版 `commandlinetools`
   - 解压到例如 `C:\Android\cmdline-tools\latest`
   - 在 PowerShell 中运行：
     ```powershell
     # 安装必要组件
     C:\Android\cmdline-tools\latest\bin\sdkmanager.bat "platform-tools" "platforms;android-34" "build-tools;34.0.0"
     ```
   - 设置环境变量 `ANDROID_HOME` 指向 SDK 根目录（如 `C:\Android`）

3. **构建 APK**
   ```powershell
   cd "d:\个人工具\背单词"
   .\build-apk.ps1
   ```
   首次运行会自动安装 Gradle Wrapper。APK 输出到 `app\build\outputs\apk\debug\app-debug.apk`。

### 方式三：使用 Android Studio

1. 从 [Android 开发者官网](https://developer.android.com/studio) 下载并安装 Android Studio
2. **File → Open** 选择本项目目录
3. 等待 Gradle 同步完成
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. APK 位于 `app/build/outputs/apk/debug/app-debug.apk`

## 安装到手机

- **USB 连接**：手机开启 USB 调试后，执行 `adb install app-debug.apk`
- **直接安装**：将 `app-debug.apk` 复制到手机，在文件管理器中点击安装

## 使用说明

1. **导出 Anki 词库**：在 Anki 中打开你的牌组 → 文件 → 导出 → 选择「Anki 集合包 (.colpkg)」
2. **在 App 中选择词库**：点击「选择词库」，选择导出的 .colpkg 文件
3. **标记单词**：在 Anki 浏览器中为需要复习的单词打上橙色或红色旗标
4. **开始背单词**：解析完成后点击「开始背单词」
5. 每 5–7 分钟会全屏弹出一个单词，30 秒后自动关闭

## 注意事项

- 部分厂商会限制后台运行，需在系统设置中允许本应用自启动、省电策略设为「无限制」
- 无法阻止用户从最近任务栏划掉应用或强制停止
