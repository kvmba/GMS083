-- 修复数据问题: 「24小时 排档」(shopid 1061001) 中青铜弓矢(2060001)、青铜弩矢(2061001)售价 2800 错误,应与其他杂货店一致为 28
UPDATE `shopitems`
SET `price` = 28
WHERE `shopid` = 1061001
  AND `itemid` IN (2060001, 2061001);
