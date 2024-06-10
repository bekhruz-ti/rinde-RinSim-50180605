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
package com.github.rinde.rinsim.pdptw.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PDPRoadModelWithDiversionTest extends PDPRoadModelCommonTest {

  public PDPRoadModelWithDiversionTest() {
    super(true);
  }

  /**
   * Check whether mixing diversion allowed/disallowed methods produce the
   * expected results.
   */
  @Test
  public void test() {
    // first move by using the location
    rm.moveTo(dv1, dp1.getPickupLocation(), time(10));
    assertEquals(rm.getPosition(dv1), dp1.getPickupLocation());
    pm.pickup(dv1, dp1, time(1));
    assertTrue(pm.containerContains(dv1, dp1));

    // now move by using the reference to the parcel
    rm.moveTo(dv1, dp1, time(300));
    assertEquals(rm.getPosition(dv1), dp1.getDestination());
  }
}
