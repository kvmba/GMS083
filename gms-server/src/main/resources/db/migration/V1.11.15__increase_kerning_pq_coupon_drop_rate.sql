-- 废弃都市组队任务首关优惠券基础爆率至少为 90%，保留已有的更高配置。
UPDATE `drop_data`
SET `chance` = 900000
WHERE `dropperid` = 9300001
  AND `itemid` = 4001007
  AND `chance` < 900000;
