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
package com.github.rinde.rinsim.central.arrays;

import static com.github.rinde.rinsim.central.arrays.ArraysSolvers.convertTW;
import static java.util.Arrays.asList;
import static javax.measure.unit.NonSI.KILOMETERS_PER_HOUR;
import static javax.measure.unit.NonSI.MINUTE;
import static javax.measure.unit.SI.KILOMETER;
import static javax.measure.unit.SI.METER;
import static javax.measure.unit.SI.MILLI;
import static javax.measure.unit.SI.SECOND;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.math.RoundingMode;

import javax.measure.Measure;
import javax.measure.converter.UnitConverter;
import javax.measure.quantity.Velocity;
import javax.measure.unit.ProductUnit;
import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;
import org.junit.Test;

import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.central.Solvers.SimulationConverter;
import com.github.rinde.rinsim.central.Solvers.SolveArgs;
import com.github.rinde.rinsim.central.Solvers.StateContext;
import com.github.rinde.rinsim.central.arrays.ArraysSolvers.ArraysObject;
import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.road.PlaneRoadModel;
import com.github.rinde.rinsim.core.pdptw.DefaultDepot;
import com.github.rinde.rinsim.core.pdptw.DefaultParcel;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.github.rinde.rinsim.core.pdptw.VehicleDTO;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.PDPRoadModel;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle;
import com.github.rinde.rinsim.util.TimeWindow;

/**
 * @author Rinde van Lon
 * 
 */
public class ArraysSolversTest {

  @Test
  public void travelTimeMatrix() {
    final Point p0 = new Point(0, 0);
    final Point p1 = new Point(10, 0);
    final Point p2 = new Point(10, 10);
    final Point p3 = new Point(0, 10);

    // input in kilometers, output in minutes (rounded up), speed 40 km/h
    final Measure<Double, Velocity> speed1 = Measure.valueOf(40d,
        KILOMETERS_PER_HOUR);
    final int[][] matrix1 = ArraysSolvers
        .toTravelTimeMatrix(asList(p0, p1, p2, p3), KILOMETER, speed1, MINUTE,
            RoundingMode.CEILING);
    assertArrayEquals(new int[] { 0, 15, 22, 15 }, matrix1[0]);
    assertArrayEquals(new int[] { 15, 0, 15, 22 }, matrix1[1]);
    assertArrayEquals(new int[] { 22, 15, 0, 15 }, matrix1[2]);
    assertArrayEquals(new int[] { 15, 22, 15, 0 }, matrix1[3]);

    final Point p4 = new Point(11, 3);
    // input in meters, output in milliseconds (round down), speed .0699
    // m/ms
    final Measure<Double, Velocity> speed2 = Measure.valueOf(.0699,
        new ProductUnit<Velocity>(METER.divide(MILLI(SECOND))));
    final int[][] matrix2 = ArraysSolvers.toTravelTimeMatrix(
        asList(p0, p1, p2, p3, p4), METER, speed2, MILLI(SECOND),
        RoundingMode.FLOOR);
    assertArrayEquals(new int[] { 0, 143, 202, 143, 163 }, matrix2[0]);
    assertArrayEquals(new int[] { 143, 0, 143, 202, 45 }, matrix2[1]);
    assertArrayEquals(new int[] { 202, 143, 0, 143, 101 }, matrix2[2]);
    assertArrayEquals(new int[] { 143, 202, 143, 0, 186 }, matrix2[3]);
    assertArrayEquals(new int[] { 163, 45, 101, 186, 0 }, matrix2[4]);
  }

  @Test
  public void convertTWtest() {
    final UnitConverter timeConverter = MILLI(SECOND).getConverterTo(SECOND);

    final int[] tw1 = convertTW(new TimeWindow(300, 800), 5, timeConverter);
    assertEquals(0, tw1[0]);
    assertEquals(1, tw1[1]);

    final int[] tw2 = convertTW(new TimeWindow(7300, 8800), 0, timeConverter);
    assertEquals(8, tw2[0]);
    assertEquals(8, tw2[1]);

    final int[] tw3 = convertTW(new TimeWindow(7300, 8800), 7300, timeConverter);
    assertEquals(0, tw3[0]);
    assertEquals(1, tw3[1]);
  }

  /**
   * Checks correctness of tardiness computation. Also checks whether the
   * arrival time at the current position is correctly ignored when calculating
   * tardiness.
   */
  @Test
  public void computeSumTardinessTest() {
    final int[] route = new int[] { 0, 1, 2, 3 };
    final int[] arrivalTimes = new int[] { 50, 70, 90, 100 };
    final int[] serviceTimes = new int[] { 0, 5, 5, 0 };
    final int[] dueDates = new int[] { 40, 70, 80, 110 };
    final int tardiness = ArraysSolvers.computeRouteTardiness(route,
        arrivalTimes, serviceTimes, dueDates, 0);
    assertEquals(20, tardiness);
  }

  /**
   * Tests whether parcel that have the same location as origin and destinations
   * are treated correctly by the conversion script.
   */
  @Test
  public void testToInventoriesArrayWithDuplicatePositions() {

    final Simulator sim = new Simulator(new MersenneTwister(123),
        Measure.valueOf(1000L, SI.MILLI(SI.SECOND)));

    sim.register(new DefaultPDPModel());
    sim.register(new PDPRoadModel(new PlaneRoadModel(new Point(0, 0),
        new Point(10, 10), 50), false));
    sim.configure();

    final RouteFollowingVehicle rfv = new RouteFollowingVehicle(VehicleDTO
        .builder()
        .startPosition(new Point(1, 1))
        .speed(50d)
        .capacity(10)
        .availabilityTimeWindow(new TimeWindow(0, 1000000))
        .build(),
        false);
    final Depot depot = new DefaultDepot(new Point(5, 5));

    final DefaultParcel dp1 = new DefaultParcel(
        ParcelDTO.builder(new Point(2, 2), new Point(3, 3))
            .pickupTimeWindow(new TimeWindow(0, 1000))
            .deliveryTimeWindow(new TimeWindow(0, 1000))
            .neededCapacity(0)
            .orderAnnounceTime(0L)
            .pickupDuration(5L)
            .deliveryDuration(5L)
            .build());

    final DefaultParcel dp2 = new DefaultParcel(
        ParcelDTO.builder(new Point(2, 2), new Point(3, 3))
            .pickupTimeWindow(new TimeWindow(0, 1000))
            .deliveryTimeWindow(new TimeWindow(0, 1000))
            .neededCapacity(0)
            .orderAnnounceTime(0L)
            .pickupDuration(5L)
            .deliveryDuration(5L)
            .build());

    sim.register(depot);
    sim.register(rfv);
    sim.register(dp1);
    sim.register(dp2);

    rfv.setRoute(asList(dp1, dp2));

    while (rfv.getRoute().size() > 0) {
      sim.tick();
    }

    final SimulationConverter simConv = Solvers.converterBuilder().with(sim)
        .build();
    final StateContext sc = simConv.convert(SolveArgs.create()
        .noCurrentRoutes().useAllParcels());

    final ArraysObject singleVehicleArrays = ArraysSolvers
        .toSingleVehicleArrays(sc.state,
            SI.MILLI(SI.SECOND));

    final int[][] inventories = ArraysSolvers.toInventoriesArray(sc.state,
        singleVehicleArrays);

    assertArrayEquals(new int[][] { { 0, 1 }, { 0, 2 } }, inventories);
  }
}
