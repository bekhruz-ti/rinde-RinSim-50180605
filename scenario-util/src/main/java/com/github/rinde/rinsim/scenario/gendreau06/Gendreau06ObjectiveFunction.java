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

import java.io.Serializable;

import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.common.StatisticsDTO;

/**
 * Objective function for Gendreau et al. (2006) problem instances.
 * @author Rinde van Lon 
 */
public final class Gendreau06ObjectiveFunction implements ObjectiveFunction,
    Serializable {
  private static final long serialVersionUID = 6069190376442772396L;
  private static final Gendreau06ObjectiveFunction INSTANCE = new Gendreau06ObjectiveFunction();
  private static final double MS_TO_MINUTES = 60000d;
  private static final double ALPHA = 1d;
  private static final double BETA = 1d;

  private Gendreau06ObjectiveFunction() {}

  /**
   * All parcels need to be delivered, all vehicles need to be back at the
   * depot.
   * @param stats The statistics object to check for validity.
   * @return <code>true</code> when the statistics object represents a valid
   *         simulation run, <code>false</code> otherwise.
   */
  @Override
  public boolean isValidResult(StatisticsDTO stats) {
    return stats.totalParcels == stats.acceptedParcels
        && stats.totalParcels == stats.totalPickups
        && stats.totalParcels == stats.totalDeliveries
        && stats.simFinish
        && stats.totalVehicles == stats.vehiclesAtDepot;
  }

  /**
   * Computes the cost according to the definition of the paper: <i>the cost
   * function used throughout this work is to minimize a weighted sum of three
   * different criteria: total travel time, sum of lateness over all pick-up and
   * delivery locations and sum of overtime over all vehicles</i>. The function
   * is defined as:
   * <code>sum(Tk) + alpha sum(max(0,tv-lv)) + beta sum(max(0,tk-l0))</code>
   * Where: Tk is the total travel time on route Rk, alpha and beta are
   * weighting parameters which were set to 1 in the paper. The definition of
   * lateness: <code>max(0,lateness)</code> is commonly referred to as
   * <i>tardiness</i>. All times are expressed in minutes.
   * @param stats The statistics object to compute the cost for.
   * @return The cost.
   */
  @Override
  public double computeCost(StatisticsDTO stats) {
    final double totalTravelTime = travelTime(stats);
    final double sumTardiness = tardiness(stats);
    final double overTime = overTime(stats);
    return totalTravelTime + ALPHA * sumTardiness + BETA * overTime;
  }

  @Override
  public String printHumanReadableFormat(StatisticsDTO stats) {
    return new StringBuilder().append("Travel time: ")
        .append(travelTime(stats)).append("\nTardiness: ")
        .append(tardiness(stats)).append("\nOvertime: ")
        .append(overTime(stats)).append("\nTotal: ").append(computeCost(stats))
        .toString();

  }

  /**
   * Computes the travel time based on the {@link StatisticsDTO}.
   * @param stats The statistics.
   * @return The travel time in minutes.
   */
  public double travelTime(StatisticsDTO stats) {
    // avg speed is 30 km/h
    // = (dist / 30.0) * 60.0
    return stats.totalDistance * 2d;
  }

  /**
   * Computes the tardiness based on the {@link StatisticsDTO}.
   * @param stats The statistics.
   * @return The tardiness in minutes.
   */
  public double tardiness(StatisticsDTO stats) {
    return (stats.pickupTardiness + stats.deliveryTardiness) / MS_TO_MINUTES;
  }

  /**
   * Computes the over time based on the {@link StatisticsDTO}.
   * @param stats The statistics.
   * @return The over time in minutes.
   */
  public double overTime(StatisticsDTO stats) {
    return stats.overTime / MS_TO_MINUTES;
  }

  @Override
  public String toString() {
    return this.getClass().getName();
  }

  /**
   * @return The instance.
   */
  public static Gendreau06ObjectiveFunction instance() {
    return INSTANCE;
  }
}
