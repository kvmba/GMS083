/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
/* Ronan
	Hidden Street - Developers' Headquarters (777777777)
	HeavenMS developer info.
 */

var status;

var ambientSong = "Bgm04/Shinin'Harbor";

var feature_tree = [];
var feature_cursor;

var tabs = ["组队任务", "技能", "任务", "玩家社交", "现金与物品", "怪物、地图与反应堆", "组队任务潜力", "玩家潜力", "服务器潜力", "命令", "自定义NPC", "本地修改", "项目"];

function addFeature(feature) {
    feature_cursor.push(feature);
}

function writeFeatureTab_PQs() {
    addFeature("冒险家组队任务/玩具城组队任务/等各类组队任务。");
    addFeature("罗密欧与朱丽叶组队任务/暗黑龙王组队任务/宝物组队任务等。");
    addFeature("玩具城组队任务作为远征队实例运行。");
    addFeature("斯卡加/暗黑龙王/昭和/蝙蝠怪/扎昆/品克缤。");
    addFeature("远征队带有入场人数限制系统。");
    addFeature("公会组队任务与多大厅排队系统。");
    addFeature("全新组队任务：Boss竞速组队任务、咖啡馆组队任务。");
    addFeature("武陵道场。");
    addFeature("怪物嘉年华1、2 - 感谢 Dragohe4rt 与 Jayd！");
    addFeature("阿里安特组队任务 - 感谢 Dragohe4rt 与 Jayd！");
    addFeature("拉塔尼亚队长，组队对抗首领。");
    addFeature("补齐了缺失的必需事件脚本方法。");
    addFeature("确保活动大厅名称实例的唯一性。");
}

function writeFeatureTab_Skills() {
    addFeature("复查了众多技能，如偷窃和魔法之门。");
    addFeature("治疗按GMS方式：固定HP回复量与治疗技能封包。");
    addFeature("改进战舰：HP可见且跨地图保留。");
    addFeature("开发了制作技能功能 - 感谢 Arnah。");
    addFeature("椅子精通 - 地图椅子提升HP/MP恢复。");
    addFeature("武陵道场技能可用。");
    addFeature("对首领使用魔法磁铁不再崩溃。");
    addFeature("对固定怪使用魔法磁铁不再崩溃 - 感谢 shavit！");
    addFeature("技能消耗HP/MP会触发宠物自动药水。");
    addFeature("元素武器附魔对其他玩家可见。");
}

function writeFeatureTab_Quests() {
    addFeature("娃娃屋任务可用。");
    addFeature("任务所需的启动物品能正确显示。");
    addFeature("任务现在能正确奖励物品。");
    addFeature("与NPC对话可重新领取任务起始物品。");
    addFeature("奖励选择功能正常。");
    addFeature("修复了大量任务。");
    addFeature("复查了阿兰任务线。");
    addFeature("复查了多个四转技能任务线。");
    addFeature("奖励系统现在会考虑物品堆叠。");
    addFeature("三转测验支持全部40道题。");
    addFeature("物品养成功能可用。");
    addFeature("修复了与NPC对话时玩家移动的问题。");
    addFeature("复查了将任务进度作为前置条件的用法。");
}

function writeFeatureTab_PlayerSocialNetwork() {
    addFeature("组队搜索完全可用。");
    addFeature("公会与联盟系统完全可用。");
    addFeature("公会签约系统，在公会总部进行。");
    addFeature("新手专用组队。");
    addFeature("组队成员血条会计算装备的HP加成。");
    addFeature("全面复查了个人商店与雇佣商店。");
    addFeature("雇佣商店交易即时通知店主。");
    addFeature("玩家交易有正确的金币空间检查。");
    addFeature("小游戏房间密码系统可用。");
    addFeature("拾取非自己物品有正确的冷却时间。");
    addFeature("改进排名系统，支持每日变动。");
    addFeature("保护并改进了表情系统。");
    addFeature("玩家NPC与名人堂的自动化支持。");
    addFeature("订婚与婚礼系统，带戒指效果。");
    addFeature("婚礼愿望清单 - 感谢 Dragohe4rt！");
    addFeature("装备的等级与经验信息对所有玩家可见。");
    addFeature("进一步改进了现有小游戏机制。");
    addFeature("交易通过握手同步完成。");
    addFeature("交易完成后显示GMS风格的结果。");
}

