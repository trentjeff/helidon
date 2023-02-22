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

package io.helidon.examples.pico.car.dagger2;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class VehiclesModule {

    static String BRAND_NAME;

    @Provides
    public Engine provideEngine() {
        return new Engine();
    }

    @Provides
    @Singleton
    public Brand provideBrand() {
        return DefaultBrand.builder().name(BRAND_NAME).build();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
