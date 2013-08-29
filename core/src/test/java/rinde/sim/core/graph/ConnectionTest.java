/**
 * 
 */
package rinde.sim.core.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.measure.Measure;
import javax.measure.quantity.Velocity;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.junit.Test;

/**
 * @author Rinde van Lon (rinde.vanlon@cs.kuleuven.be)
 * 
 */
public class ConnectionTest {

  private static final double DELTA = 0.0001;

  @Test
  public void unmodifiableConnection() {
    final Connection<LengthData> original = new Connection<LengthData>(
        new Point(2, 2), new Point(3, 3), null);
    final Connection<LengthData> unmod = Graphs
        .unmodifiableConnection(original);

    assertEquals(unmod, original);
    assertEquals(original, unmod);
    original.setData(new LengthData(300));

    assertFalse(original.getData().equals(null));

    assertEquals(original.getData(), unmod.getData());
    assertEquals(original.getData().hashCode(), unmod.getData().hashCode());
    assertEquals(original.getData().getLength(), unmod.getData().getLength(), DELTA);
    assertEquals(original.hashCode(), unmod.hashCode());
    assertEquals(original.toString(), unmod.toString());
    assertEquals(unmod, original);
    assertEquals(original, unmod);

    original.setData(null);
    assertEquals(unmod, original);
    assertEquals(original, unmod);
  }

  @Test
  public void unmodifiableMultiAttributeEdgeData() {
    final Connection<MultiAttributeData> original = new Connection<MultiAttributeData>(
        new Point(2, 2), new Point(3, 3), null);
    final Connection<MultiAttributeData> unmod = Graphs
        .unmodifiableConnection(original);

    assertEquals(unmod, original);
    assertEquals(original, unmod);
    original.setData(new MultiAttributeData(10, Measure
        .valueOf(20d, NonSI.KILOMETERS_PER_HOUR)));
    assertEquals(unmod, original);
    assertEquals(original, unmod);

    original.getData().put("test", Arrays.asList(1, 2, 3));
    assertEquals(unmod, original);
    assertEquals(original, unmod);
    assertEquals(Arrays.asList(1, 2, 3), unmod.getData()
        .get("test", Object.class));
    assertEquals(Arrays.asList(1, 2, 3), unmod.getData()
        .get("test", List.class));
    assertNull(unmod.getData().get("test", Map.class));
    assertNull(unmod.getData().get("test2", Map.class));

    assertEquals(original.getData().getLength(), unmod.getData().getLength(), DELTA);
    // both are null
    assertEquals(original.getData().getMaxSpeed(), unmod.getData()
        .getMaxSpeed());

    assertTrue(original.getData().equals(unmod.getData()));
    assertTrue(unmod.getData().equals(unmod.getData()));
    assertEquals(unmod.getData().hashCode(), unmod.getData().hashCode());
    assertEquals(original.hashCode(), unmod.hashCode());

    original.getData().put(MultiAttributeData.KEY_LENGTH, new Object());
    assertEquals(original, unmod);
    original.getData().put(MultiAttributeData.KEY_MAX_SPEED, new Object());
    assertEquals(original, unmod);

    assertNull(original.getData().getMaxSpeed());
    assertTrue(Double.isNaN(original.getData().getLength()));

    final Measure<Double, Velocity> m100mps = Measure
        .valueOf(100d, SI.METERS_PER_SECOND);
    final Measure<Double, Velocity> m200mps = Measure
        .valueOf(200d, SI.METERS_PER_SECOND);
    assertNull(original.getData().setMaxSpeed(m100mps));
    assertEquals(m100mps, original.getData().setMaxSpeed(m200mps));

    assertEquals(original, unmod);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void unmodMultiAttED() {
    Graphs.unmodifiableConnectionData(new MultiAttributeData(10,
        Measure.valueOf(20d, NonSI.KILOMETERS_PER_HOUR))).setMaxSpeed(null);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void unmodMultiAttED2() {
    Graphs.unmodifiableConnectionData(new MultiAttributeData(10,
        Measure.valueOf(20d, NonSI.KILOMETERS_PER_HOUR))).put("", null);
  }

  @Test
  public void equalsTest() {
    final Connection<LengthData> c1 = new Connection<LengthData>(
        new Point(2, 2), new Point(3, 3), null);
    final Connection<LengthData> c2 = new Connection<LengthData>(
        new Point(2, 2), new Point(2, 3), null);
    final Connection<LengthData> c3 = new Connection<LengthData>(
        new Point(3, 2), new Point(2, 3), null);
    final Connection<LengthData> c4 = new Connection<LengthData>(
        new Point(2, 2), new Point(3, 3), new LengthData(30));
    assertFalse(c1.equals(new Object()));
    assertFalse(c1.equals(c2));
    assertFalse(c1.equals(c3));
    assertFalse(c1.equals(c4));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void unmodifiableConnSetEdgeData() {
    Graphs.unmodifiableConnection(new Connection<LengthData>(new Point(2, 2),
        new Point(3, 3), null)).setData(null);
  }
}
