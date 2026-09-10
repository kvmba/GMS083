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
package org.gms.server.maps;

import org.gms.scripting.event.EventInstanceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MapManager {
    private final int channel;
    private final int world;
    private EventInstanceManager event;

    private final Map<Integer, MapleMap> maps = new HashMap<>();

    private final Lock mapsRLock;
    private final Lock mapsWLock;

    public MapManager(EventInstanceManager eim, int world, int channel) {
        this.world = world;
        this.channel = channel;
        this.event = eim;

        ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        this.mapsRLock = readWriteLock.readLock();
        this.mapsWLock = readWriteLock.writeLock();
    }

    public MapleMap resetMap(int mapid) {
        mapsWLock.lock();
        try {
            maps.remove(mapid);
        } finally {
            mapsWLock.unlock();
        }

        return getMap(mapid);
    }

    /**
     * Loads a map NOT under the instance monitor, then publishes it under the write lock with a
     * re-check, so exactly one MapleMap per id is ever visible to callers.
     *
     * <p>Not synchronized, and that is the point. This used to be
     * {@code private synchronized}, which held this MapManager's monitor across
     * {@link MapFactory#loadMapFromWz} - a slow WZ read plus a database round trip. While holding
     * that monitor it could end up needing a script call, and a script calling
     * {@code getMapFactory().getMap()} back into this class needs the same monitor from a thread
     * already holding the scripting engine's lock ({@code SynchronizedInvocable}). The two paths
     * take the locks in opposite orders, so the server deadlocks: every task parks with no CPU
     * and no IO. Loading outside the monitor removes one side of that cycle.
     *
     * <p>The monitor was also doing a second job: de-duplication. Two threads missing on the same
     * mapId used to serialise on it, so only one loaded and the other picked the result out of
     * the cache. Without it both would build their own MapleMap and publish it, and the two
     * halves of the map - players, monster respawns, the item monitor - would split across two
     * instances: a split brain where some characters cannot see others. The re-check under the
     * write lock restores that: the loser of the race returns the winner's instance instead of
     * publishing its own, so callers never observe two different maps for one id.
     *
     * <p>The costly case is two threads racing one cold id: the map is built twice and one result
     * is discarded. That is a wasted WZ read on a path that is already slow, and it is the
     * deliberate trade - correctness over one redundant load.
     */
    private MapleMap loadMapFromWz(int mapid, boolean cache) {
        MapleMap map;

        if (cache) {
            mapsRLock.lock();
            try {
                map = maps.get(mapid);
            } finally {
                mapsRLock.unlock();
            }

            if (map != null) {
                return map;
            }
        }

        // Slow part, deliberately outside every lock: WZ read + DB access, and any script call
        // reached from here must not be holding this instance's monitor.
        map = MapFactory.loadMapFromWz(mapid, world, channel, event);

        if (!cache) {
            return map; // disposable: never published, so no instance to prefer
        }

        mapsWLock.lock();
        try {
            MapleMap winner = maps.get(mapid);
            if (winner != null) {
                return winner; // someone else published first - use theirs, discard ours
            }
            maps.put(mapid, map);
            return map;
        } finally {
            mapsWLock.unlock();
        }
    }

    public MapleMap getMap(int mapid) {
        MapleMap map;

        mapsRLock.lock();
        try {
            map = maps.get(mapid);
        } finally {
            mapsRLock.unlock();
        }

        return (map != null) ? map : loadMapFromWz(mapid, true);
    }

    public MapleMap getMapByLifeId(int lifeId) {
        String mapId = MapFactory.getMapIdByLifeId(lifeId);
        return mapId == null ? null : getMap(Integer.parseInt(mapId));
    }

    public MapleMap getDisposableMap(int mapid) {
        return loadMapFromWz(mapid, false);
    }

    public boolean isMapLoaded(int mapId) {
        mapsRLock.lock();
        try {
            return maps.containsKey(mapId);
        } finally {
            mapsRLock.unlock();
        }
    }

    public Map<Integer, MapleMap> getMaps() {
        mapsRLock.lock();
        try {
            return new HashMap<>(maps);
        } finally {
            mapsRLock.unlock();
        }
    }

    public void updateMaps() {
        for (MapleMap map : getMaps().values()) {
            map.respawn();
            map.mobMpRecovery();
        }
    }

    public void dispose() {
        for (MapleMap map : getMaps().values()) {
            map.dispose();
        }

        this.event = null;
    }

}
