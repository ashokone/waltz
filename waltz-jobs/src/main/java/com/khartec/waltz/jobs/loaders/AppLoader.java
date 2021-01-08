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
import com.khartec.waltz.common.RandomUtilities;
import com.khartec.waltz.common.StringUtilities;
import com.khartec.waltz.jobs.generators.SampleDataGenerator;
import com.khartec.waltz.model.Criticality;
import com.khartec.waltz.model.application.AppRegistrationRequest;
import com.khartec.waltz.model.application.ApplicationKind;
import com.khartec.waltz.model.application.ImmutableAppRegistrationRequest;
import com.khartec.waltz.model.application.LifecyclePhase;
import com.khartec.waltz.model.orgunit.OrganisationalUnit;
import com.khartec.waltz.model.rating.RagRating;
import com.khartec.waltz.service.application.ApplicationService;
import com.khartec.waltz.service.orgunit.OrganisationalUnitService;
import org.jooq.DSLContext;
import org.jooq.lambda.Unchecked;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.khartec.waltz.common.IOUtilities.readLines;


public class AppLoader implements SampleDataGenerator {

    public static final String CSV_REGEX = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";
    private static final Random rnd = RandomUtilities.getRandom();
    private static Long longVal(String value) {
        return StringUtilities.parseLong(value, null);
    }
    private static Map<String, Integer> appColumns = new HashMap<String, Integer>();

    @Autowired
    ApplicationService applicationService;

    private void LoadAppColumns() {


        log("File location : %s", getClass().getResource("/app-columns.txt").getFile());
        List<String> columns = Unchecked.supplier(() -> readLines(getClass().getResourceAsStream("/app-columns.txt"))).get();
        for (int i = 0; i < columns.size(); i++) {
            appColumns.put( columns.get(i), i);
        }
    }

    @Override
    public Map<String, Integer> create(ApplicationContext ctx) {

        try {
            LoadAppColumns();
            appColumns.entrySet().stream().forEach(c -> log("%d : %s, ", c.getValue(), c.getKey()));
        DSLContext dsl = getDsl(ctx);
        ApplicationService applicationDao = ctx.getBean(ApplicationService.class);
        log("File Location: %s", getClass().getResource("/org-apps.csv").getFile());
        OrganisationalUnitService ouDao = ctx.getBean(OrganisationalUnitService.class);
        Supplier<List<String>> lineSupplier = Unchecked.supplier(() -> readLines(getClass().getResourceAsStream("/org-apps.csv")));


        List<AppRegistrationRequest> registrationRequests = lineSupplier
                .get()
                .stream()
                .skip(1)
                .map(line -> line.split(CSV_REGEX, -1))
                .map(cells -> {
                    log("Loading input row: %s", Arrays.toString(cells));
                    OrganisationalUnit organisationalUnit = ouDao.getByName(cells[appColumns.get("orgUnit")]);

                    LifecyclePhase phase = appColumns.get("lifecyclePhase")!=null
                            ? LifecyclePhase.valueOf(cells[appColumns.get("lifecyclePhase")])
                            : LifecyclePhase.PRODUCTION;

                    Criticality businessCriticality = appColumns.get("businessCriticality")!=null
                            ? Criticality.valueOf(cells[appColumns.get("businessCriticality")])
                            : Criticality.NONE;

                    ApplicationKind applicationKind = appColumns.get("applicationKind")!=null
                            ? ApplicationKind.valueOf(cells[appColumns.get("applicationKind")])
                            : ApplicationKind.IN_HOUSE;



                    AppRegistrationRequest appRegistrationRequest = ImmutableAppRegistrationRequest.builder()
                            .name(cells[appColumns.get("name")])
                            .assetCode(cells[appColumns.get("assetCode")])
                            .description(cells[appColumns.get("description")].replace("\"",""))
                            .applicationKind(applicationKind)
                            .lifecyclePhase(phase)
                            .overallRating(appColumns.get("overallRating")!=null?RagRating.valueOf(cells[appColumns.get("overallRating")]): RagRating.X)
                            .organisationalUnitId(organisationalUnit.id().get())
                            .businessCriticality(businessCriticality)
                            .build();

                    return appRegistrationRequest;
                })
                .collect(Collectors.toList());


        log("Loading Apps");
        registrationRequests.forEach(a -> applicationDao.registerOrUpdateApp(a, "admin"));

        return MapUtilities.newHashMap("created", registrationRequests.size());
        }
        catch(Exception e) {
            log("Exception: %s", e.toString());
            e.printStackTrace();
            return MapUtilities.newHashMap("Skipped", 0);
        }
    }

    @Override
    public boolean remove(ApplicationContext ctx) {
        log("Skipped Deleting existing Applications");
        return true;
    }

}
