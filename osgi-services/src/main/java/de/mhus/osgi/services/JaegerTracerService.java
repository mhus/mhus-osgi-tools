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
package de.mhus.osgi.services;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.ops4j.pax.logging.PaxLogger;
import org.ops4j.pax.logging.spi.PaxLoggingEvent;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;

import de.mhus.lib.core.MApi;
import de.mhus.lib.core.MString;
import de.mhus.lib.core.cfg.CfgString;
import de.mhus.lib.core.logging.DefaultTracer;
import de.mhus.lib.core.logging.ITracer;
import de.mhus.lib.core.node.INode;
import de.mhus.lib.core.service.IdentUtil;
import de.mhus.osgi.api.MOsgi;
import de.mhus.osgi.api.karaf.LogServiceTracker;
import de.mhus.osgi.api.karaf.LogServiceTracker.LOG_LEVEL;
import io.jaegertracing.Configuration;
import io.jaegertracing.internal.JaegerTracer;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.util.GlobalTracer;

// https://www.scalyr.com/blog/jaeger-tracing-tutorial/
// https://opentracing.io/guides/java/inject-extract/

@Component(service = ITracer.class)
public class JaegerTracerService extends DefaultTracer {

    private CfgString CFG_LOG_LEVEL =
            new CfgString(JaegerTracerService.class, "logLevel", "DEBUG")
                    .updateAction(v -> updateLogLevel());

    private LogServiceTracker tracker;

    private int logLevel;

    private boolean initService;

    private static String[] JAEGER_ENV =
            new String[] {
                "JAEGER_SAMPLER_TYPE",
                "JAEGER_SAMPLER_PARAM",
                "JAEGER_SAMPLER_MANAGER_HOST_PORT",
                "JAEGER_REPORTER_LOG_SPANS",
                "JAEGER_AGENT_HOST",
                "JAEGER_AGENT_PORT",
                "JAEGER_REPORTER_FLUSH_INTERVAL",
                "JAEGER_REPORTER_MAX_QUEUE_SIZE",
                "JAEGER_SERVICE_NAME"
            };

    @Activate
    public void doActivate(ComponentContext ctx) {
        MOsgi.touchConfig(JaegerTracerService.class);
        updateLogLevel();
        try {
            initService = true;
            update();
        } finally {
            initService = false;
        }

        tracker = new LogServiceTracker(ctx.getBundleContext(), e -> logEvent(e));
        tracker.open();
    }

    @Modified
    public void doModified(ComponentContext ctx) {
        MApi.get().getCfgManager().reload(JaegerTracerService.class);
        update();
    }

    @Deactivate
    public void doDeactivate(ComponentContext ctx) {
        if (tracker != null) tracker.close();
    }

    private void updateLogLevel() {
        try {
            LOG_LEVEL level =
                    LogServiceTracker.LOG_LEVEL.valueOf(CFG_LOG_LEVEL.value().toUpperCase());
            logLevel = level.toInt();
        } catch (Throwable t) {
            log().d(t);
            logLevel = LogServiceTracker.DEBUG_INT;
        }
    }

    private synchronized void update() {
        logi("Update jaeger tracer");

        // prepare env
        INode cfg = MApi.getCfg(JaegerTracerService.class);
        for (String key : JAEGER_ENV) {
            if (cfg != null && MString.isSet(cfg.getString(key, null)))
                System.setProperty(key, cfg.getString(key, null));
            else if (System.getenv(key) != null) System.setProperty(key, System.getenv(key));
        }

        Configuration.SamplerConfiguration samplerConfig =
                Configuration.SamplerConfiguration.fromEnv();
        Configuration.ReporterConfiguration reporterConfig =
                Configuration.ReporterConfiguration.fromEnv().withLogSpans(true);
        Configuration config =
                new Configuration(
                                (MString.isSetTrim(System.getProperty("JAEGER_SERVICE_NAME"))
                                                ? System.getProperty("JAEGER_SERVICE_NAME") + "@"
                                                : "")
                                        + IdentUtil.getServiceIdent().toString())
                        .withSampler(samplerConfig)
                        .withReporter(reporterConfig);

        if (GlobalTracer.isRegistered()) {
            try {
                Field field = GlobalTracer.class.getDeclaredField("tracer");
                if (!field.canAccess(null)) field.setAccessible(true);
                Tracer tracer = (Tracer) field.get(null);
                if (tracer != null && tracer instanceof JaegerTracer)
                    ((JaegerTracer) tracer).close();

                field.set(null, NoopTracerFactory.create());

            } catch (Throwable t) {
                logi(t);
            }
        }

        JaegerTracerFactory factory = new JaegerTracerFactory(config, reporterConfig);
        setTracerFactory(factory);

        reset();
    }

    /**
     * If service is initialized the usage of log() will cause in a loop. Therefore it will print
     * the log messages instead of logging.
     *
     * @param msg
     */
    private void logi(Object msg) {
        if (!initService) {
            log().i(null, msg);
            return;
        }
        if (msg != null && msg instanceof Throwable) {
            ((Throwable) msg).printStackTrace();
        } else System.out.println(msg);
    }

    private void logEvent(PaxLoggingEvent e) {
        Span span = current();
        if (span == null) return;
        if (e.getLevel().toInt() > logLevel) return;

        Map<String, String> fields = new HashMap<>();
        fields.put("level", e.getLevel().toString());
        fields.put("message", e.getMessage());
        fields.put("logger", e.getLoggerName());
        fields.put("thread", e.getThreadName());
        //        fields.put("loggerClass",e.getFQNOfLoggerClass());
        String[] stack = e.getThrowableStrRep();
        if (stack != null) {
            fields.put("stack", MString.join(stack, '\n'));
            span.setTag("error", true);
            fields.put("event", "error");
            fields.put("error.kind", "exception");
        }
        if (e.getLevel().toInt() >= PaxLogger.LEVEL_ERROR) {
            span.setTag("error", true);
            fields.put("event", "error");
        }
        // PaxLocationInfo location = e.getLocationInformation();
        // fields.put("location",
        //		location.getClassName() + "." + location.getMethodName() + "(" + location.getFileName()
        // + ":" + location.getLineNumber() + ")" );
        span.log(fields);
    }
}
