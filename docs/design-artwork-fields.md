# 艺术品字段整理设计文档

## 背景

当前 `Artwork` 实体中有两个字段命名存在歧义，导致后端、前端、数据库、抓取逻辑之间的对应关系不清晰：

| 当前字段名 | 实际含义 | 问题 |
|-----------|---------|------|
| `year` | 拍卖日期 | `year` 语义是"年份"，但实际存的是完整日期字符串（如"2023-11-15"） |
| `collection` | 拍卖公司 | `collection` 语义是"收藏品"，与拍卖公司毫无关联 |

另外，`category` 字段目前在抓取逻辑中从未赋值，前端也没有展示，属于死字段，需要删除。同时，需要新增**拍品编号**字段，该字段可以从拍卖网站抓取。

---

## 目标

确保实体中的 12 个必要字段清晰、命名准确，并在前端详情页和列表页正确展示：

| 字段含义 | 建议字段名 | 当前字段名 | 状态 |
|---------|-----------|-----------|------|
| 拍品编号 | `lotNumber` | 新增 | ➕ 新增 |
| 作者 | `artist` | `artist` | ✅ 无需改动 |
| 材质 | `medium` | `medium` | ✅ 无需改动 |
| 尺寸 | `dimensions` | `dimensions` | ✅ 无需改动 |
| 估价 | `valuation` | `valuation` | ✅ 无需改动 |
| 形制 | `format` | `format` | ✅ 无需改动 |
| 拍卖公司 | `auctionHouse` | `collection` | ❌ 需重命名 |
| 拍卖会 | `auctionName` | `auctionName` | ✅ 无需改动 |
| 拍卖专场 | `auctionSession` | `auctionSession` | ✅ 无需改动 |
| 拍卖日期 | `auctionDate` | `year` | ❌ 需重命名 |
| 拍卖地点 | `auctionLocation` | `auctionLocation` | ✅ 无需改动 |
| 预展时间 | `previewTime` | `previewTime` | ✅ 无需改动 |
| 预展地点 | `previewLocation` | `previewLocation` | ✅ 无需改动 |

---

## 变更范围

### 1. 数据库（PostgreSQL）

**历史迁移说明**：这次字段调整最初以手工 SQL 描述，后续数据库结构迁移统一纳入 Flyway。发布版本必须与最新 Flyway 迁移版本一致，详见 [GitHub Actions 离线制品发布与服务器安装升级设计](design-github-actions-release-deployment.md)。

旧手工 SQL 参考如下：

```sql
-- 重命名列
ALTER TABLE artworks RENAME COLUMN year TO auction_date;
ALTER TABLE artworks RENAME COLUMN collection TO auction_house;

-- 新增拍品编号字段
ALTER TABLE artworks ADD COLUMN IF NOT EXISTS lot_number VARCHAR(100);

-- 删除死字段
ALTER TABLE artworks DROP COLUMN IF EXISTS category;
```

> 注意：生产环境不再依赖 Hibernate `ddl-auto: update` 自动改表；后续 schema 变更必须进入 Flyway 迁移文件。

---

### 2. 后端 `Artwork.java`（实体）

```
year        → auctionDate     (DB 列名: auction_date)
collection  → auctionHouse    (DB 列名: auction_house)
新增: lotNumber  (DB 列名: lot_number) - 拍品编号
删除: category 字段
```

`description` 字段保留（有信息价值，虽不在 12 个核心字段中）。
`extraData` 字段保留（用于存储其他未结构化信息）。

---

### 3. 后端 `ArtworkDto.java`

```
year        → auctionDate
collection  → auctionHouse
新增: lotNumber - 拍品编号
删除: category 字段
```

`from()` 方法中对应的 setter 调用同步更新。

---

### 4. 后端 `FetchService.java`

`saveArtworks()` 方法中两行 setter 调用需修改：

```java
// 改前
artwork.setYear(item.auctionDate);
artwork.setCollection(item.auctionCompany);

// 改后
artwork.setAuctionDate(item.auctionDate);
artwork.setAuctionHouse(item.auctionHouse);
artwork.setLotNumber(item.lotNumber);
```

