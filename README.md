# **Feed 类客户端 APP 产品**

# 当前功能完成情况概览

**已经实现：**

### ✅ 当前整体行为（已完整跑通）

现在应用已经具备一个信息流 App 的基本行为：

1. **打开应用即自动加载第一页数据**
2. **下拉即可刷新**
3. **滑到接近底部会自动加载更多分页内容**
4. **长按任意卡片会弹出对话框，确认后删除**
5. **网络模拟失败时，会自动从本地缓存恢复数据**

系统的“刷新 → 获取数据 → 更新 UI”的全链路已经打通，为后续加入多样式卡片、曝光监控、性能优化做好了基础。

### ✅ 工程初始化

项目使用 **Java + AndroidX** 开发，并启用了 **ViewBinding**，方便直接操作布局控件，减少 `findViewById` 的使用，提高代码可读性与安全性。

### ✅ 主界面（MainActivity）

主界面的布局由 `activity_main.xml` 定义，结构非常简单：

- 外层使用 **CoordinatorLayout**（便于后续加入 AppBar、SnackBar 等效果）
- 中间是一个 **FrameLayout 容器**，用于动态放置页面（FeedFragment）
- 右下角放了一个 **FloatingActionButton**，预留给后续的“曝光调试工具”

首次进入应用时，`MainActivity` 会把 **FeedFragment** 塞进这个容器中显示。

### ✅ 列表页面（FeedFragment）

列表页由 `fragment_feed.xml` 定义：

- 上层使用 **SwipeRefreshLayout**，用来做下拉刷新动画
- 里面是一个 **RecyclerView**，展示信息流卡片列表

`FeedFragment` 负责管理：

- 下拉刷新
- 列表滚动检测（触底自动加载更多）
- 长按删除卡片
- 监听 ViewModel 的 LiveData，并更新界面

### ✅ 数据层设计（模型 / 数据源 / 仓库）

整个数据层被拆分成几部分，职责清晰：

### **1）FeedCard（卡片数据模型）**

定义“每一张卡片”长什么样，包括：

- 文本内容（标题、副标题、正文）
- 卡片类型（纯文字 / 图片 / 视频）
- 展示方式（单列或双列）
- 图片 URL、视频时长等字段

后续扩展卡片样式非常方便。

### **2）FeedPageResult（分页结果模型）**

用于描述“拉取一页数据”的结果，包括：

- 当前页的卡片列表
- 是否还需要继续请求下一页
- 下一页的页码

它让分页逻辑变得统一又清晰。

### **3）FeedRemoteDataSource（模拟服务端）**

没有接真实后端，而是“模拟 API 接口”：

- 每次根据页码生成固定数量的卡片
- 模拟网络延迟
- 模拟不同卡片样式、排版
- 返回分页信息（hasMore / nextPage）

和真实网络返回的形式完全一致，因此未来可以无缝替换成 Retrofit 接口。

### **4）FeedLocalDataSource（本地缓存）**

把当前列表存到应用私有目录下的 `feed_cache.json`，在网络失败时备用。

- 使用 Gson 做 JSON 序列化
- 使用普通文件读写即可，无需数据库

### **5）FeedRepository（仓库层）**

数据访问的“唯一入口”，负责：

- 刷新第一页
- 加载下一页
- 删除卡片
- 多线程处理数据获取
- 出错时自动 fallback 到本地缓存

UI 与 ViewModel 完全不用关心数据来自哪里，真正实现解耦。

### ✅ 业务逻辑层：FeedViewModel

ViewModel 通过 LiveData 把“界面需要的数据状态”暴露给 Fragment，包括：

- 卡片列表（cardsLiveData）
- 是否正在刷新（refreshingLiveData）
- 是否正在加载更多（loadingMoreLiveData）
- 提示信息（toastLiveData）

并提供：

- `refresh()`
- `loadMore()`
- `deleteCard()`

这 3 大操作，统一转发给 Repository。

### ✅ 列表 UI：FeedAdapter + item_feed_text.xml

- `item_feed_text.xml` 是文字卡片展示模板（后续可扩展更多样式）
- `FeedAdapter` 负责：
    - 把 FeedCard 渲染成 UI
    - 使用 ViewBinding
    - 处理长按事件并回调给 Fragment

