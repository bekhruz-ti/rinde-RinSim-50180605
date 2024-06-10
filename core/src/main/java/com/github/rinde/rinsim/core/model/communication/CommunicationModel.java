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
package com.github.rinde.rinsim.core.model.communication;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rinde.rinsim.core.TickListener;
import com.github.rinde.rinsim.core.TimeLapse;
import com.github.rinde.rinsim.core.model.Model;
import com.github.rinde.rinsim.core.model.road.RoadUser;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Predicate;

/**
 * The communication model. Messages are send at the end of a current tick.
 * @author Bartosz Michalik 
 * @author Rinde van Lon 
 * @since 2.0
 */
public class CommunicationModel implements Model<CommunicationUser>,
    TickListener, CommunicationAPI {

  // TODO remove try-catch blocks

  protected static final Logger LOGGER = LoggerFactory
      .getLogger(CommunicationModel.class);

  protected final Set<CommunicationUser> users;
  protected List<Entry<CommunicationUser, Message>> sendQueue;
  protected RandomGenerator generator;
  private final boolean ignoreDistances;

  /**
   * Constructs the communication model.
   * @param pGenerator the random number generator that is used for reliability
   *          computations
   * @param pIgnoreDistances when <code>true</code> the distances constrains are
   *          ignored.
   */
  public CommunicationModel(RandomGenerator pGenerator, boolean pIgnoreDistances) {
    checkArgument(pGenerator != null, "generator can not be null");
    users = new LinkedHashSet<CommunicationUser>();
    sendQueue = new LinkedList<Entry<CommunicationUser, Message>>();
    generator = pGenerator;
    ignoreDistances = pIgnoreDistances;
  }

  /**
   * Construct the communication model that respects the distance constrains
   * @param pGenerator the random number generator that is used for reliability
   *          computations
   */
  public CommunicationModel(RandomGenerator pGenerator) {
    this(pGenerator, false);
  }

  /**
   * Register communication user {@link CommunicationUser}. Communication user
   * is registered only when it is also {@link RoadUser}. This is required as
   * communication model depends on elements positions.
   */
  @Override
  public boolean register(CommunicationUser element) {
    if (element == null) {
      throw new IllegalArgumentException("element can not be null");
    }
    final boolean result = users.add(element);
    if (!result) {
      return false;
    }
    // callback
    try {
      element.setCommunicationAPI(this);
    } catch (final Exception e) {
      // if you miss-behave you don't deserve to use our infrastructure :D
      LOGGER
          .warn("callback for the communication user failed. Unregistering", e);
      users.remove(element);
      return false;
    }
    return true;
  }

  @Override
  public boolean unregister(CommunicationUser element) {
    if (element == null) {
      return false;
    }
    final List<Entry<CommunicationUser, Message>> toRemove = new LinkedList<Entry<CommunicationUser, Message>>();
    for (final Entry<CommunicationUser, Message> e : sendQueue) {
      if (element.equals(e.getKey())
          || element.equals(e.getValue().getSender())) {
        toRemove.add(e);
      }
    }
    sendQueue.removeAll(toRemove);

    return users.remove(element);
  }

  @Override
  public Class<CommunicationUser> getSupportedType() {
    return CommunicationUser.class;
  }

  @Override
  public void tick(TimeLapse tl) {
    // empty implementation
  }

  @Override
  public void afterTick(TimeLapse tl) {
    long timeMillis = System.currentTimeMillis();
    final List<Entry<CommunicationUser, Message>> cache = sendQueue;
    sendQueue = new LinkedList<Entry<CommunicationUser, Message>>();
    for (final Entry<CommunicationUser, Message> e : cache) {
      try {
        e.getKey().receive(e.getValue());
        // TODO [bm] add msg delivered event
      } catch (final Exception e1) {
        LOGGER.warn("unexpected exception while passing message", e1);
      }
    }
    if (LOGGER.isDebugEnabled()) {
      timeMillis = System.currentTimeMillis() - timeMillis;
      LOGGER.debug("broadcast lasted for:" + timeMillis);
    }
  }

  @Override
  public void send(CommunicationUser recipient, Message message) {
    if (!users.contains(recipient)) {
      // TODO [bm] implement dropped message EVENT
      return;
    }

    if (new CanCommunicate(message.sender).apply(recipient)) {
      sendQueue.add(SimpleEntry.entry(recipient, message));
    } else {
      // TODO [bm] implement dropped message EVENT
      return;
    }

  }

  @Override
  public void broadcast(Message message) {
    broadcast(message, new CanCommunicate(message.sender));
  }

  @Override
  public void broadcast(Message message, Class<? extends CommunicationUser> type) {
    broadcast(message, new CanCommunicate(message.sender, type));

  }

  private void broadcast(Message message, Predicate<CommunicationUser> predicate) {
    if (!users.contains(message.sender)) {
      return;
    }
    final HashSet<CommunicationUser> uSet = new HashSet<CommunicationUser>(
        users.size() / 2);

    for (final CommunicationUser u : users) {
      if (predicate.apply(u)) {
        uSet.add(u);
      }
    }

    for (final CommunicationUser u : uSet) {
      try {
        sendQueue.add(SimpleEntry.entry(u, message.clone()));
      } catch (final CloneNotSupportedException e) {
        LOGGER.error("clonning exception for message", e);
      }
    }
  }

  /**
   * Check if an message from a given sender can be deliver to recipient
   * @see CanCommunicate#apply(CommunicationUser)
   * @author Bartosz Michalik 
   * @since 2.0
   */
  class CanCommunicate implements Predicate<CommunicationUser> {

    private final Class<? extends CommunicationUser> clazz;
    private final CommunicationUser sender;
    private Rectangle rec;

    public CanCommunicate(CommunicationUser sender,
        Class<? extends CommunicationUser> clazz) {
      this.sender = sender;
      this.clazz = clazz;
      if (sender.getPosition() != null) {
        rec = new Rectangle(sender.getPosition(), sender.getRadius());
      }
    }

    public CanCommunicate(CommunicationUser sender) {
      this(sender, null);
    }

    @Override
    public boolean apply(CommunicationUser input) {
      if (input == null || rec == null) {
        return false;
      }
      if (clazz != null && !clazz.equals(input.getClass())) {
        return false;
      }
      if (input.equals(sender)) {
        return false;
      }
      final Point iPos = input.getPosition();
      if (!ignoreDistances && !rec.contains(iPos)) {
        return false;
      }
      final double prob = input.getReliability() * sender.getReliability();
      final double minRadius = Math.min(input.getRadius(), sender.getRadius());
      final double rand = generator.nextDouble();
      final Point sPos = sender.getPosition();
      return prob > rand
          && (ignoreDistances ? true : Point.distance(sPos, iPos) <= minRadius);
    }
  }

  private static class Rectangle {
    private final double y1;
    private final double x1;
    private final double y0;
    private final double x0;

    public Rectangle(Point p, double radius) {
      x0 = p.x - radius;
      y0 = p.y - radius;
      x1 = p.x + radius;
      y1 = p.y + radius;
    }

    public boolean contains(Point p) {
      if (p == null) {
        return false;
      }
      if (p.x < x0 || p.x > x1) {
        return false;
      }
      if (p.y < y0 || p.y > y1) {
        return false;
      }
      return true;
    }
  }

  protected static class SimpleEntry<K, V> implements Entry<K, V> {

    private final V value;
    private final K key;

    public SimpleEntry(K key, V value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      return value;
    }

    @Override
    public V setValue(V value) {
      return null;
    }

    static <V, K> Entry<V, K> entry(V v, K k) {
      return new SimpleEntry<V, K>(v, k);
    }

  }

}
