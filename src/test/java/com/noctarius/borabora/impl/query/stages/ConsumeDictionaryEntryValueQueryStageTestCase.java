/*
 * Copyright (c) 2016, Christoph Engelbert (aka noctarius) and
 * contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.noctarius.borabora.impl.query.stages;

import com.noctarius.borabora.Input;
import com.noctarius.borabora.spi.pipeline.QueryStage;
import com.noctarius.borabora.spi.query.ProjectionStrategy;
import com.noctarius.borabora.spi.query.QueryContext;
import org.junit.Test;
import org.mockito.Mockito;

import static com.noctarius.borabora.spi.pipeline.PipelineStage.NIL;
import static org.junit.Assert.assertEquals;

public class ConsumeDictionaryEntryValueQueryStageTestCase
        extends AbstractQueryStageTestCase {

    @Test
    public void test_tostring() {
        assertEquals("DIC_ENTRY_END", ConsumeDictionaryEntryValueQueryStage.INSTANCE.toString());
    }

    @Test
    public void test_evaluate() {
        Input input = Input.fromByteArray(new byte[0]);
        QueryStage queryStage = ConsumeDictionaryEntryValueQueryStage.INSTANCE;

        ProjectionStrategy spy = Mockito.spy(ProjectionStrategy.class);

        evaluate(input, queryStage, null, null, null, null, spy);
        Mockito.verify(spy, Mockito.times(1)).putDictionaryValue(Mockito.eq(NIL), Mockito.any(QueryContext.class));
    }

}