所有卡片渲染都集中在这一层，便于后续扩展 ViewType。

# 工程整体设计

### 1. 项目模块划分（包结构）

包结构如下（所有代码都放在 `com.example.feedapp` 下）：

```
com.example.feedapp
 ├─ data
 │   ├─ model        // 数据模型：卡片、曝光事件、分页结果等
 │   ├─ remote       // 网络层/模拟服务端
 │   ├─ local        // 本地缓存（文件/SharedPreferences）
 │   └─ repository   // 仓库：统一对外提供 Feed 数据
 ├─ exposure         // 曝光埋点相关工具、事件总线、调试工具接口
 ├─ ui
 │   ├─ main         // MainActivity，Tab/入口
 │   ├─ feed         // FeedFragment、Adapter、ViewHolder、ViewModel
 │   └─ debug        // 曝光调试页面
 └─ util             // 通用工具
```

架构：**MVVM**

- `View`：XML + Activity/Fragment
- `ViewModel`：持有 `LiveData<List<FeedCard>>`、加载状态、错误信息等
- `Repository`：从“远端 + 本地缓存”拿数据，负责分页、删除、缓存
- `RemoteDataSource`：伪造服务端（从 assets 或内存生成数据）
- `LocalDataSource`：简单文件缓存（JSON）

如何理解MVVM架构，Gemini给出的生动讲解：

<aside>
💡

- **View (顾客)** 坐下，对 **ViewModel (服务员)** 说：“我要看新闻（Feed流）”。
- **ViewModel** 转身告诉 **Repository (经理)**：“来份新闻”。
- **Repository** 打开 **LocalDataSource (冰箱)** 一看，空的。
- **Repository** 赶紧联系 **RemoteDataSource (工厂)** 生产数据。
- **RemoteDataSource** 搞定数据，交给 **Repository**。
- **Repository** 先把数据存进 **LocalDataSource**（以此备份），然后把数据交给 **ViewModel**。
- **ViewModel** 把数据放到 **LiveData (托盘)** 上。
- **View** 看到托盘上有东西了，立马刷新界面，用户这就看到了内容。
</aside>

# 创建工程

## 0、先在 `build.gradle(:app)` 中加需要的东西

- 在 `android {}` 里 **开启 ViewBinding**
    - Android Studio 会在编译的时候，自动派一个隐形的助手，把你界面（XML）里所有的按钮、图片、文字都**提前整理好**，并生成一个清单（Binding类）。以后你想用按钮，直接从清单里拿就行了：`binding.btnLogin`。
- 在 `dependencies {}` 里 加上我们要用的库（RecyclerView、SwipeRefreshLayout、Lifecycle、Gson、CardView、CoordinatorLayout）
    - **RecyclerView（最重要）：“无限传送带”**。无限下滑列表的组件。
    - **SwipeRefreshLayout：“橡皮筋”**。就是把列表往下拉，会出现一个小圆圈转啊转，松手就刷新的那个控件。它是专门负责“下拉刷新”交互的。
    - **Lifecycle：**配合 MVVM 用的。它让ViewMode能自动感知 Activity是活着还是死了。
    - **Gson：**你在 `RemoteDataSource`（伪造服务端）里拿到的数据通常是 JSON 格式（一堆看不懂的字符串文本）。Gson 的作用就是瞬间把这堆乱码翻译成 Java/Kotlin 对象（比如 `FeedCard`），让代码能读懂。
    - **CardView：“精美相框”**。它负责给每一条卡片加上**圆角**和**阴影**，让界面看起来有立体感，不像是在看枯燥的表格。
    - **CoordinatorLayout：**它负责协调各个组件的联动。比如，当你向上滑动列表时，顶部的标题栏自动折叠或隐藏，让屏幕显示更多内容。

## 1. 主界面：MainActivity + activity_main.xml

### 1.1 activity_main.xml 设计

布局采用：

- 根布局：`CoordinatorLayout`
- 中间区域：`FrameLayout` 作为 Fragment 容器（`@id/container`）
- 右下角浮动按钮：`FloatingActionButton` （`@id/fabDebugExposure`），预留为**曝光调试工具入口**

