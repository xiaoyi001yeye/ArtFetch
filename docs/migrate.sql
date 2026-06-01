-- ArtFetch 历史手工数据库迁移脚本
-- 状态：历史资料，不再作为新发布链路的迁移机制
-- 当前数据库迁移统一纳入 Flyway；发布版本必须与最新 Flyway 迁移版本一致。
-- 背景：重命名 year→auction_date, collection→auction_house，新增 lot_number，删除 category

-- 1. 重命名字段
ALTER TABLE artworks RENAME COLUMN year TO auction_date;
ALTER TABLE artworks RENAME COLUMN collection TO auction_house;

-- 2. 新增拍品编号字段
ALTER TABLE artworks ADD COLUMN IF NOT EXISTS lot_number VARCHAR(100);

-- 3. 删除无用字段
ALTER TABLE artworks DROP COLUMN IF EXISTS category;
