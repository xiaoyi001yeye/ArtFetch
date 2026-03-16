package com.artfetch.service;

import com.artfetch.config.AppProperties;
import com.artfetch.entity.Artwork;
import com.artfetch.entity.SearchTask;
import com.artfetch.repository.ArtworkRepository;
import com.artfetch.repository.SearchTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从雅昌艺搜（artso.artron.net）抓取拍卖艺术品数据。
 *
 * URL 格式：
 *   https://artso.artron.net/auction/search_auction.php?keyword=张大千&page=1
 *
 * 列表页解析逻辑：
 *   - 总页数：HTML 中 <b class="fmE">N</b>页
 *   - 每件拍品：.listImg ul li
 *     - 详情URL + 拍品ID：<a href="https://auction.artron.net/paimai-artXXX">
 *     - 标题（含拍品号）：<h3><a>[拍品号]艺术家 作品名</a></h3>
 *     - 估价/成交价：<i class="fmE fred fb">N万</i>
 *     - 拍卖行：<a href="...org_detail...">拍卖行名</a>
 *     - 拍卖日期：文本节点
 *     - 图片：JS 变量 get_arr([{url:...},...]) 中第一张
 *
 * 详情页解析逻辑：
 *   - 从 https://auction.artron.net/paimai-artXXX 获取
 *   - 解析 th/td 或 dt/dd 格式的字段：材质、形制、尺寸、估价、拍卖会、拍卖专场、拍卖日期、拍卖地点、预展时间、预展地点
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FetchService {

    private static final Pattern TOTAL_PAGES_PATTERN =
            Pattern.compile("共有<b[^>]*>(\\d+)</b>页");
    private static final Pattern IMG_URL_PATTERN =
            Pattern.compile("\"url\":\"(https://thumb\\.artron\\.net[^\"]+)\"");
    private static final Pattern LOT_TITLE_PATTERN =
            Pattern.compile("^\\[(\\d+)\\](.*)$");

    private final AppProperties appProperties;
    private final ArtworkRepository artworkRepository;
    private final SearchTaskRepository taskRepository;

    /**
     * 执行一轮完整抓取：从第1页到最后一页，逐页解析并保存。
     * 调用方负责捕获 InterruptedException 以支持暂停/取消。
     */
    public int fetchAll(SearchTask task) throws InterruptedException {
        AppProperties.Source cfg = appProperties.getSource();
        int totalNewCount = 0;
        int totalPages = 0;

        log.info("Task[{}] 开始抓取，关键词：{}", task.getId(), task.getKeyword());

        int startPage = Math.max(1, task.getCurrentPage() == 0 ? 1 : task.getCurrentPage());

        for (int page = startPage; ; page++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Task interrupted");
            }

            String html = fetchPage(task.getId(), task.getKeyword(), page, cfg);
            if (html == null) {
                log.warn("Task[{}] 第{}页请求失败，停止本轮抓取", task.getId(), page);
                break;
            }

            if (page == startPage) {
                totalPages = parseTotalPages(html);
                if (totalPages == 0) {
                    log.warn("Task[{}] 未能解析到总页数，可能关键词无结果或被反爬", task.getId());
                    break;
                }
                log.info("Task[{}] 共 {} 页", task.getId(), totalPages);
                updateTaskProgress(task, page, totalPages, task.getTotalFetched());
            }

            List<ArtworkData> artworks = parseArtworks(html);
            if (artworks.isEmpty()) {
                log.info("Task[{}] 第{}页无数据，抓取结束", task.getId(), page);
                break;
            }

            // 逐件抓取详情页以丰富字段
            for (ArtworkData data : artworks) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Task interrupted");
                }
                enrichFromDetail(data, task.getId(), cfg);
                if (cfg.getRequestDelayMs() > 0) {
                    Thread.sleep(cfg.getRequestDelayMs());
                }
            }

            int saved = saveArtworks(task, artworks);
            totalNewCount += saved;
            int newTotal = task.getTotalFetched() + saved;
            updateTaskProgress(task, page, totalPages, newTotal);

            log.info("Task[{}] 第{}/{}页，解析{}条，新增/更新{}条，累计{}条",
                    task.getId(), page, totalPages, artworks.size(), saved, newTotal);

            if (page >= totalPages) {
                log.info("Task[{}] 所有页面抓取完毕，本轮新增 {} 条", task.getId(), totalNewCount);
                break;
            }

            if (cfg.getRequestDelayMs() > 0) {
                Thread.sleep(cfg.getRequestDelayMs());
            }
        }

        return totalNewCount;
    }

    // ---- 网络请求 -------------------------------------------------------

    private String fetchPage(Long taskId, String keyword, int page, AppProperties.Source cfg) {
        try {
            String url = cfg.getBaseUrl() + "?keyword=" + encode(keyword) + "&page=" + page;
            log.debug("Task[{}] GET {}", taskId, url);

            org.jsoup.Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                               "Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", "https://artso.artron.net/")
                    .timeout(30_000)
                    .ignoreContentType(true)
                    .execute();

            return response.body();
        } catch (Exception e) {
            log.error("Task[{}] 请求第{}页异常：{}", taskId, page, e.getMessage());
            return null;
        }
    }

    // ---- HTML 解析（列表页）-----------------------------------------------

    private int parseTotalPages(String html) {
        Matcher m = TOTAL_PAGES_PATTERN.matcher(html);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    private List<ArtworkData> parseArtworks(String html) {
        List<ArtworkData> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        List<String> imgUrls = parseImgUrls(html);

        Elements lis = doc.select(".listImg ul li");
        for (int i = 0; i < lis.size(); i++) {
            Element li = lis.get(i);
            ArtworkData data = parseLi(li);
            if (data == null) continue;
            if (i < imgUrls.size()) {
                data.imageUrl = imgUrls.get(i);
            }
            list.add(data);
        }
        return list;
    }

    private ArtworkData parseLi(Element li) {
        Element linkEl = li.selectFirst("a[href*=paimai-art]");
        if (linkEl == null) return null;

        String sourceUrl = linkEl.absUrl("href");
        if (sourceUrl.isBlank()) sourceUrl = linkEl.attr("href");

        String externalId = sourceUrl.replaceAll(".*/paimai-", "");

        Element titleEl = li.selectFirst("h3 a");
        String rawTitle = titleEl != null ? titleEl.text().trim() : "";
        String artist = null;
        String artworkTitle = rawTitle;

        Matcher m = LOT_TITLE_PATTERN.matcher(rawTitle);
        if (m.matches()) {
            String rest = m.group(2).trim();
            int spIdx = rest.indexOf(' ');
            if (spIdx > 0) {
                artist = rest.substring(0, spIdx).trim();
                artworkTitle = rest.substring(spIdx + 1).trim();
            } else {
                artworkTitle = rest;
            }
        }

        if (artworkTitle.isBlank()) return null;

        // 估价/成交价（列表页初始值，详情页会覆盖）
        Element priceEl = li.selectFirst("i.fmE.fred.fb");
        String valuation = priceEl != null ? priceEl.text().trim() : null;

        // 拍卖公司
        Element orgEl = li.selectFirst("a[href*=org_detail]");
        String auctionCompany = orgEl != null ? orgEl.text().trim() : null;

        // 拍卖日期
        String auctionDate = null;
        Elements pEls = li.select("p");
        for (Element p : pEls) {
            if (p.selectFirst("a[href*=org_detail]") != null) {
                String pText = p.text().replace(auctionCompany != null ? auctionCompany : "", "").trim();
                if (!pText.isBlank()) {
                    auctionDate = pText;
                }
                break;
            }
        }

        ArtworkData data = new ArtworkData();
        data.externalId = externalId;
        data.title = artworkTitle;
        data.artist = artist;
        data.valuation = valuation;
        data.auctionDate = auctionDate;
        data.auctionCompany = auctionCompany;
        data.sourceUrl = sourceUrl;
        return data;
    }

    private List<String> parseImgUrls(String html) {
        int start = html.indexOf("var arr=get_arr('");
        if (start == -1) return List.of();

        int jsonStart = html.indexOf('[', start);
        int jsonEnd = html.indexOf(']', jsonStart);
        if (jsonStart == -1 || jsonEnd == -1) return List.of();

        String jsonPart = html.substring(jsonStart, jsonEnd + 1);

        List<String> allUrls = new ArrayList<>();
        Matcher m = IMG_URL_PATTERN.matcher(jsonPart);
        while (m.find()) {
            allUrls.add(m.group(1).replace("\\/", "/"));
        }

        if (allUrls.isEmpty()) return List.of();

        List<String> firstPerArtwork = new ArrayList<>();
        Pattern artIdPattern = Pattern.compile("/art/(\\d+)/");
        String prevArtId = null;
        for (String url : allUrls) {
            Matcher am = artIdPattern.matcher(url);
            String artId = am.find() ? am.group(1) : null;
            if (artId == null || !artId.equals(prevArtId)) {
                firstPerArtwork.add(url);
                prevArtId = artId;
            }
        }
        return firstPerArtwork;
    }

    // ---- 详情页抓取与解析 -------------------------------------------------

    /**
     * 从拍品详情页补充完整字段。
     * 典型 artron 详情页含 th/td 或 dt/dd 结构的字段描述表。
     */
    private void enrichFromDetail(ArtworkData data, Long taskId, AppProperties.Source cfg) {
        if (data.sourceUrl == null || data.sourceUrl.isBlank()) return;
        try {
            log.debug("Task[{}] 抓取详情页：{}", taskId, data.sourceUrl);
            Document doc = Jsoup.connect(data.sourceUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                               "Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", "https://artso.artron.net/")
                    .timeout(30_000)
                    .get();

            String medium        = extractByLabel(doc, "材质", "材质尺寸");
            String format        = extractByLabel(doc, "形制");
            String dimensions    = extractByLabel(doc, "尺寸");
            String valuation     = extractByLabel(doc, "估价", "参考价", "起拍价");
            String auctionName   = extractByLabel(doc, "拍卖会", "专场拍卖", "拍卖名称");
            String auctionSession = extractByLabel(doc, "专场", "拍卖专场");
            String auctionDate   = extractByLabel(doc, "拍卖日期", "拍卖时间");
            String auctionLoc    = extractByLabel(doc, "拍卖地点", "拍卖地址");
            String previewTime   = extractByLabel(doc, "预展时间", "预展日期");
            String previewLoc    = extractByLabel(doc, "预展地点", "预展地址");
            String company       = extractByLabel(doc, "拍卖公司", "拍卖行");

            if (medium != null)        data.medium = medium;
            if (format != null)        data.format = format;
            if (dimensions != null)    data.dimensions = dimensions;
            if (valuation != null)     data.valuation = valuation;
            if (auctionName != null)   data.auctionName = auctionName;
            if (auctionSession != null) data.auctionSession = auctionSession;
            if (auctionDate != null)   data.auctionDate = auctionDate;
            if (auctionLoc != null)    data.auctionLocation = auctionLoc;
            if (previewTime != null)   data.previewTime = previewTime;
            if (previewLoc != null)    data.previewLocation = previewLoc;
            if (company != null)       data.auctionCompany = company;

        } catch (Exception e) {
            log.warn("Task[{}] 详情页抓取失败 {}：{}", taskId, data.sourceUrl, e.getMessage());
        }
    }

    /**
     * 在文档中按标签文本查找对应值，支持多个候选标签名（取第一个匹配）。
     * 依次尝试：th/td、dt/dd、span[class*=label]/span[class*=value]、含冒号的 label。
     */
    private String extractByLabel(Document doc, String... labels) {
        for (String label : labels) {
            // th → td
            Element th = doc.selectFirst("th:containsOwn(" + label + ")");
            if (th != null) {
                Element td = th.nextElementSibling();
                if (td != null && !td.text().isBlank()) return td.text().trim();
            }
            // dt → dd
            Element dt = doc.selectFirst("dt:containsOwn(" + label + ")");
            if (dt != null) {
                Element dd = dt.nextElementSibling();
                if (dd != null && !dd.text().isBlank()) return dd.text().trim();
            }
            // li/div 包含 "label：value" 的文本节点
            Elements containers = doc.select("li, p, div");
            for (Element el : containers) {
                String text = el.ownText();
                if (text.contains(label + "：") || text.contains(label + ":")) {
                    String val = text.replaceFirst(".*" + label + "[：:]\\s*", "").trim();
                    if (!val.isBlank()) return val;
                }
            }
        }
        return null;
    }

    // ---- 数据库写入（upsert）----------------------------------------------

    @Transactional
    protected int saveArtworks(SearchTask task, List<ArtworkData> items) {
        int saved = 0;
        for (ArtworkData item : items) {
            if (item.externalId == null || item.externalId.isBlank()) continue;

            // 按 externalId 全局查重：已有则更新，无则新建
            Artwork artwork = artworkRepository.findByExternalId(item.externalId)
                    .orElse(null);
            boolean isNew = (artwork == null);
            if (isNew) {
                artwork = new Artwork();
                artwork.setTask(task);
                artwork.setExternalId(item.externalId);
            }

            // 更新所有字段
            artwork.setTitle(item.title);
            artwork.setArtist(item.artist);
            artwork.setMedium(item.medium);
            artwork.setFormat(item.format);
            artwork.setDimensions(item.dimensions);
            artwork.setValuation(item.valuation);
            artwork.setYear(item.auctionDate);       // year 字段存拍卖日期
            artwork.setCollection(item.auctionCompany); // collection 字段存拍卖公司
            artwork.setAuctionName(item.auctionName);
            artwork.setAuctionSession(item.auctionSession);
            artwork.setAuctionLocation(item.auctionLocation);
            artwork.setPreviewTime(item.previewTime);
            artwork.setPreviewLocation(item.previewLocation);
            artwork.setImageUrl(item.imageUrl);
            artwork.setSourceUrl(item.sourceUrl);

            artworkRepository.save(artwork);
            if (isNew) saved++;
        }
        return saved;
    }

    @Transactional
    protected void updateTaskProgress(SearchTask task, int currentPage, int totalPages, int totalFetched) {
        taskRepository.findById(task.getId()).ifPresent(t -> {
            t.setCurrentPage(currentPage);
            t.setTotalPages(totalPages);
            t.setTotalFetched(totalFetched);
            task.setCurrentPage(currentPage);
            task.setTotalFetched(totalFetched);
            taskRepository.save(t);
        });
    }

    // ---- 工具 ------------------------------------------------------------

    private String encode(String keyword) {
        try {
            return java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return keyword;
        }
    }

    // ---- 内部数据结构 -----------------------------------------------------

    private static class ArtworkData {
        String externalId;
        String title;          // 拍品名称
        String artist;         // 艺术家
        String medium;         // 材质
        String format;         // 形制
        String dimensions;     // 尺寸
        String valuation;      // 估价
        String auctionDate;    // 拍卖日期
        String auctionCompany; // 拍卖公司
        String auctionName;    // 拍卖会
        String auctionSession; // 拍卖专场
        String auctionLocation;// 拍卖地点
        String previewTime;    // 预展时间
        String previewLocation;// 预展地点
        String imageUrl;
        String sourceUrl;
    }
}
