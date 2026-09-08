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
package org.gms.net.server;

import org.gms.client.Character;
import org.gms.client.Client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PlayerStorage {
    private final Map<Integer, Character> storage = new LinkedHashMap<>();
    private final Map<String, Character> nameStorage = new LinkedHashMap<>();
    private final Lock rlock;
    private final Lock wlock;

    public PlayerStorage() {
        // NOT fair. A fair read/write lock serialises readers and writers strictly in arrival
        // order, so a steady stream of readers starves the writers - and the writers are the ones
        // registering characters. A startup dump showed dozens of threads parked in getSize()
        // holding the read lock while addPlayer() sat on the write lock and made no progress:
        // each wave of bot spawns is doing both at once, thousands of times.
        //
        // Unfair lets a reader jump ahead of a queued writer, which is what keeps the throughput
        // up. Nothing here depends on lock fairness - callers only need the maps to be consistent.
        ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        this.rlock = readWriteLock.readLock();
        this.wlock = readWriteLock.writeLock();
    }

    public void addPlayer(Character chr) {
        wlock.lock();
        try {
            storage.put(chr.getId(), chr);
            nameStorage.put(chr.getName().toLowerCase(), chr);
        } finally {
            wlock.unlock();
        }
    }

    public Character removePlayer(int chr) {
        wlock.lock();
        try {
            Character mc = storage.remove(chr);
            if (mc != null) {
                nameStorage.remove(mc.getName().toLowerCase());
            }

            return mc;
        } finally {
            wlock.unlock();
        }
    }

    public Character getCharacterByName(String name) {
        rlock.lock();
        try {
            return nameStorage.get(name.toLowerCase());
        } finally {
            rlock.unlock();
        }
    }

    public Character getCharacterById(int id) {
        rlock.lock();
        try {
            return storage.get(id);
        } finally {
            rlock.unlock();
        }
    }

    public Collection<Character> getAllCharacters() {
        rlock.lock();
        try {
            return new ArrayList<>(storage.values());
        } finally {
            rlock.unlock();
        }
    }

    public final void disconnectAll() {
        List<Character> chrList;
        rlock.lock();
        try {
            chrList = new ArrayList<>(storage.values());
        } finally {
            rlock.unlock();
        }

        for (Character mc : chrList) {
            Client client = mc.getClient();
            if (client != null) {
                client.forceDisconnect();
            }
        }

        wlock.lock();
        try {
            storage.clear();
        } finally {
            wlock.unlock();
        }
    }

    public int getSize() {
        rlock.lock();
        try {
            return storage.size();
        } finally {
            rlock.unlock();
        }
    }
}
