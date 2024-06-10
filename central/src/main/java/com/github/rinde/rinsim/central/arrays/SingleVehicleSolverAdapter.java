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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collection;

import javax.measure.quantity.Duration;
import javax.measure.unit.Unit;

import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.GlobalStateObject.VehicleStateObject;
import com.github.rinde.rinsim.central.arrays.ArraysSolvers.ArraysObject;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.google.common.collect.ImmutableList;

/**
 * Adapter for {@link SingleVehicleArraysSolver} to conform to the
 * {@link Solver} interface.
 * @author Rinde van Lon 
 */
public class SingleVehicleSolverAdapter implements Solver {

  private final SingleVehicleArraysSolver solver;
  private final Unit<Duration> outputTimeUnit;

  /**
   * @param solver The solver to use.
   * @param outputTimeUnit The time unit which is expected by the specified
   *          solver.
   */
  public SingleVehicleSolverAdapter(SingleVehicleArraysSolver solver,
      Unit<Duration> outputTimeUnit) {
    this.solver = solver;
    this.outputTimeUnit = outputTimeUnit;
  }

  @Override
  public ImmutableList<ImmutableList<ParcelDTO>> solve(GlobalStateObject state) {
    checkArgument(
        state.vehicles.size() == 1,
        "This solver can only deal with the single vehicle problem, found %s vehicles.",
        state.vehicles.size());

    final VehicleStateObject v = state.vehicles.iterator().next();
    checkArgument(
        v.remainingServiceTime == 0,
        "This solver can not deal with remaining service time, it should be 0, it was %s.",
        v.remainingServiceTime);
    final Collection<ParcelDTO> inCargo = v.contents;

    // there are always two locations: the current vehicle location and
    // the depot
    final int numLocations = 2 + (state.availableParcels.size() * 2)
        + inCargo.size();

    if (numLocations == 2) {
      // there are no orders
      final ImmutableList<ParcelDTO> empty = ImmutableList.of();
      return ImmutableList.of(empty);
    } else if (state.availableParcels.size() + inCargo.size() == 1) {
      // if there is only one order, the solution is trivial
      if (!state.availableParcels.isEmpty()) {
        // parcels on the map require two visits (one for pickup, one
        // for delivery)
        final ParcelDTO dto = state.availableParcels.iterator().next();
        return ImmutableList.of(ImmutableList.of(dto, dto));
      } // else
      return ImmutableList.of(ImmutableList.copyOf(inCargo));
    }
    // else, we are going to look for the optimal solution

    final ArraysObject ao = ArraysSolvers.toSingleVehicleArrays(state,
        outputTimeUnit);

    final SolutionObject[] curSols = ao.currentSolutions;
    final SolutionObject sol = solver.solve(ao.travelTime, ao.releaseDates,
        ao.dueDates, ao.servicePairs, ao.serviceTimes, curSols == null ? null
            : curSols[0]);

    return ImmutableList.of(ArraysSolvers.convertSolutionObject(sol,
        ao.index2parcel));
  }
}
