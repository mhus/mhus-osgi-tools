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
package de.mhus.lib.mutable;

import java.io.File;

import org.osgi.service.cm.ConfigurationAdmin;

import de.mhus.lib.core.MApi;
import de.mhus.lib.core.M;
import de.mhus.lib.core.MString;
import de.mhus.lib.core.mapi.IApiInternal;
import de.mhus.lib.core.mapi.MCfgManager;
import de.mhus.lib.core.node.INode;
import de.mhus.osgi.api.MOsgi;

public class KarafCfgManager extends MCfgManager {

    public KarafCfgManager(IApiInternal internal) {
        super(internal);
    }

    @Override
    protected void initialConfiguration() {
        CentralMhusCfgProvider provider = new CentralMhusCfgProvider();
        registerCfgProvider(provider);

        // load all from etc
        ConfigurationAdmin admin = MOsgi.getServiceOrNull(ConfigurationAdmin.class);
        if (admin == null) {
            MApi.dirtyLogError("ConfigurationAdmin is null");
        } else {
            for (File file : new File("etc").listFiles()) {
                if (file.isFile() && file.getName().endsWith(".cfg")) {
                    String pid = MString.beforeLastIndex(file.getName(), '.');
                    update(pid);
                }
            }
        }

        // prepare system config for default
        INode system = provider.getConfig();
        if (!system.containsKey(M.PROP_LOG_FACTORY_CLASS)) {
            system.setString(M.PROP_LOG_FACTORY_CLASS, "de.mhus.lib.logging.Log4JFactory");
        }
        if (!system.containsKey(M.PROP_LOG_CONSOLE_REDIRECT)) {
            system.setString(M.PROP_LOG_CONSOLE_REDIRECT, "false");
        }
    }

    public void update(String pid) {
        if (!pid.equals(M.CFG_SYSTEM)) {
            MApi.dirtyLogInfo("KarafCfgManager::Register PID", pid);
            registerCfgProvider(new KarfConfigProvider(pid));
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void reload(Object owner) {
        if (owner == null) return;

        if (owner instanceof String) {
            update(owner.toString());
        } else {
            Class clazz = owner.getClass();
            if (owner instanceof Class) clazz = (Class) owner;
            update(MOsgi.findServicePid(clazz));
        }
    }
}
