// 这一层是“数据仓库（Repository）”层：
// - 负责把「远程数据 Remote」+「本地缓存 Local」+「内存中的当前列表 currentList」+「分页状态」统一管理起来。
// - ViewModel / UI 不直接找 Remote / Local，而是通过 Repository 拿数据。
// - 这样做符合常见的 MVVM 分层：UI <- ViewModel <- Repository <- DataSource(Remote/Local)
package com.example.feedapp.data.repository;

import android.content.Context;

import com.example.feedapp.data.local.FeedLocalDataSource;
import com.example.feedapp.data.model.FeedCard;
import com.example.feedapp.data.model.FeedPageResult;
import com.example.feedapp.data.remote.FeedRemoteDataSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FeedRepository 是“单一入口”，负责：
 *
 * 1. 刷新数据（下拉刷新）：
 *    - 调用 RemoteDataSource 模拟“网络请求，拉第一页”；
 *    - 更新内存中的 currentList；
 *    - 将最新列表保存到 LocalDataSource 作为缓存；
 *    - 通过 Callback 把结果通知给 ViewModel。
 *
 * 2. 加载更多（上拉 loadMore）：
 *    - 使用 nextPage 继续去 RemoteDataSource 拉取下一页；
 *    - 把新的卡片追加到 currentList；
 *    - 更新 hasMore / nextPage 状态；
 *    - 失败时保持现有列表不变。
 *
 * 3. 删除卡片：
 *    - 从 currentList 中按 id 删除某个 FeedCard；
 *    - 同步更新本地缓存（LocalDataSource）。
 *
 * 4. 封装“是否还能加载更多”、“当前列表快照”等状态：
 *    - ViewModel 和 UI 只需要和 Repository 交互，而不关心具体网络/缓存细节。
 *
 * 和其他类的关系：
 * - FeedRemoteDataSource：提供 loadFeedPage 之类的分页数据（模拟 HTTP）。
 * - FeedLocalDataSource ：提供 loadCache，用于进阶要求中的“本地缓存”。
 * - FeedViewModel       ：通过 Repository 的 refresh/loadMore 等方法驱动 UI。
 * - FeedAdapter / FeedFragment：最终只消费 List<FeedCard>，不接触 Repository。
 */
public class FeedRepository {
    /**
     * Repository 对外暴露的回调接口：
     *
     * Repository 的所有耗时操作（网络 + IO）都在后台线程执行，
     * 然后通过这个 Callback 把结果抛给上层（ViewModel）。
     *
     * onSuccess：
     *   - 给 ViewModel 一个“当前最新列表”的快照（list）；
     *   - 告诉 ViewModel 是否还有更多（hasMore）；
     *   - 告诉下一页的页码（nextPage），以便后续 loadMore。
     *
     * onError：
     *   - 把异常对象传给 ViewModel，用于展示错误提示；
     *   - 同时提供一个 cache 列表（比如本地缓存数据）。
     *     - 对于刷新失败：cache = LocalDataSource 中的缓存；
     *     - 对于加载更多失败：cache = Collections.emptyList()，表示“列表不变”。
     */

    public interface Callback {
        void onSuccess(List<FeedCard> list, boolean hasMore, int nextPage);
        void onError(Throwable t, List<FeedCard> cache);
    }
    // -------------------- 数据源和线程池 --------------------