（`ArtworkData` 内部类字段名本身已经是语义准确的 `auctionDate` / `auctionCompany`，无需改动）

---

### 5. 后端 `ArtworkSpec.java`

当前 `year` 过滤使用精确匹配（`cb.equal`），但实际存储的是日期字符串（如 "2023-11-15 14:00"），精确匹配无法支持按年份模糊查询。

**建议改为 LIKE 匹配**，参数名同步改为 `auctionDate`：

```java
// 改前
public static Specification<Artwork> search(Long taskId, String keyword, String artist, String year)
// ...
predicates.add(cb.equal(root.get("year"), year));

// 改后
public static Specification<Artwork> search(Long taskId, String keyword, String artist, String auctionDate, String lotNumber)
// ...
predicates.add(cb.like(root.get("auctionDate"), "%" + auctionDate + "%"));
if (lotNumber != null) {
  predicates.add(cb.like(root.get("lotNumber"), "%" + lotNumber + "%"));
}
```

这样用户可以输入 "2023" 匹配所有 2023 年的拍品。

---

### 6. 后端 `ArtworkController.java`

查询参数和导出接口中的 `year` 参数改为 `auctionDate`：

```java
// 改前
@RequestParam(required = false) String year

// 改后
@RequestParam(required = false) String auctionDate
@RequestParam(required = false) String lotNumber
```

---

### 7. 前端 `types/index.ts`

```typescript
// 改前
year?: string;       // 拍卖日期
collection?: string; // 拍卖公司

// 改后
lotNumber?: string;      // 拍品编号
auctionDate?: string;    // 拍卖日期
auctionHouse?: string;   // 拍卖公司

// 删除
category?: string;
```

---

### 8. 前端 `api/index.ts`

`ArtworkQuery` 接口和 `exportArtworksUrl` 中：

```typescript
// 改前
export interface ArtworkQuery {
  year?: string;
}
if (query.year) params.set('year', query.year);

// 改后
export interface ArtworkQuery {
  lotNumber?: string;
  auctionDate?: string;
}
if (query.lotNumber) params.set('lotNumber', query.lotNumber);
if (query.auctionDate) params.set('auctionDate', query.auctionDate);
```

---

### 9. 前端 `ArtworksPage.tsx`

**列表表格列**：

| 当前 dataIndex | 当前标题 | 改后 |
|--------------|---------|------|
| `year` | 拍卖日期 | `auctionDate`，标题保持"拍卖日期" |

**新增编号列**：
- 在表格中增加 `lotNumber`，标题为 **"编号"**，宽度约 100px

**搜索筛选栏**：

- 当前"年代"过滤字段 (`name="year"`) → 改为 `name="auctionDate"`，label 改为 **"拍卖日期"**，placeholder 改为 **"例如 2023"**（说明支持模糊匹配）
- 新增编号筛选字段：`name="lotNumber"`，label 为 **"编号"**，placeholder 为 **"请输入拍品编号"**

**`handleSearch`**：

```typescript
// 改前
year: values.year || undefined,

// 改后
lotNumber: values.lotNumber || undefined,
auctionDate: values.auctionDate || undefined,
```

---

### 10. 前端 `ArtworkDetailPage.tsx`

字段引用更新：

```typescript
// 改前
artwork.year       // 拍卖日期
artwork.collection // 拍卖公司

// 改后
artwork.lotNumber   // 拍品编号
artwork.auctionDate // 拍卖日期
artwork.auctionHouse // 拍卖公司
```

中文标签已经正确（"拍卖日期"、"拍卖公司"），无需改动。

---

## 前端展示调整建议

### 详情页（`ArtworkDetailPage`）

当前已包含全部 12 个字段，字段顺序建议调整为更符合用户认知的阅读顺序：

