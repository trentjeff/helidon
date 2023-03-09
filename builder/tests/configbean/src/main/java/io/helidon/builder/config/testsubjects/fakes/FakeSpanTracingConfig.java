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

package io.helidon.builder.config.testsubjects.fakes;

import java.util.Optional;

import io.helidon.builder.config.ConfigBean;

/**
 * aka SpanTracingConfig.
 *
 * Configuration of a single traced span.
 */
@ConfigBean
public interface FakeSpanTracingConfig extends FakeTraceableConfig {

    /**
     * When rename is desired, returns the new name.
     *
     * @return new name for this span or empty when rename is not desired
     */
    Optional<String> newName();

    // TODO: in https://github.com/helidon-io/helidon/issues/6382
//    @Singular("spanLog") // B addSpanLog(String, FakeSpanLogTracingConfigBean);
//    Map<String, FakeSpanLogTracingConfig> spanLogMap();

}
