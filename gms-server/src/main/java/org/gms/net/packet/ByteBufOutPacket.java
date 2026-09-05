package org.gms.net.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.jcip.annotations.NotThreadSafe;
import org.gms.constants.string.CharsetConstants;
import org.gms.net.opcodes.Opcode;
import org.gms.net.opcodes.SendOpcode;
import org.gms.util.ThreadLocalUtil;

import java.awt.*;

@NotThreadSafe
public class ByteBufOutPacket implements OutPacket {
    private final ByteBuf byteBuf;

    public ByteBufOutPacket() {
        this.byteBuf = Unpooled.buffer();
    }

    public ByteBufOutPacket(Opcode op) {
        ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeShortLE((short) op.getValue());
        this.byteBuf = byteBuf;
    }

    public ByteBufOutPacket(SendOpcode op, int initialCapacity) {
        ByteBuf byteBuf = Unpooled.buffer(initialCapacity);
        byteBuf.writeShortLE((short) op.getValue());
        this.byteBuf = byteBuf;
    }

    @Override
    public byte[] getBytes() {
        return ByteBufUtil.getBytes(byteBuf);
    }

    @Override
    public void writeByte(byte value) {
        byteBuf.writeByte(value);
    }

    @Override
    public void writeByte(int value) {
        writeByte((byte) value);
    }

    @Override
    public void writeBytes(byte[] value) {
        byteBuf.writeBytes(value);
    }

    @Override
    public void writeShort(int value) {
        byteBuf.writeShortLE(value);
    }

    @Override
    public void writeInt(int value) {
        byteBuf.writeIntLE(value);
    }

    @Override
    public void writeLong(long value) {
        byteBuf.writeLongLE(value);
    }

    @Override
    public void writeBool(boolean value) {
        byteBuf.writeByte(value ? 1 : 0);
    }

    @Override
    public void writeString(String value) {
        byte[] bytes = value.getBytes(CharsetConstants.getCharset(ThreadLocalUtil.getClientLang()));
        writeShort(bytes.length);
        writeBytes(bytes);
    }

    @Override
    public void writeFixedString(String value) {
        writeFixedString(value, 13);
    }

    /**
     * 写入定长字符串：不足 {@code fixed} 字节补 0，超出则截断。
     */
    @Override
    public void writeFixedString(String value, int fixed) {
        byte[] bytes = (value == null ? "" : value).getBytes(CharsetConstants.getCharset(ThreadLocalUtil.getClientLang()));
        if (bytes.length >= fixed) {
            byteBuf.writeBytes(bytes, 0, fixed);
            return;
        }
        // 短于定长：先写实际字节再补 0，省掉 copyOf 的临时数组分配。
        byteBuf.writeBytes(bytes);
        byteBuf.writeZero(fixed - bytes.length);
    }

    @Override
    public void writePos(Point value) {
        writeShort((short) value.getX());
        writeShort((short) value.getY());
    }

    @Override
    public void skip(int numberOfBytes) {
        // 与 writeBytes(new byte[n]) 等价，省掉一次数组分配。
        byteBuf.writeZero(numberOfBytes);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ByteBufOutPacket other && byteBuf.equals(other.byteBuf);
    }
}
