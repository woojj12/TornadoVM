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
package tornado.graal.phases;

import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.util.Providers;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.meta.Meta;
import tornado.common.TornadoDevice;

public class TornadoHighTierContext extends HighTierContext {

    protected final ResolvedJavaMethod method;
    protected final Object[] args;
    protected final Meta meta;
    protected final boolean isKernel;

    public TornadoHighTierContext(
            Providers providers,
            PhaseSuite<HighTierContext> graphBuilderSuite,
            OptimisticOptimizations optimisticOpts,
            ResolvedJavaMethod method,
            Object[] args,
            Meta meta,
            boolean isKernel) {
        super(providers, graphBuilderSuite, optimisticOpts);
        this.method = method;
        this.args = args;
        this.meta = meta;
        this.isKernel = isKernel;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public Object[] getArgs() {
        return args;
    }

    public boolean hasArgs() {
        return args != null;
    }

    public Object getArg(int index) {
        return args[index];
    }

    public int getNumArgs() {
        return (hasArgs()) ? args.length : 0;
    }

    public Meta getMeta() {
        return meta;
    }

    public boolean hasDeviceMapping() {
        return meta != null && meta.hasProvider(TornadoDevice.class);
    }

    public TornadoDevice getDeviceMapping() {
        return meta.getProvider(TornadoDevice.class);
    }

    public boolean hasMeta() {
        return meta != null;
    }

    public boolean isKernel() {
        return isKernel;
    }

}
