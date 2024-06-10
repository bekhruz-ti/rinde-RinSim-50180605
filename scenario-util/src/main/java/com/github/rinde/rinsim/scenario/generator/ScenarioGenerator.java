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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import java.util.Iterator;
import java.util.List;

import javax.measure.Measure;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Length;
import javax.measure.quantity.Velocity;
import javax.measure.unit.Unit;

import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.model.Model;
import com.github.rinde.rinsim.core.model.pdp.PDPScenarioEvent;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModels;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.scenario.AddDepotEvent;
import com.github.rinde.rinsim.scenario.AddVehicleEvent;
import com.github.rinde.rinsim.scenario.Scenario;
import com.github.rinde.rinsim.scenario.Scenario.AbstractBuilder;
import com.github.rinde.rinsim.scenario.Scenario.ProblemClass;
import com.github.rinde.rinsim.scenario.TimedEvent;
import com.github.rinde.rinsim.scenario.generator.Depots.DepotGenerator;
import com.github.rinde.rinsim.scenario.generator.Models.ModelSupplierScenGen;
import com.github.rinde.rinsim.scenario.generator.Parcels.ParcelGenerator;
import com.github.rinde.rinsim.scenario.generator.Vehicles.VehicleGenerator;
import com.github.rinde.rinsim.util.TimeWindow;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

/**
 * A generator of {@link Scenario}s.
 * @author Rinde van Lon
 */
// TODO rename to Scenarios? or Generators?
public final class ScenarioGenerator {

  // global properties
  final Builder builder;
  final ImmutableList<Supplier<? extends Model<?>>> modelSuppliers;

  private final ParcelGenerator parcelGenerator;
  private final VehicleGenerator vehicleGenerator;
  private final DepotGenerator depotGenerator;

  ScenarioGenerator(Builder b) {
    builder = b;
    parcelGenerator = b.parcelGenerator;
    vehicleGenerator = b.vehicleGenerator;
    depotGenerator = b.depotGenerator;

    boolean containsRoadModelSupplier = false;
    final ImmutableList.Builder<Supplier<? extends Model<?>>> modelsBuilder = ImmutableList
        .builder();
    for (final ModelSupplierScenGen<?> sup : builder.modelSuppliers) {
      final Supplier<? extends Model<?>> modelSupplier = sup.get(this);
      if (modelSupplier.get() instanceof RoadModel) {
        containsRoadModelSupplier = true;
      }
      modelsBuilder.add(modelSupplier);
    }
    modelSuppliers = modelsBuilder.build();
    checkArgument(containsRoadModelSupplier,
        "A supplier of a RoadModel is mandatory. Found suppliers: %s.",
        modelSuppliers);
  }

  /**
   * @return The speed unit used in generated scenarios.
   */
  public Unit<Velocity> getSpeedUnit() {
    return builder.getSpeedUnit();
  }

  /**
   * @return The distance unit used in generated scenarios.
   */
  public Unit<Length> getDistanceUnit() {
    return builder.getDistanceUnit();
  }

  /**
   * @return The time unit used in generated scenarios.
   */
  public Unit<Duration> getTimeUnit() {
    return builder.getTimeUnit();
  }

  /**
   * @return The time window of generated scenarios.
   */
  public TimeWindow getTimeWindow() {
    return builder.getTimeWindow();
  }

  /**
   * @return The tick size of generated scenarios.
   */
  public long getTickSize() {
    return builder.getTickSize();
  }

  /**
   * @return The minimum position found in generated scenarios.
   */
  public Point getMin() {
    return parcelGenerator.getMin();
  }

  /**
   * @return The maximum position found in generated scenarios.
   */
  public Point getMax() {
    return parcelGenerator.getMax();
  }

  /**
   * @return The {@link ProblemClass} of the generated scenarios.
   */
  public ProblemClass getProblemClass() {
    return builder.problemClass;
  }

  /**
   * Generates a new {@link Scenario} instance.
   * @param rng The random number generator used for drawing random numbers.
   * @param id The id of this specific scenario.
   * @return A new instance.
   */
  // TODO change rng to seed?
  public Scenario generate(RandomGenerator rng, String id) {
    final ImmutableList.Builder<TimedEvent> b = ImmutableList.builder();
    // depots
    final Iterable<? extends AddDepotEvent> depots = depotGenerator.generate(
        rng.nextLong(), parcelGenerator.getCenter());
    b.addAll(depots);

    // vehicles
    final ImmutableList<AddVehicleEvent> vehicles = vehicleGenerator.generate(
        rng.nextLong(), parcelGenerator.getCenter(),
        builder.getTimeWindow().end);
    b.addAll(vehicles);

    final TravelTimes tm = createTravelTimes(modelSuppliers, getTimeUnit(),
        depots, vehicles);

    // parcels
    b.addAll(parcelGenerator.generate(rng.nextLong(), tm,
        builder.getTimeWindow().end));

    // time out
    b.add(new TimedEvent(PDPScenarioEvent.TIME_OUT, builder.getTimeWindow().end));

    // create
    return Scenario.builder(builder, builder.problemClass)
        .addModels(modelSuppliers)
        .addEvents(b.build())
        .instanceId(id)
        .build();
  }

