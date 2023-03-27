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

package io.helidon.pico.services;

import java.util.Objects;

import io.helidon.pico.Phase;
import io.helidon.pico.Resettable;

class State implements Resettable, Cloneable {
    private Phase currentPhase;
    private boolean isFinished;
    private Throwable lastError;

    private State() {
    }

    static State create(Phase phase) {
        return new State().currentPhase(phase);
    }

    @Override
    public State clone() {
        return create(currentPhase()).finished(finished()).lastError(lastError());
    }

    @Override
    public String toString() {
        return "currentPhase=" + currentPhase + ", isFinished=" + isFinished + ", lastError=" + lastError;
    }

    @Override
    public boolean reset(boolean deep) {
        currentPhase(Phase.INIT).finished(false).lastError(null);
        return true;
    }

    State currentPhase(Phase phase) {
        Phase lastPhase = this.currentPhase;
        this.currentPhase = Objects.requireNonNull(phase);
        if (lastPhase != this.currentPhase) {
            this.isFinished = false;
            this.lastError = null;
        }
        return this;
    }

    Phase currentPhase() {
        return currentPhase;
    }

    State finished(boolean finished) {
        this.isFinished = finished;
        return this;
    }

    boolean finished() {
        return isFinished;
    }

    State lastError(Throwable t) {
        this.lastError = t;
        return this;
    }

    Throwable lastError() {
        return lastError;
    }

}
