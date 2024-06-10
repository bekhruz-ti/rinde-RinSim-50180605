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
package com.github.rinde.rinsim.experiment.base;

/**
 * Implementors get notified of the progress of an experiment as specified by an
 * {@link ExperimentBuilder}.
 * @author Rinde van Lon
 */
public interface ResultListener {
  /**
   * This method is called to signal the start of an experiment.
   * @param numberOfSimulations The number of simulations that is going to be
   *          executed.
   */
  void startComputing(int numberOfSimulations);

  /**
   * This method is called to signal the completion of a single experiment.
   * @param result The {@link SimResult} of the simulation that is finished.
   */
  void receive(SimResultContainer result);

  /**
   * This method is called to signal the end of the experiment.
   */
  void doneComputing();
}
