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
package com.noctarius.borabora.impl;

import com.noctarius.borabora.Input;
import com.noctarius.borabora.MajorType;
import com.noctarius.borabora.Parser;
import com.noctarius.borabora.Query;
import com.noctarius.borabora.QueryParserException;
import com.noctarius.borabora.Value;
import com.noctarius.borabora.ValueType;
import com.noctarius.borabora.ValueTypes;
import com.noctarius.borabora.builder.QueryBuilder;
import com.noctarius.borabora.spi.Constants;
import com.noctarius.borabora.spi.StreamValue;
import com.noctarius.borabora.spi.codec.Decoder;
import com.noctarius.borabora.spi.codec.TagDecoder;
import com.noctarius.borabora.spi.pipeline.PipelineStageFactory;
import com.noctarius.borabora.spi.pipeline.QueryOptimizer;
import com.noctarius.borabora.spi.pipeline.QueryOptimizerStrategyFactory;
import com.noctarius.borabora.spi.pipeline.QueryPipeline;
import com.noctarius.borabora.spi.pipeline.QueryPipelineFactory;
import com.noctarius.borabora.spi.query.QueryConsumer;
import com.noctarius.borabora.spi.query.QueryContext;
import com.noctarius.borabora.spi.query.QueryContextFactory;
import com.noctarius.borabora.spi.query.SelectStatementStrategy;
import com.noctarius.borabora.spi.query.SelectStatementStrategyAware;

import java.util.List;
import java.util.function.Consumer;

final class ParserImpl
        implements Parser {

    private final List<TagDecoder> tagDecoders;
    private final QueryContextFactory queryContextFactory;
    private final SelectStatementStrategy selectStatementStrategy;
    private final QueryPipelineFactory queryPipelineFactory;
    private final PipelineStageFactory pipelineStageFactory;
    private final QueryOptimizerStrategyFactory queryOptimizerStrategyFactory;
    private final List<QueryOptimizer> queryOptimizers;

    ParserImpl(List<TagDecoder> tagDecoders, SelectStatementStrategy selectStatementStrategy,
               QueryContextFactory queryContextFactory, QueryPipelineFactory queryPipelineFactory,
               PipelineStageFactory pipelineStageFactory, QueryOptimizerStrategyFactory queryOptimizerStrategyFactory,
               List<QueryOptimizer> queryOptimizers) {

        this.tagDecoders = tagDecoders;
        this.queryContextFactory = queryContextFactory;
        this.selectStatementStrategy = selectStatementStrategy;
        this.queryPipelineFactory = queryPipelineFactory;
        this.pipelineStageFactory = pipelineStageFactory;
        this.queryOptimizerStrategyFactory = queryOptimizerStrategyFactory;
        this.queryOptimizers = queryOptimizers;
    }

    @Override
    public Value read(Input input, Query query) {
        SelectStatementStrategy selectStatementStrategy = this.selectStatementStrategy;
        if (query instanceof SelectStatementStrategyAware) {
            selectStatementStrategy = ((SelectStatementStrategyAware) query).selectStatementStrategy();
        }

        SingleConsumer consumer = new SingleConsumer();
        QueryConsumer queryConsumer = bridgeConsumer(consumer, false);
        evaluate(query, input, queryConsumer, selectStatementStrategy);
        return consumer.value == null ? Value.NULL_VALUE : consumer.value;
    }

    @Override
    public Value read(Input input, String query) {
        // #{'b'}(1)->?number
        // \#([0-9]+)? <- stream identifier and optional index, if no index defined then index=-1
        // \{(\'[^\}]+\')\} <- dictionary identifier and key spec
        // \(([0-9]+)\) <- sequence identifier and sequence index
        // ->(\?)?(.+){1} <- expected result type, if ? is defined and type does not match result=null, otherwise exception

        return read(input, prepareQuery(query));
    }

    @Override
    public Value read(Input input, long offset) {
        short head = Decoder.readUInt8(input, offset);
        MajorType majorType = MajorType.findMajorType(head);
        ValueType valueType = ValueTypes.valueType(input, offset);

        QueryContext queryContext = newQueryContext(input, Constants.EMPTY_QUERY_CONSUMER, selectStatementStrategy);
        return new StreamValue(majorType, valueType, offset, queryContext);
    }

    @Override
    public void read(Input input, Query query, Consumer<Value> consumer) {
        SelectStatementStrategy selectStatementStrategy = this.selectStatementStrategy;
        if (query instanceof SelectStatementStrategyAware) {
            selectStatementStrategy = ((SelectStatementStrategyAware) query).selectStatementStrategy();
        }

        QueryConsumer queryConsumer = bridgeConsumer(consumer, true);
        evaluate(query, input, queryConsumer, selectStatementStrategy);
    }

    @Override
    public void read(Input input, String query, Consumer<Value> consumer) {
        read(input, prepareQuery(query), consumer);
    }

    @Override
    public void read(Input input, long offset, Consumer<Value> consumer) {
        consumer.accept(read(input, offset));
    }

    @Override
    public byte[] extract(Input input, Query query) {
        Value value = read(input, query);
        return value == null ? Constants.EMPTY_BYTE_ARRAY : value.raw();
    }

    @Override
    public byte[] extract(Input input, String query) {
        return extract(input, prepareQuery(query));
    }

    @Override
    public byte[] extract(Input input, long offset) {
        return read(input, offset).raw();
    }

    @Override
    public Query prepareQuery(String query) {
        try {

            QueryBuilder queryBuilder = Query.configureBuilder().withSelectStatementStrategy(selectStatementStrategy)
                                             .withPipelineStageFactory(pipelineStageFactory)
                                             .withQueryPipelineFactory(queryPipelineFactory)
                                             .withQueryOptimizerStrategyFactory(queryOptimizerStrategyFactory)
                                             .addQueryOptimizers(queryOptimizers).newBuilder();
            QueryParser.parse(query, queryBuilder, tagDecoders);
            return queryBuilder.build();

        } catch (Exception | TokenMgrError e) {
            throw new QueryParserException(e);
        }
    }

    private QueryContext newQueryContext(Input input, QueryConsumer queryConsumer,
                                         SelectStatementStrategy selectStatementStrategy) {

        return queryContextFactory.newQueryContext(input, queryConsumer, tagDecoders, selectStatementStrategy);
    }

    private void evaluate(Query query, Input input, QueryConsumer queryConsumer,
                          SelectStatementStrategy selectStatementStrategy) {

        QueryPipeline queryPipeline = query.newQueryPipeline();
        QueryContext queryContext = newQueryContext(input, queryConsumer, selectStatementStrategy);

        queryPipeline.evaluate(queryContext);
    }

    private QueryConsumer bridgeConsumer(Consumer<Value> consumer, boolean multiConsumer) {
        return (value) -> {
            consumer.accept(value);
            return multiConsumer;
        };
    }

    private static class SingleConsumer
            implements Consumer<Value> {

        private Value value;

        @Override
        public void accept(Value value) {
            if (this.value != null) {
                throw new IllegalStateException("value already set");
            }
            this.value = value;
        }
    }

}
