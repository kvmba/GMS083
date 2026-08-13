-- 修复验证发现的数据问题:
-- 1. quick_level_rate 键名与代码读取的 quick_level_exp_rate 不一致,导致冲刺等级倍率恒为0
-- 2. drop_data 中 6 位非法 itemid 死数据(400000 系列),永不掉落
-- 3. reactordrops 重复行导致奖励权重翻倍
-- 4. lang_resources 中 en_US 下划线写法与代码查询的 en-US 不一致

UPDATE `game_config`
SET `config_code` = 'quick_level_exp_rate'
WHERE `config_code` = 'quick_level_rate';

DELETE FROM `drop_data`
WHERE `itemid` IN (400000, 400001, 400002, 400003, 400004, 400005, 400006, 400009, 400010, 400011, 401000, 404000);

DELETE FROM `reactordrops`
WHERE `reactorid` = 6802000
  AND `itemid` = 2022181
  AND `chance` = 3
  AND `questid` = -1
LIMIT 1;

DELETE FROM `reactordrops`
WHERE `reactorid` = 6702003
  AND `itemid` = 4020007
  AND `chance` = 5
  AND `questid` = -1
LIMIT 1;

DELETE FROM `reactordrops`
WHERE `reactorid` = 6702003
  AND `itemid` = 2000005
  AND `chance` = 5
  AND `questid` = -1
LIMIT 1;

UPDATE `lang_resources`
SET `lang_type` = 'en-US'
WHERE `lang_type` = 'en_US';
