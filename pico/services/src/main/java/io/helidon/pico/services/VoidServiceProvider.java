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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.ServiceInfo;
import io.helidon.pico.ServiceProvider;

import jakarta.inject.Singleton;

/**
 * A proxy service provider created internally by the framework.
 */
class VoidServiceProvider extends AbstractServiceProvider<Void> {
    public static final VoidServiceProvider INSTANCE = new VoidServiceProvider() {};
    @SuppressWarnings("unchecked")
    public static final List<ServiceProvider<Object>> LIST_INSTANCE = (List) Collections.singletonList(INSTANCE);

    private VoidServiceProvider() {
        setServiceInfo(DefaultServiceInfo.builder()
                .serviceTypeName(getServiceTypeName())
                .contractImplemented(getServiceTypeName())
                .activatorTypeName(VoidServiceProvider.class.getName())
                .scopeTypeName(Singleton.class.getName())
                .weight(DEFAULT_WEIGHT)
                .build());
    }

    public static String getServiceTypeName() {
        return Void.class.getName();
    }

    @Override
    protected Void createServiceProvider(Map<String, Object> deps) {
        return null;
    }

    @Override
    public Void get(InjectionPointInfo ignoredCtx, ServiceInfo ignoredCriteria, boolean expected) {
        return null;
    }

}
