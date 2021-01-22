/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
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
 * See the License for the specific
 *
 */

package com.khartec.waltz.jobs.loaders;

import com.khartec.waltz.common.MapUtilities;
import com.khartec.waltz.common.SetUtilities;
import com.khartec.waltz.data.logical_flow.LogicalFlowDao;
import com.khartec.waltz.jobs.generators.SampleDataGenerator;
import com.khartec.waltz.model.application.Application;
import com.khartec.waltz.model.logical_flow.ImmutableLogicalFlow;
import com.khartec.waltz.model.logical_flow.LogicalFlow;
import com.khartec.waltz.schema.tables.records.LogicalFlowRecord;
import com.khartec.waltz.service.application.ApplicationService;
import org.jooq.DSLContext;
import org.jooq.lambda.Unchecked;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

import static com.khartec.waltz.common.IOUtilities.readLines;
import static com.khartec.waltz.schema.tables.LogicalFlow.LOGICAL_FLOW;
import static java.util.stream.Collectors.toSet;


public class LogicalFlowLoader implements SampleDataGenerator {

    private static Map<String, Integer> logicalFlowColumns = new HashMap<String, Integer>();

    private void LoadColumns() {


        log("File location : %s", getClass().getResource("/logicalFlow-columns.txt").getFile());
        List<String> columns = Unchecked.supplier(() -> readLines(getClass().getResourceAsStream("/logicalFlow-columns.txt"))).get();
        for (int i = 0; i < columns.size(); i++) {
            logicalFlowColumns.put(columns.get(i), i);
        }
    }

    @Override
    public Map<String, Integer> create(ApplicationContext ctx) {


        ApplicationService applicationDao = ctx.getBean(ApplicationService.class);
        DSLContext dsl = ctx.getBean(DSLContext.class);

        try {
            LoadColumns();
            logicalFlowColumns.entrySet().stream().forEach(c -> log("%d : %s, ", c.getValue(), c.getKey()));


            log("File Location: %s", getClass().getResource("/logicalFlow.csv").getFile());
            Supplier<List<String>> lineSupplier = Unchecked.supplier(() -> readLines(getClass().getResourceAsStream("/logicalFlow.csv")));


            LocalDateTime now = LocalDateTime.now();

            Set<LogicalFlow> logicalFlows = lineSupplier
                    .get()
                    .stream()
                    .skip(1)
                    .map(line ->
                            line.split(",", -1))
                    .map(cells -> {
                        List<Application> fromApp = applicationDao.findByName(cells[logicalFlowColumns.get("fromApp")].replace("\"",""));
                        List<Application> toApp = applicationDao.findByName(cells[logicalFlowColumns.get("toApp")].replace("\"",""));

                        return ImmutableLogicalFlow.builder()
                                .source(fromApp.get(0).entityReference())
                                .target(toApp.get(0).entityReference())
                                .lastUpdatedBy("admin")
                                .provenance(SAMPLE_DATA_PROVENANCE)
                                .lastUpdatedAt(now)
                                .build();
                    })
                    .filter(Objects::nonNull)
                    .collect(toSet());


            log("--- saving: " + logicalFlows.size());

            Set<LogicalFlowRecord> records = SetUtilities.map(logicalFlows, df -> LogicalFlowDao.TO_RECORD_MAPPER.apply(df, dsl));
            dsl.batchStore(records).execute();


            return MapUtilities.newHashMap("Done", records.size());

        } catch (Exception e) {
            log("Exception: %s", e.toString());
            e.printStackTrace();
            return MapUtilities.newHashMap("Skipped", 0);
        }
    }


    @Override
    public boolean remove(ApplicationContext ctx) {
        getDsl(ctx)
                .deleteFrom(LOGICAL_FLOW)
                .where(LOGICAL_FLOW.PROVENANCE.eq(SAMPLE_DATA_PROVENANCE))
                .execute();
        return true;
    }
}
