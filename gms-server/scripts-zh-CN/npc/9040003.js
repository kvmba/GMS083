/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

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
 * @Author TheRamon
 * @Author Ronan
 * 
 * Sharen III's Soul, Sharenian: Sharen III's Grave (990000700)
 * 
 * Guild Quest - end of stage 4
 */

function clearStage(stage, eim) {
    eim.setProperty("stage" + stage + "clear", "true");
    eim.showClearEffect(true);

    eim.giveEventPlayersStageReward(stage);
}

var status = 0;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        if (mode == 1) {
            status++;
        } else {
            cm.dispose();
        }

        var eim = cm.getPlayer().getEventInstance();

        if (eim.getProperty("stage4clear") != null && eim.getProperty("stage4clear") === "true") {
"在我以为是不朽的睡眠之后，我终于发现有人能救他。我现在可以真正休息了。"
            cm.dispose();
            return;
        }

        if (status == 0) {
            if (cm.isEventLeader()) {
                cm.sendNext("“在我以为会是永恒的沉睡之后，我终于找到了一个能够拯救夏雷尼安的人。这位老人现在将为你铺平道路，让你完成这项任务。”");

                clearStage(4, eim);
                cm.getGuild().gainGP(30);
                cm.getPlayer().getMap().getReactorByName("ghostgate").forceHitReactor(1);

                cm.dispose();
            } else {
                cm.sendOk("我需要你们队伍的领袖和我交谈，其他人不要。");
                cm.dispose();
            }
        }
    }
}