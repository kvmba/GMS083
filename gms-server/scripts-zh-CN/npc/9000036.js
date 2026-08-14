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
/* NPC: Agent E (9000036)
	Victoria Road : Henesys
	
	Refining NPC:
	* Accessories refiner
        * 
        * @author Ronan Lana
*/

var status = -1;
var selectedType = -1;
var selectedItem = -1;
var item;
var items;
var mats;
var matQty;
var cost;
var qty = 1;
var equip;
var maxEqp = 0;

function start() {
    const GameConfig = Java.type('org.gms.config.GameConfig');
    if (!GameConfig.getServerBoolean("use_enable_custom_npc_script")) {
        cm.sendOk("嗨，我是 #b#p" + cm.getNpc() + "##k。");
        cm.dispose();
        return;
    }

    cm.getPlayer().setCS(true);
    var selStr = "你好，我是#b饰品NPC工匠#k！我的手艺广受认可，精湛到我制作的每件物品不仅外观逼真，连属性也完全一致！我收取的报酬就是一些'材料'，当然还有我的服务费。你对哪种装备感兴趣？#b";
    var options = ["项链", "脸饰", "眼饰", "腰带与勋章", "戒指"/*,"#t4032496#"*/];
    for (var i = 0; i < options.length; i++) {
        selStr += "\r\n#L" + i + "# " + options[i] + "#l";
    }
    cm.sendSimple(selStr);
}

