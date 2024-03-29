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
package de.mhus.karaf.commands.mhus;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;

import de.mhus.lib.core.logging.ITracer;
import de.mhus.osgi.api.karaf.AbstractCmd;
import io.opentracing.Scope;

@Command(scope = "mhus", name = "tracer-test", description = "Sample tracer span")
@Service
public class CmdTracerTest extends AbstractCmd {

    @Override
    public Object execute2() throws Exception {

        try (Scope scope = ITracer.get().enter("test")) {
            ITracer.get().activate("test");
            log().i("test");
            //            ITracer.get().current().finish();
        }
        return null;
    }
}
