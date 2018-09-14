/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flutter.inspector;

/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.AnimatedTimeRange;
import com.android.tools.adtui.SelectionComponent;
import com.android.tools.adtui.SimpleEventComponent;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.eventrenderer.EventIconRenderer;
import com.android.tools.adtui.eventrenderer.KeyboardEventRenderer;
import com.android.tools.adtui.eventrenderer.SimpleEventRenderer;
import com.android.tools.adtui.eventrenderer.TouchEventRenderer;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.event.ActivityAction;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.StackedEventType;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.perf.HeapMonitor;
import io.flutter.run.daemon.FlutterApp;
import org.dartlang.vm.service.element.IsolateRef;
import org.dartlang.vm.service.element.VM;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.android.tools.adtui.common.AdtUiUtils.GBC_FULL;
import static io.flutter.inspector.HeapDisplay.PANEL_HEIGHT;

public class FlutterLineChart {

  private static final Logger LOG = Logger.getInstance(FlutterLineChart.class);

  interface Value {
    void set(int v);

    int get();
  }
  private LineChart mLineChart;

  private List<RangedContinuousSeries> mRangedData;

  static private List<DefaultDataSeries<Long>> mData;
  static private DefaultDataSeries<EventAction<ActionType>> mMemoryActions;

  private AnimatedTimeRange mAnimatedTimeRange;

  /**
   * TODO(terry): Imported Type of memory event (only have GC for now).
   */
  public enum ActionType {
    GC
  }

  // END OF IMPORTED for GC glyph.


  // TODO(terry): Impoprted from HeapDisplay and FlutterLineChart constructor too - Necessary?
  public interface SummaryCallback {
    void updatedSummary(HeapState state);
  }
  private final SummaryCallback summaryCallback;
  public FlutterLineChart(@Nullable SummaryCallback summaryCallback) {
    this.summaryCallback = summaryCallback;
//    setVisible(true);
  }


  // TODO(terry): Imported hookup to Inspector Perf components - necessary?
  static private Updater myUpdater;
  public static JPanel createJPanelView(Disposable parentDisposable, FlutterApp app) {
    final HeapState heapState = new HeapState(60 * 1000);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.BOTTOM));
// TODO(terry): Using PANEL_HEIGHT creates a 1" high panel...
//    panel.setPreferredSize(new Dimension(-1, PANEL_HEIGHT));
//    panel.setMaximumSize(new Dimension(Short.MAX_VALUE, PANEL_HEIGHT));
    panel.setPreferredSize(new Dimension(-1, Short.MAX_VALUE));     // Height is full size.

    final JBLabel rssLabel = new JBLabel();
    rssLabel.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    rssLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    rssLabel.setForeground(UIUtil.getLabelDisabledForeground());
    rssLabel.setBorder(JBUI.Borders.empty(4));
    final JBLabel heapLabel = new JBLabel("", SwingConstants.RIGHT);
    heapLabel.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    heapLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    heapLabel.setForeground(UIUtil.getLabelDisabledForeground());
    heapLabel.setBorder(JBUI.Borders.empty(4));

//    final HeapState heapState = new HeapState(60 * 1000);
    final FlutterLineChart graph = new FlutterLineChart(state -> {
/*
      rssLabel.setText(heapState.getRSSSummary());
      heapLabel.setText(heapState.getHeapSummary());

      SwingUtilities.invokeLater(rssLabel::repaint);
      SwingUtilities.invokeLater(heapLabel::repaint);
*/
    });
