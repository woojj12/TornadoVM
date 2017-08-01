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
package tornado.graal.compiler;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import tornado.graal.phases.TornadoApiReplacement;
import tornado.graal.phases.TornadoDataflowAnalysis;
import tornado.graal.phases.TornadoInliningPolicy;
import tornado.graal.phases.TornadoSketchTierContext;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.core.phases.HighTier.Options.Inline;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

public class TornadoSketchTier extends PhaseSuite<TornadoSketchTierContext> {

    protected final CustomCanonicalizer customCanonicalizer;

    public CustomCanonicalizer getCustomCanonicalizer() {
        return customCanonicalizer;
    }

    public TornadoSketchTier(OptionValues options, CustomCanonicalizer customCanonicalizer) {
        this.customCanonicalizer = customCanonicalizer;

        final CanonicalizerPhase canonicalizer = new CanonicalizerPhase(customCanonicalizer);

        if (ImmutableCode.getValue(options)) {
            canonicalizer.disableReadCanonicalization();
        }
        appendPhase(canonicalizer);

        if (Inline.getValue(options)) {
            appendPhase(new InliningPhase(new TornadoInliningPolicy(), canonicalizer));
            appendPhase(new DeadCodeEliminationPhase(Optional));

            if (ConditionalElimination.getValue(options)) {
                appendPhase(canonicalizer);
                appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            }
        }

        appendPhase(new TornadoApiReplacement());
        appendPhase(new TornadoDataflowAnalysis());
    }
}