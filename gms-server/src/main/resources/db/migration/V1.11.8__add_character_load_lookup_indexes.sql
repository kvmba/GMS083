-- 角色加载路径上的三个查询缺少索引，导致全表扫描。
--
-- Character.fromCharactersDO() 在每次角色加载时执行：
--   SELECT ... FROM area_info      WHERE charid = ?
--   SELECT ... FROM trocklocations WHERE characterid = ?
--   SELECT ... FROM newyear        WHERE senderid = ? OR receiverid = ?
--
-- 这三张表仅在主键上建有索引，缺少上述过滤列的索引，因此每次查询都是全表
-- 扫描。SoloMapling 启动时批量生成约 1000 个 ambient bot，每个 bot 都会
-- 完整加载一次角色（脚本内其余查询均走主键或已有索引），即额外产生约 3000
-- 次全表扫描。
--
-- 只补充普通索引：不改变任何查询结果，仅改变执行计划。不清洗、裁剪或重置
-- 任何历史数据。使用 information_schema 判空以保证幂等重入。

SET @idx_area_info_charid_sql = IF(
  EXISTS(
    SELECT 1 FROM `information_schema`.`STATISTICS`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'area_info'
      AND `INDEX_NAME` = 'idx_area_info_charid'
  ),
  'SELECT 1',
  'ALTER TABLE `area_info` ADD INDEX `idx_area_info_charid` (`charid`)'
);
PREPARE idx_area_info_charid_statement FROM @idx_area_info_charid_sql;
EXECUTE idx_area_info_charid_statement;
DEALLOCATE PREPARE idx_area_info_charid_statement;

SET @idx_trocklocations_characterid_sql = IF(
  EXISTS(
    SELECT 1 FROM `information_schema`.`STATISTICS`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'trocklocations'
      AND `INDEX_NAME` = 'idx_trocklocations_characterid'
  ),
  'SELECT 1',
  'ALTER TABLE `trocklocations` ADD INDEX `idx_trocklocations_characterid` (`characterid`)'
);
PREPARE idx_trocklocations_characterid_statement FROM @idx_trocklocations_characterid_sql;
EXECUTE idx_trocklocations_characterid_statement;
DEALLOCATE PREPARE idx_trocklocations_characterid_statement;

SET @idx_newyear_senderid_sql = IF(
  EXISTS(
    SELECT 1 FROM `information_schema`.`STATISTICS`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'newyear'
      AND `INDEX_NAME` = 'idx_newyear_senderid'
  ),
  'SELECT 1',
  'ALTER TABLE `newyear` ADD INDEX `idx_newyear_senderid` (`senderid`)'
);
PREPARE idx_newyear_senderid_statement FROM @idx_newyear_senderid_sql;
EXECUTE idx_newyear_senderid_statement;
DEALLOCATE PREPARE idx_newyear_senderid_statement;

SET @idx_newyear_receiverid_sql = IF(
  EXISTS(
    SELECT 1 FROM `information_schema`.`STATISTICS`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'newyear'
      AND `INDEX_NAME` = 'idx_newyear_receiverid'
  ),
  'SELECT 1',
  'ALTER TABLE `newyear` ADD INDEX `idx_newyear_receiverid` (`receiverid`)'
);
PREPARE idx_newyear_receiverid_statement FROM @idx_newyear_receiverid_sql;
EXECUTE idx_newyear_receiverid_statement;
DEALLOCATE PREPARE idx_newyear_receiverid_statement;
