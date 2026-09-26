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

    // 封包调试日志是否过滤移动封包（对应开关关闭时过滤，开启时打印）
    // 入站：玩家/宠物/怪物移动；出站：怪物移动及怪物移动反馈（均由 use_debug_show_life_move 控制日志）
    public static boolean isFilteredMoveRecvPacket(short opcode) {
        if (opcode == (short) RecvOpcode.MOVE_PLAYER.getValue()) {
            return isMoveLogOff("use_debug_show_player_move");
        }
        if (opcode == (short) RecvOpcode.MOVE_PET.getValue()) {
            return isMoveLogOff("use_debug_show_pet_move");
        }
        if (opcode == (short) RecvOpcode.MOVE_LIFE.getValue()) {
            return isMoveLogOff("use_debug_show_life_move");
        }
        return false;
    }

    public static boolean isFilteredMoveSendPacket(short opcode) {
        if (opcode == (short) SendOpcode.MOVE_PLAYER.getValue()) {
            return isMoveLogOff("use_debug_show_player_move");
        }
        if (opcode == (short) SendOpcode.MOVE_PET.getValue()) {
            return isMoveLogOff("use_debug_show_pet_move");
        }
        if (opcode == (short) SendOpcode.MOVE_MONSTER.getValue()
                || opcode == (short) SendOpcode.MOVE_MONSTER_RESPONSE.getValue()) {
            return isMoveLogOff("use_debug_show_life_move");
        }
        return false;
    }

    // 开关关闭时过滤该移动封包，避免刷屏（与 use_debug_show_player_move/pet_move/life_move 语义一致）
    private static boolean isMoveLogOff(String configCode) {
        return !GameConfig.getServerBoolean(configCode);
    }
}
