Room 是 Android 的数据库 ORM 框架，用于简化 SQLite 数据库操作。

在 legado_Plus 中，Room 主要存储以下数据：

        
            
toolName: view_files
            
status: success
          
            
filePath: G:\Project\legado_Plus\legado_Plus\app\src\main\java\io\legado\app\data\AppDatabase.kt
          

## Room 在 legado_Plus 中的作用

**存储的核心数据**（第73-77行）：

| 数据表 | 说明 |
|--------|------|
| **Book** | 书籍信息（书名、作者、路径、阅读进度等） |
| **BookGroup** | 书籍分组（全部、本地、音频、视频等） |
| **BookSource** | 书源配置（搜索URL、规则等） |
| **BookChapter** | 书籍章节（目录、链接） |
| **ReplaceRule** | 替换规则 |
| **SearchBook** | 搜索结果缓存 |
| **SearchKeyword** | 搜索关键词历史 |
| **Cookie** | 书源Cookie |
| **RssSource** | RSS源配置 |
| **Bookmark** | 书签 |
| **RssArticle** | RSS文章 |
| **RssReadRecord** | RSS阅读记录 |
| **RssStar** | RSS收藏 |
| **TxtTocRule** | TXT目录规则 |
| **ReadRecord** | 阅读记录 |
| **HttpTTS** | HTTP朗读配置 |
| **Cache** | 缓存数据 |
| **RuleSub** | 规则订阅 |
| **DictRule** | 词典规则 |
| **KeyboardAssist** | 键盘辅助（快捷键） |
| **Server** | 服务器配置 |

**简单说**：Room 存储了用户的**所有核心数据**——书架上的书、书源配置、阅读进度、书签、RSS订阅等。用户卸载App再安装，只要数据库在，数据就还在。