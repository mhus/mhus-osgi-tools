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
import java.io.Serializable;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import de.mhus.lib.core.MApi;
import de.mhus.lib.core.MFile;
import de.mhus.lib.core.MHousekeeper;
import de.mhus.lib.core.aaa.Aaa;
import de.mhus.lib.core.cache.CacheConfig;
import de.mhus.lib.core.cache.ICache;
import de.mhus.lib.core.cache.ICacheService;
import de.mhus.lib.core.cfg.CfgProvider;
import de.mhus.lib.core.logging.MLogFactory;
import de.mhus.lib.core.logging.MLogUtil;
import de.mhus.lib.core.mapi.ApiInitialize;
import de.mhus.lib.core.mapi.DefaultMApi;
import de.mhus.lib.core.mapi.IApi;
import de.mhus.lib.core.mapi.IApiInternal;
import de.mhus.lib.core.mapi.MCfgManager;
import de.mhus.lib.core.mapi.SingleMLogInstanceFactory;
import de.mhus.lib.core.node.INode;
import de.mhus.lib.logging.JavaLoggerFactory;
import de.mhus.osgi.api.MOsgi;

/** @author mikehummel */
public class KarafMApiImpl extends DefaultMApi implements IApi, ApiInitialize, IApiInternal {

    private boolean fullTrace = false;
    private KarafHousekeeper housekeeper;
    private ICache<String, Container> apiCache;
    private boolean CFG_USE_LOOKUP_CACHE = true;
    private BundleContext context;
    private int CFG_LOOKUP_CACHE_SIZE = 1000;

    @Override
    protected MCfgManager createMCfgManager() {
        return new KarafCfgManager(this);
    }

    @Override
    public void doInitialize(ClassLoader coreLoader) {
        baseDir = new File(System.getProperty("karaf.base", "."));
        logFactory = new JavaLoggerFactory();
        mlogFactory = new SingleMLogInstanceFactory();
        base.addObject(MLogFactory.class, null, mlogFactory);

        getCfgManager(); // init
        //		TimerFactoryImpl.indoCheckTimers();

        try {
            housekeeper = new KarafHousekeeper();
            base.addObject(MHousekeeper.class, null, housekeeper);
        } catch (Throwable t) {
            System.out.println("Can't initialize housekeeper base: " + t);
        }
    }

    @Override
    public boolean isTrace(String name) {
        return fullTrace || super.isTrace(name);
    }

    public void setFullTrace(boolean trace) {
        fullTrace = trace;
    }

    public void setTrace(String name) {
        logTrace.add(name);
    }

    public void clearTrace() {
        logTrace.clear();
    }

    public String[] getTraceNames() {
        return logTrace.toArray(new String[logTrace.size()]);
    }

    public boolean isFullTrace() {
        return fullTrace;
    }

    @Override
    public Set<String> getLogTrace() {
        return logTrace;
    }

    @Override
    public void setBaseDir(File file) {
        baseDir = file;
        baseDir.mkdirs();
    }

