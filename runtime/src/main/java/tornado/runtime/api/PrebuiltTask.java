/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.runtime.api;

import java.util.Objects;
import tornado.common.SchedulableTask;
import tornado.common.TornadoDevice;
import tornado.common.enums.Access;
import tornado.meta.Meta;
import tornado.meta.domain.DomainTree;

public class PrebuiltTask implements SchedulableTask {

    protected final String id;
    protected final String entryPoint;
    protected final String filename;
    protected final Object[] args;
    protected final Access[] argumentsAccess;
    protected final Meta meta;

    protected PrebuiltTask(String id, String entryPoint, String filename, Object[] args, Access[] access, TornadoDevice device, DomainTree domain) {
        this.id = id;
        this.entryPoint = entryPoint;
        this.filename = filename;
        this.args = args;
        this.argumentsAccess = access;
        meta = device.createMeta(access.length);
        for (int i = 0; i < access.length; i++) {
            meta.getArgumentsAccess()[i] = access[i];
        }
        meta.addProvider(TornadoDevice.class, device);
        meta.setDomain(domain);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("task: ").append(entryPoint).append("()\n");
        for (int i = 0; i < args.length; i++) {
            sb.append(String.format("arg  : [%s] %s\n", argumentsAccess[i], args[i]));
        }

        sb.append("meta : ").append(meta.toString());

        return sb.toString();
    }

    @Override
    public Object[] getArguments() {
        return args;
    }

    @Override
    public Access[] getArgumentsAccess() {
        return argumentsAccess;
    }

    @Override
    public Meta meta() {
        return meta;
    }

    @Override
    public SchedulableTask mapTo(TornadoDevice mapping) {

        return this;
    }

    @Override
    public TornadoDevice getDeviceMapping() {
        return (meta.hasProvider(TornadoDevice.class)) ? meta
                .getProvider(TornadoDevice.class) : null;
    }

    @Override
    public String getName() {
        return "task - " + entryPoint;
    }

    public String getFilename() {
        return filename;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CompilableTask) {
            CompilableTask other = (CompilableTask) obj;
            return getId().equals(other.getId());
        }

        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(this.id);
        hash = 71 * hash + Objects.hashCode(this.entryPoint);
        hash = 71 * hash + Objects.hashCode(this.filename);
        return hash;
    }
}