    /**
     * 远程数据源：模拟服务端接口（例如 Retrofit 的封装）。
     * - 提供 loadPage / loadFeedPage 等方法，返回 FeedPageResult。
     */
    private final FeedRemoteDataSource remote;
    /**
     * 本地数据源：用于缓存当前列表（例如写到 json 文件）。
     * - 提供 saveCache / loadCache。
     */
    private final FeedLocalDataSource local;
    /**
     * 单线程线程池：
     * - 所有数据加载任务都丢到这个线程池执行，避免阻塞主线程；
     * - 使用 singleThread 的好处是：避免同时有多个加载任务互相抢状态。
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // -------------------- 内存中的状态 --------------------

    /**
     * 当前已经加载到内存中的完整列表：
     * - 刷新成功：会清空并重新填充；
     * - 加载更多成功：会在末尾追加；
     * - 删除某卡片：会从这里移除；
     * - 本地缓存也会以它为基准进行保存。
     *
     * 注意：所有对 currentList 的写操作都包裹在 synchronized 块中，
     *       保证多线程下不会出现并发问题。
     */
    private final List<FeedCard> currentList = new ArrayList<>();
    /**
     * 下一次加载更多时应该请求的页码：
     * - 刷新成功后由 Remote 返回的 nextPage 设置；
     * - 加载更多成功后再更新为下一页的页码；
     * - loadMore 时会把它抓出来作为 pageToLoad。
     */
    private int nextPage = 0;
    /**
     * 是否还有更多数据：
     * - 由 RemoteDataSource 返回的 hasMore 决定；
     * - 为 false 时，loadMore 应该直接 return（避免多余请求）。
     */
    private boolean hasMore = true;
    /**
     * 当前是否正在进行“网络请求 + 数据加载”：
     * - 避免用户连续触发多个刷新 / 加载更多请求，相互覆盖状态；
     * - refresh 和 loadMore 开始时把 loading 置为 true，结束时 finally 中置回 false。
     */
    private boolean loading = false;

    /**
     * Repository 构造函数：
     * - 需要 Context 是为了创建 LocalDataSource（本地缓存需要文件目录）；
     * - RemoteDataSource 不需要 Context，因为它只是模拟网络数据。
     */
    public FeedRepository(Context context) {
        this.remote = new FeedRemoteDataSource();
        this.local = new FeedLocalDataSource(context);
    }
    /**
     * 提供一个简洁的“是否还能加载更多”的查询方法给 ViewModel / UI。
     *
     * @return 当 hasMore == true 且 loading == false 时，才可以继续发起 loadMore。
     */
    public boolean canLoadMore() {
        return hasMore && !loading;
    }

    // -------------------- 刷新（从第一页重新拉取） --------------------

    /**
     * 刷新数据：
     * - 语义等价于「把列表重置到最新状态」；
     * - 对应 UI 上的下拉刷新动作。
     *
     * 流程：
     * 1. 如果当前已经在 loading，则直接 return，防止重复请求；
     * 2. 将 loading 置为 true；
     * 3. 在线程池中执行任务：
     *    3.1 调用 remote.loadFeedPage(0, 20) 模拟“网络拉第一页”；
     *    3.2 用 synchronized(currentList) 更新内存列表 + hasMore + nextPage；
     *    3.3 把最新的 currentList 保存到 LocalDataSource 作为缓存；
     *    3.4 调用 callback.onSuccess(...) 把结果通知给 ViewModel；
     * 4. 如果中间抛异常：
     *    4.1 从本地缓存 local.loadCache() 读一份数据作为 cache；
     *    4.2 调用 callback.onError(e, cache)。
     * 5. 最后在 finally 中把 loading 恢复为 false。
     *
     * 说明：
     * - page 固定写成 0，表示“首页”；
     * - pageSize 这里写死为 20，当然可以在构造函数中抽出来配置。
     */
    public void refresh(Callback callback) {
        // 正在加载中就不再重复发起刷新请求
        if (loading) return;
        loading = true;
        // 在线程池中进行“假装网络请求”
        executor.execute(() -> {
            try {
                // 访问“服务端”：拉取第一页
                FeedPageResult result = remote.loadFeedPage(0, 20);
                // 更新内存列表 + 分页状态
                synchronized (currentList) {
                    currentList.clear();
                    currentList.addAll(result.getCards());
                    hasMore = result.isHasMore();
                    nextPage = result.getNextPage();
                    // 刷新成功后，把当前最新列表写入本地缓存
                    local.saveCache(currentList);
                }
                // 把“当前最新列表的快照”抛给上层（ViewModel）。
                // 注意这里用 getCurrentSnapshot()，是为了返回一个新的 ArrayList，
                // 避免调用方直接修改到内部的 currentList。
                callback.onSuccess(getCurrentSnapshot(), hasMore, nextPage);
            } catch (Exception e) {
                // 刷新失败：从本地缓存拉一份兜底数据（如果有）
                List<FeedCard> cache = local.loadCache();
                callback.onError(e, cache);
            } finally {
                // 无论成功或失败，都要把 loading 标记恢复
                loading = false;
            }
        });
    }

