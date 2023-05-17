/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.pico.application;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.Services;
import io.helidon.pico.testing.PicoTestingSupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.testing.PicoTestingSupport.testableServices;

class PicoApplicationTest {

    protected PicoServices picoServices;
    protected Services services;

    @AfterAll
    static void tearDown() {
        PicoTestingSupport.resetAll();
    }

    protected void resetWith(Config config) {
        PicoTestingSupport.resetAll();
        this.picoServices = testableServices(config);
        this.services = picoServices.services();
    }

    @Test
    void main() {
        Config config = Config.builder()
                .addSource(ConfigSources.classpath("application.yaml"))
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();
        resetWith(config);
        Main.main();
    }

}
