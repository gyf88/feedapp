package com.example.feedapp.data.model;

import java.util.List;

/**
 * FeedPageResult 表示「一次分页请求」的返回结果。
 *
 * 它和课程要求中的「所有列表内容均由服务端下发」对应：
 * - FeedRemoteDataSource.loadPage(...) 会模拟一个 HTTP 接口，返回这个对象；
 * - FeedRepository 再把它拆开，更新：
 *      - 当前列表 List<FeedCard>
 *      - 是否还有更多 hasMore
 *      - 下一页页码 nextPage
 *
 * 这样做的好处是：
 * - 结构清晰：网络层只关心“这一页”，UI 层再决定“是覆盖还是追加”；
 * - 以后如果你真的接 HTTP 接口，也可以直接映射到类似的数据结构。
 */
public class FeedPageResult {

    /**
     * 当前这一页的所有卡片数据。
     * 注意：这里是一页的数据，最后会由 Repository 合并到总列表里。
     */
    private List<FeedCard> cards;

    /**
     * 是否还有下一页数据：
     * - true  表示还能继续上拉 loadMore；
     * - false 表示已经到底了，可以在 Footer 显示「没有更多了」。
     */
    private boolean hasMore;

    /**
     * 下一页的页码：
     * - 比如当前 page = 1，这里会是 2；
     * - 如果 hasMore = false，这个值即使设置了也不会被继续使用。
     */
    private int nextPage;

    // -------------------- Getter / Setter --------------------

    public List<FeedCard> getCards() {
        return cards;
    }

    public void setCards(List<FeedCard> cards) {
        this.cards = cards;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public int getNextPage() {
        return nextPage;
    }

    public void setNextPage(int nextPage) {
        this.nextPage = nextPage;
    }
}
