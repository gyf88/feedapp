package com.example.feedapp.data.model;

/**
 * FeedCard 表示「信息流列表」中的一张卡片的数据结构。
 *
 * 它只关心「数据长什么样」，完全不关心「怎么展示」——展示逻辑在 RecyclerView 的 Adapter 里。
 *
 * 本项目中，FeedCard 会在以下位置被使用：
 * 1. FeedRemoteDataSource：模拟服务端返回的一页列表数据时，会 new 出很多 FeedCard。
 * 2. FeedLocalDataSource ：做本地缓存时，会把 List<FeedCard> 序列化到 json 文件中。
 * 3. FeedRepository / FeedViewModel：作为界面状态的一部分，暴露给 UI 层。
 * 4. FeedAdapter        ：根据 FeedCard 的 cardType 决定用哪种 ViewHolder 渲染。
 */
public class FeedCard {

    // -------------------- 卡片类型常量 --------------------

    /** 纯文字卡片，例如只有标题、概要内容。 */
    public static final int TYPE_TEXT = 0;

    /** 图文卡片，带一张封面图。 */
    public static final int TYPE_IMAGE = 1;

    /** 视频卡片，会在列表中自动播放视频。 */
    public static final int TYPE_VIDEO = 2;


    // -------------------- 排版方式常量 --------------------

    /**
     * 单列排版：卡片占据一整行。
     * 对应课程要求中「单列排版」的情况。
     */
    public static final int LAYOUT_SINGLE = 1;

    /**
     * 双列排版：卡片占据半行，列表会并排展示两张卡片。
     * 对应课程要求中「双列排版」的情况。
     */
    public static final int LAYOUT_DOUBLE = 2;


    // -------------------- 数据字段 --------------------

    /**
     * 卡片的唯一标识：
     * - 在模拟服务端中使用 UUID 随机生成；
     * - 在 DiffUtil 中用来判断「是不是同一条卡片」。
     */
    private String id;

    /**
     * 卡片类型，对应上面的 TYPE_TEXT / TYPE_IMAGE / TYPE_VIDEO。
     * RecyclerView.Adapter 会根据它选择对应的 ViewHolder 类型。
     */
    private int cardType;

    /**
     * 排版类型，对应 LAYOUT_SINGLE / LAYOUT_DOUBLE。
     * 如果你后面实现双列布局，可以用这个字段控制 spanSize。
     */
    private int layoutType;

    /**
     * 标题文案，例如「今日头条推荐」「某某新闻」。
     * 文字卡片、图片卡片、视频卡片都会用到。
     */
    private String title;

    /**
     * 副标题 / 来源信息，例如「来自：XXX」「推荐于 5 分钟前」。
     * 目前主要用于文字卡片。
     */
    private String subTitle;

    /**
     * 主体内容 / 简要描述。
     * 对于文本卡片：展示在正文区域；
     * 对于图片 / 视频卡片：可以作为下方说明文字或不展示。
     */
    private String content;

    /**
     * 图片 URL：
     * - TYPE_IMAGE：作为封面图显示；
     * - TYPE_VIDEO：作为视频封面图显示（视频未播放或暂停时可用）。
     *
     * 我们使用的是 picsum.photos 的免费图片接口来做 mock。
     */
    private String imageUrl;

    /**
     * 本地视频资源 ID：
     * - 对应 res/raw 目录下的 mp4 文件，例如 R.raw.video1；
     * - 只在 TYPE_VIDEO 卡片中有意义；
     * - ExoPlayer 在播放本地视频时会依赖这个字段构造 MediaItem。
     */
    private int videoResId;


    // -------------------- Getter / Setter --------------------
    // 下面都是很常规的 Java Bean 写法，方便：
    // - Gson 进行序列化 / 反序列化；
    // - Adapter / ViewModel 读写这些字段。

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

    public int getVideoResId() {
        return videoResId;
    }

    public void setVideoResId(int videoResId) {
        this.videoResId = videoResId;
    }
}
