/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.test.broker.protocol.brokerapi;

import java.util.function.Consumer;
import java.util.function.Predicate;

import io.zeebe.test.broker.protocol.MsgPackHelper;

public class ControlMessageResponseTypeBuilder extends ResponseTypeBuilder<ControlMessageRequest>
{
    protected MsgPackHelper msgPackConverter;

    public ControlMessageResponseTypeBuilder(
            Consumer<ResponseStub<ControlMessageRequest>> stubConsumer,
            Predicate<ControlMessageRequest> activationFunction,
            MsgPackHelper msgPackConverter)
    {
        super(stubConsumer, activationFunction);
        this.msgPackConverter = msgPackConverter;
    }

    public ControlMessageResponseBuilder respondWith()
    {
        return new ControlMessageResponseBuilder(this::respondWith, msgPackConverter);
    }

    public ErrorResponseBuilder<ControlMessageRequest> respondWithError()
    {
        return new ErrorResponseBuilder<>(this::respondWith, msgPackConverter);
    }


}
