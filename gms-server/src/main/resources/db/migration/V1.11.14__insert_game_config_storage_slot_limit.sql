-- 仅限制玩家可购买的仓库容量，不自动扩容或缩减已购容量。
INSERT INTO `game_config`(`config_type`, `config_sub_type`, `config_clazz`, `config_code`, `config_value`, `config_desc`, `update_time`)
SELECT 'server', 'Game Mechanics', 'java.lang.Integer', 'storage_slot_limit', '48', 'storage_slot_limit', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `game_config` WHERE `config_type` = 'server' AND `config_code` = 'storage_slot_limit'
);

INSERT INTO `lang_resources`(`lang_type`, `lang_base`, `lang_code`, `lang_value`, `lang_extend`)
SELECT 'zh-CN', 'game_config', 'storage_slot_limit', '玩家可购买的仓库扩容上限（4-124，默认48；每次增加4或8格，不改变已购容量；修改后重新进入商城同步按钮，需配套ijl15客户端）', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `lang_resources` WHERE `lang_type` = 'zh-CN' AND `lang_base` = 'game_config' AND `lang_code` = 'storage_slot_limit'
);

INSERT INTO `lang_resources`(`lang_type`, `lang_base`, `lang_code`, `lang_value`, `lang_extend`)
SELECT 'en-US', 'game_config', 'storage_slot_limit', 'Purchasable storage slot limit (4-124, default 48; purchases add 4 or 8 slots; existing capacity is unchanged; re-enter the Cash Shop to sync buttons; requires the matching ijl15 client)', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `lang_resources` WHERE `lang_type` = 'en-US' AND `lang_base` = 'game_config' AND `lang_code` = 'storage_slot_limit'
);
