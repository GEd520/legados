# Android 资源与菜单 ID 学习笔记

## 1. Android 资源引用符号 `@`

`@` 是 Android 中引用资源的符号。

```xml
android:id="@+id/menu_clear"
android:title="@string/clear"
```

## 2. `@string/xxx` 引用字符串资源

`@string/xxx` 表示引用 `strings.xml` 中定义的字符串。

### 规则

- `@string/` 后面跟的就是 `strings.xml` 中 `name` 属性的值
- 编译器会在 `R.string` 中找到对应的常量

### 举例

**strings.xml 中定义：**

```xml
<string name="clear">清除</string>
<string name="app_name">阅读</string>
```

**布局文件中引用：**

```xml
<item android:title="@string/clear" />
```

**Kotlin 代码中引用：**

```kotlin
textView.text = getString(R.string.clear)
```

## 3. `@+id` vs `@id`

| 符号         | 含义              | 使用场景          |
| ---------- | --------------- | ------------- |
| `@+id/xxx` | 创建新 ID，如果不存在就创建 | 第一次定义某个 ID 时  |
| `@id/xxx`  | 引用已存在的 ID       | 引用其他地方已定义的 ID |

```xml
<!-- 创建 ID -->
<item android:id="@+id/menu_clear" ... />

<!-- 引用已存在的 ID -->
<item android:id="@id/existing_id" ... />
```

## 4. `R` 类是什么

`R` 是 Android SDK **自动生成**的工具类，全称 Resource（资源）。

### 编译时自动生成

当你写了：

```xml
<item android:id="@+id/menu_clear" ... />
```

编译后自动生成：

```kotlin
object R {
    object id {
        val menu_clear = 0x7f0a0123  // 某个整数值
    }
}
```

### R 类包含的资源类型

```kotlin
R.id       // 控件 ID
R.layout   // 布局文件
R.menu     // 菜单文件
R.string   // 字符串
R.drawable // 图片
R.color    // 颜色
R.style    // 样式
```

### 为什么用 R？

1. **类型安全** - 编译器检查资源是否存在
2. **性能高** - ID 只是整数值，比字符串比较快
3. **自动管理** - 无需手动分配 ID，编译时自动处理

## 5. 代码中使用

```kotlin
// 在 Activity 或 Fragment 中监听菜单点击
override fun onMenuItemClick(item: MenuItem): Boolean {
    when (item.itemId) {
        R.id.menu_clear -> {
            // 处理清除逻辑
            return true
        }
    }
    return false
}
```

## 6. strings.xml 字符串资源文件

`strings.xml` 是 Android 用来存放**字符串资源**的文件，位于 `res/values/` 目录下。

### 为什么要用 strings.xml？

| 好处       | 说明                                           |
| -------- | -------------------------------------------- |
| **国际化**  | 不同语言不同文件夹，如 `values-zh/`（中文）、`values/`（默认英文） |
| **统一管理** | 不用到处写硬字符串，方便修改                               |
| **复用**   | 同一个字符串可以多处引用                                 |

### 项目中的多语言示例

| 文件夹                         | 语言     |
| --------------------------- | ------ |
| `values/strings.xml`        | 默认（英文） |
| `values-zh/strings.xml`     | 简体中文   |
| `values-zh-rTW/strings.xml` | 繁体中文   |
| `values-ja-rJP/strings.xml` | 日文     |

## 7. 实际案例：日志菜单和阅读记录菜单

项目中发现的菜单复用情况：

| 菜单文件                  | 用途       |
| --------------------- | -------- |
| `app_log.xml`         | 应用运行时日志  |
| `crash_log.xml`       | 崩溃日志     |
| `rss_read_record.xml` | RSS 阅读记录 |

这三个菜单**结构完全相同**（都只有一个 `menu_clear` 菜单项），但用于不同场景。

***

*学习日期：2026-04-25*
