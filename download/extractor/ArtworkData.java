package extractor;

/**
 * 艺术品数据容器 - 存储提取结果
 */
public class ArtworkData {
    private String lotNumber;
    private String artist;
    private String medium;
    private String format;
    private String dimensions;
    private String valuation;
    private String auctionHouse;
    private String auctionName;
    private String auctionSession;
    private String auctionDate;
    private String auctionLocation;
    private String previewTime;
    private String previewLocation;

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getMedium() { return medium; }
    public void setMedium(String medium) { this.medium = medium; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }

    public String getValuation() { return valuation; }
    public void setValuation(String valuation) { this.valuation = valuation; }

    public String getAuctionHouse() { return auctionHouse; }
    public void setAuctionHouse(String auctionHouse) { this.auctionHouse = auctionHouse; }

    public String getAuctionName() { return auctionName; }
    public void setAuctionName(String auctionName) { this.auctionName = auctionName; }

    public String getAuctionSession() { return auctionSession; }
    public void setAuctionSession(String auctionSession) { this.auctionSession = auctionSession; }

    public String getAuctionDate() { return auctionDate; }
    public void setAuctionDate(String auctionDate) { this.auctionDate = auctionDate; }

    public String getAuctionLocation() { return auctionLocation; }
    public void setAuctionLocation(String auctionLocation) { this.auctionLocation = auctionLocation; }

    public String getPreviewTime() { return previewTime; }
    public void setPreviewTime(String previewTime) { this.previewTime = previewTime; }

    public String getPreviewLocation() { return previewLocation; }
    public void setPreviewLocation(String previewLocation) { this.previewLocation = previewLocation; }
}