/*
    graph.setLayout(new BoxLayout(graph, BoxLayout.X_AXIS));
    graph.add(rssLabel);
    graph.add(Box.createHorizontalGlue());
    graph.add(heapLabel);

    panel.add(graph, BorderLayout.CENTER);
*/
    // TODO(terry): Hookup UI.
    myUpdater = new Updater(new FpsTimer());
    myUpdater.register(graph.createModelList());
    graph.populateUi(panel);

    // TODO(terry): Hookup to VMService HeapMonitor.
    final HeapMonitor.HeapListener listener = new HeapMonitor.HeapListener() {
      private void updateModel(HeapState heapState) {
        for (DefaultDataSeries<Long> series : mData) {
          List<SeriesData<Long>> data = series.getAllData();
          long lastTime = data.isEmpty() ? 0 : data.get(data.size() - 1).x;

          long last = data.isEmpty() ? 0 : data.get(data.size() - 1).value;
          List<HeapMonitor.HeapSample> samples = heapState.getSamples();
          // LOG.info("Model Size = " + samples.size());
          HeapMonitor.HeapSample sample = samples.get(0);
          long microsecs = sample.getSampleTime();

          long millis = TimeUnit.MICROSECONDS.toMillis(microsecs);

          long rawTime2 = TimeUnit.NANOSECONDS.toMillis(microsecs);

          long nowNanoTime = System.nanoTime();
          long currentTimeMs = System.currentTimeMillis();
          long micro = TimeUnit.MILLISECONDS.toMicros(currentTimeMs);
          long nowUs = TimeUnit.NANOSECONDS.toMicros(nowNanoTime);

          LOG.info(">>>>> Time:");

          LOG.info("      Raw Time microsecs = " + microsecs);
/*
          LOG.info("      millis = " + millis);
          LOG.info("      rawTime2 = " + rawTime2);
          LOG.info("      currentTimeMs = " + currentTimeMs);
          LOG.info("      nowNanoTime = " + nowNanoTime);
          LOG.info("      nowUs = " + nowUs);

          java.sql.Timestamp time = new java.sql.Timestamp(millis);
          java.sql.Timestamp time2 = new java.sql.Timestamp(rawTime2);
          java.sql.Timestamp time3 = new java.sql.Timestamp(nowNanoTime);
          java.sql.Timestamp time4 = new java.sql.Timestamp(nowUs);

          LOG.info("      real SQL time (millis) " + time.toString());
          LOG.info("      real SQL time (rawTime2) " + time2.toString());
          LOG.info("      real SQL time (nowNanoTime) " + time3.toString());
          LOG.info("      real SQL time (nowUs) " + time4.toString());
*/

          // Normalize a MB to 1.
          long current = Long.valueOf(sample.getBytes()/100000);

          series.add(nowUs, current);
          //series.add(micro, current);

//if (lastTime != microsecs)
//  series.add(microsecs, current);
LOG.info(">>>>> Update Model " + nowUs + "  : " + current);
LOG.info("      currentTime: " + new java.sql.Timestamp(TimeUnit.NANOSECONDS.toMicros(System.nanoTime())));
        }
      }

      @Override
      public void handleIsolatesInfo(VM vm, List<HeapMonitor.IsolateObject> isolates) {
        heapState.handleIsolatesInfo(vm, isolates);
        // TODO(terry): Add this heapState to our DataSeries
        LOG.info("handleIsolates");
        updateModel(heapState);

        //long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());

/*

          float delta = ((float)Math.random() - 0.45f) * v;
          // Make sure not to add negative numbers.
          long current = Math.max(last + (long)delta, 0);
          series.add(nowUs, current);
*/
//        graph.updateFrom(heapState);
      }

      @Override
      public void handleGCEvent(IsolateRef iIsolateRef, HeapMonitor.HeapSpace newHeapSpace, HeapMonitor.HeapSpace oldHeapSpace) {
        heapState.handleGCEvent(iIsolateRef, newHeapSpace, oldHeapSpace);
        // TODO(terry): Add this heapState to our DataSeries
        LOG.info("<<<<< ***** GC ***** >>>>>");

        long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
        EventAction<ActionType> event =
          new EventAction<>(nowUs, nowUs, ActionType.GC);
// Need to add event to a seperate dataSeries???
//        myData.add(nowUs, event);

        updateModel(heapState);
      }
    };

    assert app.getPerfService() != null;
    app.getPerfService().addHeapListener(listener);
    Disposer.register(parentDisposable, () -> app.getPerfService().removeHeapListener(listener));

    return panel;
  }


  //@Override
  protected List<Updatable> createModelList() {
    mRangedData = new ArrayList<>();
    mData = new ArrayList<>();

    mMemoryActions = new DefaultDataSeries<>();

    long nowMs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    Range timeGlobalRangeUs = new Range(nowMs, nowMs + TimeUnit.DAYS.toMicros(1));
    //long nowMs = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
    //Range timeGlobalRangeUs = new Range(nowMs, nowMs + TimeUnit.HOURS.toMicros(1));

    LineChartModel model = new LineChartModel();
    mLineChart = new LineChart(model);
    mLineChart.setBackground(JBColor.background());
    mAnimatedTimeRange = new AnimatedTimeRange(timeGlobalRangeUs, 0);

    List<Updatable> componentsList = new ArrayList<>();

// TODO(terry): How to add little glyphs for each GC
/*
    SelectionModel selection = new SelectionModel(new Range(0, 0));
    mySelectionComponent = new SelectionComponent(selection, timeGlobalRangeUs);
    myOverlayComponent = new OverlayComponent(mySelectionComponent);
*/

    // Add the scene components to the list
    componentsList.add(mAnimatedTimeRange);
    componentsList.add(model);

// TODO(terry): Replaced?
//    Range yRange = new Range(0.0,100.0);
    Range yRange = new Range(300.0, 800.0);


    DefaultDataSeries<Long> series = new DefaultDataSeries<>();
    RangedContinuousSeries ranged =
      new RangedContinuousSeries("Memory in use", timeGlobalRangeUs, yRange, series);
    mRangedData.add(ranged);
    mData.add(series);
/*
    Range yRange = new Range(0.0, 100.0);
    for (int i = 0; i < 4; i++) {
      if (i % 2 == 0) {
        yRange = new Range(0.0, 100.0);
      }
      DefaultDataSeries<Long> series = new DefaultDataSeries<>();
      RangedContinuousSeries ranged =
        new RangedContinuousSeries("Widgets #" + i, timeGlobalRangeUs, yRange, series);
      mRangedData.add(ranged);
      mData.add(series);
    }
*/
    model.addAll(mRangedData);

    return componentsList;
  }

  //@Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Collections.singletonList(mLineChart);
  }

  //@Override
  public String getName() {
    return "LineChart";
  }

  protected static JPanel createControlledPane(JPanel panel, Component animated) {
    panel.setLayout(new BorderLayout());
    panel.add(animated, BorderLayout.CENTER);

    JPanel controls = new JPanel();
    LayoutManager manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
    controls.setLayout(manager);
    panel.add(controls, BorderLayout.WEST);
    return controls;
  }

  //@Override
  protected void populateUi(@NotNull JPanel panel) {
    JPanel layered = new JPanel(new GridBagLayout());
    JPanel controls = createControlledPane(panel, layered);
    mLineChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));
    layered.setBackground(JBColor.background());
    layered.add(mLineChart, GBC_FULL);
/*
    final AtomicInteger variance = new AtomicInteger(10);
    final AtomicInteger delay = new AtomicInteger(100);
*/
    // TODO(terry) Removed random data...
/*
    Thread updateDataThread = new Thread() {
      @Override
      public void run() {
        super.run();
        try {
          while (true) {
            int v = variance.get();
            long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
            for (DefaultDataSeries<Long> series : mData) {
              List<SeriesData<Long>> data = series.getAllData();
              long last = data.isEmpty() ? 0 : data.get(data.size() - 1).value;
              float delta = ((float)Math.random() - 0.45f) * v;
              // Make sure not to add negative numbers.
              long current = Math.max(last + (long)delta, 0);
              series.add(nowUs, current);
              LOG.info("Run: Heap Sample " + nowUs + "  : " + current);
            }
            Thread.sleep(delay.get());
          }
        }
        catch (InterruptedException e) {
        }
      }
    };

    updateDataThread.start();
*/
  }
}
