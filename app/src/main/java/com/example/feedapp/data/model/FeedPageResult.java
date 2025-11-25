package com.example.feedapp.data.model;

import java.util.List;

public class FeedPageResult {

    private List<FeedCard> cards;
    private boolean hasMore;
    private int nextPage;

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
