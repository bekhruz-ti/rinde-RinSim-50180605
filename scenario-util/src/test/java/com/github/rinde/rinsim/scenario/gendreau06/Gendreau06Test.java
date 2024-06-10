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
package com.github.rinde.rinsim.scenario.gendreau06;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.TimeLapse;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.ParcelState;
import com.github.rinde.rinsim.core.model.pdp.PDPScenarioEvent;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.pdptw.DefaultDepot;
import com.github.rinde.rinsim.core.pdptw.DefaultVehicle;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.github.rinde.rinsim.core.pdptw.VehicleDTO;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.DynamicPDPTWProblem;
import com.github.rinde.rinsim.pdptw.common.DynamicPDPTWProblem.Creator;
import com.github.rinde.rinsim.pdptw.common.StatisticsDTO;
import com.github.rinde.rinsim.scenario.AddDepotEvent;
import com.github.rinde.rinsim.scenario.AddParcelEvent;
import com.github.rinde.rinsim.scenario.AddVehicleEvent;
import com.github.rinde.rinsim.scenario.TimedEvent;
import com.github.rinde.rinsim.testutil.GuiTests;
import com.github.rinde.rinsim.util.TimeWindow;

/**
 * @author Rinde van Lon
 * 
 */
@RunWith(value = Parameterized.class)
@Category(GuiTests.class)
public class Gendreau06Test {

  protected static final double EPSILON = 0.0001;

  protected final boolean useGui;

  public Gendreau06Test(boolean gui) {
    useGui = gui;
  }

  @Parameters
  public static Collection<Object[]> data() {
    final Object[][] data = new Object[][] { { true }, { false } };
    return Arrays.asList(data);
  }

  @Test
  public void simpleScenario() throws IOException {
    final Gendreau06Scenario scenario = create(2, minutes(15),
        new AddParcelEvent(
            ParcelDTO.builder(new Point(2, 1), new Point(4, 1))
                .pickupTimeWindow(new TimeWindow(0, 720000))
                .deliveryTimeWindow(new TimeWindow(5, 720000))
                .neededCapacity(0)
                .orderAnnounceTime(0L)
                .pickupDuration(0L)
                .deliveryDuration(0L)
                .build()
        ));
    final StatisticsDTO dto = runProblem(scenario, useGui);

    // the second truck will turn around just one tick distance before
    // reaching the package. the reason is that it is too late since the
    // first truck will pickup the parcel.
    final double distInOneTick = 30.0 / 3600.0;
    assertTrue(dto.simFinish);
    assertEquals(9 - 2.0 * distInOneTick, dto.totalDistance, EPSILON);
    assertEquals(1, dto.totalParcels);
    assertEquals(0, dto.overTime);
    assertEquals(0, dto.pickupTardiness);
    assertEquals(0, dto.deliveryTardiness);
    assertEquals(2, dto.totalVehicles);
    assertEquals(2, dto.movedVehicles);
  }

  /**
   * Checks whether overtime is computed correctly.
   */
  @Test
  public void overtimeScenario() {
    final Gendreau06Scenario scenario = create(1, minutes(6),
        new AddParcelEvent(
            ParcelDTO.builder(new Point(2, 1), new Point(4, 1))
                .pickupTimeWindow(new TimeWindow(0, minutes(12)))
                .deliveryTimeWindow(new TimeWindow(5, minutes(12)))
                .neededCapacity(0)
                .orderAnnounceTime(0L)
                .pickupDuration(0L)
                .deliveryDuration(0L)
                .build()
        ));

    final StatisticsDTO dto = runProblem(scenario, useGui);

    assertTrue(dto.simFinish);
    assertEquals(6, dto.totalDistance, EPSILON);
    assertEquals(1, dto.totalDeliveries);
    assertEquals(minutes(6) - 1000, dto.overTime);
    assertEquals(0, dto.pickupTardiness);
    assertEquals(0, dto.deliveryTardiness);
  }

  /**
   * Check whether tardiness is computed correctly.
   * <p>
   * The layout of this test is shown below. P is pickup location, D is delivery
   * location, DEP is depot. A <code>-</code> or <code>|</code> is a distance of
   * .5 km which takes the vehicle exactly 1 minute to traverse. White space
   * means nothing.
   * <p>
   * <code>
   * P1 - - P2
   * |      |
   * DEP    
   * |      |
   * |      |
   * |      |
   * D1 - - D2
   * </code>
   */
  @Test
  public void tardinessScenario() {
    final Gendreau06Scenario scenario = create(1, minutes(12), /* */
        parcelEvent(2, 3, 2, 1, 0, seconds(15), 0, minutes(9)), /* */
        parcelEvent(3, 3, 3, 1, 0, minutes(3), 0, minutes(4)));
    final StatisticsDTO dto = runProblem(scenario, useGui);
    assertTrue(dto.simFinish); // the vehicles have returned to the depot
                               // just before the TIME_OUT event, but the
                               // simulation continues until the end of the
                               // scenario.
    assertEquals(6, dto.totalDistance, EPSILON);
    assertEquals(2, dto.totalDeliveries);
    assertEquals(0, dto.overTime);
    assertEquals(seconds(45), dto.pickupTardiness);
    assertEquals(minutes(3), dto.deliveryTardiness);
  }

