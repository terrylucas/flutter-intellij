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
package io.flutter.view;

/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ActiveRunnable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.UIUtil;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.inspector.InspectorService;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.AsyncUtils;
import io.flutter.utils.EventStream;
import io.flutter.utils.StreamSubscription;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.flutter.utils.AsyncUtils.whenCompleteUiThread;

@com.intellij.openapi.components.State(
  name = "FlutterView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)
public class PerfView implements PersistentStateComponent<FlutterViewState>, Disposable {

  private static final Logger LOG = Logger.getInstance(FlutterView.class);
  private final Project myProject;

  @Nullable
  @Override
  public FlutterViewState getState() {
    return null;
  }

  @Override
  public void loadState(@NotNull FlutterViewState state) {

  }

  private static class PerAppState {
    ArrayList<FlutterViewAction> flutterViewActions = new ArrayList<>();
    ArrayList<InspectorPanel> inspectorPanels = new ArrayList<>();
    JBRunnerTabs tabs;
    Content content;
    boolean sendRestartNotificationOnNextFrame = false;
  }

  public static final String TOOL_WINDOW_ID = "Flutter Perf";

  public PerfView(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void dispose() {
    Disposer.dispose(this);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  private void addCpu(FlutterApp app, @Nullable InspectorService inspectorService, ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    final SimpleToolWindowPanel toolWindowPanel = new SimpleToolWindowPanel(true);
    final JBRunnerTabs runnerTabs = new JBRunnerTabs(myProject, ActionManager.getInstance(), null, this);

    final JPanel tabContainer = new JPanel(new BorderLayout());
    final Content content = contentManager.getFactory().createContent(null, "Terry CPU", false);
    tabContainer.add(runnerTabs.getComponent(), BorderLayout.CENTER);
    content.setComponent(tabContainer);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    content.setIcon(FlutterIcons.Phone);
    contentManager.addContent(content);
    //final PerAppState state = getOrCreateStateForApp(app);
    //assert (state.content == null);
    //state.content = content;
    //state.tabs = runnerTabs;

    //final DefaultActionGroup toolbarGroup = createToolbar(toolWindow, app, runnerTabs, inspectorService);
    //toolWindowPanel.setToolbar(ActionManager.getInstance().createActionToolbar("FlutterViewToolbar", toolbarGroup, true).getComponent());

    //toolbarGroup.add(new OverflowAction(this, app));
    //final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("InspectorToolbar", toolbarGroup, true);
    //final JComponent toolbarComponent = toolbar.getComponent();
    //toolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    //tabContainer.add(toolbarComponent, BorderLayout.NORTH);

    final boolean debugConnectionAvailable = app.getLaunchMode().supportsDebugConnection();
    final boolean hasInspectorService = inspectorService != null;

    //addCpuTab(runnerTabs, app, !hasInspectorService);
    addCpuTab(runnerTabs, app, true);
  }

  private void addCpuTab(JBRunnerTabs runnerTabs,
                                 FlutterApp app,
                                 boolean selectedTab) {
    final InspectorPerfTab perfTab = new InspectorPerfTab(runnerTabs, app);
    final TabInfo tabInfo = new TabInfo(perfTab);
    runnerTabs.addTab(tabInfo);
    if (selectedTab) {
      runnerTabs.select(tabInfo, false);
    }

/*
    if (!selectedTab) {
      assert app.getPerfService() != null;
      app.getPerfService().pausePolling();
    }
*/
  }


  void initToolWindow(ToolWindow window) {
    if (window.isDisposed()) return;

    // Add a feedback button.
    if (window instanceof ToolWindowEx) {
      final AnAction sendFeedbackAction = new AnAction("Send Feedback", "Send Feedback", FlutterIcons.Feedback) {
        @Override
        public void actionPerformed(AnActionEvent event) {
          BrowserUtil.browse("https://goo.gl/WrMB43");
        }
      };

      ((ToolWindowEx)window).setTitleActions(sendFeedbackAction);
    }
  }
  public void debugActive(@NotNull FlutterViewMessages.FlutterDebugEvent event) {
    final FlutterApp app = event.app;

    if (app.getMode().isProfiling() || app.getLaunchMode().isProfiling()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        debugActiveHelper(app, null);
      });
    }
    else {
      whenCompleteUiThread(
        InspectorService.create(app, app.getFlutterDebugProcess(), app.getVmService()),
        (InspectorService inspectorService, Throwable throwable) -> {
          if (throwable != null) {
            LOG.warn(throwable);
            return;
          }
          debugActiveHelper(app, inspectorService);
        });
    }
  }

  private void debugActiveHelper(@NotNull FlutterApp app, @Nullable InspectorService inspectorService) {
    if (FlutterSettings.getInstance().isOpenInspectorOnAppLaunch()) {
//      autoActivateToolWindow();
    }

    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (!(toolWindowManager instanceof ToolWindowManagerEx)) {
      return;
    }

    final ToolWindow toolWindow = toolWindowManager.getToolWindow(FlutterView.TOOL_WINDOW_ID);
    if (toolWindow == null) {
      return;
    }

    toolWindow.setIcon(ExecutionUtil.getLiveIndicator(FlutterIcons.Flutter_13));

    addCpu(app, inspectorService, toolWindow);

    app.getVmService().addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void connectionOpened() {
//        onAppChanged(app);
      }

      @Override
      public void received(String streamId, Event event) {
        if (StringUtil.equals(streamId, VmService.EXTENSION_STREAM_ID)) {

        }
      }

      @Override
      public void connectionClosed() {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (toolWindow.isDisposed()) return;
          final ContentManager contentManager = toolWindow.getContentManager();
/*
          onAppChanged(app);

          final FlutterView.PerAppState state = perAppViewState.remove(app);
          if (state != null && state.content != null) {
            contentManager.removeContent(state.content, true);
          }
          if (perAppViewState.isEmpty()) {
            // No more applications are running.
            updateForEmptyContent(toolWindow);
          }
*/
        });
      }
    });
  }
}