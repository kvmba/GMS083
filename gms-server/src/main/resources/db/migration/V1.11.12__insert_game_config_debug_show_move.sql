-- 封包调试日志是否打印玩家移动封包（需先开启 use_debug_show_packet；false=过滤不打印）
INSERT INTO `game_config`(`config_type`, `config_sub_type`, `config_clazz`, `config_code`, `config_value`, `config_desc`, `update_time`)
SELECT 'server', 'Debug', 'java.lang.Boolean', 'use_debug_show_player_move', 'false', 'use_debug_show_player_move', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `game_config` WHERE `config_code` = 'use_debug_show_player_move'
);

-- 封包调试日志是否打印宠物移动封包（需先开启 use_debug_show_packet；false=过滤不打印）
INSERT INTO `game_config`(`config_type`, `config_sub_type`, `config_clazz`, `config_code`, `config_value`, `config_desc`, `update_time`)
SELECT 'server', 'Debug', 'java.lang.Boolean', 'use_debug_show_pet_move', 'false', 'use_debug_show_pet_move', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `game_config` WHERE `config_code` = 'use_debug_show_pet_move'
);

-- 中文内容
INSERT INTO `lang_resources`(`lang_type`, `lang_base`, `lang_code`, `lang_value`, `lang_extend`)
SELECT 'zh-CN', 'game_config', 'use_debug_show_player_move', '打印玩家移动封包（需先开启打印所有封包；false=过滤不打印）', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `lang_resources` WHERE `lang_type` = 'zh-CN' AND `lang_code` = 'use_debug_show_player_move'
);

INSERT INTO `lang_resources`(`lang_type`, `lang_base`, `lang_code`, `lang_value`, `lang_extend`)
SELECT 'zh-CN', 'game_config', 'use_debug_show_pet_move', '打印宠物移动封包（需先开启打印所有封包；false=过滤不打印）', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `lang_resources` WHERE `lang_type` = 'zh-CN' AND `lang_code` = 'use_debug_show_pet_move'
);

-- 英文内容
INSERT INTO `lang_resources`(`lang_type`, `lang_base`, `lang_code`, `lang_value`, `lang_extend`)
SELECT 'en-US', 'game_config', 'use_debug_show_player_move', 'Print player move packets (requires packet logging; false=filtered out).', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `lang_resources` WHERE `lang_type` = 'en-US' AND `lang_code` = 'use_debug_show_player_move'
);

INSERT INTO `lang_resources`(`lang_type`, `lang_base`, `lang_code`, `lang_value`, `lang_extend`)
SELECT 'en-US', 'game_config', 'use_debug_show_pet_move', 'Print pet move packets (requires packet logging; false=filtered out).', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `lang_resources` WHERE `lang_type` = 'en-US' AND `lang_code` = 'use_debug_show_pet_move'
);
