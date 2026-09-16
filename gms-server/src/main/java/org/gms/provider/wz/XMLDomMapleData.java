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
package org.gms.provider.wz;

import org.gms.provider.Data;
import org.gms.provider.DataEntity;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

/**
 * @deprecated DOM parsing was replaced by {@link XMLWZData} (Woodstox StAX, faster and leaner).
 * This class is kept only as a binary-compatibility shim for external plugins — notably
 * SoloMapling — that still do {@code new XMLDomMapleData(fis, dir)} to parse a standalone
 * WZ XML file. It delegates to {@link XMLWZData#parse(FileInputStream)} and ignores the
 * {@code imageDataDir} argument (the old implementation only ever stored it, never read it,
 * so lookups and values are unchanged). New code should call {@code XMLWZData.parse(fis)}
 * directly; this shim can be removed once plugins migrate.
 */
@Deprecated
public class XMLDomMapleData implements Data {
    private final Data delegate;

    public XMLDomMapleData(FileInputStream fis, Path imageDataDir) {
        this.delegate = XMLWZData.parse(fis);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public DataType getType() {
        return delegate.getType();
    }

    @Override
    public List<Data> getChildren() {
        return delegate.getChildren();
    }

    @Override
    public Data getChildByPath(String path) {
        return delegate.getChildByPath(path);
    }

    @Override
    public Object getData() {
        return delegate.getData();
    }

    @Override
    public String getAttributeValue(String name) {
        return delegate.getAttributeValue(name);
    }

    @Override
    public DataEntity getParent() {
        return delegate.getParent();
    }

    @Override
    public Iterator<Data> iterator() {
        return delegate.iterator();
    }
}
