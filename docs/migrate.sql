-- ArtFetch 数据库迁移脚本
-- 执行时机：在应用重启之前手动执行
-- 背景：重命名 year→auction_date, collection→auction_house，新增 lot_number，删除 category

-- 1. 重命名字段
ALTER TABLE artworks RENAME COLUMN year TO auction_date;
ALTER TABLE artworks RENAME COLUMN collection TO auction_house;

-- 2. 新增拍品编号字段
ALTER TABLE artworks ADD COLUMN IF NOT EXISTS lot_number VARCHAR(100);

-- 3. 删除无用字段
ALTER TABLE artworks DROP COLUMN IF EXISTS category;
