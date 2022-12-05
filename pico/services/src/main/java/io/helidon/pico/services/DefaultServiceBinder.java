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

import io.helidon.pico.services.DefaultServices;
import io.helidon.pico.spi.PicoServices;
import io.helidon.pico.spi.ServiceBinder;
import io.helidon.pico.spi.ServiceProvider;
import io.helidon.pico.spi.ServiceProviderBindable;

/**
 * The default implementation for {@link io.helidon.pico.spi.ServiceBinder}.
 */
public class DefaultServiceBinder implements ServiceBinder {
    private final PicoServices picoServices;
    private final DefaultServices serviceRegistry;
    private final String moduleName;

    protected DefaultServiceBinder(PicoServices picoServices, DefaultServices serviceRegistry, String moduleName) {
        this.picoServices = picoServices;
        this.serviceRegistry = serviceRegistry;
        this.moduleName = moduleName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bind(ServiceProvider<?> sp) {
        ServiceProviderBindable<?> bindableSp = ServiceProviderBindable.toBindableProvider(sp);

        if (Objects.nonNull(moduleName)
                && Objects.nonNull(bindableSp)) {
            bindableSp.setModuleName(moduleName);
        }

        serviceRegistry.bind(picoServices, sp);

        if (Objects.nonNull(bindableSp)) {
            bindableSp.setPicoServices(picoServices);
        }
    }

}
