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
package de.mhus.osgi.api.util;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import de.mhus.osgi.api.MOsgi;

public abstract class AbstractServiceTracker<T> {

    private BundleContext context;
    protected Class<T> clazz;
    private ServiceTrackerCustomizer<T, T> customizer =
            new ServiceTrackerCustomizer<T, T>() {

                @Override
                public T addingService(ServiceReference<T> reference) {
                    T service = context.getService(reference);
                    if (service != null && service instanceof ReferenceInject)
                        ((ReferenceInject) service).setReference(reference);
                    AbstractServiceTracker.this.addService(reference, service);
                    return service;
                }

                @Override
                public void modifiedService(ServiceReference<T> reference, T service) {
                    if (service != null && service instanceof ReferenceInject)
                        ((ReferenceInject) service).setReference(reference);
                    AbstractServiceTracker.this.modifyService(reference, service);
                }

                @Override
                public void removedService(ServiceReference<T> reference, T service) {
                    AbstractServiceTracker.this.removeService(reference, service);
                }
            };
    private ServiceTracker<T, T> tracker;

    public AbstractServiceTracker(Class<T> clazz) {
        this(null, clazz);
    }

    protected void modifyService(ServiceReference<T> reference, T service) {
        removeService(reference, service);
        addService(reference, service);
    }

    protected abstract void removeService(ServiceReference<T> reference, T service);

    protected abstract void addService(ServiceReference<T> reference, T service);

    public AbstractServiceTracker(BundleContext context, Class<T> clazz) {
        if (context == null) {
            try {
                context = MOsgi.getBundleContext();
            } catch (Throwable t) {
            }
        }
        this.context = context;
        this.clazz = clazz;
    }

    public AbstractServiceTracker<T> start(ComponentContext ctx) {
        if (ctx != null) return start(ctx.getBundleContext());
        else return start();
    }

    public AbstractServiceTracker<T> start(BundleContext ctx) {
        if (ctx != null) context = ctx;
        return start();
    }

    public AbstractServiceTracker<T> start() {
        if (tracker != null) return this;
        tracker = new ServiceTracker<>(context, clazz, customizer);
        tracker.open(true);
        return this;
    }

    public void stop() {
        if (tracker == null) return;
        tracker.close();
        tracker = null;
    }

    public boolean isRunning() {
        return tracker != null;
    }

    public BundleContext getBundleContext() {
        return context;
    }

    public void setBundleContext(BundleContext context) {
        this.context = context;
    }
}