## 2. 列表页面：FeedFragment + fragment_feed.xml

### 2.1 fragment_feed.xml 布局

结构：

- 根布局：`SwipeRefreshLayout`（`@id/swipeRefresh`）
    - `SwipeRefreshLayout` 是官方推荐的下拉刷新容器，集成刷新动画简单、稳定，方便满足“支持下拉刷新”。（？？？）
- 子控件：`RecyclerView`（`@id/recyclerView`）
    - `RecyclerView` 是基础列表组件。

### 2.2 FeedFragment 逻辑

目前 `FeedFragment` 的主要职责：

1. **初始化 UI 组件**：
    - 为 `RecyclerView` 设置 `LinearLayoutManager`（后面会换成 `GridLayoutManager` 做单列/双列混排）。
    - 绑定 `FeedAdapter`。
2. **绑定 ViewModel**：
    - 使用 `ViewModelProvider(this).get(FeedViewModel.class)` 获取 `FeedViewModel` 实例。
    - 观察：
        - `cards`：列表数据变化 → 通知 Adapter 更新。
        - `refreshing`：控制 `SwipeRefreshLayout.setRefreshing(...)`。**下拉刷新**
        - `toastMessage`：通过 Toast 显示错误/提示信息。
3. **交互逻辑**：
    - 下拉刷新：调用 `viewModel.refresh()`。当用户按住屏幕往下拉时，会触发这个监听。它立刻给 ViewModel 下命令：“我们要最新的数据，原来的清空，重头再来！”
    - 滑动监听：
        - **策略是：** 当看到用户滑到**倒数第 3 条**的时候，它就判断“用户快看完了”，赶紧偷偷让 ViewModel 去后台拿新数据（`loadMore`）。这样等用户真的划到底部时，新数据已经准备好了，感觉就像永远划不完一样。
    - 长按卡片：
        - Adapter 回调 `onItemLongClick()` → Fragment 显示确认对话框 → 确认后调用 `viewModel.deleteCard()`。
        告诉适配器：如果用户一直按着某条卡片不松手，就弹出一个**确认框（AlertDialog）**。**Dialog逻辑：** 弹窗问“确定删除吗？”。如果用户点“删除”，它再转头告诉 ViewModel：“把这条数据干掉。”
4. **首次加载**：
    - `onViewCreated` 中判断 `savedInstanceState == null` 时：
        - 显示刷新动画；
        - 主动触发 `viewModel.refresh()` 实现“进入页面自动拉取第一页数据”。

## 3. 数据模型层：FeedCard & FeedPageResult

### 3.1 FeedCard 设计

`FeedCard` 包含：

- 基本信息：
    - `id`：唯一标识一条卡片，便于删除、埋点。
    - `title` / `subTitle` / `content`：展示文案。
- 样式信息：
    - `cardType`：文本卡 / 图片卡 / 视频卡（对应不同 ViewType）。
    - `layoutType`：单列 / 双列，后续供 GridLayoutManager 的 `spanSizeLookup` 使用。
- 媒体信息：
    - `imageUrl`：图片卡使用。
    - `fakeVideoDurationSec`：模拟视频卡的“时长”，后续用倒计时来模拟自动播放/暂停。

### 3.2 FeedPageResult 设计

`FeedPageResult` 很简单：

- `List<FeedCard> cards`：当前页数据。
- `boolean hasMore`：是否还有下一页。
- `int nextPage`：下一页页码。

**为什么要一个单独的分页模型？**

- 便于 `FeedRemoteDataSource` 使用分页逻辑；
- `FeedRepository` 只用接收一个 `FeedPageResult` 就能更新内部状态；
- 后续如果改成真接口（例如 Retrofit），可以直接映射服务端返回的数据结构。

## 4. 数据访问层：Remote + Local + Repository

### 4.1 FeedRemoteDataSource（模拟服务端）

职责：

- 按页码和 pageSize 生成一批 `FeedCard`。
- 控制：
    - 卡片类型：通过 `i % 3` 模拟文字/图片/视频分布；
    - 排版方式：通过 `i % 4` 模拟单列/双列布局；