function writeFeatureTab_CashItems() {
    addFeature("经验/掉落/装扮优惠券。");
    addFeature("经验/掉落优惠券在有效期内作为增益效果。");
    addFeature("大量现金道具可用。");
    addFeature("可使用的现金道具正确消耗所选槽位。");
    addFeature("兑换码可用，支持多种物品。");
    addFeature("宠物、戒指与现金道具共用唯一ID。");
    addFeature("枫叶TV机制稳定并按世界拆分。");
    addFeature("GMS风格的五子棋/翻牌掉落几率。");
    addFeature("新增城镇卷轴：防驱逐，克制首领驱逐。");
    addFeature("背包系统检查空槽与堆叠空间。");
    addFeature("仓库支持'整理物品'功能。");
    addFeature("物品的近身鉴定模式。");
    addFeature("复查了因果剪刀与不可交易物品。");
    addFeature("修复了现金背包中宠物位置的问题。");
    addFeature("复查了时装相关内容，几乎与GMS一致。");
    addFeature("整容/造型NPC不再卡住角色。");
    addFeature("重做抽奖机奖励，与网站列表一致。");
    addFeature("鞋子防滑卷轴。");
    addFeature("防寒卷轴。");
    addFeature("维加的祝福。");
    addFeature("密涅瓦猫头鹰。");
    addFeature("宠物物品忽略。");
    addFeature("新年贺卡。");
    addFeature("风筝。");
    addFeature("商城惊喜箱。");
    addFeature("枫叶生活。");
    addFeature("经验提升。");
}

function writeFeatureTab_MonstersMapsReactors() {
    addFeature("所有怪物书卡片均可掉落。");
    addFeature("为许多缺失的怪物补充了金币掉落数据。");
    addFeature("怪物书显示更新后的掉落数据。");
    addFeature("所有技能书/熟练书均可获得。");
    addFeature("增强仇恨系统，支持实时DPS检测。");
    addFeature("木偶在重新拉仇恨时使目标怪物保持在附近。");
    addFeature("怪物现在可掉落多件相同装备。");
    addFeature("怪物只掉落玩家/队伍可拾取的物品。");
    addFeature("怪物现在不会频繁从立足点掉落。");
    addFeature("非技能类怪物移动正确消耗MP。");
    addFeature("限制了小怪刷出数量。");
    addFeature("实现了怪物接触与技能移动的驱逐。");
    addFeature("重新设计暗黑龙王机制：部件与承伤。");
    addFeature("实现了僵尸化疾病状态。");
    addFeature("伤害反射怪物技能图标不再延迟显示。");
    addFeature("为数十个首领添加了血条。");
    addFeature("游戏将优先显示目标首领的血条。");
    addFeature("首领血条与服务端消息开关 - GabrielSin 的想法。");
    addFeature("地图持续伤害与中和剂功能可用。");
    addFeature("物品会保持在可行走区域内。");
    addFeature("船只、电梯及其他交通机制可用。");
    addFeature("蝙蝠怪船只接近的视觉效果可用。");
    addFeature("永久物品地图不再使物品过期。");
    addFeature("组队任务、出租车与活动会将玩家传送到随机出生点。");
    addFeature("补全了脚本传送门缺失的音效。");
    addFeature("组队任务箱子打开时喷射物品，GMS风格。");
    addFeature("反应堆会智能地从地面拾取物品。");
    addFeature("更新脚本传送门，带有正确的传送音效。");
    addFeature("复查了玛斯特里亚、武陵道场旅行、尼哈沙漠与新叶城。");
    addFeature("为蘑菇城堡、武陵道场旅行与艾林地区添加世界地图。");
    addFeature("在世界地图中添加武陵道场旅行与玛斯特里亚大陆。");
    addFeature("修复了世界地图提示与链接的若干问题。");
    addFeature("按大陆分离的全局掉落。");
    addFeature("巨型蛋糕首领掉落糖果袋与枫叶物品。");
}

function writeFeatureTab_PQpotentials() {
    addFeature("先进且安全的组队任务注册系统。");
    addFeature("大厅系统：同频道多组队任务实例。");
    addFeature("远征系统：多个队伍可加入同一实例。");
    addFeature("公会队列：公会组队任务的公会注册。");
    addFeature("EIM池系统：优化实例加载。");
    addFeature("召回系统：断线玩家可重新加入组队任务。");
}