  /**
   * Create a {@link Builder} for constructing {@link ScenarioGenerator}s.
   * @param problemClass The {@link ProblemClass} of the scenarios that will be
   *          generated by the generator under construction.
   * @return The builder.
   */
  public static Builder builder(ProblemClass problemClass) {
    return new Builder(problemClass);
  }

  /**
   * Create a {@link Builder} for constructing {@link ScenarioGenerator}s.
   * @return The builder.
   */
  public static Builder builder() {
    return new Builder(Scenario.DEFAULT_PROBLEM_CLASS);
  }

  /**
   * Creates a {@link TravelTimes} instance based on the specified
   * {@link Scenario}.
   * @param s The scenario.
   * @return The travel times.
   */
  public static TravelTimes createTravelTimes(Scenario s) {
    final Iterable<AddDepotEvent> depots = FluentIterable.from(s.asList())
        .filter(AddDepotEvent.class);
    final Iterable<AddVehicleEvent> vehicles = FluentIterable.from(s.asList())
        .filter(AddVehicleEvent.class);

    final List<RoadModel> roadModels = newArrayList();
    for (final Supplier<? extends Model<?>> sup : s.getModelSuppliers()) {
      final Model<?> m = sup.get();
      if (m instanceof RoadModel) {
        roadModels.add((RoadModel) m);
      }
    }
    checkArgument(roadModels.size() == 1);
    return new DefaultTravelTimes(roadModels.get(0), s.getTimeUnit(),
        depots, vehicles);
  }

  static TravelTimes createTravelTimes(
      Iterable<? extends Supplier<? extends Model<?>>> modelSuppliers,
      Unit<Duration> tu,
      Iterable<? extends AddDepotEvent> depots,
      Iterable<? extends AddVehicleEvent> vehicles) {
    final RoadModel rm = getRm(modelSuppliers);
    return new DefaultTravelTimes(rm, tu, depots, vehicles);
  }

  static RoadModel getRm(
      Iterable<? extends Supplier<? extends Model<?>>> modelSuppliers) {
    for (final Supplier<?> sup : modelSuppliers) {
      final Object v = sup.get();
      if (v instanceof RoadModel) {
        return (RoadModel) v;
      }
    }
    throw new IllegalArgumentException("There is no RoadModel supplier in "
        + modelSuppliers + ".");
  }

  /**
   * Builder for creating {@link ScenarioGenerator} instances.
   * @author Rinde van Lon
   */
  public static class Builder extends AbstractBuilder<Builder> {
    static final ParcelGenerator DEFAULT_PARCEL_GENERATOR = Parcels.builder()
        .build();
    static final VehicleGenerator DEFAULT_VEHICLE_GENERATOR = Vehicles
        .builder().build();
    static final DepotGenerator DEFAULT_DEPOT_GENERATOR = Depots
        .singleCenteredDepot();

    ParcelGenerator parcelGenerator;
    VehicleGenerator vehicleGenerator;
    DepotGenerator depotGenerator;
    final List<ModelSupplierScenGen<?>> modelSuppliers;
    final ProblemClass problemClass;

    Builder(ProblemClass pc) {
      super(Optional.<AbstractBuilder<?>> absent());
      problemClass = pc;
      parcelGenerator = DEFAULT_PARCEL_GENERATOR;
      vehicleGenerator = DEFAULT_VEHICLE_GENERATOR;
      depotGenerator = DEFAULT_DEPOT_GENERATOR;
      modelSuppliers = newArrayList();
    }

    // copying constructor
    Builder(Builder b) {
      super(Optional.<AbstractBuilder<?>> of(b));
      problemClass = b.problemClass;
      parcelGenerator = b.parcelGenerator;
      vehicleGenerator = b.vehicleGenerator;
      depotGenerator = b.depotGenerator;
      modelSuppliers = newArrayList(b.modelSuppliers);
    }

    @Override
    protected Builder self() {
      return this;
    }

