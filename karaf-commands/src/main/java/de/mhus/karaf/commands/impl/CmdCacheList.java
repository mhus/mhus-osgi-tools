/**
 * Copyright (C) 2018 Mike Hummel (mh@mhus.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mhus.karaf.commands.impl;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;

import de.mhus.lib.core.cache.ICache;
import de.mhus.lib.core.cache.ICacheService;
import de.mhus.lib.core.cache.ICacheStatistics;
import de.mhus.lib.core.console.ConsoleTable;
import de.mhus.osgi.api.karaf.AbstractCmd;
import de.mhus.osgi.services.cache.LocalCacheWrapper;

@Command(
        scope = "mhus",
        name = "cache-list",
        description = "Cache Control Service - List all caches")
@Service
public class CmdCacheList extends AbstractCmd {

    @Reference ICacheService service;

    @Override
    public Object execute2() throws Exception {

        if (service == null) {
            System.out.println("CacheService not found, exiting");
            return null;
        }

        ConsoleTable table = new ConsoleTable(tblOpt);
        table.setHeaderValues(
                "Name", "Size", "Bytes", "Gets", "Puts", "Removed", "Hits", "Missed", "AvrRemove");

        for (String name : service.getCacheNames()) {
            ICache<Object, Object> cache = service.getCache(name);
            if (cache != null) {
                ICacheStatistics cacheStatistics =
                        ((LocalCacheWrapper<?, ?>) cache).getCacheStatistics();
                table.addRowValues(
                        name,
                        cacheStatistics.getCacheSize(),
                        cacheStatistics.getOccupiedByteSize(),
                        cacheStatistics.getCacheGets(),
                        cacheStatistics.getCachePuts(),
                        cacheStatistics.getCacheRemovals(),
                        cacheStatistics.getCacheHits(),
                        cacheStatistics.getCacheMisses(),
                        cacheStatistics.getAverageRemoveTime());
            }
        }
        table.print();

        return null;
    }
}
