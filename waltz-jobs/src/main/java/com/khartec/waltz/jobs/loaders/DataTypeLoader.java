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
import com.khartec.waltz.jobs.generators.SampleDataGenerator;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.schema.tables.records.DataTypeRecord;
import com.khartec.waltz.service.entity_hierarchy.EntityHierarchyService;
import com.khartec.waltz.service.orgunit.OrganisationalUnitService;
import org.jooq.DSLContext;
import org.jooq.lambda.Unchecked;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.khartec.waltz.common.IOUtilities.readLines;
import static com.khartec.waltz.schema.tables.DataType.DATA_TYPE;


public class DataTypeLoader implements SampleDataGenerator {

    public static final String CSV_REGEX = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";
    private static Map<String, Integer> dataTypeColumns = new HashMap<String, Integer>();

    private void LoadDataTypeColumns() {

        log("File location : %s", getClass().getResource("/datatype-columns.txt").getFile());
        List<String> columns = Unchecked.supplier(() -> readLines(getClass().getResourceAsStream("/datatype-columns.txt"))).get();
        for (int i = 0; i < columns.size(); i++) {
            dataTypeColumns.put( columns.get(i), i);
        }
    }

    @Override
    public Map<String, Integer> create(ApplicationContext ctx) {

        try {
            LoadDataTypeColumns();
            dataTypeColumns.entrySet().stream().forEach(c -> log("%d : %s, ", c.getValue(), c.getKey()));
            DSLContext dsl = getDsl(ctx);

            log("File Location: %s", getClass().getResource("/datatype.csv").getFile());
            OrganisationalUnitService ouDao = ctx.getBean(OrganisationalUnitService.class);
            Supplier<List<String>> lineSupplier = Unchecked.supplier(() -> readLines(getClass().getResourceAsStream("/datatype.csv")));


            List<DataTypeRecord> dataTypeRecords = lineSupplier
                    .get()
                    .stream()
                    .skip(1)
                    .map(line ->
                            line.split(CSV_REGEX, -1))
                    .map(cells -> {
                        DataTypeRecord record = new DataTypeRecord();
                        record.setCode(cells[dataTypeColumns.get("code")].replace("\"",""));
                        record.setDescription(cells[dataTypeColumns.get("description")].replace("\"",""));
                        record.setName(cells[dataTypeColumns.get("name")].replace("\"",""));
                        record.setId(Long.parseLong(cells[dataTypeColumns.get("id")]));
                        record.setParentId(
                                cells[dataTypeColumns.get("parent_id")].replace("\"","").length()>0 ?
                                Long.parseLong(cells[dataTypeColumns.get("parent_id")].replace("\"","")) : null
                        );
                        return record;
                    })
                    .collect(Collectors.toList());

            log("Loading Data Types");
            dsl.batchInsert(dataTypeRecords).execute();

            EntityHierarchyService ehSvc = ctx.getBean(EntityHierarchyService.class);
            ehSvc.buildFor(EntityKind.ORG_UNIT);

            return MapUtilities.newHashMap("created", dataTypeRecords.size());
        } catch (Exception e) {
            log("Exception: %s", e.toString());
            e.printStackTrace();
            return MapUtilities.newHashMap("Skipped", 0);
        }
    }


    @Override
    public boolean remove(ApplicationContext ctx) {
        getDsl(ctx).deleteFrom(DATA_TYPE).execute();
        return true;
    }
}