    /**
     * Set the {@link VehicleGenerator} to use for adding vehicles to the
     * scenario.
     * @param vg The vehicle generator.
     * @return This, as per the builder pattern.
     */
    public Builder vehicles(VehicleGenerator vg) {
      vehicleGenerator = vg;
      return this;
    }

    /**
     * Set the {@link ParcelGenerator} to use for adding parcels to the
     * scenario.
     * @param pg The parcel generator.
     * @return This, as per the builder pattern.
     */
    public Builder parcels(ParcelGenerator pg) {
      parcelGenerator = pg;
      return this;
    }

    /**
     * Set the {@link DepotGenerator} to use for adding depots to the scenario.
     * @param ds The depot generator.
     * @return This, as per the builder pattern.
     */
    public Builder depots(DepotGenerator ds) {
      depotGenerator = ds;
      return this;
    }

    /**
     * Add a supplier of a {@link Model}. The provided supplier will use default
     * values provided by the {@link ScenarioGenerator} instance which is
     * currently being constructed. For most models implementations are
     * available in {@link Models}.
     * @param modelSup The supplier to add.
     * @return This, as per the builder pattern.
     */
    public Builder addModel(ModelSupplierScenGen<?> modelSup) {
      modelSuppliers.add(modelSup);
      return this;
    }

    /**
     * Add a supplier of a {@link Model}.
     * @param modelSup The supplier to add.
     * @return This, as per the builder pattern.
     */
    public Builder addModel(Supplier<? extends Model<?>> modelSup) {
      modelSuppliers.add(Models.adapt(modelSup));
      return this;
    }

    /**
     * @return Constructs a new {@link ScenarioGenerator} instance based on this
     *         builder.
     */
    public ScenarioGenerator build() {
      return new ScenarioGenerator(new Builder(this));
    }
  }

  /**
   * Implementations should provide information about travel times in a
   * scenario. The travel times are usually extracted from a {@link RoadModel}.
   * @author Rinde van Lon
   */
  public interface TravelTimes {
    /**
     * Computes the travel time between <code>from</code> and <code>to</code>
     * using the fastest available vehicle.
     * @param from The origin position.
     * @param to The destination position.
     * @return The expected travel time between the two positions.
     */
    long getShortestTravelTime(Point from, Point to);

    /**
     * Computes the travel time between <code>from</code> and the nearest depot
     * using the fastest available vehicle.
     * @param from The origin position.
     * @return The expected travel time between the two positions.
     */
    long getTravelTimeToNearestDepot(Point from);
  }

  static class DefaultTravelTimes implements TravelTimes {
    private final RoadModel roadModel;
    private final Measure<Double, Velocity> vehicleSpeed;
    private final Unit<Duration> timeUnit;
    private final ImmutableList<Point> depotLocations;

    DefaultTravelTimes(RoadModel rm, Unit<Duration> tu,
        Iterable<? extends AddDepotEvent> depots,
        Iterable<? extends AddVehicleEvent> vehicles) {
      roadModel = rm;

      double max = 0;
      for (final AddVehicleEvent ave : vehicles) {
        max = Math.max(max, ave.vehicleDTO.speed);
      }
      vehicleSpeed = Measure.valueOf(max, roadModel.getSpeedUnit());

      final ImmutableList.Builder<Point> depotBuilder = ImmutableList.builder();
      for (final AddDepotEvent ade : depots) {
        depotBuilder.add(ade.position);
      }
      depotLocations = depotBuilder.build();

      timeUnit = tu;
    }

    @Override
    public long getShortestTravelTime(Point from, Point to) {
      final Iterator<Point> path = roadModel.getShortestPathTo(from, to)
          .iterator();

      long travelTime = 0L;
      final Point prev = path.next();
      while (path.hasNext()) {
        final Point cur = path.next();
        final Measure<Double, Length> distance = Measure.valueOf(
            Point.distance(prev, cur), roadModel.getDistanceUnit());
        travelTime += RoadModels.computeTravelTime(vehicleSpeed, distance,
            timeUnit);
      }
      return travelTime;
    }

    @Override
    public long getTravelTimeToNearestDepot(Point from) {
      return getShortestTravelTime(from, findNearestDepot(from));
    }

    private Point findNearestDepot(Point from) {
      final Iterator<Point> it = depotLocations.iterator();
      Point nearestDepot = it.next();
      final double dist = Point.distance(from, nearestDepot);
      while (it.hasNext()) {
        final Point cur = it.next();
        final double d = Point.distance(from, cur);
        if (d < dist) {
          nearestDepot = cur;
        }
      }
      return nearestDepot;
    }
  }
}