  static long minutes(long n) {
    return n * seconds(60);
  }

  static long seconds(long n) {
    return n * 1000;
  }

  static AddParcelEvent parcelEvent(double x1, double y1, double x2, double y2,
      long tw1b, long tw1e, long tw2b, long tw2e) {
    return new AddParcelEvent(
        ParcelDTO.builder(new Point(x1, y1), new Point(x2, y2))
            .pickupTimeWindow(new TimeWindow(tw1b, tw1e))
            .deliveryTimeWindow(new TimeWindow(tw2b, tw2e))
            .neededCapacity(0)
            .orderAnnounceTime(0L)
            .pickupDuration(0L)
            .deliveryDuration(0L)
            .build());
  }

  static StatisticsDTO runProblem(Gendreau06Scenario s, boolean useGui) {
    final DynamicPDPTWProblem problem = new DynamicPDPTWProblem(s, 123);
    if (useGui) {
      problem.enableUI(new TestUICreator(problem, 50));
    }
    problem.addCreator(AddVehicleEvent.class, new Creator<AddVehicleEvent>() {
      @Override
      public boolean create(Simulator sim, AddVehicleEvent event) {
        return sim.register(new SimpleTruck(event.vehicleDTO,
            new ClosestParcelStrategy()));
      }
    });
    problem.simulate();
    return problem.getStatistics();
  }

  static Gendreau06Scenario create(int numVehicles, long endTime,
      AddParcelEvent... parcelEvents) {
    final List<TimedEvent> events = newArrayList();

    final Point depotPosition = new Point(2.0, 2.5);
    final double truckSpeed = 30;
    events.add(new AddDepotEvent(-1, depotPosition));
    for (int i = 0; i < numVehicles; i++) {
      events.add(new AddVehicleEvent(-1, VehicleDTO.builder()
          .startPosition(depotPosition)
          .speed(truckSpeed)
          .capacity(0)
          .availabilityTimeWindow(TimeWindow.ALWAYS)
          .build()));
    }

    events.addAll(asList(parcelEvents));

    events.add(new TimedEvent(PDPScenarioEvent.TIME_OUT, endTime));

    final Set<Enum<?>> eventTypes = new HashSet<Enum<?>>(
        asList(PDPScenarioEvent.values()));
    return new Gendreau06Scenario(events, eventTypes, 1000L,
        GendreauProblemClass.LONG_LOW_FREQ, 1, false);
  }

  static class SimpleTruck extends DefaultVehicle {
    protected VehicleStrategy strategy;

    public SimpleTruck(VehicleDTO dto, VehicleStrategy vs) {
      super(dto);
      strategy = vs;
    }

    @Override
    protected void tickImpl(TimeLapse time) {
      strategy.execute(time);
    }

    @Override
    public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
      super.initRoadPDP(pRoadModel, pPdpModel);
      strategy.init(this, pRoadModel, pPdpModel);
    }
  }

  interface VehicleStrategy {
    void init(DefaultVehicle vehicle, RoadModel rm, PDPModel pm);

    void execute(TimeLapse time);
  }

  static class ClosestParcelStrategy implements VehicleStrategy {

    protected DefaultVehicle vehicle;
    protected RoadModel roadModel;
    protected PDPModel pdpModel;
    protected Parcel target;
    protected DefaultDepot depot;

    public ClosestParcelStrategy() {}

    @Override
    public void init(DefaultVehicle v, RoadModel rm, PDPModel pm) {
      checkState(vehicle == null && roadModel == null && pdpModel == null,
          "init can be called only once!");
      vehicle = v;
      roadModel = rm;
      pdpModel = pm;

      final Set<DefaultDepot> set = rm.getObjectsOfType(DefaultDepot.class);
      checkArgument(set.size() == 1,
          "This strategy only supports problems with one depot.");
      depot = set.iterator().next();
    }

    @Override
    public void execute(TimeLapse time) {
      while (time.hasTimeLeft()) {
        final Set<Parcel> parcels = newHashSet(pdpModel
            .getParcels(ParcelState.AVAILABLE));
        if (!pdpModel.getContents(vehicle).isEmpty()) {
          parcels.addAll(pdpModel.getContents(vehicle));
        }

        double dist = Double.POSITIVE_INFINITY;
        Parcel closest = null;
        for (final Parcel p : parcels) {
          final Point pos = pdpModel.containerContains(vehicle, p) ? p
              .getDestination() : roadModel.getPosition(p);
          final double d = Point.distance(roadModel.getPosition(vehicle), pos);
          if (d < dist) {
            dist = d;
            closest = p;
          }
        }

        if (closest != null) {
          roadModel.moveTo(vehicle, closest, time);
          if (roadModel.equalPosition(vehicle, closest)) {
            pdpModel.service(vehicle, closest, time);
          }
        } else {
          roadModel.moveTo(vehicle, depot, time);
          if (roadModel.equalPosition(vehicle, depot)) {
            time.consumeAll();
          }
        }
      }
    }
  }
}
