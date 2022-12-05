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

import java.util.List;
import java.util.Objects;

import io.helidon.common.LazyValue;
import io.helidon.pico.spi.ActivationPhase;
import io.helidon.pico.spi.Activator;
import io.helidon.pico.spi.DeActivator;
import io.helidon.pico.spi.DependenciesInfo;
import io.helidon.pico.spi.InjectionPointInfo;
import io.helidon.pico.spi.PostConstructMethod;
import io.helidon.pico.spi.PreDestroyMethod;
import io.helidon.pico.spi.ServiceInfo;
import io.helidon.pico.spi.ServiceProvider;
import io.helidon.pico.spi.ext.AbstractServiceProvider;

/**
 * A service provider that is bound to a particular injection point context.
 *
 * @param <T> the type of the bound service provider
 */
public class BoundedServiceProvider<T> implements ServiceProvider<T> {

    private final ServiceProvider<T> binding;
    private final io.helidon.pico.spi.impl.DefaultInjectionPointInfo ipInfoCtx;
    private final LazyValue<T> instance;
    private final LazyValue<List<T>> instances;

    private BoundedServiceProvider(ServiceProvider<T> binding, io.helidon.pico.spi.impl.DefaultInjectionPointInfo ipInfoCtx) {
        this.binding = Objects.requireNonNull(binding);
        this.ipInfoCtx = Objects.requireNonNull(ipInfoCtx);
        this.instance = LazyValue.create(() -> binding.get(ipInfoCtx, ipInfoCtx.getDependencyToServiceInfo(), true));
        this.instances = LazyValue.create(() -> binding.getList(ipInfoCtx, ipInfoCtx.getDependencyToServiceInfo(), true));
    }

    /**
     * Creates a bound service provider to a specific binding.
     *
     * @param binding   the bound service provider
     * @param ipInfoCtx the binding context
     * @param <T> the type of the service provider
     * @return the service provider created, wrapping the binding delegate provider
     */
    public static <T> ServiceProvider<T> create(ServiceProvider<T> binding, io.helidon.pico.spi.impl.DefaultInjectionPointInfo ipInfoCtx) {
        assert (Objects.nonNull(binding));
        assert (!(binding instanceof BoundedServiceProvider));
        if (binding instanceof AbstractServiceProvider) {
            AbstractServiceProvider sp = (AbstractServiceProvider) binding;
            if (!sp.isProvider()) {
                return binding;
            }
        }
        return new BoundedServiceProvider<>(binding, ipInfoCtx);
    }

    @Override
    public String toString() {
        return binding.toString();
    }

    @Override
    public int hashCode() {
        return binding.hashCode();
    }

    @Override
    public boolean equals(Object another) {
        return binding.equals(another);
    }

    @Override
    public T get(InjectionPointInfo ipInfoCtx, ServiceInfo criteria, boolean expected) {
        assert (Objects.isNull(ipInfoCtx) || this.ipInfoCtx.equals(ipInfoCtx))
                : ipInfoCtx + " was not equal to " + this.ipInfoCtx;
        assert (Objects.isNull(criteria) || this.ipInfoCtx.getDependencyToServiceInfo().matches(criteria))
                : criteria + " did not match "
                + this.ipInfoCtx.getDependencyToServiceInfo();
        return instance.get();
    }

    @Override
    public List<T> getList(InjectionPointInfo ipInfoCtx, ServiceInfo criteria, boolean expected) {
        assert (Objects.isNull(ipInfoCtx) || this.ipInfoCtx.equals(ipInfoCtx))
                : ipInfoCtx + " was not equal to " + this.ipInfoCtx;
        assert (Objects.isNull(criteria) || this.ipInfoCtx.getDependencyToServiceInfo().matches(criteria))
                : criteria + " did not match "
                + this.ipInfoCtx.getDependencyToServiceInfo();
        return instances.get();
    }

    @Override
    public String toIdentityString() {
        return binding.toIdentityString();
    }

    @Override
    public String getDescription() {
        return binding.getDescription();
    }

    @Override
    public boolean isProvider() {
        return binding.isProvider();
    }

    @Override
    public ServiceInfo getServiceInfo() {
        return binding.getServiceInfo();
    }

    @Override
    public DependenciesInfo getDependencies() {
        return binding.getDependencies();
    }

    @Override
    public ActivationPhase getCurrentActivationPhase() {
        return binding.getCurrentActivationPhase();
    }

    @Override
    public Activator<T> getActivator() {
        return binding.getActivator();
    }

    @Override
    public DeActivator<T> getDeActivator() {
        return binding.getDeActivator();
    }

    @Override
    public PostConstructMethod getPostConstructMethod() {
        return binding.getPostConstructMethod();
    }

    @Override
    public PreDestroyMethod getPreDestroyMethod() {
        return binding.getPreDestroyMethod();
    }

    @Override
    public ServiceProvider<T> getServiceProviderBindable() {
        return binding;
    }
}
