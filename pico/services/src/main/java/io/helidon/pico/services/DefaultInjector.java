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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Weight;
import io.helidon.pico.spi.ActivationLog;
import io.helidon.pico.spi.ActivationPhase;
import io.helidon.pico.spi.ActivationResult;
import io.helidon.pico.spi.ActivationStatus;
import io.helidon.pico.spi.Activator;
import io.helidon.pico.spi.DeActivator;
import io.helidon.pico.spi.InjectionException;
import io.helidon.pico.spi.InjectionPointInfo;
import io.helidon.pico.spi.Injector;
import io.helidon.pico.spi.PicoServices;
import io.helidon.pico.spi.ServiceInfo;
import io.helidon.pico.spi.ServiceProvider;
import io.helidon.pico.spi.Services;
import io.helidon.pico.spi.ext.AbstractServiceProvider;

import jakarta.inject.Singleton;

/**
 * Default reference implementation for the {@link Injector}.
 */
@Singleton
@Weight(PicoServices.DEFAULT_WEIGHT)
@SuppressWarnings("unchecked")
public class DefaultInjector implements Injector {

    @Override
    public <T> T activateInject(T instance,
                      InjectionPointInfo ipInfoCtx,
                      ActivationPhase startAtPhase,
                      ActivationPhase finishAtPhase,
                      Services services,
                      ActivationLog log,
                      Strategy strategy,
                      AtomicReference<ActivationResult<T>> resultHolder) throws InjectionException {
        if (Objects.isNull(strategy) || Strategy.ANY == strategy) {
            strategy = Strategy.ACTIVATOR;
        }

        final boolean throwOnError = Objects.isNull(resultHolder);

        if (strategy != Strategy.ACTIVATOR) {
            io.helidon.pico.spi.impl.DefaultActivationResult<T> result = AbstractServiceProvider.failedFinish(
                    new UnsupportedOperationException("this provider only supports the "
                            + Strategy.ACTIVATOR + " strategy"), throwOnError);
            resultHolder.set(result);
            return null;
        }

        if (Objects.isNull(instance)) {
            io.helidon.pico.spi.impl.DefaultActivationResult<T> result = AbstractServiceProvider.failedFinish(
                    new IllegalArgumentException("target instance is required"), throwOnError);
            resultHolder.set(result);
            return null;
        }

        if (instance instanceof AbstractServiceProvider) {
            AbstractServiceProvider<T> serviceProvider = (AbstractServiceProvider<T>) instance;
            Activator<T> activator = serviceProvider.getActivator();
            if (Objects.isNull(activator)) {
                io.helidon.pico.spi.impl.DefaultActivationResult<T> result = AbstractServiceProvider.failedFinish(
                        new UnsupportedOperationException("the service provider does not have an activator"), throwOnError);
                resultHolder.set(result);
                return null;
            }

            io.helidon.pico.spi.impl.DefaultActivationResult<T> result = (io.helidon.pico.spi.impl.DefaultActivationResult<T>)
                    activator.activate(serviceProvider, ipInfoCtx, ActivationPhase.ACTIVE, false);
            if (Objects.nonNull(resultHolder)) {
                resultHolder.set(result);
            }

            if (DefaultActivationResult.isSuccess(result.getFinishingStatus())) {
                ServiceInfo criteria = null;
                if (ipInfoCtx instanceof DefaultInjectionPointInfo) {
                    criteria = ((DefaultInjectionPointInfo) ipInfoCtx).getDependencyToServiceInfo();
                }
                return result.getServiceProvider().get(ipInfoCtx, criteria, false);
            }
        }

        io.helidon.pico.spi.impl.DefaultActivationResult<T> result = AbstractServiceProvider.failedFinish(
                new IllegalStateException("this provider only supports the default ServiceProvider targets"), throwOnError);
        resultHolder.set(result);
        return null;
    }

    @Override
    public <T> ActivationResult<T> deactivate(T instance,
                                              Services services,
                                              ActivationLog log,
                                              Strategy strategy) throws InjectionException {
        if (Objects.isNull(strategy) || Strategy.ANY == strategy) {
            strategy = Strategy.ACTIVATOR;
        }

        if (strategy != Strategy.ACTIVATOR) {
            return AbstractServiceProvider.failedFinish(
                    new UnsupportedOperationException("this provider only supports the "
                            + Strategy.ACTIVATOR + " strategy"), false);
        }

        if (Objects.isNull(instance)) {
            return AbstractServiceProvider.failedFinish(
                    new IllegalArgumentException("target instance is required"), false);
        }

        if (instance instanceof ServiceProvider) {
            ServiceProvider sp = (ServiceProvider) instance;
            ActivationPhase phase = sp.getCurrentActivationPhase();
            if (!phase.isEligibleForDeactivation()) {
                return io.helidon.pico.DefaultActivationResult.builder()
                        .serviceProvider(sp)
                        .startingActivationPhase(phase)
                        .finishingActivationPhase(phase)
                        .finishingStatus(ActivationStatus.WARNING_GENERAL)
                        .finished(true)
                        .build();
            }
        }

        if (instance instanceof AbstractServiceProvider) {
            AbstractServiceProvider<T> serviceProvider = (AbstractServiceProvider<T>) instance;
            DeActivator<T> deActivator = serviceProvider.getDeActivator();
            if (Objects.isNull(deActivator)) {
                return AbstractServiceProvider.failedFinish(
                        new UnsupportedOperationException("the service provider does not have a deactivator"), false);
            }

            return deActivator.deactivate(serviceProvider, false);
        }

        return AbstractServiceProvider.failedFinish(
                new IllegalStateException("this provider only supports the default ServiceProvider targets"), false);
    }

}
