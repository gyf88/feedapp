# 📱 Feed 类客户端 APP 产品（信息流 Demo）

一个仿「今日头条 / 抖音搜索结果」的信息流 Demo，用来展示：

- 多类型卡片（文字 / 图片 / 视频）
- 单列 / 双列混排布局
- 下拉刷新 & 无限加载更多
- 长按删卡
- 本地缓存兜底
- **卡片曝光埋点系统 + 悬浮调试面板 / 调试 Activity**
- **列表内视频卡片的自动播放 / 停止（中心锁定，基于 ExoPlayer）**

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
   - 若网络失败且本地也没有缓存：显示**错误覆盖层**，提供「重试」按钮。

3. **无限加载更多（loadMore）**
   - 列表滑到底部附近时（倒数第 N 个 item），触发 `loadMore()`。
   - Repository 请求下一页，拼接到当前列表末尾。
   - 当前 Demo 中，`FeedRemoteDataSource` 固定返回 `hasMore = true`：
     - 因此理论上可以**无限生成新数据**，方便测试「加载更多」的体验。
     - 如果想限制最多页数，只需把代码中的 `setHasMore(true)` 改回按页数判断即可。

4. **长按删卡**
   - 长按任意卡片 → 弹出确认对话框。
   - 点击删除 → 调用 `viewModel.deleteCard(card)`。
   - Repository 从内存列表中删除该条卡片 → 通过 LiveData 通知 UI 刷新。
   - 删除逻辑与分页逻辑解耦，不会影响后续刷新和加载更多。

5. **网络模拟失败 → 本地缓存兜底**
   - RemoteDataSource 在某些条件下可以模拟“网络失败”（比如随机抛异常 / 延迟）。
   - 刷新 / 加载更多失败时，Repository 会：
     - 尝试从 `FeedLocalDataSource` 读取 `feed_cache.json`。
     - 若缓存存在：展示缓存列表并 Toast 提示“使用本地缓存数据”。
     - 若缓存不存在：显示错误页或 Toast 提示“刷新失败 / 加载失败”。

6. **多种卡片类型 & 排版方式**
   - 文本卡：标题 + 副标题 + 正文。
   - 图片卡：标题 + 封面图（Glide 加载） + 文案。
   - 视频卡：标题 + 封面图 + **基于 ExoPlayer 播放本地 mp4**。
   - 每条卡片有 `layoutType` 字段，控制是 **单列** 还是 **双列**，通过 `GridLayoutManager.SpanSizeLookup` 支持混排。

7. **视频自动播放 / 停止（中心锁定 + 倒计时）**
   - 视频资源全部来自 `res/raw` 下的本地 mp4，共 **5 个视频资源轮流分配**。
   - 使用 **单一 ExoPlayer 实例 + 多个 PlayerView**：
     - 整个 `FeedFragment` 只创建一个全局 `ExoPlayer`（media3）。
     - 每个视频卡片里是一个 `PlayerView`，通过 `attachPlayer / detachPlayer` 与全局 Player 绑定/解绑。
   - 滚动时自动计算：**离屏幕中心最近的视频卡片** → 自动播放（`autoPlayCenterVideo()`）。
   - 同一时刻只会播放一条视频：
     - 滚动到新的视频卡片：旧卡停播，新卡开始播。
     - 滚出所有视频卡片：自动暂停播放并解绑。
   - 卡片右下角有 **倒计时文本**：
     - 通过 `Handler + Runnable` 每秒读取 `player.getDuration()/getCurrentPosition()`。
     - 显示为类似 “8s” 的剩余时间。
     - 播放结束后显示「已结束」等文案（可扩展）。
     - 已去掉「播放中」字样，不再遮挡画面。

8. **卡片曝光事件 + 调试面板 / 调试 Activity**
   - 曝光阶段：ENTER / HALF / FULL / EXIT（露出 / 超过 50% / 完全露出 / 消失）。
   - `ExposureTracker` 每次滚动或布局变化时计算每个可见 item 的可见比例，映射到阶段。
   - 阶段变化时生成 `ExposureEvent`，写入 `ExposureLogger`。
   - 有两种调试姿势：
     1. **MainActivity 内部的悬浮日志面板**（右下角 FAB 打开/关闭）。
     2. **独立的 `ExposureDebugActivity`**：单独展示曝光日志列表页面，便于专门调试。

