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

var status;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        var mapobj = cm.getMap();

        if (mode == 0 && type > 0) {
            cm.getPlayer().dropMessage(5, "埃雷奥诺尔:哦，失去了女皇，还还想挑战我们？现在你做到了！做好准备吧！！！");

            const LifeFactory = Java.type('org.gms.server.life.LifeFactory');
            const Point = Java.type('java.awt.Point');
            mapobj.spawnMonsterOnGroundBelow(LifeFactory.getMonster(9001010), new Point(850, 0));
            mapobj.destroyNPC(1104002);

            cm.dispose();
            return;
        }
        if (mode == 1) {
            status++;
        } else {
            status--;
        }

        if (status == 0) {
            if (!cm.isQuestStarted(20407)) {
                cm.sendOk("“... 骑士，你似乎还不确定要面对这场战斗，是吗？挑战一个人的时候，如果他们还没有心理准备进行战斗，那就没有什么优雅可言。和你那只笨拙的大鸟说说你的想法，也许它会给你一些勇气。”");
                cm.dispose();
                return;
            }

            cm.sendAcceptDecline("哈哈哈哈哈!女皇已经在我的地盘上了，这无疑是一个很大的进步#b黑色之翼#k"'向冒险岛世界倾覆。。。你呢？还想面对我们？或者，更好的是，既然你看起来足够强大，可以作为我们服务的补充力量，#r你能满足我们的期望和加入我们的愿望吗#k或者你已经无能为力了？");
        } else if (status == 1) {
            cm.sendOk("“哈，懦夫在#r黑魔法师#k的军队中没有立足之地。滚吧！”");
            cm.dispose();
        }
    }
}