```
拍品名称
编号
作者
材质
形制
尺寸
估价
─────────────── 拍卖信息 ───────────────
拍卖公司
拍卖会
拍卖专场
拍卖日期
拍卖地点
预展时间
预展地点
─────────────── 系统信息 ───────────────
来源任务
抓取时间
```

可以在 `Descriptions` 中加一个分组标题（`Descriptions` 原生不支持，可用分隔线 `Divider` 实现）或直接用两个 `Descriptions` 块。

### 列表页（`ArtworksPage`）

当前表格列：图片、标题、艺术家、材质、拍卖日期、估价、来源任务、操作

建议增加**编号**和**拍卖公司**两列：

```
图片 | 标题 | 编号 | 艺术家 | 材质 | 拍卖公司 | 拍卖日期 | 估价 | 来源任务 | 操作
```

- `lotNumber`: 宽度约 100px
- `auctionHouse`: 宽度约 130px

---

## 变更文件清单

| 文件 | 变更类型 |
|------|---------|
| 数据库迁移脚本（新建） | 新增 |
| `backend/.../entity/Artwork.java` | 重命名字段 + 新增 lotNumber + 删除 category |
| `backend/.../dto/ArtworkDto.java` | 重命名字段 + 新增 lotNumber + 删除 category |
| `backend/.../service/FetchService.java` | 更新 setter 调用（新增 lotNumber） |
| `backend/.../repository/ArtworkSpec.java` | 重命名参数 + 新增 lotNumber 查询 + 改为 LIKE 查询 |
| `backend/.../controller/ArtworkController.java` | 重命名请求参数 + 新增 lotNumber 参数 |
| `frontend/src/types/index.ts` | 重命名字段 + 新增 lotNumber + 删除 category |
| `frontend/src/api/index.ts` | 重命名查询参数 + 新增 lotNumber 查询 |
| `frontend/src/pages/ArtworksPage.tsx` | 更新列定义 + 新增编号筛选 |
| `frontend/src/pages/ArtworkDetailPage.tsx` | 更新字段引用 + 顺序调整 |

---

## 待讨论问题

1. **数据库迁移时机**：已决策为引入 Flyway。发布版本必须与最新 Flyway 迁移版本一致，生产环境不再依赖手工 SQL 或 Hibernate `ddl-auto: update`。

2. **`description` 字段**：是否在详情页展示？当前代码里有"描述"卡片，但 12 个核心字段里没有它。建议保留显示，但不列入核心字段。

3. **列表页新增编号和拍卖公司列**：表格宽度会增加，是否接受？

4. **详情页分组**：是用 `Divider` 分隔两段 `Descriptions`，还是保持单一列表只调整顺序？

5. **拍品编号抓取**：需要确认抓取数据源中 `ArtworkData` 内部类是否已有 `lotNumber` 字段定义。

---

## 附件：HTML样本分析与抓取逻辑细化

### 样本来源

已保存 5 个张大千拍品详情页原始 HTML 到 `./download/` 目录：

| 文件名 | URL | 拍品编号 |
|--------|-----|---------|
| `art31600061.html` | https://auction.artron.net/paimai-art31600061/ | Lot0061 |
| `art5218483031.html` | https://auction.artron.net/paimai-art5218483031/ | Lot3031 |
| `art5242552056.html` | https://auction.artron.net/paimai-art5242552056/ | Lot2056 |
| `art5141700424.html` | https://auction.artron.net/paimai-art5141700424/ | Lot0424 |
| `art0011950838.html` | https://auction.artron.net/paimai-art0011950838/ | Lot0838 |

### HTML页面结构分析

通过样本观察，雅昌拍卖详情页结构一致：

1. **拍品编号位置**：
   - 页面顶部有 `LOT XXX` 或 `LotXXX` 标题，通常在 `<div>` 或 `<span>` 中，class 包含 `lot` 或直接是文本 `LOT`
   - URL 本身也隐含信息：`/paimai-art0011950838/` → 最后部分可作为 `externalId`，但编号需要从页面提取
   - 列表页标题中也有 `[0061]` 格式的编号，可在列表页解析时提取

