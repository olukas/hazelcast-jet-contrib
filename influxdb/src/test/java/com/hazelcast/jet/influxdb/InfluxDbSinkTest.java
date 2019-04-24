/*
 * Copyright (c) 2008-2019, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.jet.influxdb;

import com.hazelcast.jet.IListJet;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sources;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult.Result;
import org.influxdb.dto.QueryResult.Series;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class InfluxDbSinkTest {

    private static final String URL = "http://localhost:8086";
    private static final String DATABASE_NAME = "test";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "root";
    private static final String SERIES = "mem_usage";
    private static final int VALUE_COUNT = 16 * 1024;

    private JetInstance jet;

    @Before
    public void setup() {
        jet = Jet.newJetInstance();
    }

    @Test
    @Ignore("Connects to actual database")
    public void test_influxDbsink() {
        IListJet<Integer> measurements = jet.getList("mem_usage");
        for (int i = 0; i < VALUE_COUNT; i++) {
            measurements.add(i);
        }

        InfluxDB db = InfluxDBFactory.connect(URL, "root", "root")
                .setDatabase(DATABASE_NAME);

        db.query(new Query("DROP SERIES FROM mem_usage"));
        Pipeline p = Pipeline.create();

        int startTime = 0;
        p.drawFrom(Sources.list(measurements))
                .map(index -> Point.measurement("mem_usage")
                        .time(startTime + index, TimeUnit.MILLISECONDS)
                        .addField("value", index)
                        .build())
                .drainTo(InfluxDbSinks.influxDb(URL, DATABASE_NAME, USERNAME, PASSWORD));

        jet.newJob(p).join();


        List<Result> results = db.query(new Query("SELECT * FROM mem_usage")).getResults();
        assertEquals(1, results.size());
        List<Series> seriesList = results.get(0).getSeries();
        assertEquals(1, seriesList.size());
        Series series = seriesList.get(0);
        assertEquals(SERIES, series.getName());
        assertEquals(VALUE_COUNT, series.getValues().size());
    }

    @After
    public void after() {
        jet.shutdown();
    }
}