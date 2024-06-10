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
package com.github.rinde.rinsim.experiment;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.github.rinde.rinsim.experiment.ExperimentProgressBar;
import com.github.rinde.rinsim.testutil.GuiTests;

@Category(GuiTests.class)
public class ExperimentProgressBarTest {

  @SuppressWarnings("null")
  @Test
  public void test() {
    final ExperimentProgressBar pb = new ExperimentProgressBar();
    pb.startComputing(30);
    for (int i = 0; i < 30; i++) {
      try {
        Thread.sleep(50);
      } catch (final InterruptedException e) {
        throw new IllegalStateException();
      }
      pb.receive(null);
    }
    pb.doneComputing();
  }

}