2. **字段表格结构**（核心信息区）：
   - 多数情况使用 `<th>` + `<td>` 结构，标签文本在 `<th>`，值在 `<td>`
   - 部分页面使用 `<dt>` + `<dd>` 结构
   - 少数页面使用 `label：value` 纯文本格式嵌在 `<p>` 或 `<li>` 中
   - 当前抓取代码 `extractByLabel()` 已覆盖这三种情况，无需大改

3. **典型字段位置**：

| 字段 | HTML中标签文本示例 | 说明 |
|------|------------------|------|
| **lotNumber** | `LOT`, `Lot`, `拍品号`, `拍品编号` | 页面顶部显示，如 `Lot 0061` 或 `LOT 3031` |
| **artist** | `作者`, `艺术家` | 拍品名称中已经包含，详情页可覆盖 |
| **medium** | `材质` | 如 `设色纸本`、`水墨绢本` |
| **format** | `形制` | 如 `立轴`、`成扇`、`镜心`、`手卷` |
| **dimensions** | `尺寸`, `大小` | 如 `27.8*21.5cm` |
| **valuation** | `估价`, `参考价`, `起拍价` | 如 `RMB 120,000-140,000` |
| **auctionHouse** | `拍卖公司`, `拍卖行` | 如 `中国嘉德`、`蘇富比` |
| **auctionName** | `拍卖会`, `拍卖名称` | 如 `中国嘉德香港2026春季拍卖会` |
| **auctionSession** | `专场`, `拍卖专场` | 如 `文物公司旧藏中国古代书画及美术文献专场` |
| **auctionDate** | `拍卖日期`, `拍卖时间` | 如 `2023.12.13` |
| **auctionLocation** | `拍卖地点` | |
| **previewTime** | `预展时间`, `预展日期` | |
| **previewLocation** | `预展地点`, `预展地址` | |

### 拍品编号（lotNumber）抓取逻辑

#### 1. 列表页抓取
在列表页的 `parseLi()` 方法中，标题格式为：
```
[0061]张大千 仿宋人滕昌祐笔秋鸣图
```
已有正则 `LOT_TITLE_PATTERN = ^\[(\\d+)\\](.*)$` 可直接提取编号：
- 第 1 分组即为编号 `0061`
- 赋值给 `data.lotNumber`

#### 2. 详情页抓取
在 `enrichFromDetail()` 方法中增加：
```java
String lotNumber = extractByLabel(doc, "LOT", "Lot", "拍品号", "拍品编号");
if (lotNumber != null) {
  // 去除 "LOT" 前缀只保留数字部分
  lotNumber = lotNumber.replaceAll("^(?i)lot\\s*", "").trim();
  data.lotNumber = lotNumber;
}
```
如果提取失败，保留列表页提取到的编号作为 fallback。

#### 3. 页面顶部提取增强
除了 `extractByLabel()`，可增加一个专门提取顶部 LOT 编号的逻辑：
```java
// 在 doc 中查找包含 "LOT" 或 "Lot" 的元素
Elements lotElements = doc.select("*:containsOwn(LOT), *:containsOwn(Lot)");
for (Element el : lotElements) {
  String text = el.text().trim();
  Matcher m = Pattern.compile("(?i)lot\\D*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(text);
  if (m.find()) {
    data.lotNumber = m.group(1);
    break;
  }
}
```

### 各字段抓取优先级

| 字段 | 列表页 | 详情页 | 优先级 |
|------|--------|--------|--------|
| lotNumber | 从 `[xxx]` 提取 | 从页面顶部LOT提取 | 详情页 > 列表页 |
| auctionDate | 从列表行文本提取 | 从详情页字段提取 | 详情页 > 列表页 |
| auctionHouse | 从列表页链接提取 | 从详情页字段提取 | 详情页 > 列表页 |
| 其他 | - | 仅详情页 | 详情页 |

### 需要更新的代码位置

