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
package com.github.rinde.rinsim.scenario.fabrirecht;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

import com.github.rinde.rinsim.core.model.pdp.PDPScenarioEvent;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.github.rinde.rinsim.core.pdptw.VehicleDTO;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.scenario.AddDepotEvent;
import com.github.rinde.rinsim.scenario.AddParcelEvent;
import com.github.rinde.rinsim.scenario.AddVehicleEvent;
import com.github.rinde.rinsim.scenario.ScenarioIO;
import com.github.rinde.rinsim.scenario.TimedEvent;
import com.github.rinde.rinsim.scenario.TimedEvent.TimeComparator;
import com.github.rinde.rinsim.util.TimeWindow;
import com.google.common.collect.ImmutableSet;

/**
 * Parser for {@link FabriRechtScenario}s.
 * @author Rinde van Lon
 */
public final class FabriRechtParser {
  private static final String LINE_SEPARATOR = ";";

  private FabriRechtParser() {}

  /**
   * Parse Fabri {@literal &} Recht scenario.
   * @param coordinateFile The coordinate file.
   * @param ordersFile The orders file.
   * @return The scenario.
   * @throws IOException When parsing fails.
   */
  public static FabriRechtScenario parse(String coordinateFile,
      String ordersFile) throws IOException {
    final ImmutableSet<Enum<?>> eventTypes = ImmutableSet.<Enum<?>> of(
        PDPScenarioEvent.ADD_DEPOT,
        PDPScenarioEvent.ADD_PARCEL,
        PDPScenarioEvent.ADD_VEHICLE,
        PDPScenarioEvent.TIME_OUT);
    final List<TimedEvent> events = newArrayList();

    final BufferedReader coordinateFileReader = new BufferedReader(
        new FileReader(coordinateFile));
    final BufferedReader ordersFileReader = new BufferedReader(new FileReader(
        ordersFile));

    final List<Point> coordinates = newArrayList();
    String line;
    int coordinateCounter = 0;
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    while ((line = coordinateFileReader.readLine()) != null) {
      final String[] parts = line.split(LINE_SEPARATOR);
      if (Integer.parseInt(parts[0]) != coordinateCounter) {
        coordinateFileReader.close();
        ordersFileReader.close();
        throw new IllegalArgumentException(
            "The coordinate file seems to be in an unrecognized format.");
      }
      final int x = Integer.parseInt(parts[1]);
      final int y = Integer.parseInt(parts[2]);

      minX = Math.min(x, minX);
      minY = Math.min(y, minY);
      maxX = Math.max(x, maxX);
      maxY = Math.max(y, maxY);

      coordinates.add(new Point(x, y));
      if (Integer.parseInt(parts[0]) == 0) {
        events.add(new AddDepotEvent(0, new Point(x, y)));
      }
      coordinateCounter++;
    }
    coordinateFileReader.close();

    final Point min = new Point(minX, minY);
    final Point max = new Point(maxX, maxY);

    // Anzahl der Fahrzeuge; Kapazität; untere Zeitfenstergrenze; obere
    // Zeitfenstergrenze
    final String firstLineString = ordersFileReader.readLine();
    checkArgument(firstLineString != null);
    final String[] firstLine = firstLineString.split(LINE_SEPARATOR);
    // line 0 contains number of vehicles, but this is not needed
    final int capacity = Integer.parseInt(firstLine[1]);
    final long startTime = Long.parseLong(firstLine[2]);
    final long endTime = Long.parseLong(firstLine[3]);
    final TimeWindow timeWindow = new TimeWindow(startTime, endTime);

    events.add(new TimedEvent(PDPScenarioEvent.TIME_OUT, endTime));
    final VehicleDTO defaultVehicle = VehicleDTO.builder()
        .startPosition(coordinates.get(0))
        .speed(1d)
        .capacity(capacity)
        .availabilityTimeWindow(timeWindow)
        .build();

    // Nr. des Pickup-Orts; Nr. des Delivery-Orts; untere Zeitfenstergrenze
    // Pickup; obere Zeitfenstergrenze Pickup; untere Zeitfenstergrenze
    // Delivery; obere Zeitfenstergrenze Delivery; benötigte Kapazität;
    // Anrufzeit; Servicezeit Pickup; Servicezeit Delivery
    while ((line = ordersFileReader.readLine()) != null) {
      final String[] parts = line.split(LINE_SEPARATOR);

      final int neededCapacity = 1;

      final ParcelDTO o = ParcelDTO
          .builder(coordinates.get(Integer
              .parseInt(parts[0])), coordinates.get(Integer.parseInt(parts[1])))
          .pickupTimeWindow(
              new TimeWindow(Long.parseLong(parts[2]), Long.parseLong(parts[3])))
          .deliveryTimeWindow(
              new TimeWindow(Long.parseLong(parts[4]), Long.parseLong(parts[5])))
          .neededCapacity(neededCapacity)
          .orderAnnounceTime(Long.parseLong(parts[7]))
          .pickupDuration(Long.parseLong(parts[8]))
          .deliveryDuration(Long.parseLong(parts[9]))
          .build();

      events.add(new AddParcelEvent(o));
    }
    ordersFileReader.close();
    Collections.sort(events, TimeComparator.INSTANCE);
    return new FabriRechtScenario(events, eventTypes, min, max,
        timeWindow, defaultVehicle);
  }

  static String toJson(FabriRechtScenario scenario) {
    return ScenarioIO.write(scenario);
  }

  /**
   * Write the scenario to disk in JSON format.
   * @param scenario The scenario to write.
   * @param writer The writer to use.
   * @throws IOException When writing fails.
   */
  public static void toJson(FabriRechtScenario scenario, Writer writer)
      throws IOException {
    final String s = ScenarioIO.write(scenario);
    writer.append(s);
    writer.close();
  }

  /**
   * Convert the specified JSON string to a {@link FabriRechtScenario}.
   * @param json The JSON string to parse.
   * @return A new instance of [@link {@link FabriRechtScenario}.
   */
  public static FabriRechtScenario fromJson(String json) {
    return ScenarioIO.read(json, FabriRechtScenario.class);
  }

  /**
   * Read a scenario from JSON string.
   * @param json The JSON string.
   * @param numVehicles The number of vehicles in the resulting scenario.
   * @param vehicleCapacity The vehicle capacity of the vehicles in the
   *          resulting scenario.
   * @return The scenario.
   */
  public static FabriRechtScenario fromJson(String json, int numVehicles,
      int vehicleCapacity) {
    final FabriRechtScenario scen = fromJson(json);
    return change(scen, numVehicles, vehicleCapacity);
  }

  static FabriRechtScenario change(FabriRechtScenario scen, int numVehicles,
      int vehicleCapacity) {
    final List<TimedEvent> events = newArrayList();
    for (int i = 0; i < numVehicles; i++) {
      events.add(new AddVehicleEvent(0,
          VehicleDTO.builder()
              .use(scen.defaultVehicle)
              .capacity(vehicleCapacity)
              .build()
          ));
    }
    events.addAll(scen.asList());
    return new FabriRechtScenario(events, scen.getPossibleEventTypes(),
        scen.min, scen.max, scen.timeWindow, scen.defaultVehicle);
  }
}
