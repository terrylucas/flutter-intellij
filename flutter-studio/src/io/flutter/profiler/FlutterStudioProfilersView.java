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
package io.flutter.profiler;

import com.android.tools.adtui.flat.FlatComboBox;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.android.tools.profilers.*;
import com.android.tools.profilers.sessions.SessionsView;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBEmptyBorder;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.function.BiFunction;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER;
import static com.android.tools.profilers.ProfilerFonts.H4_FONT;
import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_HEIGHT;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.META_DOWN_MASK;

// Refactored from Android 3.3 adt-ui code.
public class FlutterStudioProfilersView
  extends AspectObserver implements Disposable {

  private final static String LOADING_VIEW_CARD = "LoadingViewCard";
  private final static String STAGE_VIEW_CARD = "StageViewCard";
  private static final int SHORTCUT_MODIFIER_MASK_NUMBER = SystemInfo.isMac ? META_DOWN_MASK : CTRL_DOWN_MASK;
  @NotNull public static final String ATTACH_LIVE = "Attach to live";
  @NotNull public static final String DETACH_LIVE = "Detach live";
  @NotNull public static final String ZOOM_IN = "Zoom in";
  @NotNull public static final String ZOOM_OUT = "Zoom out";

  private final FlutterStudioProfilers profiler;
  private final ViewBinder<FlutterStudioProfilersView, FlutterStage,
    FlutterStageView> binder;
  private FlutterStageView stageView;

  @NotNull
  private final ProfilerLayeredPane layeredPane;
  /**
   * Splitter between the sessions and main profiler stage panel. We use IJ's
   * {@link ThreeComponentsSplitter} as it supports zero-width
   * divider while still handling mouse resize properly.
   */
  @NotNull private final ThreeComponentsSplitter splitter;
  //@NotNull private final LoadingPanel stageLoadingPanel;
  private final JPanel stageComponent;
  private final JPanel stageCenterComponent;
  private final CardLayout stageCenterCardLayout;
  private SessionsView sessionsView;
  private JPanel toolbar;
  private JPanel stageToolbar;
  private JPanel monitoringToolbar;
  private JPanel commonToolbar;
  private JPanel goLiveToolbar;
  private JToggleButton goLive;
  private CommonButton zoomOut;
  private CommonButton zoomIn;
  private CommonButton resetZoom;
  private CommonButton frameSelection;
  private ProfilerAction frameSelectionAction;

  public FlutterStudioProfilersView(@NotNull FlutterStudioProfilers theProfiler) {
    profiler = theProfiler;
    stageView = null;
    stageComponent = new JPanel(new BorderLayout());
    stageCenterCardLayout = new CardLayout();
    stageCenterComponent = new JPanel(stageCenterCardLayout);

    // TODO(terry): Multiple profiler views.
    //stageLoadingPanel = myIdeProfilerComponents.createLoadingPanel(0);
    //stageLoadingPanel.setLoadingText("");
    //stageLoadingPanel.getComponent().setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    splitter = new ThreeComponentsSplitter();
    // Override the splitter's custom traversal policy back to the default,
    // because the custom policy prevents the profilers from tabbing
    // across the components (e.g. sessions panel and the main stage UI).
    splitter.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    splitter.setDividerWidth(0);
    splitter.setDividerMouseZoneSize(-1);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setLastComponent(stageComponent);
    Disposer.register(this, splitter);

    layeredPane = new ProfilerLayeredPane(splitter);

    initializeStageUi();

    binder = new ViewBinder<>();
    binder.bind(FlutterStudioMonitorStage.class, FlutterStudioMonitorStageView::new);
    // TODO(terry): Multiple profiler views.
    //binder.bind(CpuProfilerStage.class, CpuProfilerStageView::new);
    //binder.bind(MemoryProfilerStage.class, MemoryProfilerStageView::new);
    //binder.bind(NetworkProfilerStage.class, NetworkProfilerStageView::new);
    //binder.bind(NullMonitorStage.class, NullMonitorStageView::new); // This is for no device detected
    //binder.bind(EnergyProfilerStage.class, EnergyProfilerStageView::new);

    profiler.addDependency(this)
            .onChange(ProfilerAspect.STAGE, this::updateStageView);
    updateStageView();
  }

  @Override
  public void dispose() {
  }

  @VisibleForTesting
  public <S extends FlutterStage, T extends FlutterStageView>
  void bind(@NotNull Class<S> clazz,
            @NotNull BiFunction<FlutterStudioProfilersView, S, T> constructor) {

    binder.bind(clazz, constructor);
  }

  @VisibleForTesting
  @NotNull
  CommonButton getZoomInButton() {
    return zoomIn;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getZoomOutButton() {
    return zoomOut;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getResetZoomButton() {
    return resetZoom;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getFrameSelectionButton() {
    return frameSelection;
  }

  @VisibleForTesting
  @NotNull
  JToggleButton getGoLiveButton() {
    return goLive;
  }

  @VisibleForTesting
  public FlutterStageView getStageView() {
    return stageView;
  }

  @VisibleForTesting
  @NotNull
  SessionsView getSessionsView() {
    return sessionsView;
  }

  private void initializeStageUi() {
    toolbar = new JPanel(new BorderLayout());
    JPanel leftToolbar = new JPanel(ProfilerLayout.createToolbarLayout());

    toolbar.setBorder(DEFAULT_BOTTOM_BORDER);
    toolbar.setPreferredSize(new Dimension(0, TOOLBAR_HEIGHT));

    commonToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    JButton button = new CommonButton(StudioIcons.Common.BACK_ARROW);
    button.addActionListener(action -> {
      profiler.setMonitoringStage();
      //profiler.getIdeServices().getFeatureTracker().trackGoBack();
    });
    commonToolbar.add(button);
    commonToolbar.add(new FlatSeparator());

    JComboBox<Class<? extends FlutterStage>> stageCombo = new FlatComboBox<>();
    JComboBoxView stages = new JComboBoxView<>(stageCombo, profiler,
                                               ProfilerAspect.STAGE,
                                               profiler::getDirectStages,
                                               profiler::getStageClass,
                                               stage -> {
                                                 // Track first, so current
                                                 // stage is sent the event
                                                 //profiler.getIdeServices().getFeatureTracker().trackSelectMonitor();
                                                 profiler.setNewStage(stage);
                                               });
    stageCombo.setRenderer(new StageComboBoxRenderer());
    stages.bind();
    commonToolbar.add(stageCombo);
    commonToolbar.add(new FlatSeparator());

    monitoringToolbar = new JPanel(ProfilerLayout.createToolbarLayout());

    leftToolbar.add(commonToolbar);
    toolbar.add(leftToolbar, BorderLayout.WEST);

    JPanel rightToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    toolbar.add(rightToolbar, BorderLayout.EAST);
    rightToolbar.setBorder(new JBEmptyBorder(0, 0, 0, 2));

    ProfilerTimeline timeline = profiler.getTimeline();
    zoomOut = new CommonButton(StudioIcons.Common.ZOOM_OUT);
    zoomOut.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_OUT));
    zoomOut.addActionListener(event -> {
      timeline.zoomOut();
      //profiler.getIdeServices().getFeatureTracker().trackZoomOut();
    });
    ProfilerAction zoomOutAction =
      new ProfilerAction.Builder(ZOOM_OUT)
        .setContainerComponent(stageComponent)
        .setActionRunnable(() -> zoomOut.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, SHORTCUT_MODIFIER_MASK_NUMBER))
        .build();

    zoomOut.setToolTipText(zoomOutAction.getDefaultToolTipText());
    rightToolbar.add(zoomOut);

    zoomIn = new CommonButton(StudioIcons.Common.ZOOM_IN);
    zoomIn.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_IN));
    zoomIn.addActionListener(event -> {
      timeline.zoomIn();
      //profiler.getIdeServices().getFeatureTracker().trackZoomIn();
    });
    ProfilerAction zoomInAction =
      new ProfilerAction.Builder(ZOOM_IN).setContainerComponent(stageComponent)
                                         .setActionRunnable(() -> zoomIn.doClick(0))
                                         .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                                                        KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SHORTCUT_MODIFIER_MASK_NUMBER),
                                                        KeyStroke.getKeyStroke(KeyEvent.VK_ADD, SHORTCUT_MODIFIER_MASK_NUMBER))
                                         .build();
    zoomIn.setToolTipText(zoomInAction.getDefaultToolTipText());
    rightToolbar.add(zoomIn);

    resetZoom = new CommonButton(StudioIcons.Common.RESET_ZOOM);
    resetZoom.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.RESET_ZOOM));
    resetZoom.addActionListener(event -> {
      timeline.resetZoom();
      //profiler.getIdeServices().getFeatureTracker().trackResetZoom();
    });
    ProfilerAction resetZoomAction =
      new ProfilerAction.Builder("Reset zoom").setContainerComponent(stageComponent)
                                              .setActionRunnable(() -> resetZoom.doClick(0))
                                              .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0),
                                                             KeyStroke.getKeyStroke(KeyEvent.VK_0, 0)).build();
    resetZoom.setToolTipText(resetZoomAction.getDefaultToolTipText());
    rightToolbar.add(resetZoom);

    frameSelection = new CommonButton(StudioIcons.Common.ZOOM_SELECT);
    frameSelection.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_SELECT));
    frameSelection.addActionListener(event -> {
      timeline.frameViewToRange(timeline.getSelectionRange());
    });
    frameSelectionAction = new ProfilerAction.Builder("Zoom to Selection")
      .setContainerComponent(stageComponent)
      .setActionRunnable(() -> frameSelection.doClick(0))
      .setEnableBooleanSupplier(() -> !timeline.getSelectionRange().isEmpty()).build();
    frameSelection.setToolTipText(frameSelectionAction.getDefaultToolTipText());
    rightToolbar.add(frameSelection);
    timeline.getSelectionRange()
            .addDependency(this)
            .onChange(Range.Aspect.RANGE,
                      () -> frameSelection.setEnabled(frameSelectionAction.isEnabled()));

    goLiveToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    goLiveToolbar.add(new FlatSeparator());

    goLive = new CommonToggleButton("", StudioIcons.Profiler.Toolbar.GOTO_LIVE);
    goLive.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.GOTO_LIVE));
    goLive.setFont(H4_FONT);
    goLive.setHorizontalTextPosition(SwingConstants.LEFT);
    goLive.setHorizontalAlignment(SwingConstants.LEFT);
    goLive.setBorder(new JBEmptyBorder(3, 7, 3, 7));
    // Configure shortcuts for GoLive.
    ProfilerAction attachAction =
      new ProfilerAction.Builder(ATTACH_LIVE).setContainerComponent(stageComponent)
                                             .setActionRunnable(() -> goLive.doClick(0))
                                             .setEnableBooleanSupplier(
                                               () -> goLive.isEnabled() &&
                                                     !goLive.isSelected() &&
                                                     stageView.navigationControllersEnabled())
                                             .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT,
                                                                                   SHORTCUT_MODIFIER_MASK_NUMBER))
                                             .build();
    ProfilerAction detachAction =
      new ProfilerAction.Builder(DETACH_LIVE).setContainerComponent(stageComponent)
                                             .setActionRunnable(() -> goLive.doClick(0))
                                             .setEnableBooleanSupplier(
                                               () -> goLive.isEnabled() &&
                                                     goLive.isSelected() &&
                                                     stageView.navigationControllersEnabled())
                                             .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,
                                                                                   0)).build();

    goLive.setToolTipText(detachAction.getDefaultToolTipText());
    goLive.addActionListener(event -> {
      timeline.toggleStreaming();
      //profiler.getIdeServices().getFeatureTracker().trackToggleStreaming();
    });
    goLive.addChangeListener(e -> {
      boolean isSelected = goLive.isSelected();
      goLive.setIcon(isSelected ? StudioIcons.Profiler.Toolbar.PAUSE_LIVE : StudioIcons.Profiler.Toolbar.GOTO_LIVE);
      goLive.setToolTipText(isSelected ? detachAction.getDefaultToolTipText() : attachAction.getDefaultToolTipText());
    });
    timeline.addDependency(this).onChange(ProfilerTimeline.Aspect.STREAMING, this::updateStreaming);
    goLiveToolbar.add(goLive);
    rightToolbar.add(goLiveToolbar);

    ProfilerContextMenu.createIfAbsent(stageComponent)
                       .add(attachAction, detachAction, ContextMenuItem.SEPARATOR, zoomInAction, zoomOutAction);
    toggleTimelineButtons();

    stageToolbar = new JPanel(new BorderLayout());
    toolbar.add(stageToolbar, BorderLayout.CENTER);

    stageComponent.add(toolbar, BorderLayout.NORTH);
    stageComponent.add(stageCenterComponent, BorderLayout.CENTER);

    updateStreaming();
  }

  private void toggleTimelineButtons() {
    zoomOut.setEnabled(true);
    zoomIn.setEnabled(true);
    resetZoom.setEnabled(true);
    frameSelection.setEnabled(frameSelectionAction.isEnabled());
    goLive.setEnabled(true);
    goLive.setSelected(true);
  }

  private void updateStreaming() {
    goLive.setSelected(profiler.getTimeline().isStreaming());
  }

  private void updateStageView() {
    FlutterStage stage = profiler.getStage();
    if (stageView != null && stageView.getStage() == stage) {
      return;
    }

    stageView = binder.build(this, stage);
    SwingUtilities.invokeLater(() -> stageView.getComponent().requestFocusInWindow());

    stageCenterComponent.removeAll();
    stageCenterComponent.add(stageView.getComponent(), STAGE_VIEW_CARD);
    //stageCenterComponent.add(stageLoadingPanel.getComponent(), LOADING_VIEW_CARD);
    stageCenterComponent.revalidate();
    stageToolbar.removeAll();
    stageToolbar.add(stageView.getToolbar(), BorderLayout.CENTER);
    stageToolbar.revalidate();
    toolbar.setVisible(stageView.isToolbarVisible());
    goLiveToolbar.setVisible(stageView.navigationControllersEnabled());

    boolean topLevel = stageView == null || stageView.needsProcessSelection();
    monitoringToolbar.setVisible(topLevel);
    commonToolbar.setVisible(!topLevel && stageView.navigationControllersEnabled());
  }

  @NotNull
  public JLayeredPane getComponent() {
    return layeredPane;
  }

  /**
   * Installs the {@link ContextMenuItem} common to all profilers.
   *
   * @param component
   */
  public void installCommonMenuItems(@NotNull JComponent component) {
    //ContextMenuInstaller contextMenuInstaller = getIdeProfilerComponents().createContextMenuInstaller();
    //ProfilerContextMenu.createIfAbsent(stageComponent).getContextMenuItems()
    //                   .forEach(item -> contextMenuInstaller.installGenericContextMenu(component, item));
  }

  @VisibleForTesting
  final JPanel getStageComponent() {
    return stageComponent;
  }


  @VisibleForTesting
  public static class StageComboBoxRenderer extends ColoredListCellRenderer<Class> {

    private static ImmutableMap<Class<? extends FlutterStage>, String> CLASS_TO_NAME = ImmutableMap.of(
      //CpuProfilerStage.class, "CPU",
      //MemoryProfilerStage.class, "MEMORY",
      //NetworkProfilerStage.class, "NETWORK",
      //EnergyProfilerStage.class, "ENERGY"
    );

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Class value, int index, boolean selected, boolean hasFocus) {
      String name = CLASS_TO_NAME.get(value);
      append(name == null ? "[UNKNOWN]" : name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
