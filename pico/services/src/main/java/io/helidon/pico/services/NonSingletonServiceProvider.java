/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.pico.services;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.pico.spi.ActivationLog;
import io.helidon.pico.spi.ActivationPhase;
import io.helidon.pico.spi.InjectionPointInfo;
import io.helidon.pico.spi.PicoServices;
import io.helidon.pico.spi.Services;
import io.helidon.pico.spi.impl.CompositeActivationLog;
import io.helidon.pico.spi.impl.DefaultActivationLog;
import io.helidon.pico.spi.impl.DefaultActivationResult;

/**
 * A provider that represents a provider-like, non-singleton service.
 *
 * @param <T> the types that are produced
 */
class NonSingletonServiceProvider<T> extends io.helidon.pico.spi.ext.AbstractServiceProvider<T> {

    private static final boolean WANT_LOGGING = false;

    private NonSingletonServiceProvider(io.helidon.pico.spi.ext.AbstractServiceProvider<T> parent) {
        setPicoServices(parent.getPicoServices());
        setServiceInfo(parent.getServiceInfo());
        setDependencies(parent.getDependencies());
    }

    static <T> T createAndActivate(io.helidon.pico.spi.ext.AbstractServiceProvider<T> parent,
                                   InjectionPointInfo optCtx) {
        NonSingletonServiceProvider<T> serviceProvider = new NonSingletonServiceProvider<>(parent);

        final PicoServices picoServices = serviceProvider.getPicoServices();
        final Optional<ActivationLog> log = picoServices.getActivationLog().isPresent()
                ? Optional.of(new CompositeActivationLog(picoServices.getActivationLog().get(),
                     DefaultActivationLog.privateCapturedAndRetainedLog(DefaultActivationLog.NAME_LOCAL, null)))
                : Optional.empty();
        final Services services = picoServices.getServices().getContextualServices(optCtx);
        final ActivationPhase ultimateTargetPhase = ActivationPhase.ACTIVE;

        // if we are here then we are not yet at the ultimate target phase, and we either have to activate or deactivate...
        if (WANT_LOGGING) {
            DefaultActivationLog.Entry entry = serviceProvider.toLogEntry(ActivationLog.Event.STARTING, ultimateTargetPhase);
            entry.setFinishingActivationPhase(ActivationPhase.ACTIVATION_STARTING);
            log.ifPresent(activationLog -> activationLog.recordActivationEvent(entry));
        }

        final DefaultActivationResult<T> settableResult =
                serviceProvider.createResultPlaceholder(services, log, ultimateTargetPhase);
        if (WANT_LOGGING) {
            serviceProvider.recordActivationEvent(ActivationLog.Event.STARTING,
                    serviceProvider.getCurrentActivationPhase(), settableResult);
            serviceProvider.recordActivationEvent(ActivationLog.Event.STARTING,
                    ActivationPhase.GATHERING_DEPENDENCIES, settableResult);
        }

        Map<String, io.helidon.pico.spi.ext.InjectionPlan<Object>> plans = parent.getOrCreateInjectionPlan(false);
        Map<String, Object> deps = parent.resolveDependencies(plans);
        if (WANT_LOGGING) {
            serviceProvider.recordActivationEvent(ActivationLog.Event.FINISHED,
                    ActivationPhase.GATHERING_DEPENDENCIES, settableResult);
            serviceProvider.recordActivationEvent(ActivationLog.Event.STARTING,
                    ActivationPhase.CONSTRUCTING, settableResult);
        }
        T instance = parent.createServiceProvider(deps);

        if (WANT_LOGGING) {
            serviceProvider.recordActivationEvent(ActivationLog.Event.FINISHED, ActivationPhase.CONSTRUCTING, settableResult);
        }

        if (Objects.nonNull(instance)) {
            if (WANT_LOGGING) {
                serviceProvider.recordActivationEvent(ActivationLog.Event.STARTING, ActivationPhase.INJECTING, settableResult);
            }

            List<String> serviceTypeOrdering = Objects.requireNonNull(parent.getServiceTypeInjectionOrder());
            LinkedHashSet<String> injections = new LinkedHashSet<>();
            serviceTypeOrdering.forEach((forServiceType) -> {
                parent.doInjectingFields(instance, deps, injections, forServiceType);
                parent.doInjectingMethods(instance, deps, injections, forServiceType);
            });

            if (WANT_LOGGING) {
                serviceProvider.recordActivationEvent(ActivationLog.Event.FINISHED, ActivationPhase.INJECTING, settableResult);
            }
        }

        if (WANT_LOGGING) {
            serviceProvider.recordActivationEvent(ActivationLog.Event.FINISHED,
                    ActivationPhase.POST_CONSTRUCTING, settableResult);
        }

        serviceProvider.doPostConstructing(settableResult, ActivationPhase.POST_CONSTRUCTING);

        if (WANT_LOGGING) {
            serviceProvider.recordActivationEvent(ActivationLog.Event.FINISHED,
                    ActivationPhase.POST_CONSTRUCTING, settableResult);
            serviceProvider.recordActivationEvent(ActivationLog.Event.FINISHED,
                    ActivationPhase.ACTIVE, settableResult);
        }

        return instance;
    }

}
