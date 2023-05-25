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
 * Pico Integrations OCI Runtime module.
 */
module io.helidon.pico.integrations.oci.runtime {
    requires static jakarta.inject;
    requires static jakarta.annotation;

    requires io.helidon.builder.config;
    requires io.helidon.common;
    requires io.helidon.common.config;
    requires io.helidon.config.metadata;
    requires transitive io.helidon.pico.runtime;
    requires oci.java.sdk.common;
    requires io.helidon.common.types;

    exports io.helidon.pico.integrations.oci.runtime;

    uses io.helidon.pico.api.ModuleComponent;

    provides io.helidon.pico.api.ModuleComponent with
            io.helidon.pico.integrations.oci.runtime.Pico$$Module;
}