---

# 1. 工程整体设计

## 1.1 技术栈 & 运行环境

- 语言：**Java（Android）**
- UI：AndroidX + Material Components
- 最低 SDK：`minSdk = 24`
- 目标 SDK：`targetSdk = 36`（可按实际工程配置调整）
- 主要依赖：
  - RecyclerView：信息流列表
  - SwipeRefreshLayout：下拉刷新
  - ViewBinding：类型安全地绑定布局
  - Glide：图片加载（图片卡封面 + 视频卡封面）
  - CardView / MaterialCardView：卡片视觉效果
  - **AndroidX Media3 ExoPlayer + PlayerView：播放本地 raw mp4 视频**

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
 │   └─ debug          // ExposureDebugActivity、LogAdapter 等调试相关 UI
 │
 └─ util               // 通用工具
````

整体架构采用 **MVVM + Repository**：

* **View（Activity/Fragment + XML 布局）**

  * 只负责展示数据、响应用户操作（点击、滑动、下拉等）
  * 不直接访问数据来源

* **ViewModel（FeedViewModel）**

  * 持有 `LiveData<List<FeedCard>>` + 状态：刷新中、加载更多、空页面、错误页面、提示信息
  * 对外暴露 `refresh() / loadMore() / deleteCard()` 接口

* **Repository（FeedRepository）**

  * 把 Remote / LocalDataSource 封装起来，对 ViewModel 提供统一的接口
  * 管理分页状态、当前列表快照、并发加载标志位等

* **DataSource（FeedRemoteDataSource / FeedLocalDataSource）**

  * Remote 负责“伪造服务端数据”（包含 5 个本地视频轮流分配）
  * Local 负责“本地持久化缓存”（JSON 文件）

> 可以把整个系统理解成：
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

   * 使用 `RecyclerView + LogAdapter` 展示 `ExposureLogger` 的历史日志字符串。
   * 列表按“最新在上”排序。

3. **注册 / 注销曝光监听**

   * 点击 FAB → `toggleExposurePanel()`：

     * 打开面板时：`ExposureLogger.addListener(this)`
     * 关闭面板时：`ExposureLogger.removeListener(this)`
   * `onNewEvent(...)` 收到曝光事件时：

     * 把格式化后的字符串插到日志列表顶部
     * 通知 `LogAdapter` 刷新 UI

4. **生命周期收尾**

   * 在 `onDestroy()` 中确保 `ExposureLogger.removeListener(this)`，避免内存泄漏。

> 另外，还提供了一个独立的 `ExposureDebugActivity`，方便直接进一个 Activity 看曝光日志，不依赖 MainActivity 的悬浮层。

---

## 2.2 列表页（FeedFragment + fragment_feed.xml）

布局：`fragment_feed.xml`

* 根：`SwipeRefreshLayout`
* 内部：`RecyclerView`
* 上方 / 中部：可选的空页面 `emptyView` 和错误覆盖层 `errorView`，由 ViewModel 状态控制显隐。

核心逻辑：

1. **全局 ExoPlayer 初始化**

   * 在 `onViewCreated` 中创建：

     ```java
     player = new ExoPlayer.Builder(requireContext()).build();
     player.setRepeatMode(Player.REPEAT_MODE_ONE); // 抖音式循环播放
     ```
   * 整个 Fragment 只用一个 Player，避免多实例导致资源浪费和复杂度。

2. **RecyclerView 初始化**

   * 使用 `GridLayoutManager(spanCount = 2)`。
   * 通过 `SpanSizeLookup` 按 `FeedCard.layoutType` 控制某个 item 占 1 列还是 2 列：

     * 单列卡片：spanSize = 2（占满一行）
     * 双列卡片：spanSize = 1（左右布局各一条）
     * Footer：总是占满一行。

3. **绑定 Adapter**

   * `FeedAdapter` 继承 `ListAdapter<FeedCard, ViewHolder>`，内部使用 DiffUtil。
   * 提供长按回调给 Fragment 实现删卡弹窗。
   * 对视频卡片，提供 `VideoViewHolder`，包含 `PlayerView + 封面 + 倒计时 TextView`。

