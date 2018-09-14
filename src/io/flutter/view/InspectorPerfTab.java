/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.android.tools.profilers.IdeProfilerServices;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.ui.JBUI;
import io.flutter.inspector.FPSDisplay;
import io.flutter.inspector.FlutterLineChart;
// TODO(terry): Renable when using
import io.flutter.inspector.FlutterStudioProfilers;
import io.flutter.inspector.HeapDisplay;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import  io.flutter.inspector.*;


import javax.swing.*;
import java.awt.*;


public class InspectorPerfTab extends JPanel implements InspectorTabPanel {
  private @NotNull FlutterApp app;

  InspectorPerfTab(Disposable parentDisposable, @NotNull FlutterApp app) {
    this.app = app;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    add(Box.createVerticalStrut(6));

    Box labelBox = Box.createHorizontalBox();
    labelBox.add(new JLabel("Running in " + app.getLaunchMode() + " mode"));
    labelBox.add(Box.createHorizontalGlue());
    labelBox.setBorder(JBUI.Borders.empty(3, 10));
    add(labelBox);


    if (app.getLaunchMode() == FlutterLaunchMode.DEBUG) {
      labelBox = Box.createHorizontalBox();
      labelBox.add(new JLabel("Note: for best results, re-run in profile mode"));
      labelBox.add(Box.createHorizontalGlue());
      labelBox.setBorder(JBUI.Borders.empty(3, 10));
      add(labelBox);
    }

    add(Box.createVerticalStrut(6));

    // TODO(terry): Add Basic line chart Memory View
    //JPanel memChart = FlutterLineChart.createJPanelView(parentDisposable, app);
    //add(memChart, BorderLayout.NORTH);

// TODO(terry): How to get an IdeServices???
// TODO(terry): Renable when using below
    FlutterStudioProfilers fsp = new FlutterStudioProfilers(parentDisposable, app);
    FlutterStudioProfilersView view = new FlutterStudioProfilersView(fsp);
    add(view.getComponent(), BorderLayout.CENTER);

    // TODO(terry): Basic linechart test.
//    JPanel basicChart = FlutterLineChart.createJPanelView(parentDisposable, app);
//    add(basicChart, BorderLayout.NORTH);

    // TODO(terry): Flutter Memory View.
//    JPanel memoryView = FlutterMemoryView.createJPanelView(parentDisposable, app);
//    add(memoryView, BorderLayout.NORTH);

/*
    add(Box.createVerticalStrut(16));

    add(FPSDisplay.createJPanelView(parentDisposable, app), BorderLayout.NORTH);
    add(Box.createVerticalStrut(16));

    // Old CPU View
    add(HeapDisplay.createJPanelView(parentDisposable, app), BorderLayout.SOUTH);
    add(Box.createVerticalGlue());
*/
  }

  @Override
  public void setVisibleToUser(boolean visible) {
    assert app.getPerfService() != null;

    if (visible) {
      app.getPerfService().resumePolling();
    }
    else {
      app.getPerfService().pausePolling();
    }
  }
}
