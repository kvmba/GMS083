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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MapManager {
    private final int channel;
    private final int world;
    private volatile EventInstanceManager event;

    // Concurrent, and loaded through computeIfAbsent below.
    //
    // This used to be a plain HashMap guarded by a ReentrantReadWriteLock, with the load itself
    // done in a synchronized method. That deadlocked: resetMap() took the WRITE lock and then
    // called getMap(), which needed that method's monitor; while the load held the monitor across
    // the slow WZ read and then took the WRITE lock to cache the result. Two threads entering
    // those two paths waited on each other forever - the startup hang where the wave stops with
    // no CPU and no IO.
    //
    // Loading per key instead removes the shared monitor entirely: different maps load in
    // parallel, and one map is still never loaded twice.
    private final ConcurrentMap<Integer, MapleMap> maps = new ConcurrentHashMap<>();

    public MapManager(EventInstanceManager eim, int world, int channel) {
        this.world = world;
        this.channel = channel;
        this.event = eim;
    }

    public MapleMap resetMap(int mapid) {
        maps.remove(mapid);
        return getMap(mapid);
    }

    private MapleMap loadMapFromWz(int mapid) {
        return MapFactory.loadMapFromWz(mapid, world, channel, event);
    }

    public MapleMap getMap(int mapid) {
        return maps.computeIfAbsent(mapid, this::loadMapFromWz);
    }

    public MapleMap getMapByLifeId(int lifeId) {
        String mapId = MapFactory.getMapIdByLifeId(lifeId);
        return mapId == null ? null : getMap(Integer.parseInt(mapId));
    }

    public MapleMap getDisposableMap(int mapid) {
        return loadMapFromWz(mapid); // deliberately not cached
    }

    public boolean isMapLoaded(int mapId) {
        return maps.containsKey(mapId);
    }

    public Map<Integer, MapleMap> getMaps() {
        return new HashMap<>(maps);
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