4. **绑定 ViewModel & LiveData**

   * `cardsLiveData` → `adapter.submitList(newList)`。
   * `refreshingLiveData` → `swipeRefresh.setRefreshing(isRefreshing)`。
   * `loadingMoreLiveData` → 控制 Adapter 是否显示 Footer。
   * `toastMessageLiveData` → 弹出 Toast 提示。
   * `showEmptyViewLiveData` → 控制 `emptyView` 的显隐。
   * `showErrorViewLiveData` → 控制 `errorView` 的显隐。

5. **下拉刷新**

   * `swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());`
   * 刷新时不允许并发重复刷新，由 ViewModel/Repository 控制。

6. **加载更多（滑动监听）**

   * `recyclerView.addOnScrollListener(...)`：

     * 每次 `onScrolled` 时，如果向下滚动（`dy > 0`）且接近列表末尾：

       * `lastVisible >= totalItemCount - 3` → 调用 `viewModel.loadMore()`。
     * Repository 内部保证不会重复触发并发加载。

7. **长按删除**

   * Adapter 提供接口 `setOnItemLongClickListener(...)`。
   * Fragment 在回调中弹出 `AlertDialog`：

     * 确认：调用 `viewModel.deleteCard(card)`。
     * 取消：不处理。

8. **曝光系统绑定**

   * Fragment 内持有一个 `ExposureTracker`：

     ```java
     exposureTracker = new ExposureTracker(binding.recyclerView, adapter);
     ```
   * `ExposureTracker` 注册为 RecyclerView 的 `OnChildAttachStateChangeListener`，在滚动 / attach / detach 时自动计算曝光。

9. **中心锁定自动播放**

   * 在 `onScrolled` 中调用 `autoPlayCenterVideo()`：

     * 遍历当前可见的 child view，找到**距离 RecyclerView 中心 Y 最近**且 cardType=VIDEO 的 item。
     * 如果该视频 position 与当前播放中的不同：

       * 调用 `stopVideoAt(oldPos)` 停掉旧视频并解绑 Player。
       * 调用 `startVideoAt(newPos)`：

         1. 找到对应的 `VideoViewHolder`。
         2. 调用 `vvh.attachPlayer(player)` 把 Player 接到该卡片的 `PlayerView`。
         3. 使用 `RawResourceDataSource.buildRawResourceUri(resId)` 构造本地资源 URI。
         4. `MediaItem.fromUri(uri) → player.setMediaItem(mediaItem) → prepare() → play()`。
     * 若当前屏幕内没有视频卡片，则停止当前播放并清空 `currentPlayingVideoPos`。

---

## 2.3 数据层：模型 & 分页

### 2.3.1 FeedCard（数据模型）

典型字段：

* `id`：唯一标识
* `title` / `subTitle` / `content`：文本内容
* `cardType`：

  * `TYPE_TEXT`
  * `TYPE_IMAGE`
  * `TYPE_VIDEO`
* `layoutType`：

  * `LAYOUT_SINGLE`（单列）
  * `LAYOUT_DOUBLE`（双列）
* `imageUrl`：图片封面 / 视频封面
* `videoResId`：视频卡片对应的本地 raw 资源 id（`R.raw.videoX`）

特点：

* 标准 Java Bean：private 字段 + public getter/setter。
* 可被 Gson 序列化/反序列化便于本地缓存。
* DiffUtil 用它来判断内容是否发生变化。

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

  * 按 `i % 3` 切换 cardType：文字 / 图片 / 视频。
  * `imageUrl` 使用 picsum 随机图。
  * 视频卡片：

    * 调用 `card.setCardType(FeedCard.TYPE_VIDEO)`
    * 设置封面 `imageUrl`
    * 从 `VIDEO_RES_IDS` 数组中轮流分配 5 个本地视频资源：

      ```java
      private static final int[] VIDEO_RES_IDS = {
          R.raw.video1,
          R.raw.video2,
          R.raw.video3,
          R.raw.video4,
          R.raw.video5
      };
      ```
* 控制分页：

  * 当前 Demo 中 `setHasMore(true)`，表示“永远还有下一页”，方便测试无限加载。
  * 如需限制页数，可以切换为：`result.setHasMore(page < 10);` 等。