    // -------------------- 加载更多 --------------------

    /**
     * 加载更多数据：
     * - 对应 UI 上的“滑到底部触发 loadMore”；
     * - 使用当前的 nextPage 作为要请求的页码。
     *
     * 流程：
     * 1. 如果当前 loading == true 或 hasMore == false，则直接 return；
     * 2. 把 loading 置为 true；
     * 3. 记录下 pageToLoad = nextPage（避免多线程情况被改动）；
     * 4. 在线程池中执行：
     *    4.1 调用 remote.loadPage(pageToLoad, 20)；
     *    4.2 synchronized 块中把结果 append 到 currentList；
     *    4.3 更新 hasMore / nextPage；
     *    4.4 同步保存缓存；
     *    4.5 调用 onSuccess(...) 把“追加后的完整列表快照”抛给上层；
     * 5. 出错时：
     *    - 不修改 currentList（保持原样）；
     *    - onError(e, Collections.emptyList()) 告诉上层“列表不变，只是报错”；
     * 6. finally 里恢复 loading。
     */
    public void loadMore(Callback callback) {
        // loading 中或已经没有更多了：都不应该继续发起请求
        if (loading || !hasMore) return;
        loading = true;
        // 把当前的 nextPage 保存下来，避免在后台线程中被其他操作修改
        final int pageToLoad = nextPage;
        executor.execute(() -> {
            try {
                // 从“服务端”拉取下一页数据
                FeedPageResult result = remote.loadFeedPage(pageToLoad, 20);
                synchronized (currentList) {
                    // 在现有列表后面追加新数据
                    currentList.addAll(result.getCards());
                    hasMore = result.isHasMore();
                    nextPage = result.getNextPage();
                    // 同步保存到本地缓存：下次进 app 时可以直接展示一份较新的列表
                    local.saveCache(currentList);
                }
                // 把「追加后的完整列表快照」返回给上层
                callback.onSuccess(getCurrentSnapshot(), hasMore, nextPage);
            } catch (Exception e) {
                // 加载更多失败时：不改 currentList，只把错误回调出去。
                // cache 这里传空列表，表示“列表没有变，UI 只需停止 loading、提示一下即可”。
                callback.onError(e, Collections.emptyList());
            } finally {
                loading = false;
            }
        });
    }

    // -------------------- 删除卡片（长按删除） --------------------

    /**
     * 删除某一条卡片（通过 id）：
     * - 对应 UI 中“长按弹出删除确认框，点击确定后执行删除”；
     * - Repository 负责从内存列表中删除，并同步更新本地缓存。
     *
     * @param id 被删除卡片的 id（FeedCard.getId()）
     */
    public void deleteCard(String id) {
        synchronized (currentList) {
            // 使用迭代器安全地删除列表中的某一项
            Iterator<FeedCard> iterator = currentList.iterator();
            while (iterator.hasNext()) {
                FeedCard card = iterator.next();
                if (card.getId().equals(id)) {
                    iterator.remove();
                    break;
                }
            }
            // 删除后也更新一份缓存，保证下次打开 app 时能看到已经删除后的列表
            local.saveCache(currentList);
        }
    }
    // -------------------- 工具方法：获取当前列表的快照 --------------------

    /**
     * 获取当前内存列表的“安全快照”：
     *
     * - synchronized(currentList) 保证多线程读写安全；
     * - 返回的是 new ArrayList<>(currentList)，而不是直接返回 currentList 本身，
     *   这样调用方就算对返回的 List 做增删改，也不会影响内部真实状态。
     *
     * 典型使用场景：
     * - 刷新成功 / 加载更多成功后，
     *   Repository 会用这个方法把“当前最新列表”传给 ViewModel / UI。
     */
    public List<FeedCard> getCurrentSnapshot() {
        synchronized (currentList) {
            return new ArrayList<>(currentList);
        }
    }
}
