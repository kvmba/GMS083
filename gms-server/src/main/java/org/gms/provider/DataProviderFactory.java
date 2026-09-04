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
package org.gms.provider;

import org.gms.provider.wz.WZFiles;
import org.gms.provider.wz.XMLWZFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public class DataProviderFactory {

    // Building a DataProvider walks the whole WZ directory tree to index every file, which
    // measured ~30ms per construction on the v83 tree (Map.wz is ~5.7k files). Callers used to
    // build one per call site invocation (some of them per request), so the same tree was
    // re-walked over and over. Providers are immutable after construction, so one per WZFiles
    // entry is enough for the whole process.
    private static final Map<WZFiles, DataProvider> PROVIDERS = new EnumMap<>(WZFiles.class);

    private static DataProvider getWZ(Path in) {
        return new XMLWZFile(in);
    }

    public static synchronized DataProvider getDataProvider(WZFiles in) {
        DataProvider cached = PROVIDERS.get(in);
        if (cached != null) {
            return cached;
        }
        Path basePath = in.getBaseFile();
        Path languagePath = in.getLanguageFile();
        DataProvider baseProvider = getWZ(basePath);
        DataProvider provider;
        if (!Files.exists(languagePath) || languagePath.equals(basePath)) {
            provider = baseProvider;
        } else {
            // 中文 WZ 只维护被本地化过的文件，缺失的文件继续回退到原始 WZ。
            provider = new LocalizedDataProvider(getWZ(languagePath), baseProvider);
        }
        PROVIDERS.put(in, provider);
        return provider;
    }
}