### 2.4.2 FeedLocalDataSource（本地缓存）

职责：

* 使用 Gson 将 `List<FeedCard>` → JSON → 写入应用私有目录 `feed_cache.json`。
* 启动或网络失败时，从该文件读取 JSON → 反序列化为 `List<FeedCard>`。

特点：

* 使用 `Context.getFilesDir()`，不需要额外权限。
* 解析失败时不会崩溃，返回空列表。

### 2.4.3 FeedRepository（仓库）

职责：

* 持有：

  * 当前列表：`List<FeedCard> currentList`
  * 分页信息：`int nextPage`, `boolean hasMore`
  * 状态：`boolean loading`（防止重复加载）
* 对外暴露：

  * `void refresh(Callback callback)`
  * `void loadMore(Callback callback)`
  * `void deleteCard(String id)`
  * `List<FeedCard> getCurrentSnapshot()`

内部逻辑：

* `refresh`：

  * 调用 Remote 获取第一页数据；
  * 成功：更新 `currentList`，重置分页，写入 Local 缓存；
  * 失败：尝试 Local 缓存，有则使用缓存；无则透传错误。

* `loadMore`：

  * 若 `!hasMore` 或 `loading == true` 直接返回；
  * 调用 Remote 获取下一页；
  * 成功：`currentList.addAll(newCards)`，更新 `hasMore/nextPage` 并写入缓存；
  * 失败：不修改 currentList，向上层返回错误。

* `deleteCard`：

  * 在 `currentList` 中按 id 移除对应 item；
  * 不直接操作 UI，由 ViewModel 再次发布 `cardsLiveData`。

---

## 2.5 FeedViewModel（业务状态管理）

* 继承 `AndroidViewModel`
* 内部持有 `FeedRepository`

对外状态：

* `LiveData<List<FeedCard>> cardsLiveData`
* `LiveData<Boolean> refreshingLiveData`
* `LiveData<Boolean> loadingMoreLiveData`
* `LiveData<Boolean> showEmptyViewLiveData`
* `LiveData<Boolean> showErrorViewLiveData`
* `LiveData<String> toastMessageLiveData`

对外方法：

* `refresh()`

  * 设置 `refreshing = true`。
  * 调用 `repository.refresh(...)`。
  * 成功：更新 `cardsLiveData`、`showEmptyView` 等状态。
  * 失败：根据是否有缓存，决定显示错误页 / 使用缓存。

* `loadMore()`

  * 判断 Repository 是否还能加载更多。
  * 设置 `loadingMore = true`。
  * 调用 `repository.loadMore(...)`。

* `deleteCard(FeedCard card)`

  * 调用 `repository.deleteCard(card.getId())`。
  * 再次通过 `cardsLiveData.postValue(repository.getCurrentSnapshot())` 通知 UI。

---

## 2.6 列表 UI：FeedAdapter（多类型 + DiffUtil + ExoPlayer 倒计时）

`FeedAdapter` 继承自 `ListAdapter<FeedCard, RecyclerView.ViewHolder>`。

支持 4 类 ViewType：

* `VIEW_TYPE_TEXT`：纯文字卡
* `VIEW_TYPE_IMAGE`：图片卡
* `VIEW_TYPE_VIDEO`：视频卡（内含 PlayerView + 倒计时）
* `VIEW_TYPE_FOOTER`：底部加载更多 Footer

核心特点：

1. **DiffUtil 高效刷新**

   * 自定义 `DiffUtil.ItemCallback<FeedCard>`：

     * `areItemsTheSame` 根据 id 比较。
     * `areContentsTheSame` 比较主要字段（title/subTitle/content/cardType/layoutType/imageUrl/videoResId）。
   * 调用 `submitList(newList)` 时只局部刷新。

2. **ViewBinding + 多种 ViewHolder**

   * `TextViewHolder` → `ItemFeedTextBinding`
   * `ImageViewHolder` → `ItemFeedImageBinding`
   * `VideoViewHolder` → `ItemFeedVideoBinding`
   * `FooterViewHolder` → `ItemFeedFooterLoadingBinding`

