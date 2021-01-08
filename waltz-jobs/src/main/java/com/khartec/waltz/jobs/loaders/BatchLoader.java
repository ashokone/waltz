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

import com.khartec.waltz.common.LoggingUtilities;
import com.khartec.waltz.jobs.generators.*;
import com.khartec.waltz.service.DIConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;


public class BatchLoader {

    private static final boolean SKIP_SLOW = false;

    private static final HashMap<String, SampleDataGenerator> dataLoaders = new HashMap<String, SampleDataGenerator>() {{
        put("OrgUnit", new OrgUnitGenerator());
        put( "AppLoader",  new AppLoader());
    }};


    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(DIConfiguration.class);

        LoggingUtilities.configureLogging();


        ArrayList<SampleDataGenerator> loaders = new ArrayList<SampleDataGenerator>();

        if(args.length > 0) {
            for (String arg : args) {
                log("Data to be loaded : %s ", arg);
                loaders.add(dataLoaders.get(arg));
            }
        }
        else {
            log("ALL Data to be loaded");
            loaders.addAll(dataLoaders.values());
        }

        try {
            loaders.stream()
                    .filter(Objects::nonNull)
                    .forEach(loader -> {
                        log("Starting loader: %s", loader.getClass().getSimpleName());
                        log("Generate");
                        loader.create(ctx);
                        log("Done");
                    });
        }
        catch(Exception e) {
            log("Exception: %s", e.toString());
            e.printStackTrace();
        }
    }

    private static void log(String s, Object... args) {
        System.out.println(String.format(s, args));
    }
}
