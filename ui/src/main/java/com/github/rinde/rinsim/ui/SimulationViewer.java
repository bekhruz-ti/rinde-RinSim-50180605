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
package com.github.rinde.rinsim.ui;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.TickListener;
import com.github.rinde.rinsim.core.TimeLapse;
import com.github.rinde.rinsim.core.model.ModelReceiver;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.ui.renderers.CanvasRenderer;
import com.github.rinde.rinsim.ui.renderers.PanelRenderer;
import com.github.rinde.rinsim.ui.renderers.Renderer;
import com.github.rinde.rinsim.ui.renderers.ViewPort;
import com.github.rinde.rinsim.ui.renderers.ViewRect;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

/**
 * Simulation viewer.
 * 
 * @author Bartosz Michalik 
 * @author Rinde van Lon 
 */
final class SimulationViewer extends Composite implements TickListener,
    ControlListener, PaintListener, SelectionListener {

  static final PeriodFormatter FORMATTER = new PeriodFormatterBuilder()
      .appendDays()
      .appendSeparator(" ")
      .minimumPrintedDigits(2)
      .printZeroAlways()
      .appendHours()
      .appendLiteral(":")
      .appendMinutes()
      .appendLiteral(":")
      .appendSeconds()
      .toFormatter();

  // TODO need to refactor this class in separate logical parts:
  // Time stuff: receives ticks and decides when gui should be updated.
  // > show fps?
  // Time display: move into separate TimeRenderer
  // Canvas stuff: zooming, scrolling, renderers
  // Menu stuff: accelerators/names

  private static final int MIN_SPEED_UP = 1;
  private static final int MAX_SPEED_UP = 512;
  private static final int MAX_ZOOM_LEVEL = 16;

  boolean firstTime = true;
  final Simulator simulator;
  @Nullable
  ViewRect viewRect;
  @Nullable
  Label timeLabel;

  private Canvas canvas;
  private org.eclipse.swt.graphics.Point origin;
  private org.eclipse.swt.graphics.Point size;

  @Nullable
  private Image image;
  private final ImmutableList<PanelRenderer> panelRenderers;
  private final List<CanvasRenderer> renderers;
  private final Set<ModelReceiver> modelRenderers;
  private final boolean autoPlay;
  private MenuItem playPauseMenuItem;
  // multiplier
  private double m;

  @Nullable
  private ScrollBar hBar;
  @Nullable
  private ScrollBar vBar;

  // rendering frequency related
  private int speedUp;
  private long lastRefresh;

  private int zoomRatio;
  private final Display display;
  private final Map<MenuItems, Integer> accelerators;

  SimulationViewer(Shell shell, final Simulator sim, int pSpeedUp,
      boolean pAutoPlay, List<Renderer> pRenderers, Map<MenuItems, Integer> acc) {
    super(shell, SWT.NONE);

    accelerators = acc;
    autoPlay = pAutoPlay;

    final Multimap<Integer, PanelRenderer> panels = LinkedHashMultimap.create();
    renderers = newArrayList();
    modelRenderers = newLinkedHashSet();
    for (final Renderer r : pRenderers) {
      if (r instanceof ModelReceiver) {
        modelRenderers.add((ModelReceiver) r);
      }
      boolean valid = false;
      if (r instanceof PanelRenderer) {
        panels.put(((PanelRenderer) r).getPreferredPosition(),
            (PanelRenderer) r);
        valid = true;
      }

      if (r instanceof CanvasRenderer) {
        renderers.add((CanvasRenderer) r);
        valid = true;
      }

      checkState(valid, "A renderer was not of a recognized subtype: %s", r);

      if (r instanceof TickListener) {
        sim.addTickListener((TickListener) r);
      }
    }
    panelRenderers = ImmutableList.copyOf(panels.values());

    simulator = sim;
    simulator.addTickListener(this);

    speedUp = pSpeedUp;
    shell.setLayout(new FillLayout());
    display = shell.getDisplay();
    setLayout(new FillLayout());

    createMenu(shell);
    panelsLayout(panels);
  }

  void panelsLayout(Multimap<Integer, PanelRenderer> panels) {
    if (panels.isEmpty()) {
      createContent(this);
    } else {

      final SashForm vertical = new SashForm(this, SWT.VERTICAL | SWT.SMOOTH);
      vertical.setLayout(new FillLayout());

      final int topHeight = configurePanels(vertical, panels.removeAll(SWT.TOP));

      final SashForm horizontal = new SashForm(vertical, SWT.HORIZONTAL
          | SWT.SMOOTH);
      horizontal.setLayout(new FillLayout());

      final int leftWidth = configurePanels(horizontal,
          panels.removeAll(SWT.LEFT));

      // create canvas
      createContent(horizontal);

      final int rightWidth = configurePanels(horizontal,
          panels.removeAll(SWT.RIGHT));
      final int bottomHeight = configurePanels(vertical,
          panels.removeAll(SWT.BOTTOM));

      final int canvasHeight = size.y - topHeight - bottomHeight;
      if (topHeight > 0 && bottomHeight > 0) {
        vertical.setWeights(varargs(topHeight, canvasHeight, bottomHeight));
      } else if (topHeight > 0) {
        vertical.setWeights(varargs(topHeight, canvasHeight));
      } else if (bottomHeight > 0) {
        vertical.setWeights(varargs(canvasHeight, bottomHeight));
      }

      final int canvasWidth = size.x - leftWidth - rightWidth;
      if (leftWidth > 0 && rightWidth > 0) {
        horizontal.setWeights(varargs(leftWidth, canvasWidth, rightWidth));
      } else if (leftWidth > 0) {
        horizontal.setWeights(varargs(leftWidth, canvasWidth));
      } else if (rightWidth > 0) {
        horizontal.setWeights(varargs(canvasWidth, rightWidth));
      }

      checkState(panels.isEmpty(),
          "Invalid preferred position set for panels: %s", panels.values());
    }
  }

  static int[] varargs(int... ints) {
    return ints;
  }

  int configurePanels(SashForm parent, Collection<PanelRenderer> panels) {
    if (panels.isEmpty()) {
      return 0;
    }

    int prefSize = 0;
    for (final PanelRenderer p : panels) {
      prefSize = Math.max(p.preferredSize(), prefSize);
    }
    if (panels.size() == 1) {
      final PanelRenderer p = panels.iterator().next();
      final Group g = new Group(parent, SWT.SHADOW_NONE);
      p.initializePanel(g);
    } else {
      final TabFolder tab = new TabFolder(parent, SWT.NONE);

      for (final PanelRenderer p : panels) {
        final TabItem ti = new TabItem(tab, SWT.NONE);
        ti.setText(p.getName());
        final Composite comp = new Composite(tab, SWT.NONE);
        ti.setControl(comp);
        p.initializePanel(comp);
      }
    }
    return prefSize;
  }

  void configureModelRenderers() {
    for (final ModelReceiver mr : modelRenderers) {
      mr.registerModelProvider(simulator.getModelProvider());
    }
  }

  /**
   * Configure shell.
   */
  void createContent(Composite parent) {
    canvas = new Canvas(parent, SWT.DOUBLE_BUFFERED | SWT.NONE
        | SWT.NO_REDRAW_RESIZE | SWT.V_SCROLL | SWT.H_SCROLL);
    canvas.setBackground(display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));

    origin = new org.eclipse.swt.graphics.Point(0, 0);
    size = new org.eclipse.swt.graphics.Point(800, 500);
    canvas.addPaintListener(this);
    canvas.addControlListener(this);
    this.layout();

    timeLabel = new Label(canvas, SWT.NONE);
    timeLabel.setText("hello world");
    timeLabel.pack();
    timeLabel.setLocation(50, 10);
    timeLabel
        .setBackground(canvas.getDisplay().getSystemColor(SWT.COLOR_WHITE));

    hBar = canvas.getHorizontalBar();
    hBar.addSelectionListener(this);
    vBar = canvas.getVerticalBar();
    vBar.addSelectionListener(this);
  }

  @SuppressWarnings("unused")
  void createMenu(Shell shell) {
    final Menu bar = new Menu(shell, SWT.BAR);
    shell.setMenuBar(bar);

    final MenuItem fileItem = new MenuItem(bar, SWT.CASCADE);
    fileItem.setText("&Control");

    final Menu submenu = new Menu(shell, SWT.DROP_DOWN);
    fileItem.setMenu(submenu);

    // play switch
    playPauseMenuItem = new MenuItem(submenu, SWT.PUSH);
    playPauseMenuItem.setText("&Play\tCtrl+P");
    playPauseMenuItem.setAccelerator(accelerators.get(MenuItems.PLAY));
    playPauseMenuItem.addListener(SWT.Selection, new Listener() {

      @Override
      public void handleEvent(@Nullable Event e) {
        checkState(e != null);
        onToglePlay((MenuItem) e.widget);
      }
    });

    new MenuItem(submenu, SWT.SEPARATOR);
    // step execution switch
    final MenuItem nextItem = new MenuItem(submenu, SWT.PUSH);
    nextItem.setText("Next tick\tCtrl+Shift+]");
    nextItem.setAccelerator(accelerators.get(MenuItems.NEXT_TICK));
    nextItem.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(@Nullable Event e) {
        checkState(e != null);
        onTick((MenuItem) e.widget);
      }
    });

    // view options

    final MenuItem viewItem = new MenuItem(bar, SWT.CASCADE);
    viewItem.setText("&View");

    final Menu viewMenu = new Menu(shell, SWT.DROP_DOWN);
    viewItem.setMenu(viewMenu);

    // zooming
    final MenuItem zoomInItem = new MenuItem(viewMenu, SWT.PUSH);
    zoomInItem.setText("Zoom &in\tCtrl++");
    zoomInItem.setAccelerator(accelerators.get(MenuItems.ZOOM_IN));
    zoomInItem.setData(MenuItems.ZOOM_IN);

    final MenuItem zoomOutItem = new MenuItem(viewMenu, SWT.PUSH);
    zoomOutItem.setText("Zoom &out\tCtrl+-");
    zoomOutItem.setAccelerator(accelerators.get(MenuItems.ZOOM_OUT));
    zoomOutItem.setData(MenuItems.ZOOM_OUT);

    final Listener zoomingListener = new Listener() {
      @Override
      public void handleEvent(@Nullable Event e) {
        checkState(e != null);
        onZooming((MenuItem) e.widget);
      }
    };
    zoomInItem.addListener(SWT.Selection, zoomingListener);
    zoomOutItem.addListener(SWT.Selection, zoomingListener);

    // speedUp

    final Listener speedUpListener = new Listener() {

      @Override
      public void handleEvent(@Nullable Event e) {
        checkState(e != null);
        onSpeedChange((MenuItem) e.widget);
      }
    };

    final MenuItem increaseSpeedItem = new MenuItem(submenu, SWT.PUSH);
    increaseSpeedItem
        .setAccelerator(accelerators.get(MenuItems.INCREASE_SPEED));
    increaseSpeedItem.setText("Speed &up\tCtrl+]");
    increaseSpeedItem.setData(MenuItems.INCREASE_SPEED);
    increaseSpeedItem.addListener(SWT.Selection, speedUpListener);
    //
    final MenuItem decreaseSpeed = new MenuItem(submenu, SWT.PUSH);
    decreaseSpeed.setAccelerator(accelerators.get(MenuItems.DECREASE_SPEED));
    decreaseSpeed.setText("Slow &down\tCtrl+[");
    decreaseSpeed.setData(MenuItems.DECREASE_SPEED);
    decreaseSpeed.addListener(SWT.Selection, speedUpListener);

  }

  /**
   * Default implementation of the play/pause action. Can be overridden if
   * needed.
   * 
   * @param source
   */
  void onToglePlay(MenuItem source) {
    if (simulator.isPlaying()) {
      source.setText("&Play\tCtrl+P");
    } else {
      source.setText("&Pause\tCtrl+P");
    }
    new Thread() {
      @Override
      public void run() {
        simulator.togglePlayPause();
      }
    }.start();
  }

  /**
   * Default implementation of step execution action. Can be overridden if
   * needed.
   * 
   * @param source
   */
  void onTick(MenuItem source) {
    if (simulator.isPlaying()) {
      simulator.stop();
    }
    simulator.tick();
  }

  void onZooming(MenuItem source) {
    if (source.getData() == MenuItems.ZOOM_IN) {
      if (zoomRatio == MAX_ZOOM_LEVEL) {
        return;
      }
      m *= 2;
      origin.x *= 2;
      origin.y *= 2;
      zoomRatio <<= 1;
    } else {
      if (zoomRatio < 2) {
        return;
      }
      m /= 2;
      origin.x /= 2;
      origin.y /= 2;
      zoomRatio >>= 1;
    }
    if (image != null) {
      image.dispose();
    }
    // this forces a redraw
    image = null;
    canvas.redraw();
  }

  void onSpeedChange(MenuItem source) {
    if (source.getData() == MenuItems.INCREASE_SPEED) {
      if (speedUp < MAX_SPEED_UP) {
        speedUp <<= 1;
      }
    } else {
      if (speedUp > MIN_SPEED_UP) {
        speedUp >>= 1;
      }
    }
  }

  Image renderStatic() {
    size = new org.eclipse.swt.graphics.Point((int) (m * viewRect.width),
        (int) (m * viewRect.height));
    final Image img = new Image(getDisplay(), size.x, size.y);
    final GC gc = new GC(img);

    for (final CanvasRenderer r : renderers) {
      r.renderStatic(gc, new ViewPort(new Point(0, 0), viewRect, m));
    }
    gc.dispose();
    return img;
  }

  @Override
  public void paintControl(@Nullable PaintEvent e) {
    checkState(e != null);
    final GC gc = e.gc;

    final boolean wasFirstTime = firstTime;
    if (firstTime) {
      configureModelRenderers();
      calculateSizes();
      firstTime = false;
    }

    if (image == null) {
      image = renderStatic();
      updateScrollbars(false);
    }

    final org.eclipse.swt.graphics.Point center = getCenteredOrigin();

    gc.drawImage(image, center.x, center.y);
    for (final CanvasRenderer renderer : renderers) {
      renderer.renderDynamic(gc, new ViewPort(new Point(center.x,
          center.y),
          viewRect, m), simulator.getCurrentTime());
    }
    for (final PanelRenderer renderer : panelRenderers) {
      renderer.render();
    }

    final Rectangle content = image.getBounds();
    final Rectangle client = canvas.getClientArea();

    hBar.setVisible(content.width > client.width);
    vBar.setVisible(content.height > client.height);

    // auto play sim if required
    if (wasFirstTime && autoPlay) {
      onToglePlay(playPauseMenuItem);
    }
  }

  org.eclipse.swt.graphics.Point getCenteredOrigin() {
    final Rectangle rect = image.getBounds();
    final Rectangle client = canvas.getClientArea();
    final int zeroX = client.x + client.width / 2 - rect.width / 2;
    final int zeroY = client.y + client.height / 2 - rect.height / 2;
    return new org.eclipse.swt.graphics.Point(origin.x + zeroX, origin.y
        + zeroY);
  }

  void updateScrollbars(boolean adaptToScrollbar) {
    final Rectangle rect = image.getBounds();
    final Rectangle client = canvas.getClientArea();

    hBar.setMaximum(rect.width);
    vBar.setMaximum(rect.height);
    hBar.setThumb(Math.min(rect.width, client.width));
    vBar.setThumb(Math.min(rect.height, client.height));
    if (!adaptToScrollbar) {
      final org.eclipse.swt.graphics.Point center = getCenteredOrigin();
      hBar.setSelection(-center.x);
      vBar.setSelection(-center.y);
    }
  }

  private void calculateSizes() {
    if (!simulator.isConfigured()) {
      return;
    }

    double minX = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;

    boolean isDefined = false;
    for (final CanvasRenderer r : renderers) {
      final ViewRect rect = r.getViewRect();
      if (rect != null) {
        minX = Math.min(minX, rect.min.x);
        maxX = Math.max(maxX, rect.max.x);
        minY = Math.min(minY, rect.min.y);
        maxY = Math.max(maxY, rect.max.y);
        isDefined = true;
      }
    }

    checkState(
        isDefined,
        "none of the available renderers implements getViewRect(), known renderers: %s",
        renderers);

    viewRect = new ViewRect(new Point(minX, minY), new Point(maxX, maxY));

    final Rectangle area = canvas.getClientArea();
    if (viewRect.width > viewRect.height) {
      m = area.width / viewRect.width;
    } else {
      m = area.height / viewRect.height;
    }
    zoomRatio = 1;
  }

  @Override
  public void controlMoved(ControlEvent e) {}

  @Override
  public void controlResized(ControlEvent e) {
    if (image != null) {
      updateScrollbars(true);
      scrollHorizontal();
      scrollVertical();
      canvas.redraw();
    }
  }

  @Override
  public void widgetSelected(SelectionEvent e) {
    if (e.widget == vBar) {
      scrollVertical();
    } else {
      scrollHorizontal();
    }
  }

  void scrollVertical() {
    final org.eclipse.swt.graphics.Point center = getCenteredOrigin();
    final Rectangle content = image.getBounds();
    final Rectangle client = canvas.getClientArea();
    if (client.height > content.height) {
      origin.y = 0;
    }
    else {
      final int vSelection = vBar.getSelection();
      final int destY = -vSelection - center.y;
      canvas.scroll(center.x, destY, center.x, center.y, content.width,
          content.height, false);
      origin.y = -vSelection + origin.y - center.y;
    }
  }

  void scrollHorizontal() {
    final org.eclipse.swt.graphics.Point center = getCenteredOrigin();
    final Rectangle content = image.getBounds();
    final Rectangle client = canvas.getClientArea();
    if (client.width > content.width) {
      origin.x = 0;
    }
    else {
      final int hSelection = hBar.getSelection();
      final int destX = -hSelection - center.x;
      canvas.scroll(destX, center.y, center.x, center.y, content.width,
          content.height, false);
      origin.x = -hSelection + origin.x - center.x;
    }
  }

  @Override
  public void widgetDefaultSelected(SelectionEvent e) {}

  @Override
  public void tick(TimeLapse timeLapse) {}

  @Override
  public void afterTick(final TimeLapse timeLapse) {
    if (simulator.isPlaying()
        && lastRefresh + timeLapse.getTimeStep() * speedUp > timeLapse
            .getStartTime()) {
      return;
    }
    lastRefresh = timeLapse.getStartTime();
    // TODO sleep should be relative to speedUp as well?
    try {
      Thread.sleep(30);
    } catch (final InterruptedException e) {
      throw new RuntimeException(e);
    }
    if (display.isDisposed()) {
      return;
    }
    display.syncExec(new Runnable() {
      @Override
      public void run() {
        if (!canvas.isDisposed()) {
          if (simulator.getTimeStep() > 500) {
            final String formatted = FORMATTER
                .print(
                new Period(0, simulator.getCurrentTime()));
            timeLabel.setText(formatted);
          } else {
            timeLabel.setText("" + simulator.getCurrentTime());
          }
          timeLabel.pack();
          canvas.redraw();
        }
      }
    });
  }
}