    @Override
    public File getFile(MApi.SCOPE scope, String dir) {
        dir = MFile.normalizePath(dir);
        switch (scope) {
            case DATA:
                return new File(baseDir, "data/" + dir);
            case DEPLOY:
                return new File(baseDir, "deploy/" + dir);
            case ETC:
                return new File(baseDir, "etc/" + dir);
            case LOG:
                return new File(baseDir, "data/log/" + dir);
            case TMP:
                return new File(baseDir, "data/tmp" + dir);
            default:
                break;
        }
        return new File(baseDir, "data" + File.separator + "mhus" + File.separator + dir);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, D extends T> T lookup(Class<T> ifc, Class<D> def) {

        if (ifc == null) return null;

        T result = null;

        if (def == null && ifc.isInterface()) { // only interfaces can be OSGi services

            if (apiCache == null && CFG_USE_LOOKUP_CACHE) {
                try {
                    MApi.dirtyLogDebug("KarafMApiImpl:create cache");
                    ICacheService cacheService = MOsgi.getService(ICacheService.class);
                    apiCache =
                            cacheService.createCache(
                                    this,
                                    "lookup",
                                    String.class,
                                    Container.class,
                                    new CacheConfig().setHeapSize(CFG_LOOKUP_CACHE_SIZE));
                } catch (Throwable e) {
                    MApi.dirtyLogDebug("KarafMApiImpl:lookup", e.toString());
                }
            }

            Container cached = null;
            if (apiCache != null) {
                try {
                    cached = apiCache.get(ifc.getCanonicalName());
                    if (cached != null) {
                        Bundle bundle = MOsgi.getBundleOrNull(cached.bundleId);
                        if (bundle == null
                                || bundle.getState() != Bundle.ACTIVE
                                || cached.modified != bundle.getLastModified()) {
                            cleanupLookup(ifc);
                            cached = null;
                        }
                    }
                    if (cached != null) {
                        try {
                            @SuppressWarnings("unused")
                            T obj = (T) cached.api;
                        } catch (ClassCastException e) {
                            MApi.dirtyLogInfo(
                                    "KarafMAPiImpl.lookup: Can't cast from cache into ifc: try to reload");
                            cleanupLookup(ifc);
                            cached = null;
                        }
                    }
                } catch (Throwable t) {
                    MApi.dirtyLogDebug("KarafMApiImpl:lookup", t.toString(), ifc);
                }
            }

            if (cached == null) {
                if (context == null) {
                    Bundle bundle = FrameworkUtil.getBundle(KarafMApiImpl.class);
                    if (bundle != null) {
                        context = bundle.getBundleContext();
                    }
                }
                if (context != null) {
                    String filter = null;
                    INode cfg = MApi.getCfg(ifc);
                    if (cfg != null) {
                        filter = cfg.getString("mhusApiBaseFilter", null);
                    }
                    ServiceReference<T> ref = null;
                    try {
                        ServiceReference<?>[] refs =
                                context.getServiceReferences(ifc.getCanonicalName(), filter);
                        if (refs != null) {
                            if (refs.length > 0) ref = (ServiceReference<T>) refs[0];
                            if (refs.length > 1) {
                                for (ServiceReference<?> refx : refs)
                                    MApi.dirtyLogDebug(
                                            "more then one service found",
                                            ifc,
                                            refx.getBundle().getSymbolicName(),
                                            refx.getBundle().getBundleId(),
                                            context.getService(refx).getClass().getCanonicalName());
                            }
                        }
                        //                        Collection<ServiceReference<T>> refs =
                        //                                context.getServiceReferences(ifc, filter);
                        //                        Iterator<ServiceReference<T>> refsIterator =
                        // refs.iterator();
                        //
                        //                        if (refsIterator.hasNext()) ref =
                        // refs.iterator().next();
                        //                        if (refsIterator.hasNext())
                        //                            MApi.dirtyLogDebug(
                        //                                    "more then one service found for
                        // singleton", ifc, filter);
                    } catch (InvalidSyntaxException e) {
                        MApi.dirtyLogError(ifc, filter, e);
                    }
                    if (ref != null) {
                        if (ref.getBundle().getState() != Bundle.ACTIVE) {
                            MLogUtil.log()
                                    .d(
                                            "KarafBase",
                                            "found in bundle but not jet active",
                                            ifc,
                                            context.getBundle().getSymbolicName());
                            return null;
                        }
                        T obj = null;
                        try {
                            obj = ref.getBundle().getBundleContext().getService(ref);
                            //                              obj = context.getService(ref);
                        } catch (Throwable t) {
                            System.out.println("Error lookup for: " + ref);
                            t.printStackTrace();
                        }
                        if (obj != null) {
                            MApi.dirtyLogDebug(
                                    "KarafBase", "loaded from OSGi", ifc, apiCache != null);
                            try {
                                cached = new Container();
                                cached.bundleId = ref.getBundle().getBundleId();
                                cached.bundleName = ref.getBundle().getSymbolicName();
                                cached.modified = ref.getBundle().getLastModified();
                                cached.api = obj;
                                cached.ifc = ifc;
                                cached.filter = filter;
                                if (apiCache != null) apiCache.put(ifc.getCanonicalName(), cached);
                            } catch (java.lang.IllegalStateException e) {
                                MApi.dirtyLogDebug("KarafBase: close Cache", e);
                                try {
                                    apiCache.close();
                                } catch (Throwable t) {
                                }
                                apiCache = null;
                            } catch (Throwable t) {
                                MApi.dirtyLogDebug("KarafBase", t);
                            }
                        }
                    }
                }
            }
            if (cached != null) result = (T) cached.api;
        }

        if (result == null) result = super.lookup(ifc, def);

        if (result != null) {
            Aaa.checkPermission(result);
        }

        return result;
    }

    @Override
    public <T> void cleanupLookup(Class<T> ifc) {
        if (ifc == null || apiCache == null) return;
        apiCache.remove(ifc.getCanonicalName());
    }

    public static class Container implements Serializable {

        public String filter;
        private static final long serialVersionUID = 1L;
        public long modified;
        public Class<?> ifc;
        public Object api;
        public String bundleName;
        public long bundleId;
    }

    @Override
    public void updateSystemCfg(CfgProvider system) {
        super.updateSystemCfg(system);
        if (system == null) return;
        CFG_USE_LOOKUP_CACHE =
                system.getConfig().getBoolean("lookupCacheEnabled", CFG_USE_LOOKUP_CACHE);
        CFG_LOOKUP_CACHE_SIZE = system.getConfig().getInt("lookupCacheSize", CFG_LOOKUP_CACHE_SIZE);
    }
}
