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

package io.helidon.pico.tools.creator;

import io.helidon.pico.Contract;

/**
 * Responsible for creating the {@link io.helidon.pico.Application} instance. This is generally called by the pico maven plugin.
 */
@Contract
public interface ApplicationCreator {

    /**
     * Used to create the {@link io.helidon.pico.Application} source for the entire
     * application / assembly, ultimately using the {@link io.helidon.pico.Services} found in the current set of
     * {@link io.helidon.pico.Module}'s in the thread context classpath.
     *
     * @param request the request for what to generate
     * @return the response result for the create operation
     */
    ApplicationCreatorResponse createApplication(ApplicationCreatorRequest request);

}
