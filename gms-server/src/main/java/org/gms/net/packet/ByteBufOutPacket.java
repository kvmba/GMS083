package org.gms.net.packet;

import org.gms.client.Client;
import org.gms.constants.string.CharsetConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.jcip.annotations.NotThreadSafe;
import org.gms.net.opcodes.Opcode;
import org.gms.net.opcodes.SendOpcode;
import org.gms.util.ThreadLocalUtil;

import java.awt.*;
import java.util.Arrays;
import java.util.Optional;

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

    // TEMP experiment: force GBK for every outgoing string, ignoring the per-client charset.
    // If the garbled yellow notice clears up, the cause is server-side charset selection;
    // if it persists, that text is not produced by this path at all.
    private static final java.nio.charset.Charset GBK = java.nio.charset.Charset.forName("GBK");

    @Override
    public void writeString(String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(GBK);
        writeShort(bytes.length);
        writeBytes(bytes);
    }

    @Override
    public void writeFixedString(String value) {
        writeFixedString(value, 13);
    }

    @Override
    public void writeFixedString(String value, int fixed) {
        writeBytes(Arrays.copyOf(value.getBytes(CharsetConstants.getCharset(ThreadLocalUtil.getClientLang())), fixed));
    }

    @Override
    public void writePos(Point value) {
        writeShort((short) value.getX());
        writeShort((short) value.getY());
    }

    @Override
    public void skip(int numberOfBytes) {
        writeBytes(new byte[numberOfBytes]);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ByteBufOutPacket other && byteBuf.equals(other.byteBuf);
    }
}