function writeFeatureTab_Playerpotentials() {
    addFeature("冒险家坐骑任务可用。");
    addFeature("所有装备均可升级。");
    addFeature("玩家等级倍率。");
    addFeature("通过任务与活动实例获得人气。");
    addFeature("宠物进化可用（非GMS风格）。");
    addFeature("复查了按键绑定系统。");
    addFeature("按世界/全服的角色槽位。");
    addFeature("可选的按职业分离商城背包。");
    addFeature("玩家只能管理同类型的邀请。");
    addFeature("带条件激活效果的玩家增益。");
    addFeature("石头剪刀布小游戏 - 感谢 Arnah！");
}

function writeFeatureTab_Serverpotentials() {
    addFeature("多世界支持。");
    addFeature("每个世界从服务器启动起可拥有自己的倍率。");
    addFeature("动态世界/频道部署。");
    addFeature("背包自动拾取与自动整理功能。");
    addFeature("增强自动药水系统：智能宠物药水管理。");
    addFeature("增强增益系统：最优增益效果生效。");
    addFeature("增强AP自动分配：聚焦装备需求。");
    addFeature("增强背包检查：智能获取空槽。");
    addFeature("增强宠物拾取处理：不做暴力背包检查。");
    addFeature("匹配系统：全员决定触发动作。");
    addFeature("玩家指定猫头鹰与商城的畅销品。");
    addFeature("调整宠物/坐骑饥饿度为平衡的成长率。");
    addFeature("一致的经验与金币获取系统。");
    addFeature("弗雷德里克商店银行提醒/删除未领取物品。");
    addFeature("NPC工匠不再免费拿走物品。");
    addFeature("快递：收到包裹弹窗与多种派送机制。");
    addFeature("宠物拾取优先拾取玩家攻击目标。");
    addFeature("频道容量条与带容量检查的世界。");
    addFeature("疾病对其他玩家可见，即使换图后仍可见。");
    addFeature("持久疾病。玩家登录后保留状态。");
    addFeature("中毒伤害值对其他玩家可见。");
    addFeature("怪物书播报按需显示信息。");
    addFeature("自定义监狱系统。");
    addFeature("带'季节性'垂钓时间的自定义钓鱼系统。");
    addFeature("渔网的实际钓鱼处理 - 感谢 Dragohe4rt！");
    addFeature("自定义地图租赁系统。");
    addFeature("删除角色。");
    addFeature("流畅的查看全部角色，显示账号所有角色。");
    addFeature("集中式服务器时间，提升处理器性能。");
    addFeature("集中式时间戳，未使用接收时间戳。");
    addFeature("自动存档（定期保存玩家数据到数据库）。");
    addFeature("支持固定与随机HP/MP成长率。");
    addFeature("玩家最大HP/MP计算装备加成。");
    addFeature("修复了'运行一段时间后NPC消失'的问题。");
    addFeature("10级及以下新手可分配AP。");
    addFeature("超出职业等级的SP上限，转职后恢复。");
    addFeature("已认证用户可跳过的PIN/PIC系统。");
    addFeature("自动账号注册 - 感谢 shavit！");
    addFeature("任意NPC可脚本化 - 感谢 GabrielSin！");
}

function writeFeatureTab_Commands() {
    addFeature("生成扎昆/暗黑龙王/品克缤。");
    addFeature("永久NPC与怪物。");
    addFeature("若干新命令。");
    addFeature("排名命令按世界或全服高亮用户。");
    addFeature("按GM等级分层的服务端命令。");
    addFeature("重做命令文件结构 - 感谢 Arthur L！");
    addFeature("改进'搜索'性能并添加地图搜索。");
}

function writeFeatureTab_CustomNPCs() {
    addFeature("斯皮格尔曼：自动矿石提炼。");
    addFeature("亚洲：卷轴与稀有物品商店NPC。");
    addFeature("阿卜杜拉：列出所需技能/熟练书的掉落怪物。");
    addFeature("E特工：饰品工匠。");
    addFeature("达莱尔：自动装备融合。");
    addFeature("捐赠箱：自动物品购买。");
    addFeature("可可与红心A：混沌卷轴工匠。");
    addFeature("巴里（枫叶TV）：填书并用物品兑换卷轴。");
}

