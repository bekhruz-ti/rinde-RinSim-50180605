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

import javax.measure.quantity.Duration;
import javax.measure.unit.Unit;

import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.arrays.ArraysSolvers.MVArraysObject;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.google.common.collect.ImmutableList;

/**
 * Adapter for {@link MultiVehicleArraysSolver} to conform to the {@link Solver}
 * interface.
 * @author Rinde van Lon 
 */
public class MultiVehicleSolverAdapter implements Solver {

  private final MultiVehicleArraysSolver solver;
  private final Unit<Duration> outputTimeUnit;

  /**
   * @param solver The solver to use.
   * @param outputTimeUnit The time unit which is expected by the specified
   *          solver.
   */
  public MultiVehicleSolverAdapter(MultiVehicleArraysSolver solver,
      Unit<Duration> outputTimeUnit) {
    this.solver = solver;
    this.outputTimeUnit = outputTimeUnit;
  }

  @Override
  public ImmutableList<ImmutableList<ParcelDTO>> solve(GlobalStateObject state) {
    final MVArraysObject o = ArraysSolvers.toMultiVehicleArrays(state,
        outputTimeUnit);

    final SolutionObject[] sols = solver.solve(o.travelTime, o.releaseDates,
        o.dueDates, o.servicePairs, o.serviceTimes, o.vehicleTravelTimes,
        o.inventories, o.remainingServiceTimes, o.currentDestinations,
        o.currentSolutions);
    final ImmutableList.Builder<ImmutableList<ParcelDTO>> b = ImmutableList
        .builder();
    for (final SolutionObject sol : sols) {
      b.add(ArraysSolvers.convertSolutionObject(sol, o.index2parcel));
    }
    return b.build();
  }
}
