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
package com.noctarius.borabora.spi.query.pipeline;

import com.noctarius.borabora.spi.query.QueryContext;

/**
 * The <tt>VisitResult</tt> enum defines the constants for controlling the pipeline flow based
 * on actions given by the pipeline stages.
 */
public enum VisitResult {

    /**
     * Continue pipeline execution with next child or sibling
     */
    Continue,

    /**
     * Loop on current {@link QueryStage#evaluate(PipelineStage, PipelineStage, QueryContext)}
     */
    Loop,

    /**
     * Stop execution on current subtree and skip children but continue
     * on next sibling
     */
    Break,

    /**
     * Stop execution altogether
     */
    Exit
}
