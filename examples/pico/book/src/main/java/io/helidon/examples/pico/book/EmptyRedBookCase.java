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

package io.helidon.examples.pico.book;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import io.helidon.pico.Contract;

import jakarta.inject.Inject;

@Contract
@org.jvnet.hk2.annotations.Service
@jakarta.inject.Singleton
@Red
public class EmptyRedBookCase extends RedColor implements BookHolder {

    private Collection<?> books;

    @Override
    public Collection<?> getBooks() {
        return books;
    }

    @Inject
    public EmptyRedBookCase(@Red Optional<Book> preferrredRedBook) {
        this.books = Collections.singleton(preferrredRedBook);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(books=" + getBooks() + ")";
    }

}
