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

import io.helidon.builder.processor.spi.BuilderCreatorProvider;

/**
 * Helidon Pico ConfigBean Builder Processor (Tools) module.
 */
module io.helidon.pico.builder.config.processor {
    requires jakarta.inject;
    requires io.helidon.common;
    requires io.helidon.common.config;
    requires io.helidon.config.metadata;
    requires io.helidon.pico.builder.config;
    requires io.helidon.builder.processor;
    requires io.helidon.builder.processor.spi;
    requires io.helidon.builder.processor.tools;
    requires io.helidon.pico.types;
    requires io.helidon.pico;

    exports io.helidon.pico.builder.config.processor.tools;

    uses BuilderCreatorProvider;

    provides BuilderCreatorProvider
            with io.helidon.pico.builder.config.processor.tools.ConfigBeanBuilderCreator;
}
