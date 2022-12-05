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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.helidon.pico.spi.ActivationLog;
import io.helidon.pico.spi.ActivationLogQuery;
import io.helidon.pico.spi.ServiceProvider;

/**
 * Represents a composite activity log, usually that combining the entirety of all activations as well as activations
 * just for a specific provider.
 */
public class CompositeActivationLog implements ActivationLog, ActivationLogQuery {
    private final ActivationLog[] activationLogs;

    /**
     * Construct the composite log.
     * @param activationLogs the logs to combine to treat as one composite
     */
    public CompositeActivationLog(ActivationLog... activationLogs) {
         this.activationLogs = activationLogs;
    }
    @Override
    public ActivationEntry recordActivationEvent(ActivationEntry entry) {
        Arrays.stream(activationLogs).forEach(log -> log.recordActivationEvent(entry));
        return entry;
    }

    @Override
    public Optional<ActivationLogQuery> toQuery() {
        return Optional.of(this);
    }

    /**
     * Clear the log.
     */
    public void clear() {
        Arrays.stream(activationLogs).forEach(log -> log.toQuery().ifPresent(ActivationLogQuery::clear));
    }

    @Override
    public List<ActivationEntry> getFullActivationLog() {
        Optional<ActivationLog> matchingLog = Arrays.stream(activationLogs)
                .filter(log -> log.toQuery().isPresent()).findFirst();
        return matchingLog.map(activationLog ->
                activationLog.toQuery().get().getFullActivationLog()).orElse(null);
    }

    @Override
    public List<ActivationEntry> getServiceProviderActivationLog(ServiceProvider<?>... serviceProviders) {
        Optional<ActivationLog> matchingLog = Arrays.stream(activationLogs)
                .filter(log -> log.toQuery().isPresent()).findFirst();
        return matchingLog.map(activationLog ->
                activationLog.toQuery().get().getServiceProviderActivationLog(serviceProviders)).orElse(null);
    }

    @Override
    public List<ActivationEntry> getServiceProviderActivationLog(String... serviceTypeNames) {
        Optional<ActivationLog> matchingLog = Arrays.stream(activationLogs)
                .filter(log -> log.toQuery().isPresent()).findFirst();
        return matchingLog.map(activationLog ->
                activationLog.toQuery().get().getServiceProviderActivationLog(serviceTypeNames)).orElse(null);
    }

    @Override
    public List<ActivationEntry> getManagedServiceActivationLog(Object... instances) {
        Optional<ActivationLog> matchingLog = Arrays.stream(activationLogs)
                .filter(log -> log.toQuery().isPresent()).findFirst();
        return matchingLog.map(activationLog ->
                activationLog.toQuery().get().getManagedServiceActivationLog(instances)).orElse(null);
    }

}