function action(mode, type, selection) {
    status++;
    if (mode != 1) {
        cm.dispose();
        return;
    }
    if (status == 0) {
        if (selection == 0) { //pendants
            var selStr = "嗯，我这里有这些项链可供选择：#b";
            items = [1122018, 1122007, 1122001, 1122003, 1122004, 1122006, 1122002, 1122005, 1122058];
            for (var i = 0; i < items.length; i++) {
                selStr += "\r\n#L" + i + "##t" + items[i] + "##b";
            }
        } else if (selection == 1) { //face accessory
            var selStr = "嗯，脸饰？给你：#b";
            items = [1012181, 1012182, 1012183, 1012184, 1012185, 1012186, 1012108, 1012109, 1012110, 1012111];
            for (var i = 0; i < items.length; i++) {
                selStr += "\r\n#L" + i + "##t" + items[i] + "##b";
            }
        } else if (selection == 2) { //eye accessory
            var selStr = "眼神不太好？好吧，你想让我制作哪副眼镜？#b";
            items = [1022073, 1022088, 1022103, 1022089, 1022082];
            for (var i = 0; i < items.length; i++) {
                selStr += "\r\n#L" + i + "##t" + items[i] + "##b";
            }
        } else if (selection == 3) { //belt & medal
            var selStr = "嗯……这些就比较棘手了。因为这些物品太相似又太短小，我不确定合成完成后会出现什么物品。还想试试吗？";
            items = [];
            maxEqp = 0;

            for (var x = 1132005; x < 1132017; maxEqp++, x++) {
                items[maxEqp] = x;
            }

            for (var x = 1142000; x < 1142102; maxEqp++, x++) {
                items[maxEqp] = x;
            }

            for (var x = 1142107; x < 1142121; maxEqp++, x++) {
                items[maxEqp] = x;
            }

            for (var x = 1142122; x < 1142143; maxEqp++, x++) {
                items[maxEqp] = x;
            }
            selStr += "\r\n#L" + i + "##bTry it!#b";

        } else if (selection == 4) { //ring refine
            var selStr = "戒指，嗯？这可是我的拿手好戏，你自己看看吧！#b";
            items = [1112407, 1112408, 1112401, 1112413, 1112414, 1112405, 1112402];

            for (var i = 0; i < items.length; i++) {
                selStr += "\r\n#L" + i + "##t" + items[i] + "##b";
            }

        }/*else if (selection == 5) { //make necklace
            var selStr = "需要制作#t4032496#吗？#b";
            items = [4032496];
            for (var i = 0; i < items.length; i++)
                selStr += "\r\n#L" + i + "##t" + items[i] + "##l";
        }*/
        selectedType = selection;
        cm.sendSimple(selStr);
    } else if (status == 1) {
        if (selectedType != 3) {
            selectedItem = selection;
        }

        if (selectedType == 0) { //pendant refine
            var matSet = [[4003004, 4030012, 4001356, 4000026], [4000026, 4001356, 4000073, 4001006], [4001343, 4011002, 4003004, 4003005], [4001343, 4011006, 4003004, 4003005], [4000091, 4011005, 4003004, 4003005], [4000091, 4011001, 4003004, 4003005], [4000469, 4011000, 4003004, 4003005], [4000469, 4011004, 4003004, 4003005], [1122007, 4003002, 4000413]];
            var matQtySet = [[20, 20, 5, 1], [5, 5, 10, 1], [10, 2, 20, 4], [10, 1, 20, 4], [15, 3, 30, 6], [15, 3, 30, 6], [20, 5, 20, 8], [20, 4, 40, 8], [1, 1, 1]];
            var costSet = [150000, 500000, 200000, 200000, 300000, 300000, 400000, 400000, 2500000];
        } else if (selectedType == 1) { //face accessory refine
            var matSet = [[4006000, 4003004], [4006000, 4003004, 4000026], [4006000, 4003004, 4000026, 4000082, 4003002], [4006000, 4003005], [4006000, 4003005, 4000026], [4006000, 4003005, 4000026, 4000082, 4003002], [4001006, 4011008], [4001006, 4011008], [4001006, 4011008], [4001006, 4011008]];
            var matQtySet = [[5, 5], [5, 5, 5], [5, 5, 5, 5, 1], [5, 5], [5, 5, 5], [5, 5, 5, 5, 1], [1, 1], [1, 1], [1, 1], [1, 1]];
            var costSet = [100000, 200000, 300000, 125000, 250000, 375000, 500000, 500000, 500000, 500000, 25000, 25000, 25000, 25000];
        } else if (selectedType == 2) { //eye accessory refine
            var matSet = [[4001006, 4003002, 4000082, 4031203], [4001005, 4011008], [4001005, 4011008], [4001005, 4011008, 4000082], [4001006, 4003002, 4003000, 4003001]];
            var matQtySet = [[2, 2, 5, 10], [3, 2], [4, 3], [5, 3, 10], [2, 2, 10, 5]];
            var costSet = [250000, 250000, 300000, 400000, 200000];
        } else if (selectedType == 3) { //belt & medals refine
            var matSet = [[4001006, 4003005, 4003004], [7777, 7777]];
            var matQtySet = [[2, 5, 10], [7777, 7777]];
            var costSet = [15000, 7777];
        } else if (selectedType == 4) { //ring refine
            var matSet = [[4003001, 4001344, 4006000], [4003001, 4001344, 4006000], [4021004, 4011008], [4011008, 4001006], [1112413, 2022039], [1112414, 4000176], [4011007, 4021009]];
            var matQtySet = [[2, 2, 2], [2, 2, 2], [1, 1], [1, 1], [1, 1], [1, 1], [1, 1]];
            var costSet = [10000, 10000, 10000, 20000, 15000, 15000, 10000];
        }/*else if (selectedType == 5) { //necklace refine
            var matSet = [[4011007, 4011008, 4021009]];
            var matQtySet = [[1, 1, 1]];
            var costSet = [10000];
        }*/

        if (selectedType == 3) {
            selectedItem = Math.floor(Math.random() * maxEqp);
            item = items[selectedItem];
            mats = matSet[0];
            matQty = matQtySet[0];
            cost = costSet[0];
        } else {
            item = items[selectedItem];
            mats = matSet[selectedItem];
            matQty = matQtySet[selectedItem];
            cost = costSet[selectedItem];
        }

        var prompt = "你想让我制作 ";
        if (selectedType != 3) {
            if (qty == 1) {
                prompt += "a #b#t" + item + "##k?";
            } else {
                prompt += "#b" + qty + " #t" + item + "##k?";
            }
        } else {
            prompt += "一条#b腰带#k还是一枚#b勋章#k？";
        }

        prompt += " 好的！制作这件物品需要一些材料。请确保你的背包里有#b空位#k！#b";
        if (mats instanceof Array) {
            for (var i = 0; i < mats.length; i++) {
                prompt += "\r\n#i" + mats[i] + "# " + (matQty[i] * qty) + " #t" + mats[i] + "#";
            }
        } else {
            prompt += "\r\n#i" + mats + "# " + (matQty * qty) + " #t" + mats + "#";
        }
        if (cost > 0) {
            prompt += "\r\n#i4031138# " + (cost * qty) + " 金币";
        }
        cm.sendYesNo(prompt);
    } else if (status == 2) {
        if (cm.getMeso() < (cost * qty)) {
            cm.sendOk("这是我制作物品所收取的费用！不接受信用。");
        } else {
            var complete = true;
            if (mats instanceof Array) {
                for (var i = 0; complete && i < mats.length; i++) {
                    if (!cm.haveItem(mats[i], matQty[i] * qty)) {
                        complete = false;
                    }
                }
            } else if (!cm.haveItem(mats, matQty * qty)) {
                complete = false;
            }

            if (!complete) {
                cm.sendOk("你确定你拿齐了所有需要的物品吗？再检查一遍！");
            } else {
                if (cm.canHold(item, qty)) {
                    if (mats instanceof Array) {
                        for (var i = 0; i < mats.length; i++) {
                            cm.gainItem(mats[i], -(matQty[i] * qty));
                        }
                    } else {
                        cm.gainItem(mats, -(matQty * qty));
                    }
                    cm.gainMeso(-(cost * qty));

                    cm.gainItem(item, qty);
                    cm.sendOk("物品已经完成了！拿去试试这件艺术品吧。");
                } else {
                    cm.sendOk("你的库存中没有空闲的插槽。");
                }
            }
        }

        cm.dispose();
    }
}