3. **长按监听**

   * 提供 `setOnItemLongClickListener(...)`。
   * 在 `onBindViewHolder` 中设置 `itemView.setOnLongClickListener(...)`，把事件回调给 Fragment。

4. **视频卡片 + ExoPlayer 倒计时**

   * `VideoViewHolder` 内部持有：

     * `PlayerView`：用于显示视频画面。
     * 封面图 `ivVideoCover`：未播放或解绑 Player 时显示。
     * 倒计时 `tvCountdown`：播放时显示剩余秒数。
   * 关键方法：

     * `bind(card)`：绑定标题、封面和 `videoResId`，不直接启动播放。
     * `attachPlayer(ExoPlayer player)`：

       * 把传入的全局 Player 设置到 `PlayerView` 上。
       * 隐藏封面图，显示 `tvCountdown`。
       * 启动 `startCountdown(player)`，每秒更新剩余时间。
     * `detachPlayer()`：

       * 将 `PlayerView.setPlayer(null)`。
       * 恢复封面图，隐藏倒计时。
       * 调用 `cancelCountdown()` 停止计时任务。
     * `startCountdown(ExoPlayer player)`：

       * 每秒读取 `getDuration()` 和 `getCurrentPosition()`。
       * 计算剩余秒数，更新到 `tvCountdown`。
       * 播放结束时可显示“已结束”（可扩展）。
   * 在 `onViewRecycled` 中对 `VideoViewHolder` 统一调用 `detachPlayer()`，避免滑出屏幕后仍然在更新 UI。

5. **曝光事件预留接口**

   * `onExposureEvent(FeedCard card, int position, ExposureStage stage)`：

     * 当前仅作为占位，用于未来在 Adapter 内根据曝光状态修改卡片 UI（例如标记“已读”等）。
     * 真正的曝光上报逻辑由 `ExposureTracker → ExposureLogger` 完成。

---

# 3. 曝光事件系统（Exposure）

### 3.1 目标

* 精准记录每条卡片在列表中的曝光阶段变化，用于：

  * 日志调试（当前版本已实现）
  * 将来接入埋点上报（可直接基于 `ExposureEvent`）

### 3.2 核心组件

* `ExposureStage`：枚举，四个阶段：

  * `ENTER`：露出（0% < ratio < 50%）
  * `HALF`：露出 ≥ 50% 且 < 100%
  * `FULL`：完全露出（≈100%）
  * `EXIT`：从可见变为完全不可见

* `ExposureEvent`：

  * `id`、`position`、`stage`、`timestamp`、`title`、`cardType` 等字段。

* `ExposureLogger`：

  * 内部缓存所有事件的字符串日志（带时间戳、位置、阶段等）。
  * 提供 `log(event)`、`getAllLogs()`、`addListener/removeListener`。
  * 在 `log()` 时会同步通知所有 Listener。

* `ExposureTracker`：

  * 绑定 RecyclerView + Adapter：

    ```java
    exposureTracker = new ExposureTracker(recyclerView, adapter);
    ```
  * 实现 `RecyclerView.OnChildAttachStateChangeListener`：

    * 在 child attach/detach 以及手动调用 `dispatchExposure()` 时：

      * 计算当前所有可见 item 的可见比例。
      * 根据比例映射为 `ENTER/HALF/FULL/EXIT`。
      * 与上一帧阶段对比，发生变化则生成/记录 `ExposureEvent`。

### 3.3 悬浮日志面板（MainActivity 内）

* MainActivity 实现 `ExposureLogger.Listener`：

  * `onNewEvent(event, formatted)` 收到新日志。
  * 将 `formatted` 插入悬浮窗 RecyclerView 顶部，更新 UI。
* 通过 FAB 控制显示 / 隐藏：

  * 默认隐藏，不打扰正常用户使用。
  * 调试时点击 FAB 即可实时看到曝光日志。

### 3.4 独立曝光调试 Activity（ExposureDebugActivity）

* `ExposureDebugActivity` 使用 `ActivityExposureDebugBinding` 布局：

  * 内部一个 RecyclerView + `LogAdapter` 展示曝光日志。
* 启动时：

  * 一次性从 `ExposureLogger.getAllLogs()` 取出当前日志列表。
  * 注册为 `ExposureLogger.Listener`，实时追加之后的日志。
