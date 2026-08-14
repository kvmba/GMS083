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
/* NPC: Donation Box (9000041)
	Victoria Road : Henesys
	
	NPC Bazaar:
        * @author Ronan Lana
*/

var options = ["EQUIP", "USE", "SET-UP", "ETC"];
var name;
var status;
var selectedType = 0;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    status++;
    if (mode != 1) {
        cm.dispose();
        return;
    }

    if (status == 0) {
        const GameConfig = Java.type('org.gms.config.GameConfig');
        if (!GameConfig.getServerBoolean("use_enable_custom_npc_script")) {
            cm.sendOk("勋章排名系统目前不可用。");
            cm.dispose();
            return;
        }

        var selStr = "你好，我是#b集市NPC#k！把你背包里不需要的物品卖给我吧。#r警告#b：请确保你要出售的物品放在所选物品#r之后#b的槽位。#k所选物品#b之前#k的所有物品都将被全部出售。";
        for (var i = 0; i < options.length; i++) {
            selStr += "\r\n#L" + i + "# " + options[i] + "#l";
        }
        cm.sendSimple(selStr);
    } else if (status == 1) {
        selectedType = selection;
        cm.sendGetText("你想从你#r" + options[selectedType] + "#k栏中的哪一件物品开始交易？");
    } else if (status == 2) {
        name = cm.getText();
        var res = cm.getPlayer().sellAllItemsFromName(selectedType + 1, name);

        if (res > -1) {
            cm.sendOk("交易完成！你从这个行动中获得了#r" + cm.numberWithCommas(res) + "金币#k。");
        } else {
            cm.sendOk("你的#b'" + name + "'#k物品栏中没有#b" + options[selectedType] + "#k！");
        }

        cm.dispose();
    }
}