| 文件 | 变更内容 |
|------|---------|
| `FetchService.java` | 1. 在 `ArtworkData` 内部类新增 `String lotNumber` 字段<br>2. 在 `parseLi()` 中提取列表页编号赋值给 `lotNumber`<br>3. 在 `enrichFromDetail()` 中增加详情页编号提取<br>4. 在 `saveArtworks()` 中增加 `artwork.setLotNumber(item.lotNumber)` |

---

## 架构重构：字段提取器策略模式设计

### 问题背景

当前 `FetchService.enrichFromDetail()` 采用硬编码方式逐个提取字段：

```java
String medium        = extractByLabel(doc, "材质", "材质尺寸");
String format        = extractByLabel(doc, "形制");
String dimensions    = extractByLabel(doc, "尺寸");
...
```

这种方式存在几个问题：
1. 新增/修改字段提取逻辑需要改动 `enrichFromDetail` 主方法，违反开闭原则
2. 不同字段可能需要不同的提取算法，难以复用和独立调试
3. 不利于单元测试，无法单独测试某个字段的提取准确性
4. 代码集中在一个方法中，可读性差

### 设计目标

引入**策略模式**，为每个需要提取的字段定义独立的提取器，达到：
- ✅ 每个字段提取逻辑完全分离
- ✅ 可独立测试、调试单个字段
- ✅ 新增/修改字段不影响其他提取逻辑
- ✅ 提取完成后自动赋值到数据对象

### 设计方案

#### 1. 定义字段提取器接口

```java
package com.artfetch.service.extractor;

import com.artfetch.service.FetchService.ArtworkData;
import org.jsoup.nodes.Document;

/**
 * 字段提取器接口 - 每个需要从详情页提取的字段都实现此接口
 */
public interface FieldExtractor {
    /**
     * 从 Document 中提取对应字段的值，并设置到 ArtworkData 对象中
     * @param doc 详情页 JSoup Document
     * @param data 输出数据对象
     */
    void extract(Document doc, ArtworkData data);

    /**
     * 获取提取器名称（用于日志和调试）
     */
    default String getExtractorName() {
        return this.getClass().getSimpleName();
    }
}
```

#### 2. 每个字段一个独立的提取器实现

按字段拆分，每个提取器只负责一个字段：

```
com.artfetch.service.extractor/
├── FieldExtractor.java          # 接口
├── LotNumberExtractor.java       # 拍品编号
├── ArtistExtractor.java          # 作者
├── MediumExtractor.java          # 材质
├── FormatExtractor.java          # 形制
├── DimensionsExtractor.java      # 尺寸
├── ValuationExtractor.java       # 估价
├── AuctionHouseExtractor.java    # 拍卖公司
├── AuctionNameExtractor.java     # 拍卖会
├── AuctionSessionExtractor.java # 拍卖专场
├── AuctionDateExtractor.java     # 拍卖日期
├── AuctionLocationExtractor.java # 拍卖地点
├── PreviewTimeExtractor.java    # 预展时间
└── PreviewLocationExtractor.java # 预展地点
```

**示例：`LotNumberExtractor.java`**

```java
package com.artfetch.service.extractor;

import com.artfetch.service.FetchService.ArtworkData;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LotNumberExtractor implements FieldExtractor {

    private static final Pattern LOT_NUMBER_PATTERN = 
            Pattern.compile("(?i)lot\\D*(\\d+)", Pattern.CASE_INSENSITIVE);

    private final String[] candidateLabels = {"LOT", "Lot", "拍品号", "拍品编号"};

    @Override
    public void extract(Document doc, ArtworkData data) {
        String lotNumber = null;

        // 策略 1：尝试按标签文本提取
        for (String label : candidateLabels) {
            lotNumber = extractByLabel(doc, label);
            if (lotNumber != null) break;
        }

        // 策略 2：如果标签提取失败，尝试在整个文档搜索 "LOT XXX"
        if (lotNumber == null) {
            lotNumber = extractFromDocumentText(doc);
        }

        // 如果提取到结果，清理并赋值
        if (lotNumber != null) {
            lotNumber = lotNumber.replaceAll("^(?i)lot\\s*", "").trim();
            if (!lotNumber.isBlank()) {
                data.setLotNumber(lotNumber);
            }
        }
    }

    private String extractByLabel(Document doc, String label) {
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
        return null;
    }

    private String extractFromDocumentText(Document doc) {
        Elements elements = doc.select("*:containsOwn(LOT), *:containsOwn(Lot)");
        for (Element el : elements) {
            String text = el.text().trim();
            Matcher m = LOT_NUMBER_PATTERN.matcher(text);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    @Override
    public String getExtractorName() {
        return "LotNumberExtractor";
    }
}
```

