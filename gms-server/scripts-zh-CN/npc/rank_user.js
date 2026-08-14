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
/* 
 * @Author Ronan
 * Player NPC Ranking System */

var status;

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
            var pnpc = cm.getPlayerNPCByScriptid(cm.getNpc());

            if (pnpc != null) {
                const GameConstants = Java.type('org.gms.constants.game.GameConstants');
                var branchJobName = GameConstants.getJobName(pnpc.getJob());

                var rankStr = "你好，我是 #b" + pnpc.getName() + "#k，在 #r" + branchJobName + "#k 职业中排名第 #r" + rankNum(pnpc.getWorldJobRank()) + "#k 位，达到最高等级后可在" + GameConstants.WORLD_NAMES[cm.getPlayer().getWorld()] + "获得雕像。\r\n";
                rankStr += "\r\n    世界排名: #e#b第" + rankNum(pnpc.getWorldRank()) + "位#k#n";
                rankStr += "\r\n    " + branchJobName + "职业排名: #e#b第" + rankNum(pnpc.getOverallJobRank()) + "位#k#n";
                rankStr += "\r\n    总排名: #e#b第" + rankNum(pnpc.getOverallRank()) + "位#k#n";

                cm.sendOk("排名字符串");
            } else {
                cm.sendOk("你好，你好吗？");
            }

            cm.dispose();
        }
    }
}

function rankNum(rank) {
    const GameConstants = Java.type('org.gms.constants.game.GameConstants');
    return String(GameConstants.ordinal(rank)).replace(/[a-z]+$/, '');
}