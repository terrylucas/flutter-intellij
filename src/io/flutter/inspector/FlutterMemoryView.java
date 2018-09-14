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

import com.android.tools.adtui.*;
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
import com.google.common.collect.ImmutableList;
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


public class FlutterMemoryView {
  private static final Logger LOG = Logger.getInstance(FlutterLineChart.class);

  static final String STREAM_0_NAME = "New Generation";
  static final String STREAM_1_NAME = "Old Generation";

  private TimelineComponent mTimeline;

  private EventData mEvents;

  private TimelineData mData;

  static private Updater myUpdater;
  public static JPanel createJPanelView(Disposable parentDisposable, FlutterApp app) {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.BOTTOM));
    // TODO(terry): Using PANEL_HEIGHT creates a 1" high panel...
    //    panel.setPreferredSize(new Dimension(-1, PANEL_HEIGHT));
    //    panel.setMaximumSize(new Dimension(Short.MAX_VALUE, PANEL_HEIGHT));
    panel.setPreferredSize(new Dimension(-1, Short.MAX_VALUE));     // Height is full size.

    //    final HeapState heapState = new HeapState(60 * 1000);
    final FlutterMemoryView memoryView = new FlutterMemoryView();

    // TODO(terry): Hookup UI.
    myUpdater = new Updater(new FpsTimer());
    myUpdater.register(memoryView.createModelList());
    memoryView.populateUi(panel);

    return panel;
  }

  //@Override
  protected List<Updatable> createModelList() {
    mEvents = new EventData();
    mData = new TimelineData(2, 2000);
    mTimeline = new TimelineComponent(mData, mEvents, 1.0f, 10.0f, 1000.0f, 10.0f);
    return ImmutableList.of(
      mTimeline,
      elapsed -> mTimeline.repaint()
    );
  }

  //@Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Collections.singletonList(mTimeline);
  }

  //@Override
  public String getName() {
    return "Timeline";
  }

  //@Override
  protected void populateUi(@NotNull JPanel panel) {
    final AtomicInteger streamSize = new AtomicInteger(2);
    final List<String> labelSharingStreams = new ArrayList<String>();
    final int maxNumStreams = 10;
    Thread updateDataThread = new Thread() {
      @Override
      public void run() {
        super.run();
        try {
          float[] values = new float[maxNumStreams];
          while (true) {
            int v = 10;
            int numStreams = streamSize.get();
            for (int i = 0; i < numStreams; i++) {
              float delta = (float)Math.random() * v - v * 0.5f;
              values[i] = delta + values[i];
            }
            float[] valuesCopy = new float[numStreams];
            System.arraycopy(values, 0, valuesCopy, 0, numStreams);
            for (int i = 0; i < numStreams; i++) {
              valuesCopy[i] = Math.abs(valuesCopy[i]);
            }
            synchronized (mData) {
              int oldStreams = mData.getStreamCount();
              for (int i = oldStreams; i < numStreams; i++) {
                if (i == 0)
                  mData.addStream(STREAM_0_NAME);
                if (i == 1)
                  mData.addStream(STREAM_1_NAME);
              }
              for (int i = numStreams; i < oldStreams; i++) {
                if (i == 0)
                  mData.removeStream(STREAM_0_NAME);
                if (i == 1)
                  mData.removeStream(STREAM_1_NAME);
              }
              mData.add(System.currentTimeMillis(), 0, valuesCopy);

            }
            Thread.sleep(100);    // TODO(terry): Why is this delay necessary? W/O data doesn't appear in chart?
          }
        }
        catch (InterruptedException e) {
        }
      }
    };
    updateDataThread.start();

    mTimeline.configureStream(0, STREAM_0_NAME, new Color(0x78abd9));
    mTimeline.configureStream(1, STREAM_1_NAME, new Color(0xbaccdc));

    mTimeline.configureUnits("@");
    mTimeline.configureEvent(1, 0, UIManager.getIcon("Tree.leafIcon"), new Color(0x92ADC6), new Color(0x2B4E8C), false);
    mTimeline.configureEvent(2, 1, UIManager.getIcon("Tree.leafIcon"), new Color(255, 191, 176), new Color(76, 14, 29), true);
    mTimeline.configureType(1, TimelineComponent.Style.SOLID);
    mTimeline.configureType(2, TimelineComponent.Style.DASHED);

    TimelineComponent.Listener listener = new TimelineComponent.Listener() {
      private final Color[] COLORS =
        {Color.decode("0xe6550d"), Color.decode("0xfd8d3c"), Color.decode("0x31a354"), Color.decode("0x74c476")};

      @Override
      public void onStreamAdded(int stream, String id) {
        mTimeline.configureStream(stream, id, COLORS[stream % COLORS.length]);
        synchronized (labelSharingStreams) {
          if (labelSharingStreams.contains(id)) {
            int streamIndex = labelSharingStreams.indexOf(id);
            String anotherStreamId = labelSharingStreams.get(streamIndex % 2 == 0 ? streamIndex + 1 : streamIndex - 1);
            boolean combined = mTimeline.linkStreams(id, anotherStreamId);
            if (combined) {
              labelSharingStreams.remove(id);
              labelSharingStreams.remove(anotherStreamId);
            }
          }
        }
      }
    };
    mTimeline.addListener(listener);

    panel.add(mTimeline, BorderLayout.CENTER);
  }
}
