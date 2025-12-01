# 📱 Feed 类客户端 APP 产品（信息流 Demo）

一个仿「今日头条 / 抖音搜索结果」的信息流 Demo，用来展示：

- 多类型卡片（文字 / 图片 / 视频）
- 单列 / 双列混排布局
- 下拉刷新 & 无限加载更多
- 长按删卡
- 本地缓存兜底
- **卡片曝光埋点系统 + 悬浮调试面板**
- **列表内视频卡片的自动播放 / 停止（中心锁定）**

全部基于 **Java + AndroidX + RecyclerView + MVVM 架构** 实现。

---

# 0. 当前功能完成情况概览

## ✅ 整体行为（已完整跑通）

当前版本打开应用后，整体行为已经接近一个真正的资讯流 App：

1. **启动自动加载第一页数据**
   - 进入 App，`FeedFragment` 会自动触发 `viewModel.refresh()`。
   - ViewModel 调用 Repository → RemoteDataSource 生成第一页数据。
   - UI 显示下拉刷新动画，加载完后自动停止。

2. **下拉刷新**
   - 用户下拉列表 → `SwipeRefreshLayout` 进入刷新状态。
   - 调用 `FeedViewModel.refresh()` → 重新拉第一页数据。
   - 成功：替换整个列表；失败：尝试本地缓存 + Toast 提示。

3. **无限加载更多（loadMore）**
   - 列表滑到底部附近时（倒数第 N 个 item），触发 `loadMore()`。
   - Repository 请求下一页，拼接到当前列表末尾。
   - 没有更多数据时，`hasMore=false`，不再触发加载更多，Footer 显示「已经到底啦」或直接隐藏。

4. **长按删卡**
   - 长按任意卡片 → 弹出确认对话框。
   - 点击删除 → 调用 `viewModel.deleteCard(card)`。
   - Repository 从内存列表中删除该条卡片 → 通过 LiveData 通知 UI 刷新。
   - 删除逻辑与分页逻辑解耦，不会影响后续刷新和加载更多。

5. **网络模拟失败 → 本地缓存兜底**
   - RemoteDataSource 在某些条件下可以模拟“网络失败”（比如随机抛异常）。
   - 刷新 / 加载更多失败时，Repository 会：
     - 尝试从 `FeedLocalDataSource` 读取 `feed_cache.json`。
     - 若缓存存在：展示缓存列表并 Toast 提示“使用本地缓存数据”。
     - 若缓存不存在：提示“刷新失败 / 加载失败”。

6. **多种卡片类型 & 排版方式**
   - 文本卡：标题 + 副标题 + 正文。
   - 图片卡：标题 + 封面图（Glide 加载） + 文案。
   - 视频卡：标题 + 封面图 + **真正 VideoView 播放本地 mp4**。
   - 每条卡片有 `layoutType` 字段，控制是 **单列** 还是 **双列**，通过 `GridLayoutManager.SpanSizeLookup` 支持混排。

7. **视频自动播放 / 停止**
   - 视频资源全部来自 `res/raw` 下的本地 mp4，避免网络不稳定。
   - 滚动时自动计算：**离屏幕中心最近的视频卡片** → 自动播放。
   - 同一时刻只会播放一条视频：
     - 滚动到新的视频卡片：旧卡停播，新卡开始播。
     - 滚出所有视频卡片：自动暂停播放。
   - 卡片右下角有倒计时文本，显示播放剩余时间（或“已结束”）。

8. **卡片曝光事件 + 悬浮调试面板**
   - 曝光阶段：ENTER / HALF / FULL / EXIT（露出 / 超过 50% / 完全露出 / 消失）。
   - `ExposureTracker` 每次滚动时计算每个可见 item 的可见比例，映射到阶段。
   - 阶段变化时生成 `ExposureEvent`，写入 `ExposureLogger`。
   - `MainActivity` 监听曝光事件，并把它们显示到右侧的 **悬浮日志面板**。
   - 点击右下角的 FAB：
     - 第一次：打开悬浮日志面板，实时观察曝光日志。
     - 再次点击：关闭面板，停止监听。

---

# 1. 工程整体设计