- 模拟网络延迟：`SystemClock.sleep(600)`，更接近真实环境；
- 设置 `hasMore` 和 `nextPage`，模拟最多 10 页数据。

### 4.2 FeedLocalDataSource（本地缓存）

职责：

- 把当前列表序列化为 JSON 写入内部存储文件 `feed_cache.json`。
- 失败时从该文件加载上次缓存的列表。

**技术选择：**

- 存储：使用 `context.getFilesDir()` 下的应用私有目录 → 无需额外权限，安全简单。
- 序列化：使用 Gson 将 `List<FeedCard>` 转为 JSON，再写入文件。
- 反序列化：从文件读取 JSON 文本，再转为 `List<FeedCard>`。

### 4.3 FeedRepository（仓库层）

职责：

- 作为数据的“单一入口”，对外提供：
    - `refresh(Callback)`：刷新（拉第一页）。
    - `loadMore(Callback)`：加载更多。
    - `deleteCard(String)`：删除。
    - `getCurrentSnapshot()`：获取当前列表快照。
    - `canLoadMore()`：是否可以继续加载下一页。
- 管理：
    - 内存中的 `currentList`。
    - 分页状态：`nextPage, hasMore`。
    - 正在加载中的状态：`loading`。
- 使用单线程 `ExecutorService` 在线程池中执行网络/IO 操作，避免阻塞主线程。

## 5. 业务层：FeedViewModel（MVVM 的核心）

`FeedViewModel` 继承自 `AndroidViewModel`，持有 `Application`，方便构造 `FeedRepository`。

暴露的状态：

- `LiveData<List<FeedCard>> cards`：当前列表的数据。
- `LiveData<Boolean> refreshing`：是否正在下拉刷新。
- `LiveData<Boolean> loadingMore`：是否正在加载更多。
- `LiveData<String> toastMessage`：需要提示给用户的信息。

提供的操作方法：

- `refresh()`：
    - 先设置 `refreshing = true`，让 UI 显示刷新动画；
    - 调用 `repository.refresh(...)`：
        - 成功：更新列表数据，`refreshing = false`。
        - 失败：
            - 先尝试使用缓存列表。
            - 有缓存：展示缓存 + 提示“网络失败，展示本地缓存”。
            - 无缓存：提示“刷新失败：xxx”。
- `loadMore()`：
    - 如果 `repository.canLoadMore()` 为 false，则直接返回（防止重复加载或没有更多）。
    - 设置 `loadingMore = true`。
    - 调用 `repository.loadMore(...)`：
        - 成功：更新列表数据，`loadingMore = false`。
        - 失败：`loadingMore = false`，并提示“加载更多失败”。
- `deleteCard(FeedCard)`：
    - 调用 `repository.deleteCard(id)` 修改仓库中的列表；
    - 重新发布 `cardsLiveData` 为当前快照，驱动 UI 更新。

## 6. UI 列表层：item 布局 + FeedAdapter

### 6.1 item_feed_text.xml

- 使用 `CardView + LinearLayout` 组合，布局简洁但观感接近今日头条/信息流风格。
- 内部三行：
    - 标题（加粗、大号字体）。
    - 副标题（小号、浅色）。
    - 正文内容（中号字体）。

### 6.2 FeedAdapter（当前乞丐版）

当前版本：

- 内部维护 `List<FeedCard>`。
- 提供 `submitList(List<FeedCard>)` 用于外部更新数据。
- `onCreateViewHolder` 中通过 `ItemFeedTextBinding` inflate 布局。
- `onBindViewHolder` 中将 `FeedCard` 的 title/subTitle/content 绑定到 TextView。
- 提供回调接口 `OnItemLongClickListener`：
    - 在 `onBindViewHolder` 里为 `itemView` 设置 `setOnLongClickListener`。
    - 将事件回调给 Fragment，由 Fragment 决定是否弹出确认框。

什么还没有完成：

- 多种卡片样式真实渲染（图片卡、视频卡、文字卡）。
- 单列/双列排版混排。
- 曝光事件及调试工具。
- 视频自动播放停（用倒计时模拟）。
- 插件式卡片扩展机制、性能优化（预加载、预渲染等）。
