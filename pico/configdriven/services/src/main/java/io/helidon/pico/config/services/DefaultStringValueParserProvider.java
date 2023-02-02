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

package io.helidon.pico.config.services;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.pico.builder.config.spi.StringValueParser;
import io.helidon.pico.builder.config.spi.StringValueParserProvider;

/**
 * Service-loaded provider for {@link StringValueParserProvider}.
 *
 * @deprecated
 */
@Weight(Weighted.DEFAULT_WEIGHT)
public class DefaultStringValueParserProvider implements StringValueParserProvider {
    static final LazyValue<StringValueParser> INSTANCE = LazyValue.create(DefaultStringValueParser::new);

    /**
     * Service loaded constructor.
     */
    public DefaultStringValueParserProvider() {
    }

    @Override
    public StringValueParser stringValueParser() {
        return INSTANCE.get();
    }

}