function writeFeatureTab_Localhostedits() {
    addFeature("移除了'NPC对话'问题。");
    addFeature("移除了魔法攻击、武器防御、命中与回避的上限。");
    addFeature("移除了MTS限制。");
    addFeature("移除了10级以下新手的组队限制。");
    addFeature("将移动速度上限设置得更高。");
    addFeature("移除了新手的AP使用限制。");
    addFeature("移除了制作技能对非武器镶嵌攻击宝石的限制。");
    addFeature("移除了AP过量弹窗 - 感谢 kevintjuh93！");
    addFeature("移除了'GM不能攻击' - 感谢 kevintjuh93！");
    addFeature("移除了'升级了！'提示 - 感谢 PrinceReborn！");
}

function writeFeatureTab_Project() {
    addFeature("整理了项目代码。");
    addFeature("大幅更新的掉落数据。");
    addFeature("高度可配置且优化的服务器。");
    addFeature("修复/添加了许多缺失的封包操作码。");
    addFeature("在整个源码中发现了许多操作码。");
    addFeature("复查了多个需要关注的Java方面。");
    addFeature("复查SQL数据，消除重复条目。");
    addFeature("改进登录阶段，用缓存替代数据库查询。");
    addFeature("通过登录管理系统修复了许多缺陷。");
    addFeature("开发了健壮的反利用登录协调器。");
    addFeature("修改了登录账号的唯一性方面。");
    addFeature("使用HikariCP改进数据库连接调用。");
    addFeature("使用Java线程池改进可运行调用。");
    addFeature("开发了许多用于内容分析的调查工具。");
    addFeature("移除了整个游戏文件中的悬空物品名。");
    addFeature("重构物品脚本，正确使用NPC对话框。");
    addFeature("线程追踪器：死锁检测的运行时工具。");
    addFeature("频道、世界与全服范围的定时器管理。");
    addFeature("将服务开发为先发制人的任务调度器。");
    addFeature("全面复查了玩家属性的封装。");
    addFeature("重点复查了未来任务管理，减少线程创建并减轻TimerManager的任务过载。");
}

function writeAllFeatures() {
    var re = /[ ,&\/]+/g;

    for (var i = 0; i < tabs.length; i++) {
        feature_cursor = [];

        var tabName = (tabs[i]).replace(re, "");
        this["writeFeatureTab_" + tabName]();

        feature_tree.push(feature_cursor);
    }
}

function start() {
    status = -1;
    writeAllFeatures();
    action(1, 0, 0);
}

function action(mode, type, selection) {
    const PacketCreator = Java.type('org.gms.util.PacketCreator');
    if (mode == -1) {
        cm.getPlayer().sendPacket(PacketCreator.musicChange(ambientSong));
        cm.dispose();
    } else {
        if (mode == 0 && type > 0) {
            cm.getPlayer().sendPacket(PacketCreator.musicChange(ambientSong));
            cm.dispose();
            return;
        }
        if (mode == 1) {
            status++;
        } else {
            status--;
        }

        if (status == 0) {
            var sendStr = "HeavenMS的开发历时4年，建立在Solaxia的成果之上。我很高兴地说，这项开发本身不断得到数十位贡献者与支持者的鼓舞（真心感谢你们的信任之约，朋友们！）。\r\n\r\n谈到成果：涌现了许多优秀的功能，开发目标是重现旧版GMS的体验。如今这些久违的功能以本服务器的形式优雅地呈现在你面前。枫叶物语万岁！！\r\n\r\n以下是来自#bHeavenMS#k的功能：\r\n\r\n";
            for (var i = 0; i < tabs.length; i++) {
                sendStr += "#L" + i + "##b" + tabs[i] + "#k#l\r\n";
            }

            cm.sendSimple(sendStr);
        } else if (status == 1) {
            var tabName;

            for (var i = 0; i < tabs.length; i++) {
                if (selection == i) {
                    tabName = feature_tree[i];
                    break;
                }
            }

            var sendStr = "#b" + tabs[selection] + "#k:\r\n\r\n";
            for (var i = 0; i < tabName.length; i++) {
                sendStr += "  #L" + i + "# " + tabName[i];
                sendStr += "#l\r\n";
            }

            cm.sendPrev(sendStr);
        } else {
            cm.getPlayer().sendPacket(PacketCreator.musicChange(ambientSong));
            cm.dispose();
        }
    }
}

function generateSelectionMenu(array) {
    var menu = "";
    for (var i = 0; i < array.length; i++) {
        menu += "#L" + i + "#" + array[i] + "#l\r\n";
    }
    return menu;
}