**示例：`MediumExtractor.java`**（更简单）

```java
package com.artfetch.service.extractor;

import com.artfetch.service.FetchService.ArtworkData;
import org.jsoup.nodes.Document;

public class MediumExtractor extends BaseLabelExtractor {

    public MediumExtractor() {
        super("材质", "材质尺寸");
    }

    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) {
            data.setMedium(value);
        }
    }
}
```

#### 3. 抽象基类复用公共逻辑

由于大多数字段都是基于"标签→值"模式提取，可以抽出抽象基类：

```java
package com.artfetch.service.extractor;

import com.artfetch.service.FetchService.ArtworkData;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public abstract class BaseLabelExtractor implements FieldExtractor {

    protected final String[] candidateLabels;

    public BaseLabelExtractor(String... candidateLabels) {
        this.candidateLabels = candidateLabels;
    }

    protected String extractFirstMatch(Document doc) {
        for (String label : candidateLabels) {
            String value = extractByLabel(doc, label);
            if (value != null) return value;
        }
        return null;
    }

    protected String extractByLabel(Document doc, String label) {
        Element th = doc.selectFirst("th:containsOwn(" + label + ")");
        if (th != null) {
            Element td = th.nextElementSibling();
            if (td != null && !td.text().isBlank()) return td.text().trim();
        }
        Element dt = doc.selectFirst("dt:containsOwn(" + label + ")");
        if (dt != null) {
            Element dd = dt.nextElementSibling();
            if (dd != null && !dd.text().isBlank()) return dd.text().trim();
        }
        return null;
    }
}
```

这样简单的字段提取器可以非常简洁：

```java
public class AuctionDateExtractor extends BaseLabelExtractor {
    public AuctionDateExtractor() {
        super("拍卖日期", "拍卖时间");
    }
    @Override
    public void extract(Document doc, ArtworkData data) {
        String value = extractFirstMatch(doc);
        if (value != null) data.setAuctionDate(value);
    }
}
```

#### 4. 提取器上下文协调类

创建 `FieldExtractorChain` 来管理所有提取器并按顺序执行：

```java
package com.artfetch.service.extractor;

import com.artfetch.service.FetchService.ArtworkData;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;

import java.util.List;

@Slf4j
public class FieldExtractorChain {

    private final List<FieldExtractor> extractors;

    public FieldExtractorChain() {
        // 按页面顺序注册提取器
        this.extractors = List.of(
            new LotNumberExtractor(),
            new ArtistExtractor(),
            new MediumExtractor(),
            new FormatExtractor(),
            new DimensionsExtractor(),
            new ValuationExtractor(),
            new AuctionHouseExtractor(),
            new AuctionNameExtractor(),
            new AuctionSessionExtractor(),
            new AuctionDateExtractor(),
            new AuctionLocationExtractor(),
            new PreviewTimeExtractor(),
            new PreviewLocationExtractor()
        );
    }

    public void extractAll(Document doc, ArtworkData data) {
        log.debug("Starting field extraction with {} extractors", extractors.size());
        for (FieldExtractor extractor : extractors) {
            long start = System.currentTimeMillis();
            try {
                extractor.extract(doc, data);
                long elapsed = System.currentTimeMillis() - start;
                log.debug("Extractor {} done in {}ms", extractor.getExtractorName(), elapsed);
            } catch (Exception e) {
                log.warn("Extractor {} failed: {}", extractor.getExtractorName(), e.getMessage());
            }
        }
    }

    /**
     * 用于单元测试：获取指定类型的提取器
     */
    @SuppressWarnings("unchecked")
    public <T extends FieldExtractor> T getExtractor(Class<T> type) {
        for (FieldExtractor extractor : extractors) {
            if (type.isInstance(extractor)) {
                return (T) extractor;
            }
        }
        return null;
    }
}
```

