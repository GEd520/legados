# Android 打包失败排查记录

日期：2026-04-20

项目这次“之前能正常打包，突然失败”的情况，实际是两个问题叠在一起：

1. 本机 Gradle 缓存目录被锁住，导致 `gradlew` 启动阶段就失败。
2. 项目代码里新增了 `Flow.map {}` 调用，但漏掉了 `map` 的导入，导致 Kotlin 编译失败。

下面按“报错 -> 原因 -> 解决方法”对照整理。

---

## 1. Gradle 启动即失败

### 报错

```text
Exception in thread "main" java.io.FileNotFoundException:
G:\software_data\Ggradle-cache\wrapper\dists\gradle-8.14-bin\...\gradle-8.14-bin.zip.lck (拒绝访问。)
```

### 原因

系统环境变量里设置了：

```text
GRADLE_USER_HOME=G:\software_data\Ggradle-cache
```

Gradle 启动时会先访问这个全局缓存目录。当前该目录下残留了：

```text
gradle-8.14-bin.zip.lck
```

这通常说明：

- 上一次 Gradle 或 Android Studio 异常退出
- 有残留的 Java/Gradle 进程还在占用缓存
- 缓存目录权限异常

所以这一步还没进入项目编译，`gradlew` 就已经失败了。

### 解决方法

可任选一种或组合处理：

1. 删除 `G:\software_data\Ggradle-cache` 里残留的 `.lck` 锁文件。
2. 执行 `gradlew --stop`，或关闭 Android Studio 后重启。
3. 检查 `G:\software_data\Ggradle-cache` 是否有当前用户的读写权限。
4. 临时改用项目内单独的 Gradle 缓存目录，例如：

```powershell
$env:GRADLE_USER_HOME=(Resolve-Path .\.gradle-user-home).Path
./gradlew.bat :app:assembleDebug
```

### 结论

这是环境问题，不是项目源码本身直接导致的。

---

## 2. Android Gradle Plugin 初始化失败

### 报错

```text
Unable to initialize metrics, ensure C:\Users\CodexSandboxOffline\.android is writable
...
Failed to apply plugin 'com.android.internal.application'
...
java.nio.file.AccessDeniedException: C:\Users\CodexSandboxOffline\.android
```

### 原因

Android Gradle Plugin 在初始化时会访问用户目录下的 `.android` 目录，用于偏好设置、debug keystore 等。

当前构建环境的用户目录被映射到了一个不存在或不可写的位置：

```text
C:\Users\CodexSandboxOffline\.android
```

所以插件在配置阶段就报错。

### 解决方法

确保构建用户目录可写，或者显式指定一个可写的用户目录。例如：

```powershell
New-Item -ItemType Directory -Force -Path .codex-home,.codex-home\.android | Out-Null
$env:USERPROFILE=(Resolve-Path .\.codex-home).Path
$env:HOME=$env:USERPROFILE
./gradlew.bat :app:assembleDebug
```

### 结论

这仍然属于构建环境问题，不是业务代码错误。

---

## 3. Kotlin daemon 权限报错

### 报错

```text
Failed to compile with Kotlin daemon: java.lang.RuntimeException: Could not connect to Kotlin compile daemon
...
java.nio.file.AccessDeniedException:
C:\Users\ASUS\AppData\Local\kotlin\daemon\kotlin-daemon-client-*.tmp
```

### 原因

Kotlin 编译器默认会尝试使用 Kotlin daemon。当前环境里，daemon 临时文件目录访问不稳定，导致它反复报“无法连接 Kotlin compile daemon”。

不过这个问题没有直接导致最终不能打包，因为 Kotlin 编译器随后回退到了非 daemon 模式继续编译。

### 解决方法

如果本机也遇到同类问题，可以尝试：

1. 执行 `gradlew --stop`
2. 删除 Kotlin daemon 缓存目录后重试
3. 确保 `C:\Users\当前用户名\AppData\Local\kotlin\daemon` 可写
4. 临时禁用 Kotlin daemon

```powershell
./gradlew.bat :app:assembleDebug --no-daemon
```

或者在需要时附加 JVM 参数。

### 结论

这是环境噪音问题，会干扰排查，但不是这次最终编译失败的根因。

---

## 4. 真正导致源码编译失败的报错

### 报错

```text
app\src\main\java\io\legado\app\ui\rss\source\manage\RssSourceActivity.kt:362:15:
error: unresolved reference 'map'

app\src\main\java\io\legado\app\ui\rss\source\manage\RssSourceActivity.kt:362:21:
error: cannot infer type for value parameter 'data'

app\src\main\java\io\legado\app\ui\rss\source\manage\RssSourceActivity.kt:373:15:
error: cannot infer type for type parameter 'T'

app\src\main\java\io\legado\app\ui\rss\source\manage\RssSourceActivity.kt:375:15:
error: cannot infer type for type parameter 'T'
```

### 对应代码

文件：

`app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt`

相关代码大致如下：

```kotlin
}.map { data ->
    hostMap.clear()
    if (groupSourcesByDomain) {
        data.sortedWith(...)
    } else {
        data
    }
}.catch {
    ...
}.flowOn(IO).conflate().collect {
    ...
}
```

### 原因

这里使用了 `kotlinx.coroutines.flow.map`，但文件顶部缺少对应导入：

```kotlin
import kotlinx.coroutines.flow.map
```

由于 `map` 没有被正确解析，后续整条 `Flow` 调用链的类型推断也全部连锁失败，所以报出一串 `cannot infer type`。

### 解决方法

补上导入即可：

```kotlin
import kotlinx.coroutines.flow.map
```

这次实际修复的就是这个地方。

---

## 最终修改

修改文件：

`app/src/main/java/io/legado/app/ui/rss/source/manage/RssSourceActivity.kt`

新增导入：

```kotlin
import kotlinx.coroutines.flow.map
```

---

## 最终验证结果

修复后重新执行：

```powershell
./gradlew.bat :app:assembleDebug
```

构建成功：

```text
BUILD SUCCESSFUL in 45s
```

APK 输出位置：

```text
app\build\outputs\apk\app\debug\legado_app_3.26.042005.apk
```

---

## 一句话总结

这次“突然打包失败”表面上像是 Gradle 或 Android 环境炸了，实际上是：

1. 环境里先有 Gradle 缓存锁文件，导致启动就报错。
2. 就算绕过环境问题，项目里还存在一个真实的 Kotlin 编译错误：漏写了 `import kotlinx.coroutines.flow.map`。

也就是说，这是“环境问题 + 代码问题”同时存在，不是单一原因。
