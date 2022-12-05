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

import java.lang.System.Logger;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import io.helidon.pico.ActivationLog;
import io.helidon.pico.ActivationLogEntry;
import io.helidon.pico.ActivationLogQuery;
import io.helidon.pico.ServiceProvider;

/**
 * The default reference implementation for the activity log.
 */
class DefaultActivationLog implements ActivationLog, ActivationLogQuery {
    static final String NAME_LOCAL = "local";
    static final String NAME_FULL = "full";

    private final List<ActivationLogEntry<?>> log;
    private final Logger logger;
    private final String name;

    private DefaultActivationLog(String name,
                                 List<ActivationLogEntry<?>> log,
                                 Logger logger) {
        this.name = Objects.requireNonNull(name);
        this.log = log;
        this.logger = logger;
    }

    /**
     * Create a new captured and retained activation log that tee's to the provided logger.
     *
     * @param name      the name of the services registry
     * @param logger    the logger to use
     * @return the activity log
     */
    static DefaultActivationLog create(String name,
                                       Logger logger) {
        return new DefaultActivationLog(name, new CopyOnWriteArrayList<>(), logger);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public ActivationLogEntry<?> recordActivationEvent(ActivationLogEntry<?> entry) {
        return null;
    }

    @Override
    public Optional<ActivationLogQuery> toQuery() {
        return Objects.nonNull(log) ? Optional.of(this) : Optional.empty();
    }

    @Override
    public ActivationEntry recordActivationEvent(ActivationEntry entry) {
        Objects.requireNonNull(entry, "entry is required");
        if (Objects.nonNull(logger)) {
            logger.log(Logger.Level.TRACE, "{0}", entry);
        }
        if (Objects.nonNull(log)) {
            log.add(entry);
        }
        return entry;
    }

    @Override
    public void clear() {
        log.clear();
    }

    @Override
    public List<ActivationLogEntry<?>> fullActivationLog() {
        return null;
    }

    @Override
    public List<ActivationLogEntry<?>> serviceProviderActivationLog(ServiceProvider<?>... serviceProviders) {
        return null;
    }

    @Override
    public List<ActivationLogEntry<?>> serviceProviderActivationLog(String... serviceTypeNames) {
        return null;
    }

    @Override
    public List<ActivationLogEntry<?>> managedServiceActivationLog(Object... instances) {
        return null;
    }

    @Override
    public List<ActivationEntry> getFullActivationLog() {
        return (Objects.nonNull(log)) ? Collections.unmodifiableList(log) : null;
    }

    @Override
    public List<ActivationEntry> getServiceProviderActivationLog(ServiceProvider<?>... serviceProviders) {
        Set<ServiceProvider<?>> filter = new HashSet<>(Arrays.asList(serviceProviders));
        return getFullActivationLog().stream()
                .filter(entry -> filter.contains(entry.getServiceProvider()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ActivationEntry> getServiceProviderActivationLog(String... serviceTypeNames) {
        Set<String> filter = new HashSet<>(Arrays.asList(serviceTypeNames));
        return getFullActivationLog().stream()
                .filter(entry -> filter.contains(entry.getServiceProvider().getServiceInfo().getServiceTypeName()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ActivationEntry> getManagedServiceActivationLog(Object... instances) {
        Set<Object> filter = new HashSet<>(Arrays.asList(instances));
        return getFullActivationLog().stream()
                .filter(entry -> filter.contains(entry.getManagedServiceInstance()))
                .collect(Collectors.toList());
    }

    List<ActivationEntry> getLog() {
        return log;
    }

}
