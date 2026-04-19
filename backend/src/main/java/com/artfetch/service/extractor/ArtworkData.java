package com.artfetch.service.extractor;

/**
 * 艺术品抓取数据容器 - 存储从列表页和详情页提取的结果
 */
public class ArtworkData {
    public String externalId;
    public String sourceUrl;
    public String imageUrl;
    public String originalImageUrl;

    // 列表页 + 详情页均可提取，详情页优先
    public String lotNumber;       // 拍品编号
    public String title;           // 拍品名称
    public String artist;          // 作者
    public String valuation;       // 估价
    public String transactionPrice; // 成交价
    public boolean transactionPriceLoginRequired; // 未拿到成交价时是否因登录受限
    public String transactionPriceMessage; // 成交价补充说明
    public String auctionDate;     // 拍卖日期
    public String auctionHouse;    // 拍卖公司

    // 仅详情页提取
    public String medium;          // 材质
    public String format;          // 形制
    public String dimensions;      // 尺寸
    public String auctionName;     // 拍卖会
    public String auctionSession;  // 拍卖专场
    public String auctionLocation; // 拍卖地点
    public String previewTime;     // 预展时间
    public String previewLocation; // 预展地点
    public String description;     // 拍品描述
    public String extraData;       // 额外非结构化数据
}
