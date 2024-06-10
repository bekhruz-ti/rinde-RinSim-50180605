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
package com.github.rinde.rinsim.central;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.unmodifiableList;

import java.util.List;

import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.google.common.collect.ImmutableList;

/**
 * Allows keeping track of the inputs and outputs of a {@link Solver}.
 * @author Rinde van Lon 
 */
public final class SolverDebugger implements Solver {
  private final List<GlobalStateObject> inputs;
  private final List<ImmutableList<ImmutableList<ParcelDTO>>> outputs;
  private final Solver delegate;
  private final boolean print;

  private SolverDebugger(Solver delegate, boolean debugPrints) {
    this.delegate = delegate;
    print = debugPrints;
    inputs = newArrayList();
    outputs = newArrayList();
  }

  @Override
  public ImmutableList<ImmutableList<ParcelDTO>> solve(GlobalStateObject state) {
    if (print) {
      System.out.println(state);
    }
    inputs.add(state);
    final ImmutableList<ImmutableList<ParcelDTO>> result = delegate
        .solve(state);
    outputs.add(result);
    if (print) {
      System.out.println(result);
    }
    return result;
  }

  /**
   * @return An unmodifiable view on the inputs that were used when calling
   *         {@link #solve(GlobalStateObject)}. The list is in order of
   *         invocation.
   */
  public List<GlobalStateObject> getInputs() {
    return unmodifiableList(inputs);
  }

  /**
   * @return An unmodifiable view on the outputs that are generated when calling
   *         {@link #solve(GlobalStateObject)}. The list is in order of
   *         invocation.
   */
  public List<ImmutableList<ImmutableList<ParcelDTO>>> getOutputs() {
    return unmodifiableList(outputs);
  }

  /**
   * Wraps the specified solver in a {@link SolverDebugger} instance to allow
   * easier debugging of a solver.
   * @param s The solver to wrap.
   * @param print Whether debug printing should be enabled.
   * @return The wrapped solver.
   */
  public static SolverDebugger wrap(Solver s, boolean print) {
    return new SolverDebugger(s, print);
  }
}
