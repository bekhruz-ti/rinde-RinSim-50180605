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
package com.github.rinde.rinsim.core;

import java.util.HashSet;

import com.github.rinde.rinsim.core.SimulatorTest.DummyObject;
import com.github.rinde.rinsim.core.model.Model;

public class DummyModel implements Model<DummyObject> {

	private final HashSet<DummyObject> objs;

	public DummyModel() {
		objs = new HashSet<DummyObject>();
	}

	@Override
	public boolean register(DummyObject element) {
		return objs.add(element);
	}

	@Override
	public boolean unregister(DummyObject element) {
		return objs.remove(element);
	}

	@Override
	public Class<DummyObject> getSupportedType() {
		return DummyObject.class;
	}
}