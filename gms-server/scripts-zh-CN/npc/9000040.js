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
/* Dalair
	Medal NPC.

        NPC Equipment Merger:
        * @author Ronan Lana
 */

var status;
var mergeFee = 50000;
var name;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        if (mode == 0 && type > 0) {
            cm.dispose();
            return;
        }
        if (mode == 1) {
            status++;
        } else {
            status--;
        }

        if (status == 0) {
            const GameConfig = Java.type('org.gms.config.GameConfig');
            if (!GameConfig.getServerBoolean("use_enable_custom_npc_script")) {
                cm.sendOk("勋章排名系统目前不可用。");
                cm.dispose();
                return;
            }

            var levelLimit = !cm.getPlayer().isCygnus() ? 160 : 110;
            'var selStr = "勋章排名系统目前不可用……因此，我来提供#b装备融合#k服务！ ";'

            const MakerProcessor = Java.type('org.gms.client.processor.action.MakerProcessor');
            if (!GameConfig.getServerBoolean("use_starter_merge") && (cm.getPlayer().getLevel() < levelLimit || MakerProcessor.getMakerSkillLevel(cm.getPlayer()) < 3)) {
                selStr += "不过，你必须拥有#r制作技能3级#k并且至少达到#r110级#k（女皇骑士团）、#r160级#k（其他职业），同时携带#r" + cm.numberWithCommas(mergeFee) + " 金币#k才能使用这项服务。";
                cm.sendOk(selStr);
                cm.dispose();
            } else if (cm.getMeso() < mergeFee) {
                selStr += "很抱歉，这项服务需要支付#r" + cm.numberWithCommas(mergeFee) + " 金币#k的手续费，看起来你目前没有足够的金币……请改天再来。";
                cm.sendOk(selStr);
                cm.dispose();
            } else {
                selStr += "只需支付#r" + cm.numberWithCommas(mergeFee) + "#k金币，就可以将你背包中多余的装备融合到你当前装备的武器/防具上，根据所融合装备的属性获得属性加成！";
                cm.sendNext(selStr);
            }
        } else if (status == 1) {
            selStr = "#r警告#b：请确保你要融合的物品放在所选物品#r之后#b的槽位。#k所选物品#b之前#k的所有物品都将被彻底融合。\r\n\r\n请注意，获得融合加成的装备将变为#r不可交易#k，而已获得融合加成的装备#r不能再次用于融合#k。\r\n\r\n";
            cm.sendGetText(selStr);
        } else if (status == 2) {
            name = cm.getText();

            if (cm.getPlayer().mergeAllItemsFromName(name)) {
                cm.gainMeso(-mergeFee);
                cm.sendOk("合并完成！感谢您使用本服务，祝您享受新的装备属性。");
            } else {
                cm.sendOk("你的#b装备#k库中没有#b'" + name + "'#k！");
            }

            cm.dispose();
        }
    }
}