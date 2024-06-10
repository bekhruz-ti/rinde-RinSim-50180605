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
package com.github.rinde.rinsim.examples.core.comm;

import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;

import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.examples.core.comm.AgentCommunicationExample.Colors;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.ui.renderers.CanvasRenderer;
import com.github.rinde.rinsim.ui.renderers.UiSchema;
import com.github.rinde.rinsim.ui.renderers.ViewPort;
import com.github.rinde.rinsim.ui.renderers.ViewRect;

class MessagingLayerRenderer implements CanvasRenderer {

  private final RoadModel roadModel;
  private final UiSchema uiSchema;

  MessagingLayerRenderer(RoadModel rm, UiSchema uis) {
    roadModel = rm;
    uiSchema = uis;
  }

  @Override
  public void renderDynamic(GC gc, ViewPort vp, long time) {
    final int size = 4;
    uiSchema.initialize(gc.getDevice());

    final Set<RandomWalkAgent> objects = roadModel
        .getObjectsOfType(RandomWalkAgent.class);

    synchronized (objects) {
      for (final RandomWalkAgent a : objects) {
        Point p = a.getPosition();
        if (p == null) {
          continue;
        }
        final int x = (int) (vp.origin.x + (p.x - vp.rect.min.x) * vp.scale);
        final int y = (int) (vp.origin.y + (p.y - vp.rect.min.y) * vp.scale);

        final int radius = (int) (a.getRadius() * vp.scale);

        Color c = null;
        if (a.getReliability() < 0.15) {
          c = uiSchema.getColor(Colors.BLACK.name());
        } else if (a.getReliability() >= 0.15 && a.getReliability() < 0.3) {
          c = uiSchema.getColor(Colors.RED.name());
        } else {
          c = uiSchema.getColor(Colors.GREEN.name());
        }

        gc.setForeground(c);
        gc.setBackground(c);

        gc.setAlpha(50);
        gc.fillOval(x - radius, y - radius, 2 * radius, 2 * radius);
        gc.setAlpha(255);

        gc.fillOval(x - size, y - size, size * 2, size * 2);

        gc.drawOval(x - radius, y - radius, radius * 2, radius * 2);
        gc.drawText("r:" + a.getNoReceived(), x, y, true);

        final Set<RandomWalkAgent> communicatedWith = a.getCommunicatedWith();
        for (final RandomWalkAgent cw : communicatedWith) {
          p = cw.getPosition();
          if (p == null) {
            continue;
          }
          final int xCW = (int) (vp.origin.x + (p.x - vp.rect.min.x) * vp.scale);
          final int yCW = (int) (vp.origin.y + (p.y - vp.rect.min.y) * vp.scale);
          gc.drawLine(x, y, xCW, yCW);
        }
      }
    }
  }

  @Override
  public void renderStatic(GC gc, ViewPort vp) {}

  @Nullable
  @Override
  public ViewRect getViewRect() {
    return null;
  }
}
