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

package org.gms.server.events.gm;

import org.gms.client.Character;
import org.gms.constants.id.MapId;
import org.gms.server.TimerManager;
import org.gms.util.PacketCreator;

import java.util.concurrent.ScheduledFuture;

/**
 * @author kevintjuh93
 */
public class Fitness {
    private final Character chr;
    private long time = 0;
    private long timeStarted = 0;
    private ScheduledFuture<?> schedule = null;
    private ScheduledFuture<?> schedulemsg = null;

    public Fitness(final Character chr) {
        this.chr = chr;
        this.schedule = TimerManager.getInstance().schedule(() -> {
            if (MapId.isPhysicalFitness(chr.getMapId())) {
                chr.changeMap(chr.getMap().getReturnMap());
            }
        }, 900000);
    }

    public void startFitness() {
        chr.getMap().startEvent();
        chr.getClient().sendPacket(PacketCreator.getClock(900));
        this.timeStarted = System.currentTimeMillis();
        this.time = 900000;
        checkAndMessage();

        chr.getMap().getPortal("join00").setPortalStatus(true);
        chr.sendPacket(PacketCreator.serverNotice(0, "传送门现已开启。在传送门处按上方向键即可进入。"));
    }

    public boolean isTimerStarted() {
        return time > 0 && timeStarted > 0;
    }

    public long getTime() {
        return time;
    }

    public void resetTimes() {
        this.time = 0;
        this.timeStarted = 0;
        schedule.cancel(false);
        schedulemsg.cancel(false);
    }

    public long getTimeLeft() {
        return time - (System.currentTimeMillis() - timeStarted);
    }

    public void checkAndMessage() {
        this.schedulemsg = TimerManager.getInstance().register(() -> {
            if (chr.getFitness() == null) {
                resetTimes();
            }
            if (MapId.isPhysicalFitness(chr.getMapId())) {
                if (getTimeLeft() > 9000 && getTimeLeft() < 11000) {
                    chr.sendPacket(PacketCreator.serverNotice(0, "还剩10秒。没有完成游戏的朋友，希望你们下次能成功！大家都很棒！！回头见~"));
                } else if (getTimeLeft() > 99000 && getTimeLeft() < 101000) {
                    chr.sendPacket(PacketCreator.serverNotice(0, "好了，剩下的时间不多了。请稍微抓紧一点！"));
                } else if (getTimeLeft() > 239000 && getTimeLeft() < 241000) {
                    chr.sendPacket(PacketCreator.serverNotice(0, "第4阶段是[冒险岛体能测试]的最后一关。请不要在最后关头放弃，全力以赴。奖励就在最顶端等着你！"));
                } else if (getTimeLeft() > 299000 && getTimeLeft() < 301000) {
                    chr.sendPacket(PacketCreator.serverNotice(0, "第3阶段有陷阱，你能看到它们，但不能踩上去。向上前进时请小心。"));
                } else if (getTimeLeft() > 359000 && getTimeLeft() < 361000) {
                    chr.sendPacket(PacketCreator.serverNotice(0, "网络延迟较高的玩家请注意缓慢移动，避免因延迟而一路跌落。"));
                } else if (getTimeLeft() > 499000 && getTimeLeft() < 501000) {
                    chr.sendPacket(PacketCreator.serverNotice(0, "请记住，如果你在活动中死亡，将被淘汰出局。如果HP不足，请先使用药水或恢复HP再继续前进。"));
                } else if (getTimeLeft() > 599000 && getTimeLeft() < 601000) {
                    chr.sendPacket(PacketCreator.serverNotice(0, "躲避猴子扔香蕉的关键就是*时机*，时机决定一切！"));
                } else if (getTimeLeft() > 659000 && getTimeLeft() < 661000) {
                    chr.sendPacket(PacketCreator.serverNotice(0, "第2阶段有猴子扔香蕉。请注意掌握好时机移动来躲避它们。"));
                } else if (getTimeLeft() > 699000 && getTimeLeft() < 701000) {
                    chr.sendPacket(PacketCreator.serverNotice(0, "请记住，如果你在活动中死亡，将被淘汰出局。时间还很充裕，请先使用药水或恢复HP再继续前进。"));
                } else if (getTimeLeft() > 779000 && getTimeLeft() < 781000) {
                    chr.sendPacket(PacketCreator.serverNotice(0, "所有按时通过[冒险岛体能测试]的玩家都将获得物品奖励，与完成顺序无关，所以放轻松，慢慢来，通过这4个阶段吧。"));
                } else if (getTimeLeft() > 839000 && getTimeLeft() < 841000) {
                    chr.sendPacket(PacketCreator.serverNotice(0, "由于第1阶段玩家众多，可能会有严重的延迟。这一关并不难，请注意不要因为延迟而跌落。"));
                } else if (getTimeLeft() > 869000 && getTimeLeft() < 871000) {
                    chr.sendPacket(PacketCreator.serverNotice(0, "[冒险岛体能测试]由4个阶段组成，如果在游戏中死亡将被淘汰，请注意。"));
                }
            } else {
                resetTimes();
            }
        }, 5000, 29500);
    }
    // 14:30 [Notice][冒险岛体能测试]由4个阶段组成，如果在游戏中死亡将被淘汰，请注意。
    // 14:00 [Notice]由于第1阶段玩家众多，可能会有严重的延迟。这一关并不难，请注意不要因为延迟而跌落。
    // 13:00 [Notice]所有按时通过[冒险岛体能测试]的玩家都将获得物品奖励，与完成顺序无关，所以放轻松，慢慢来，通过这4个阶段吧。
    // 11:40 [Notice]请记住，如果你在活动中死亡，将被淘汰出局。时间还很充裕，请先使用药水或恢复HP再继续前进。
    // 11:00 [Notice]第2阶段有猴子扔香蕉。请注意掌握好时机移动来躲避它们。
    // 10:00 [Notice]躲避猴子扔香蕉的关键就是*时机*，时机决定一切！
    // 8:20 [Notice]请记住，如果你在活动中死亡，将被淘汰出局。如果HP不足，请先使用药水或恢复HP再继续前进。
    // 6:00 [Notice]网络延迟较高的玩家请注意缓慢移动，避免因延迟而一路跌落。
    // 5:00 [Notice]第3阶段有陷阱，你能看到它们，但不能踩上去。向上前进时请小心。
    // 4:00 [Notice]第4阶段是[冒险岛体能测试]的最后一关。请不要在最后关头放弃，全力以赴。奖励就在最顶端等着你！
    // 1:40 [Notice]好了，剩下的时间不多了。请稍微抓紧一点！
    // 0:10 [Notice]还剩10秒。没有完成游戏的朋友，希望你们下次能成功！大家都很棒！！回头见~
}
