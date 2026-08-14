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
package org.gms.net.server.services;

import org.gms.config.GameConfig;
import org.gms.net.server.Server;
import org.gms.server.TimerManager;
import org.gms.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Ronan
 */
public abstract class BaseScheduler {
    private int idleProcs = 0;
    private final List<SchedulerListener> listeners = new LinkedList<>();
    private final List<Lock> externalLocks = new LinkedList<>();
    private final Map<Object, Pair<Runnable, Long>> registeredEntries = new HashMap<>();

    private ScheduledFuture<?> schedulerTask = null;
    private final Lock schedulerLock = new ReentrantLock(true);

    protected BaseScheduler() {
    }

    // NOTE: practice EXTREME caution when adding external locks to the scheduler system, if you don't know what you're doing DON'T USE THIS.
    protected BaseScheduler(List<Lock> extLocks) {
        externalLocks.addAll(extLocks);
    }

    protected void addListener(SchedulerListener listener) {
        listeners.add(listener);
    }

    private void lockScheduler() {
        externalLocks.forEach(Lock::lock);
        schedulerLock.lock();
    }

    private void unlockScheduler() {
        externalLocks.forEach(Lock::unlock);
        schedulerLock.unlock();
    }

    private void runBaseSchedule() {
        long timeNow = Server.getInstance().getCurrentTime();
        List<Object> toRemove = new LinkedList<>();
        List<Runnable> toRun = new LinkedList<>();

        lockScheduler();
        try {
            if (registeredEntries.isEmpty()) {
                idleProcs++;

                if (idleProcs >= GameConfig.getServerInt("mob_status_monitor_idle")) {
                    if (schedulerTask != null) {
                        schedulerTask.cancel(false);
                        schedulerTask = null;
                    }
                }

                return;
            }

            idleProcs = 0;
            // 锁内认领过期条目,与 interruptEntry 竞争时保证每条动作只会被一方执行一次
            for (Entry<Object, Pair<Runnable, Long>> rmd : new ArrayList<>(registeredEntries.entrySet())) {
                Pair<Runnable, Long> r = rmd.getValue();

                if (r.getRight() < timeNow) {
                    if (registeredEntries.remove(rmd.getKey()) != null) {
                        toRemove.add(rmd.getKey());
                        toRun.add(r.getLeft());
                    }
                }
            }
        } finally {
            unlockScheduler();
        }

        for (Runnable r : toRun) {
            r.run();  // runs the scheduled action
        }

        if (!toRemove.isEmpty()) {
            dispatchRemovedEntries(toRemove, true);
        }
    }

    protected void registerEntry(Object key, Runnable removalAction, long duration) {
        lockScheduler();
        try {
            idleProcs = 0;
            if (schedulerTask == null) {
                schedulerTask = TimerManager.getInstance().register(this::runBaseSchedule, GameConfig.getServerLong("mob_status_monitor_proc"), GameConfig.getServerLong("mob_status_monitor_proc"));
            }

            registeredEntries.put(key, new Pair<>(removalAction, Server.getInstance().getCurrentTime() + duration));
        } finally {
            unlockScheduler();
        }
    }

    protected void interruptEntry(Object key) {
        interruptEntry(key, true);
    }

    protected void interruptEntry(Object key, boolean executeBeforeStop) {
        Runnable toRun = null;

        lockScheduler();
        try {
            Pair<Runnable, Long> rm = registeredEntries.remove(key);
            if (rm != null) {
                toRun = rm.getLeft();
            }
        } finally {
            unlockScheduler();
        }

        if (toRun != null && executeBeforeStop) {
            toRun.run();
        }

        dispatchRemovedEntries(Collections.singletonList(key), false);
    }

    private void dispatchRemovedEntries(List<Object> toRemove, boolean fromUpdate) {
        for (SchedulerListener listener : listeners.toArray(new SchedulerListener[listeners.size()])) {
            listener.removedScheduledEntries(toRemove, fromUpdate);
        }
    }

    public void dispose() {
        lockScheduler();
        try {
            if (schedulerTask != null) {
                schedulerTask.cancel(false);
                schedulerTask = null;
            }

            listeners.clear();
            registeredEntries.clear();
        } finally {
            unlockScheduler();
            externalLocks.clear();
        }
    }
}
