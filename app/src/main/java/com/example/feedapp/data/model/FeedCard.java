package com.example.feedapp.data.model;

public class FeedCard {

    // 卡片类型（后面会扩展多样式）
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_VIDEO = 2;

    // 单列 / 双列排版
    public static final int LAYOUT_SINGLE = 1;
    public static final int LAYOUT_DOUBLE = 2;

    private String id;
    private int cardType;
    private int layoutType;
    private String title;
    private String subTitle;
    private String content;
    private String imageUrl;
    private int fakeVideoDurationSec;

    // —— 下面是 getter / setter，可以让 AS 自动生成 —— //

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getCardType() {
        return cardType;
    }

    public void setCardType(int cardType) {
        this.cardType = cardType;
    }

    public int getLayoutType() {
        return layoutType;
    }

    public void setLayoutType(int layoutType) {
        this.layoutType = layoutType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubTitle() {
        return subTitle;
    }

    public void setSubTitle(String subTitle) {
        this.subTitle = subTitle;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getFakeVideoDurationSec() {
        return fakeVideoDurationSec;
    }

    public void setFakeVideoDurationSec(int fakeVideoDurationSec) {
        this.fakeVideoDurationSec = fakeVideoDurationSec;
    }
}
