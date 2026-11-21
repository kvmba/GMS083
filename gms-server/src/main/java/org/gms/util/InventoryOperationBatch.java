package org.gms.util;

import org.gms.client.Client;
import org.gms.client.inventory.Item;
import org.gms.net.opcodes.SendOpcode;
import org.gms.net.packet.ByteBufOutPacket;

/** 同一次业务中的普通背包变更。需要一起应用的两条记录不会被分到不同包。 */
public final class InventoryOperationBatch implements AutoCloseable {
    private final Client client;
    private final boolean releaseRequest;
    private ByteBufOutPacket packet;
    private int count;

    public InventoryOperationBatch(Client client, boolean releaseRequest) {
        this.client = client;
        this.releaseRequest = releaseRequest;
    }

    // 在调用方持有背包锁时直接编码，不缓存会继续变动的 Item 引用。
    public void add(int mode, Item item) {
        append(mode, item, (short) 0, 0, null);
    }

    public void move(Item item, short oldPosition) {
        append(2, item, oldPosition, 0, null);
    }

    public void addPair(int mode, Item item, int nextMode, Item nextItem) {
        append(mode, item, (short) 0, nextMode, nextItem);
    }

    private void append(int mode, Item item, short oldPosition, int nextMode, Item nextItem) {
        int added = nextItem == null ? 1 : 2;
        if (count + added > PacketCreator.MAX_INVENTORY_OPERATIONS) {
            send(false);
        }
        if (tryAdd(mode, item, oldPosition, nextMode, nextItem, added)) {
            return;
        }
        if (count != 0) {
            send(false);
            if (tryAdd(mode, item, oldPosition, nextMode, nextItem, added)) {
                return;
            }
        }
        throw new IllegalArgumentException("Inventory operations are too large for one packet");
    }

    private boolean tryAdd(int mode, Item item, short oldPosition, int nextMode, Item nextItem, int added) {
        if (packet == null) {
            packet = new ByteBufOutPacket(SendOpcode.INVENTORY_OPERATION);
            packet.skip(2); // updateTick、操作数在发送前回填。
        }
        int start = packet.size();
        boolean appended = false;
        try {
            if (PacketCreator.writeInventoryOperation(packet, mode, item, oldPosition) >= 0
                    || (nextItem != null && PacketCreator.writeInventoryOperation(packet, nextMode, nextItem, (short) 0) >= 0)) {
                throw new IllegalArgumentException("Equipped item operations must be sent separately");
            }
            if (packet.size() > PacketCreator.MAX_INVENTORY_PACKET_LENGTH) {
                return false;
            }
            count += added;
            appended = true;
            return true;
        } finally {
            if (!appended) {
                packet.truncate(start);
            }
        }
    }

    /** 在物品获得、背包已满等依赖前述变更的回包之前主动发送。 */
    public void flush() {
        send(releaseRequest);
    }

    private void send(boolean releaseRequest) {
        if (count == 0) {
            return;
        }
        ByteBufOutPacket pending = packet;
        pending.setByte(Short.BYTES, releaseRequest ? 1 : 0);
        pending.setByte(Short.BYTES + 1, count);
        packet = null;
        count = 0;
        client.sendPacket(pending);
    }

    @Override
    public void close() {
        flush();
    }
}
