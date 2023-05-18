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

/**
 * Pico Test Resources.
 */
module io.helidon.pico.tests.pico {
    requires static jakarta.inject;
    requires static jakarta.annotation;

    requires io.helidon.common.types;
    requires io.helidon.common;
    requires io.helidon.pico.api;
    requires io.helidon.pico.runtime;
    requires io.helidon.pico.tests.plain;

    exports io.helidon.pico.tests.pico;
    exports io.helidon.pico.tests.pico.interceptor;
    exports io.helidon.pico.tests.pico.stacking;
    exports io.helidon.pico.tests.pico.tbox;

    provides io.helidon.pico.api.ModuleComponent with io.helidon.pico.tests.pico.Pico$$Module;

    // difficult decision here:
    // 1. we would like to provide the Application to enable its lookup, so therefore
    // 2. we need the pico-processor to "pre create" it (see pom.xml -Apico.application.pre.create=true), but
    // 3. we really need pico-maven-plugin to build the final version of the Application.java, results in
    // 4. not being able to use IDE to directly run unit tests. Ugh.
//    provides io.helidon.pico.api.Application with io.helidon.pico.tests.pico.Pico$$Application;
}