* 适用场景：

  * 不想每次都用悬浮层，可以单独开一个 Activity 做「曝光日志监视终端」。

---

# 4. 视频播放与自动播放（中心锁定）

### 4.1 视频资源

* 所有视频来自 `res/raw` 下的 mp4 文件。
* 在 `FeedRemoteDataSource` 中通过 `VIDEO_RES_IDS` 数组维护 5 个视频：

  * `R.raw.video1` ~ `R.raw.video5`。
* 生成视频卡片时按序轮流分配，有效模拟多视频场景。

### 4.2 播放内核：ExoPlayer + PlayerView

* 使用 **AndroidX Media3 ExoPlayer**：

  * 在 Fragment 中创建单一 ExoPlayer 实例。
  * 使用 `PlayerView` 作为显示控件。
* 播放本地 raw 资源示意：

  ```java
  int resId = vvh.getVideoResId();
  Uri uri = RawResourceDataSource.buildRawResourceUri(resId);
  MediaItem mediaItem = MediaItem.fromUri(uri);
  player.setMediaItem(mediaItem);
  player.prepare();
  player.play();
  ```
* 默认 `setRepeatMode(Player.REPEAT_MODE_ONE)`，形成循环播放效果。

### 4.3 自动播放策略（中心锁定）

* 调用时机：

  * RecyclerView 滚动时（`onScrolled`）。
  * 列表数据更新完成后手动触发一次（确保初次进入时也能自动播放）。

* 核心逻辑（`autoPlayCenterVideo()`）：

  1. 计算 RecyclerView 在屏幕上的可见区域及中心 Y。
  2. 遍历当前可见的 child view：

     * 拿到每个 child 的中心 Y。
     * 若对应 item 为 `TYPE_VIDEO`，计算与 RecyclerView 中心的距离。
  3. 找到距离最小的那条视频卡片 → 记为 `bestPos`。
  4. 若 `bestPos` 与当前播放位置 `currentPlayingVideoPos` 不同：

     * `stopVideoAt(currentPlayingVideoPos)`。
     * `startVideoAt(bestPos)`。
  5. 若当前屏幕没有视频卡片：

     * 停止 Player 播放并解绑当前 VideoViewHolder。

### 4.4 倒计时 UI 实现

* 在 `VideoViewHolder.attachPlayer(player)` 时：

  * 将 Player 绑定到 PlayerView。
  * 启动 `startCountdown(player)`：

    * 每 1 秒执行一次：

      * `duration = player.getDuration()`
      * `position = player.getCurrentPosition()`
      * 计算 `remainSec` 并更新 `tvCountdown`。
    * duration 未准备好时暂时隐藏倒计时。
* 在 `detachPlayer()` 和 `onViewRecycled()` 中：

  * 调用 `cancelCountdown()` 停止任务，避免内存泄漏和多次重复更新。

---

# 5. 性能与用户体验

* DiffUtil + ListAdapter：避免整表刷新，滚动顺滑。
* Glide：异步图片加载 + 缓存。
* **单一 ExoPlayer 实例**：

  * 通过 attach/detach 到不同 PlayerView 实现切换播放。
  * 避免创建多个 Player，降低内存和 CPU 占用。
* IO、网络模拟均在单线程线程池中执行，避免阻塞主线程。
* 友好的状态提示：

  * 下拉刷新动画。
  * LoadMore Footer。
  * 空页面占位（无数据时）。
  * 错误覆盖层 + 重试按钮（网络失败且无缓存时）。

---

# 6. 如何运行本项目

1. 使用 Android Studio 打开工程。
2. 确认已配置好 Android SDK（API 24+）。
3. 连接模拟器或真机（Android 7.0+）。
4. 运行 `app` 模块。
5. 推荐交互体验：

   * 下拉刷新多次，观察缓存 & 错误页行为。
   * 连续多次滑到列表底部触发「加载更多」，体验无限生成。
   * 长按删除不同类型卡片。
   * 点击右下角悬浮按钮，观察曝光日志在滚动时的变化。
   * 滚动寻找视频卡片，观察：

     * 中心锁定自动播放逻辑；
     * 右下角剩余时间倒计时；
     * 滑出屏幕后自动停止播放和倒计时。