## 1.1 技术栈 & 运行环境

- 语言：**Java 11**
- UI：AndroidX + Material Components
- 最低 SDK：`minSdk = 24`
- 目标 SDK：`targetSdk = 36`
- 主要依赖：
  - RecyclerView：信息流列表
  - SwipeRefreshLayout：下拉刷新
  - ViewBinding：类型安全地绑定布局
  - Glide：图片加载（图片卡封面 + 视频卡封面）
  - CardView / MaterialCardView：卡片视觉效果
  - （系统）VideoView：视频播放（本地 raw mp4）

## 1.2 包结构（逻辑模块划分）

```text
com.example.feedapp
 ├─ data
 │   ├─ model          // 数据模型：FeedCard, FeedPageResult 等
 │   ├─ remote         // 模拟服务端：FeedRemoteDataSource
 │   ├─ local          // 本地缓存：FeedLocalDataSource
 │   └─ repository     // 仓库层：FeedRepository，聚合 Remote + Local
 │
 ├─ exposure           // 曝光系统：ExposureTracker, ExposureLogger, ExposureEvent, ExposureStage
 │
 ├─ ui
 │   ├─ main           // MainActivity：入口 + 悬浮曝光日志面板
 │   ├─ feed           // FeedFragment、FeedAdapter、FeedViewModel 等
 │   └─ debug          // 日志列表适配器（LogAdapter）等调试相关 UI
 │
 └─ util               // 通用工具（如线程池封装、log 工具等，可按需扩展）


整体架构采用 **MVVM + Repository**：

* **View（Activity/Fragment + XML 布局）**

  * 只负责展示数据、响应用户操作（点击、滑动、下拉等）
  * 不直接访问数据来源
* **ViewModel（FeedViewModel）**

  * 持有 `LiveData<List<FeedCard>>` + 各种状态：刷新中、加载更多、提示信息
  * 对外暴露 `refresh() / loadMore() / deleteCard()` 接口
* **Repository（FeedRepository）**

  * 把 Remote / LocalDataSource 封装起来，对 ViewModel 提供统一的接口
  * 管理分页状态、当前列表快照、并发加载标志位等
* **DataSource（FeedRemoteDataSource / FeedLocalDataSource）**

  * Remote 负责“伪造服务端数据”
  * Local 负责“本地持久化缓存”

> 你可以把整个系统理解成：
> View（前台服务员） ←→ ViewModel（前台主管） ←→ Repository（后厨经理） ←→ Remote/Local（原料仓库 + 外卖供应）

---

# 2. 当前功能详细说明

## 2.1 主界面（MainActivity）

布局：`activity_main.xml`

* 根布局：`CoordinatorLayout`
* 中间：`FrameLayout`（`@id/container`），用来托管 `FeedFragment`
* 右下角：`FloatingActionButton`（`@id/fabDebugExposure`），控制曝光日志悬浮面板显示/隐藏
* 覆盖层：`exposureOverlay + RecyclerView`，用来展示曝光事件日志列表

主要职责：

1. **启动时加载 FeedFragment**

   ```java
   if (savedInstanceState == null) {
       getSupportFragmentManager()
           .beginTransaction()
           .replace(R.id.container, new FeedFragment())
           .commit();
   }
   ```

2. **初始化曝光日志面板**

   * 使用 `RecyclerView + LogAdapter` 展示 `ExposureLogger` 的历史日志。
   * 列表按“最新在上”排序。

3. **注册 / 注销曝光监听**

   * 点击 FAB → `toggleExposurePanel()`：

     * 打开面板时：`ExposureLogger.addListener(this)`
     * 关闭面板时：`ExposureLogger.removeListener(this)`
   * `onNewEvent(...)` 收到曝光事件时：

     * 把格式化字符串插到日志列表顶部
     * 通知 `LogAdapter` 刷新 UI

4. **生命周期收尾**

   * 在 `onDestroy()` 中确保 `ExposureLogger.removeListener(this)`，避免内存泄漏。

---

## 2.2 列表页（FeedFragment + fragment_feed.xml）

布局：`fragment_feed.xml`

* 根：`SwipeRefreshLayout`
* 内部：`RecyclerView`

核心逻辑：

1. **RecyclerView 初始化**

   * 使用 `GridLayoutManager`：

     * spanCount = 2（两列）
     * 通过 `SpanSizeLookup` 按 `FeedCard.layoutType` 控制某个 item 占 1 列还是 2 列：

       * 单列卡片：spanSize = 2（占满一行）
       * 双列卡片：spanSize = 1（左右布局各一条）

2. **绑定 Adapter**

   * `FeedAdapter` 继承 `ListAdapter<FeedCard, ViewHolder>`，内部使用 DiffUtil 自动计算列表差异，更新更丝滑。
   * 提供长按回调给 Fragment 实现删卡弹窗。

3. **绑定 ViewModel & LiveData**

   * 观察 `cardsLiveData` → 调用 `adapter.submitList(newList)`。
   * 观察 `refreshingLiveData` → 控制 `swipeRefresh.setRefreshing(isRefreshing)`。
   * 观察 `loadingMoreLiveData` → 控制 Adapter 是否显示 Footer。
   * 观察 `toastMessageLiveData` → 弹出 Toast 提示。

4. **下拉刷新交互**

   * `swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());`
   * 刷新时不允许并发重复刷新。

5. **加载更多（滑动监听）**

   * `recyclerView.addOnScrollListener(...)` 中：

     * 拿到 `lastVisiblePosition`
     * 如果 `lastVisiblePosition >= totalItemCount - 3` → 调用 `viewModel.loadMore()`
     * 避免重复触发（ViewModel 里用 `loadingMore` 标记）

6. **长按删除**

   * Adapter 提供接口 `setOnItemLongClickListener(...)`。
   * Fragment 在回调中弹出对话框：

     * 确认：调用 `viewModel.deleteCard(card)`
     * 取消：不处理

7. **绑定曝光系统（关键）**

   * Fragment 内持有一个 `ExposureTracker`：

     * 初始化时：`exposureTracker.bind(recyclerView, adapter)`
     * 在 `onScrolled`、`onScrollStateChanged` 中调用 `exposureTracker.dispatchExposure()`
   * 这样，每次滑动都会重新计算当前所有可见卡片的曝光比例。

---

## 2.3 数据层：模型 & 分页

### 2.3.1 FeedCard（数据模型）

典型字段：

* `id`：唯一标识
* `title` / `subTitle` / `content`：文本内容
* `cardType`：

  * TYPE_TEXT
  * TYPE_IMAGE
  * TYPE_VIDEO
* `layoutType`：

  * LAYOUT_SINGLE（单列）
  * LAYOUT_DOUBLE（双列）
* `imageUrl`：图片封面 / 视频封面
* `videoResId`：视频卡片对应的本地 raw 资源 id

特点：

* 是一个标准 **Java Bean**：

  * private 字段 + public getter/setter
  * 方便 Gson 序列化、本地缓存、DiffUtil 判断内容是否改变。

### 2.3.2 FeedPageResult（分页结果模型）

包含：

* `List<FeedCard> cards`
* `boolean hasMore`
* `int nextPage`

主要用途：

* RemoteDataSource 返回一个 `FeedPageResult`，Repository 根据 `hasMore/nextPage` 更新分页状态。
* ViewModel 不需要关心分页细节，只负责触发 `refresh` / `loadMore`。

---

## 2.4 数据访问层：Repository + Remote + Local

### 2.4.1 FeedRemoteDataSource（模拟服务端）

职责：

* 根据页码生成不同内容的假数据：

  * 不同的 `cardType`、`layoutType`
  * 不同的 `title` / `imageUrl`
  * 视频卡片轮流使用 5 个本地 mp4 资源的 `videoResId`
* 可选地模拟网络延迟（`Thread.sleep`）
* 控制最多页数，例如 5～10 页。

### 2.4.2 FeedLocalDataSource（本地缓存）

职责：

* 使用 Gson 将 `List<FeedCard>` → JSON 字符串 → 写入应用私有目录文件 `feed_cache.json`
* 再次启动或网络失败时，从该文件读取 JSON → 反序列化为 `List<FeedCard>`。

特点：

* 不需要额外权限（使用 `Context.getFilesDir()` 路径）
* IO 操作在后台线程中完成（通过 Repository 的线程池），不会阻塞主线程。

### 2.4.3 FeedRepository（仓库）

职责非常核心：

* 持有：

  * 当前列表：`List<FeedCard> currentList`
  * 分页信息：`int nextPage`, `boolean hasMore`
  * 状态：`boolean loading`（防止重复加载）
* 对外暴露：

  * `void refresh(Callback callback)`
  * `void loadMore(Callback callback)`
  * `void deleteCard(String id)`
  * `List<FeedCard> getCurrentSnapshot()`
* 内部流程：

  * `refresh`：

    * 调用 Remote 获取第一页数据；
    * 成功：更新 `currentList`，重置分页，写入 Local 缓存；
    * 失败：尝试 Local 缓存，有则用缓存，无则返回错误。
  * `loadMore`：

    * 如果 `!hasMore` 或 `loading == true` → 直接返回；
    * 调用 Remote 获取下一页；
    * 成功：`currentList.addAll(newCards)`，更新 `hasMore/nextPage`；
    * 失败：不改 currentList，向上层返回错误。
  * `deleteCard`：

    * 在 currentList 中按 id 移除对应 item，不直接操作 UI。
    * 上层 ViewModel 重新发布 `cardsLiveData`。

---

## 2.5 FeedViewModel（业务状态管理）

* 继承 `AndroidViewModel`
* 内部持有 `FeedRepository`

对外状态：

* `LiveData<List<FeedCard>> cardsLiveData`
* `LiveData<Boolean> refreshingLiveData`
* `LiveData<Boolean> loadingMoreLiveData`
* `LiveData<String> toastMessageLiveData`

对外方法：

* `refresh()`

  * 设置 `refreshing = true`
  * 调用 `repository.refresh(...)`
  * 成功：`cardsLiveData.postValue(list)`，`refreshing = false`
  * 失败：提示错误 / 使用缓存结果
* `loadMore()`

  * 判断 Repository 是否还能加载更多
  * 设置 `loadingMore = true`
  * 调用 `repository.loadMore(...)`
* `deleteCard(FeedCard card)`

  * 调用 `repository.deleteCard(card.getId())`
  * 重新 `postValue(repository.getCurrentSnapshot())`

View 不直接操作数据，只操作 ViewModel，这是 MVVM 的核心。

---

## 2.6 列表 UI：FeedAdapter（多类型 + DiffUtil）

`FeedAdapter` 继承自 `ListAdapter<FeedCard, RecyclerView.ViewHolder>`。

支持 4 类 ViewType：

* `VIEW_TYPE_TEXT`：纯文字卡
* `VIEW_TYPE_IMAGE`：图片卡
* `VIEW_TYPE_VIDEO`：视频卡（带 VideoView）
* `VIEW_TYPE_FOOTER`：底部加载更多 Footer

核心特点：

1. **DiffUtil 支撑的高效刷新**

   * 自定义 `DiffUtil.ItemCallback<FeedCard>`：

     * `areItemsTheSame` 根据 id 比较
     * `areContentsTheSame` 比较 title/subTitle/content/cardType/layoutType/imageUrl/videoResId
   * 调用 `submitList(newList)` 时，RecyclerView 只刷新变化了的 item。

2. **ViewBinding + 多种 ViewHolder**

   * `TextViewHolder` → `ItemFeedTextBinding`
   * `ImageViewHolder` → `ItemFeedImageBinding`
   * `VideoViewHolder` → `ItemFeedVideoBinding`
   * `FooterViewHolder` → `ItemFeedFooterLoadingBinding`

3. **长按监听**

   * 提供 `setOnItemLongClickListener(...)` 回调。
   * 在 `onBindViewHolder` 给 `itemView.setOnLongClickListener(...)`，把事件回调给 Fragment。

4. **视频卡片的特殊支持**

   * `VideoViewHolder` 内部集成 VideoView：

     * `bind(card)` 时显示封面图与标题，不自动播放。
     * 提供 `startVideo(@RawRes int videoResId)` 和 `stopVideo()`，由 Fragment 的“中心锁定逻辑”调用。
   * 在 `onViewRecycled` 中对 VideoViewHolder 调用 `stopVideo()`，避免复用时出现残影或继续播放。

5. **曝光事件预留接口**

   * `onExposureEvent(FeedCard card, int position, ExposureStage stage)`：

     * 当前版本主要用于视频自动播放逻辑（例如“某个视频 FULL 时触发自动播放”）。
     * 后续也可以扩展为：第一次 FULL 时标记“已读”等 UI 变化。

---

# 3. 曝光事件系统（Exposure）

### 3.1 目标

* 精准记录每条卡片在列表中的曝光阶段变化，用于：

  * 日志调试（当前版本已实现）
  * 将来接入埋点上报（可以直接基于 `ExposureEvent`）

### 3.2 核心组件

* `ExposureStage`：枚举，四个阶段：

  * `ENTER`：露出（0% < ratio < 50%）
  * `HALF`：露出 ≥ 50% 且 < 100%
  * `FULL`：完全露出（≈100%）
  * `EXIT`：从可见变为完全不可见
* `ExposureEvent`：

  * `id`、`position`、`stage`、`timestamp`、`title`、`cardType`
* `ExposureLogger`：

  * 内部缓存所有事件
  * 提供 `log(event)`、`getAllLogs()`、`addListener/removeListener` 等
* `ExposureTracker`：

  * 绑定 RecyclerView + Adapter
  * 在滚动时计算每个可见 item 的 `ratio`
  * 映射阶段 + 与 `stageMap<id, stage>` 对比
  * 阶段变化时生成 `ExposureEvent` → 交给 `ExposureLogger`

### 3.3 悬浮日志面板

* MainActivity 实现 `ExposureLogger.Listener`：

  * `onNewEvent(event, formatted)` 收到新日志
  * 把 `formatted` 插入悬浮窗中的 `RecyclerView` 顶部
* 通过 FAB 控制显示 / 隐藏：

  * 避免在正常用户场景中打扰 UI
  * 只在调试时打开即可

---

# 4. 视频播放与自动播放（中心锁定）

### 4.1 视频资源

* 所有视频来自 `res/raw` 下的 mp4 文件。
* RemoteDataSource 生成视频卡片时，从这 5 个视频中轮询分配 `videoResId`。

### 4.2 播放内核

* 使用系统自带 `VideoView` 播放本地资源：

  * `videoView.setVideoURI(Uri.parse("android.resource://<package_name>/" + resId));`
  * `videoView.start()` / `stopPlayback()`

### 4.3 自动播放策略（中心锁定）

* RecyclerView 的滚动监听中：

  * 当滚动停止（`SCROLL_STATE_IDLE`）时：

    * 遍历当前可见的所有 item
    * 寻找“**距离屏幕中心最近的那条视频卡片**”
  * 如果该视频卡片的 position 与当前播放的不同：

    * 停止旧 VideoView，启动新 VideoView 的播放
  * 如果当前屏幕内没有视频卡片：

    * 停止当前播放

### 4.4 UI 细节

* 视频卡片显示：

  * 封面图 + 标题
  * 播放时隐藏封面，显示 VideoView 画面
  * 右下角有播放剩余时间或“已结束”的文本
* 播放结束事件：

  * `setOnCompletionListener` 中更新“已结束”等状态文案。

---

# 5. 性能与用户体验

* DiffUtil + ListAdapter：避免整表刷新
* Glide：异步图片加载 + 缓存
* 单一 VideoView per ViewHolder / 自动停止播放：节省资源
* IO、网络模拟均在后台线程中执行
* 足够的加载状态提示：

  * 刷新动画
  * LoadMore Footer
  * 错误提示与空视图（可扩展）

---

# 6. 如何运行本项目

1. Android Studio 2025+ 打开工程
2. 连接模拟器（API 24+）或真机
3. 运行 `app` 模块
4. 交互建议：

   * 下拉刷新
   * 连续多次滑到列表底部触发加载更多
   * 长按删除不同类型卡片
   * 点击右下角悬浮按钮，观察曝光日志在滚动时的变化
   * 滚动寻找视频卡片，观察自动播放行为

---


