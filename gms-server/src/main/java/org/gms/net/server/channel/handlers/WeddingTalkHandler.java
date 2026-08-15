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

package org.gms.net.server.channel.handlers;

import org.gms.client.Client;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.scripting.event.EventInstanceManager;
import org.gms.util.PacketCreator;
import org.gms.util.packets.WeddingPackets;

/**
 * @author Ronan
 */
public final class WeddingTalkHandler extends AbstractPacketHandler {

    @Override
    public final void handlePacket(InPacket p, Client c) {
        EventInstanceManager eim = c.getPlayer().getEventInstance();
        boolean isGroomOrBride = eim != null && (c.getPlayer().getId() == eim.getIntProperty("groomId") || c.getPlayer().getId() == eim.getIntProperty("brideId"));

        if (p.available() > 0) {
            // 带负载:神父对话/进度校验(TALK)。action==1 为进度校验,其余为通用进度响应。
            byte action = p.readByte();
            if (action == 1 && !isGroomOrBride) {
                c.sendPacket(WeddingPackets.OnWeddingProgress(false, 0, 0, (byte) 2));
                c.sendPacket(PacketCreator.enableActions());
                return;
            }
        } else {
            // 无负载:宾客祝福(TALK_MORE),非新人将祝福计入爱的积分
            // 玩家不在婚礼事件中(eim 为 null)时直接忽略,避免 NPE
            if (eim != null && !isGroomOrBride) {
                eim.gridInsert(c.getPlayer(), 1);
                c.getPlayer().dropMessage(5, "约翰大祭司：你的祝福已融入他们的爱中。对这对可爱的夫妇来说，这是多么高尚的举动！");
            }
        }

        c.sendPacket(WeddingPackets.OnWeddingProgress(true, 0, 0, (byte) 3));
        c.sendPacket(PacketCreator.enableActions());
    }
}