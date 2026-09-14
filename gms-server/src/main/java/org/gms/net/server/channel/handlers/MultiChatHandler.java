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
package org.gms.net.server.channel.handlers;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.autoban.AutobanFactory;
import org.gms.extension.event.CharacterDirectChatEvent;
import org.gms.extension.event.ChatType;
import org.gms.extension.runtime.HostHooks;
import org.gms.net.AbstractPacketHandler;
import org.gms.net.packet.InPacket;
import org.gms.net.server.Server;
import org.gms.net.server.guild.Alliance;
import org.gms.net.server.guild.Guild;
import org.gms.net.server.guild.GuildCharacter;
import org.gms.net.server.world.Party;
import org.gms.net.server.world.PartyCharacter;
import org.gms.net.server.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gms.server.ChatLogger;
import org.gms.util.PacketCreator;

import java.util.ArrayList;
import java.util.List;

public final class MultiChatHandler extends AbstractPacketHandler {
    private static final Logger log = LoggerFactory.getLogger(MultiChatHandler.class);

    @Override
    public void handlePacket(InPacket p, Client c) {
        Character player = c.getPlayer();
        if (player.getAutoBanManager().getLastSpam(7) + 200 > currentServerTime()) {
            return;
        }

        int type = p.readByte(); // 0 for buddies, 1 for parties, 2 for guilds, 3 for alliances
        // 如果人数超过255仍会溢出，需要和客户端同步修改才能支持更多人数
        int numRecipients = Byte.toUnsignedInt(p.readByte());
        if (numRecipients > p.available() / Integer.BYTES) {
            return;
        }
        int[] recipients = new int[numRecipients];
        for (int i = 0; i < numRecipients; i++) {
            recipients[i] = p.readInt();
        }
        String chattext = p.readString();
        if (chattext.length() > Byte.MAX_VALUE && !player.isGM()) {
            AutobanFactory.PACKET_EDIT.alert(c.getPlayer(), c.getPlayer().getName() + " tried to packet edit chats.");
            log.warn("Chr {} tried to send text with length of {}", c.getPlayer().getName(), chattext.length());
            c.disconnect(true, false);
            return;
        }
        World world = c.getWorldServer();
        if (type == 0) {
            world.buddyChat(recipients, player.getId(), player.getName(), chattext);
            ChatLogger.log(c, "Buddy", chattext);
            publishToArtificialRecipients(player, buddyRecipients(world, recipients, player), chattext, ChatType.BUDDY);
        } else if (type == 1 && player.getParty() != null) {
            world.partyChat(player.getParty(), chattext, player.getName());
            ChatLogger.log(c, "Party", chattext);
            publishToArtificialRecipients(player, partyRecipients(player.getParty()), chattext, ChatType.PARTY);
        } else if (type == 2 && player.getGuildId() > 0) {
            Server.getInstance().guildChat(player.getGuildId(), player.getName(), player.getId(), chattext);
            ChatLogger.log(c, "Guild", chattext);
            publishToArtificialRecipients(player, guildRecipients(player.getGuild()), chattext, ChatType.GUILD);
        } else if (type == 3 && player.getGuild() != null) {
            int allianceId = player.getGuild().getAllianceId();
            if (allianceId > 0) {
                Server.getInstance().allianceMessage(allianceId, PacketCreator.multiChat(player.getName(), chattext, 3), player.getId(), -1);
                ChatLogger.log(c, "Ally", chattext);
                publishToArtificialRecipients(player, allianceRecipients(allianceId), chattext, ChatType.ALLIANCE);
            }
        }
        player.getAutoBanManager().spam(7);
    }

    /**
     * The recipient sets mirror what the engine broadcast actually reaches, so the event and the
     * packet agree. Buddy chat is the odd one: the client supplies the recipient ids and the engine
     * only delivers to those on the sender's visible buddy list.
     */
    private static List<Character> buddyRecipients(World world, int[] recipients, Character sender) {
        List<Character> out = new ArrayList<>(recipients.length);
        for (int id : recipients) {
            Character chr = world.getPlayerStorage().getCharacterById(id);
            if (chr != null && chr.getBuddylist().containsVisible(sender.getId())) {
                out.add(chr);
            }
        }
        return out;
    }

    private static List<Character> partyRecipients(Party party) {
        List<Character> out = new ArrayList<>();
        for (PartyCharacter pc : party.getMembers()) {
            if (pc != null && pc.getPlayer() != null) {
                out.add(pc.getPlayer());
            }
        }
        return out;
    }

    private static List<Character> guildRecipients(Guild guild) {
        List<Character> out = new ArrayList<>();
        if (guild == null) {
            return out;
        }
        for (GuildCharacter mgc : guild.getMembers()) {
            if (mgc != null && mgc.isOnline() && mgc.getCharacter() != null) {
                out.add(mgc.getCharacter());
            }
        }
        return out;
    }

    private static List<Character> allianceRecipients(int allianceId) {
        List<Character> out = new ArrayList<>();
        Alliance alliance = Server.getInstance().getAlliance(allianceId);
        if (alliance == null) {
            return out;
        }
        for (int gid : alliance.getGuilds()) {
            out.addAll(guildRecipients(Server.getInstance().getGuild(gid)));
        }
        return out;
    }

    // A plugin-owned recipient has no real client to read the chat packet, so it answers through
    // this event instead. Published once per artificial recipient; real recipients keep the packet.
    private static void publishToArtificialRecipients(Character sender, List<Character> recipients,
                                                      String message, ChatType type) {
        for (Character recipient : recipients) {
            if (HostHooks.isArtificial(recipient)) {
                HostHooks.publish(new CharacterDirectChatEvent(sender, recipient, message, type));
            }
        }
    }
}
