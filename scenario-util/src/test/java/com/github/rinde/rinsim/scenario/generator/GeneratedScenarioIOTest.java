/*
 * Copyright (C) 2011-2014 Rinde van Lon, iMinds DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.rinsim.scenario.generator;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;
import org.junit.Test;

import com.github.rinde.rinsim.core.model.pdp.TimeWindowPolicy.TimeWindowPolicies;
import com.github.rinde.rinsim.pdptw.common.DynamicPDPTWProblem.StopConditions;
import com.github.rinde.rinsim.scenario.Scenario;
import com.github.rinde.rinsim.scenario.Scenario.ProblemClass;
import com.github.rinde.rinsim.scenario.ScenarioIO;
import com.google.common.base.Charsets;
import com.google.common.base.Predicates;
import com.google.common.io.Files;

/**
 * @author Rinde van Lon 
 * 
 */
public class GeneratedScenarioIOTest {

  enum TestPC implements ProblemClass {
    CLASS_A;

    @Override
    public String getId() {
      return name();
    }
  }

  /**
   * Test reading and writing a generated scenario.
   * @throws IOException when something IO related went wrong.
   */
  @Test
  public void testIO() throws IOException {
    final ScenarioGenerator generator = ScenarioGenerator
        .builder(TestPC.CLASS_A)
        .timeUnit(SI.MILLI(SI.SECOND))
        .distanceUnit(SI.KILOMETER)
        .speedUnit(NonSI.KILOMETERS_PER_HOUR)
        .tickSize(1000L)
        .scenarioLength(4 * 60 * 60 * 1000L)
        .stopCondition(
            Predicates.and(StopConditions.ANY_TARDINESS,
                StopConditions.TIME_OUT_EVENT))
        .parcels(
            Parcels
                .builder()
                .announceTimes(
                    TimeSeries.homogenousPoisson(4 * 60 * 60 * 1000L, 10))
                .locations(Locations.builder().square(5).buildUniform())
                .timeWindows(TimeWindows.builder().build())
                .build()
        )
        // .deliveryDurations(constant(10L))
        .addModel(Models.roadModel(50d, true))
        .addModel(Models.pdpModel(TimeWindowPolicies.TARDY_ALLOWED))
        .build();

    final Scenario scenario = generator
        .generate(new MersenneTwister(123), "id123");

    // if this call fails, something has changed in the scenario format.
    final Scenario originalScenario = ScenarioIO.read(Paths
        .get("files/scen.json"));
    assertEquals("Change in scenario format detected.", originalScenario,
        scenario);

    final String output = ScenarioIO.write(scenario);
    Files.write(output, new File("files/scen.json"), Charsets.UTF_8);
    final Scenario converted = ScenarioIO.read(output);
    assertEquals(scenario, converted);
  }
}