#### 5. 修改后的调用流程

在 `FetchService.enrichFromDetail()` 中，原来的硬编码被替换为：

```java
private final FieldExtractorChain extractorChain = new FieldExtractorChain();

private void enrichFromDetail(ArtworkData data, Long taskId, AppProperties.Source cfg) {
    if (data.sourceUrl == null || data.sourceUrl.isBlank()) return;
    try {
        log.debug("Task[{}] 抓取详情页：{}", taskId, data.sourceUrl);
        Document doc = Jsoup.connect(data.sourceUrl)
                .userAgent(...)
                .get();

        // 一行代码调用所有提取器
        extractorChain.extractAll(doc, data);

    } catch (Exception e) {
        log.warn("Task[{}] 详情页抓取失败 {}：{}", taskId, data.sourceUrl, e.getMessage());
    }
}
```

### 优势对比

| 维度 | 原有硬编码方式 | 策略模式提取器 |
|------|---------------|---------------|
| **开闭原则** | 新增字段需要修改 `enrichFromDetail` | 新增提取器即可，不影响现有代码 | ✅
| **独立性** | 所有逻辑混合在一起 | 每个提取器完全独立 | ✅
| **可测试性** | 只能整页测试 | 可单独单元测试某个字段 | ✅
| **可调试性** | 日志混在一起 | 每个提取器独立计时、日志 | ✅
| **可读性** | 主方法越来越长 | 每个提取器只做一件事 | ✅
| **复用性** | 难以复用提取逻辑 | 公共逻辑放基类，特定逻辑子类实现 | ✅

### 便于调试的设计

1. **独立日志**：每个提取器有独立日志输出，可观察提取耗时和结果
2. **异常隔离**：一个提取器抛出异常不影响其他字段继续提取
3. **单元测试友好**：可以对单个提取器进行单元测试：

```java
@Test
void testLotNumberExtraction() {
    Document doc = Jsoup.parse("<html>...<div>LOT 0061</div>...</html>");
    ArtworkData data = new ArtworkData();
    new LotNumberExtractor().extract(doc, data);
    assertEquals("0061", data.getLotNumber());
}
```

4. **保存样本HTML便于调试**：`./download/` 目录下已保存5个真实HTML样本，可以直接用于本地测试

### 文件结构变化

```
backend/src/main/java/com/artfetch/service/
├── FetchService.java              # 简化，调用 extractorChain
├── extractor/                     # 新增包
│   ├── FieldExtractor.java        # 接口
│   ├── BaseLabelExtractor.java    # 抽象基类
│   ├── FieldExtractorChain.java   # 执行链
│   ├── LotNumberExtractor.java
│   ├── MediumExtractor.java
│   ├── FormatExtractor.java
│   ├── DimensionsExtractor.java
│   ├── ValuationExtractor.java
│   ├── AuctionHouseExtractor.java
│   ├── AuctionNameExtractor.java
│   ├── AuctionSessionExtractor.java
│   ├── AuctionDateExtractor.java
│   ├── AuctionLocationExtractor.java
│   ├── PreviewTimeExtractor.java
│   └── PreviewLocationExtractor.java
```

### 迁移路径

1. 新建 `extractor` 包和所有提取器类
2. 在 `FetchService` 中注入/创建 `FieldExtractorChain`
3. 将 `enrichFromDetail` 中原来的逐个字段提取替换为 `extractorChain.extractAll(doc, data)`
4. 测试验证功能正常
5. 删除原来的硬编码，完成重构

整个重构过程可以平滑迁移，不影响外部接口。
