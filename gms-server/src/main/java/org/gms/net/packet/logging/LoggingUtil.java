package org.gms.net.packet.logging;

import io.netty.buffer.Unpooled;
import org.gms.config.GameConfig;
import org.gms.net.opcodes.RecvOpcode;
import org.gms.net.opcodes.SendOpcode;

import java.util.Set;

public class LoggingUtil {
    private static final Set<Short> ignoredDebugRecvPackets = Set.of(
            (short) RecvOpcode.MOVE_PLAYER.getValue(), // 41
            (short) RecvOpcode.HEAL_OVER_TIME.getValue(), // 89
            (short) RecvOpcode.SPECIAL_MOVE.getValue(), // 91
            (short) RecvOpcode.QUEST_ACTION.getValue(), // 107
            (short) RecvOpcode.MOVE_PET.getValue(), // 167
            (short) RecvOpcode.MOVE_LIFE.getValue(), // 188
            (short) RecvOpcode.NPC_ACTION.getValue() // 197
    );

    public static short readFirstShort(byte[] bytes) {
        return Unpooled.wrappedBuffer(bytes).readShortLE();
    }

    public static boolean isIgnoredRecvPacket(short opcode) {
        return ignoredDebugRecvPackets.contains(opcode);
    }

    // 封包调试日志是否过滤玩家/宠物移动封包（开关关闭时过滤，开启时打印）
    public static boolean isFilteredMoveRecvPacket(short opcode) {
        return isFilteredMovePacket(opcode, (short) RecvOpcode.MOVE_PLAYER.getValue(), (short) RecvOpcode.MOVE_PET.getValue());
    }

    public static boolean isFilteredMoveSendPacket(short opcode) {
        return isFilteredMovePacket(opcode, (short) SendOpcode.MOVE_PLAYER.getValue(), (short) SendOpcode.MOVE_PET.getValue());
    }

    private static boolean isFilteredMovePacket(short opcode, short playerMoveOpcode, short petMoveOpcode) {
        if (opcode == playerMoveOpcode) {
            return !GameConfig.getServerBoolean("use_debug_show_player_move");
        }
        if (opcode == petMoveOpcode) {
            return !GameConfig.getServerBoolean("use_debug_show_pet_move");
        }
        return false;
    }
}
