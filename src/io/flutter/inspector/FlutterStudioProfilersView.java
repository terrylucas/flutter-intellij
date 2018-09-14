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
package io.flutter.inspector;

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

public class FlutterStudioProfilersView extends AspectObserver implements Disposable {
  private final static String LOADING_VIEW_CARD = "LoadingViewCard";
  private final static String STAGE_VIEW_CARD = "StageViewCard";
  private static final int SHORTCUT_MODIFIER_MASK_NUMBER = SystemInfo.isMac ? META_DOWN_MASK : CTRL_DOWN_MASK;
  @NotNull public static final String ATTACH_LIVE = "Attach to live";
  @NotNull public static final String DETACH_LIVE = "Detach live";
  @NotNull public static final String ZOOM_IN = "Zoom in";
  @NotNull public static final String ZOOM_OUT = "Zoom out";

  private final FlutterStudioProfilers myProfiler;
  private final ViewBinder<FlutterStudioProfilersView, FlutterStage, FlutterStageView> myBinder;
  private FlutterStageView myStageView;

  @NotNull
  private final ProfilerLayeredPane myLayeredPane;
  /**
   * Splitter between the sessions and main profiler stage panel. We use IJ's {@link ThreeComponentsSplitter} as it supports zero-width
   * divider while still handling mouse resize properly.
   */
  @NotNull private final ThreeComponentsSplitter mySplitter;
  //@NotNull private final LoadingPanel myStageLoadingPanel;
  private final JPanel myStageComponent;
  private final JPanel myStageCenterComponent;
  private final CardLayout myStageCenterCardLayout;
  private SessionsView mySessionsView;
  private JPanel myToolbar;
  private JPanel myStageToolbar;
  private JPanel myMonitoringToolbar;
  private JPanel myCommonToolbar;
  private JPanel myGoLiveToolbar;
  private JToggleButton myGoLive;
  private CommonButton myZoomOut;
  private CommonButton myZoomIn;
  private CommonButton myResetZoom;
  private CommonButton myFrameSelection;
  private ProfilerAction myFrameSelectionAction;

  public FlutterStudioProfilersView(@NotNull FlutterStudioProfilers profiler) {
    myProfiler = profiler;
    myStageView = null;
    myStageComponent = new JPanel(new BorderLayout());
    myStageCenterCardLayout = new CardLayout();
    myStageCenterComponent = new JPanel(myStageCenterCardLayout);

    //myStageLoadingPanel = myIdeProfilerComponents.createLoadingPanel(0);
    //myStageLoadingPanel.setLoadingText("");
    //myStageLoadingPanel.getComponent().setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    mySplitter = new ThreeComponentsSplitter();
    // Override the splitter's custom traversal policy back to the default, because the custom policy prevents the profilers from tabbing
    // across the components (e.g. sessions panel and the main stage UI).
    mySplitter.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    mySplitter.setDividerWidth(0);
    mySplitter.setDividerMouseZoneSize(-1);
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setLastComponent(myStageComponent);
    Disposer.register(this, mySplitter);

    myLayeredPane = new ProfilerLayeredPane(mySplitter);

    initializeStageUi();

    myBinder = new ViewBinder<>();
    myBinder.bind(FlutterStudioMonitorStage.class, FlutterStudioMonitorStageView::new);
    //myBinder.bind(CpuProfilerStage.class, CpuProfilerStageView::new);
    //myBinder.bind(MemoryProfilerStage.class, MemoryProfilerStageView::new);
    //myBinder.bind(NetworkProfilerStage.class, NetworkProfilerStageView::new);
    //myBinder.bind(NullMonitorStage.class, NullMonitorStageView::new); // This is for no device detected
    //myBinder.bind(EnergyProfilerStage.class, EnergyProfilerStageView::new);

    myProfiler.addDependency(this)
              .onChange(ProfilerAspect.STAGE, this::updateStageView);
    updateStageView();
  }

  @Override
  public void dispose() {
  }

  @VisibleForTesting
  public <S extends FlutterStage, T extends FlutterStageView> void bind(@NotNull Class<S> clazz,
                                                          @NotNull BiFunction<FlutterStudioProfilersView, S, T> constructor) {
    myBinder.bind(clazz, constructor);
  }

  @VisibleForTesting
  @NotNull
  CommonButton getZoomInButton() {
    return myZoomIn;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getZoomOutButton() {
    return myZoomOut;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getResetZoomButton() {
    return myResetZoom;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getFrameSelectionButton() {
    return myFrameSelection;
  }

  @VisibleForTesting
  @NotNull
  JToggleButton getGoLiveButton() {
    return myGoLive;
  }

  @VisibleForTesting
  public FlutterStageView getStageView() {
    return myStageView;
  }

  @VisibleForTesting
  @NotNull
  SessionsView getSessionsView() {
    return mySessionsView;
  }

  private void initializeStageUi() {
    myToolbar = new JPanel(new BorderLayout());
    JPanel leftToolbar = new JPanel(ProfilerLayout.createToolbarLayout());

    myToolbar.setBorder(DEFAULT_BOTTOM_BORDER);
    myToolbar.setPreferredSize(new Dimension(0, TOOLBAR_HEIGHT));

    myCommonToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    JButton button = new CommonButton(StudioIcons.Common.BACK_ARROW);
    button.addActionListener(action -> {
      myProfiler.setMonitoringStage();
      //myProfiler.getIdeServices().getFeatureTracker().trackGoBack();
    });
    myCommonToolbar.add(button);
    myCommonToolbar.add(new FlatSeparator());

    JComboBox<Class<? extends FlutterStage>> stageCombo = new FlatComboBox<>();
    JComboBoxView stages = new JComboBoxView<>(stageCombo, myProfiler, ProfilerAspect.STAGE,
                                               myProfiler::getDirectStages,
                                               myProfiler::getStageClass,
                                               stage -> {
                                                 // Track first, so current stage is sent with the event
                                                 //myProfiler.getIdeServices().getFeatureTracker().trackSelectMonitor();
                                                 myProfiler.setNewStage(stage);
                                               });
    stageCombo.setRenderer(new StageComboBoxRenderer());
    stages.bind();
    myCommonToolbar.add(stageCombo);
    myCommonToolbar.add(new FlatSeparator());

    myMonitoringToolbar = new JPanel(ProfilerLayout.createToolbarLayout());

    leftToolbar.add(myCommonToolbar);
    myToolbar.add(leftToolbar, BorderLayout.WEST);

    JPanel rightToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    myToolbar.add(rightToolbar, BorderLayout.EAST);
    rightToolbar.setBorder(new JBEmptyBorder(0, 0, 0, 2));

    ProfilerTimeline timeline = myProfiler.getTimeline();
    myZoomOut = new CommonButton(StudioIcons.Common.ZOOM_OUT);
    myZoomOut.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_OUT));
    myZoomOut.addActionListener(event -> {
      timeline.zoomOut();
      //myProfiler.getIdeServices().getFeatureTracker().trackZoomOut();
    });
    ProfilerAction zoomOutAction =
      new ProfilerAction.Builder(ZOOM_OUT).setContainerComponent(myStageComponent).setActionRunnable(() -> myZoomOut.doClick(0))
                                          .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                                                         KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, SHORTCUT_MODIFIER_MASK_NUMBER))
                                          .build();

    myZoomOut.setToolTipText(zoomOutAction.getDefaultToolTipText());
    rightToolbar.add(myZoomOut);

    myZoomIn = new CommonButton(StudioIcons.Common.ZOOM_IN);
    myZoomIn.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_IN));
    myZoomIn.addActionListener(event -> {
      timeline.zoomIn();
      //myProfiler.getIdeServices().getFeatureTracker().trackZoomIn();
    });
    ProfilerAction zoomInAction =
      new ProfilerAction.Builder(ZOOM_IN).setContainerComponent(myStageComponent)
                                         .setActionRunnable(() -> myZoomIn.doClick(0))
                                         .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                                                        KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SHORTCUT_MODIFIER_MASK_NUMBER),
                                                        KeyStroke.getKeyStroke(KeyEvent.VK_ADD, SHORTCUT_MODIFIER_MASK_NUMBER)).build();
    myZoomIn.setToolTipText(zoomInAction.getDefaultToolTipText());
    rightToolbar.add(myZoomIn);

    myResetZoom = new CommonButton(StudioIcons.Common.RESET_ZOOM);
    myResetZoom.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.RESET_ZOOM));
    myResetZoom.addActionListener(event -> {
      timeline.resetZoom();
      //myProfiler.getIdeServices().getFeatureTracker().trackResetZoom();
    });
    ProfilerAction resetZoomAction =
      new ProfilerAction.Builder("Reset zoom").setContainerComponent(myStageComponent)
                                              .setActionRunnable(() -> myResetZoom.doClick(0))
                                              .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0),
                                                             KeyStroke.getKeyStroke(KeyEvent.VK_0, 0)).build();
    myResetZoom.setToolTipText(resetZoomAction.getDefaultToolTipText());
    rightToolbar.add(myResetZoom);

    myFrameSelection = new CommonButton(StudioIcons.Common.ZOOM_SELECT);
    myFrameSelection.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_SELECT));
    myFrameSelection.addActionListener(event -> {
      timeline.frameViewToRange(timeline.getSelectionRange());
    });
    myFrameSelectionAction = new ProfilerAction.Builder("Zoom to Selection")
      .setContainerComponent(myStageComponent)
      .setActionRunnable(() -> myFrameSelection.doClick(0))
      .setEnableBooleanSupplier(() -> !timeline.getSelectionRange().isEmpty())
      .build();
    myFrameSelection.setToolTipText(myFrameSelectionAction.getDefaultToolTipText());
    rightToolbar.add(myFrameSelection);
    timeline.getSelectionRange().addDependency(this)
            .onChange(Range.Aspect.RANGE, () -> myFrameSelection.setEnabled(myFrameSelectionAction.isEnabled()));

    myGoLiveToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    myGoLiveToolbar.add(new FlatSeparator());

    myGoLive = new CommonToggleButton("", StudioIcons.Profiler.Toolbar.GOTO_LIVE);
    myGoLive.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.GOTO_LIVE));
    myGoLive.setFont(H4_FONT);
    myGoLive.setHorizontalTextPosition(SwingConstants.LEFT);
    myGoLive.setHorizontalAlignment(SwingConstants.LEFT);
    myGoLive.setBorder(new JBEmptyBorder(3, 7, 3, 7));
    // Configure shortcuts for GoLive.
    ProfilerAction attachAction =
      new ProfilerAction.Builder(ATTACH_LIVE).setContainerComponent(myStageComponent)
                                             .setActionRunnable(() -> myGoLive.doClick(0))
                                             .setEnableBooleanSupplier(
                                               () -> myGoLive.isEnabled() &&
                                                     !myGoLive.isSelected() &&
                                                     myStageView.navigationControllersEnabled())
                                             .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, SHORTCUT_MODIFIER_MASK_NUMBER))
                                             .build();
    ProfilerAction detachAction =
      new ProfilerAction.Builder(DETACH_LIVE).setContainerComponent(myStageComponent)
                                             .setActionRunnable(() -> myGoLive.doClick(0))
                                             .setEnableBooleanSupplier(
                                               () -> myGoLive.isEnabled() &&
                                                     myGoLive.isSelected() &&
                                                     myStageView.navigationControllersEnabled())
                                             .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)).build();

    myGoLive.setToolTipText(detachAction.getDefaultToolTipText());
    myGoLive.addActionListener(event -> {
      timeline.toggleStreaming();
      //myProfiler.getIdeServices().getFeatureTracker().trackToggleStreaming();
    });
    myGoLive.addChangeListener(e -> {
      boolean isSelected = myGoLive.isSelected();
      myGoLive.setIcon(isSelected ? StudioIcons.Profiler.Toolbar.PAUSE_LIVE : StudioIcons.Profiler.Toolbar.GOTO_LIVE);
      myGoLive.setToolTipText(isSelected ? detachAction.getDefaultToolTipText() : attachAction.getDefaultToolTipText());
    });
    timeline.addDependency(this).onChange(ProfilerTimeline.Aspect.STREAMING, this::updateStreaming);
    myGoLiveToolbar.add(myGoLive);
    rightToolbar.add(myGoLiveToolbar);

    ProfilerContextMenu.createIfAbsent(myStageComponent)
                       .add(attachAction, detachAction, ContextMenuItem.SEPARATOR, zoomInAction, zoomOutAction);
    toggleTimelineButtons();

    myStageToolbar = new JPanel(new BorderLayout());
    myToolbar.add(myStageToolbar, BorderLayout.CENTER);

    myStageComponent.add(myToolbar, BorderLayout.NORTH);
    myStageComponent.add(myStageCenterComponent, BorderLayout.CENTER);

    updateStreaming();
  }

  private void toggleTimelineButtons() {
    myZoomOut.setEnabled(true);
    myZoomIn.setEnabled(true);
    myResetZoom.setEnabled(true);
    myFrameSelection.setEnabled(myFrameSelectionAction.isEnabled());
    myGoLive.setEnabled(true);
    myGoLive.setSelected(true);
  }

  private void updateStreaming() {
    myGoLive.setSelected(myProfiler.getTimeline().isStreaming());
  }

  private void updateStageView() {
    FlutterStage stage = myProfiler.getStage();
    if (myStageView != null && myStageView.getStage() == stage) {
      return;
    }

    myStageView = myBinder.build(this, stage);
    SwingUtilities.invokeLater(() -> myStageView.getComponent().requestFocusInWindow());

    myStageCenterComponent.removeAll();
    myStageCenterComponent.add(myStageView.getComponent(), STAGE_VIEW_CARD);
    //myStageCenterComponent.add(myStageLoadingPanel.getComponent(), LOADING_VIEW_CARD);
    myStageCenterComponent.revalidate();
    myStageToolbar.removeAll();
    myStageToolbar.add(myStageView.getToolbar(), BorderLayout.CENTER);
    myStageToolbar.revalidate();
    myToolbar.setVisible(myStageView.isToolbarVisible());
    myGoLiveToolbar.setVisible(myStageView.navigationControllersEnabled());

    boolean topLevel = myStageView == null || myStageView.needsProcessSelection();
    myMonitoringToolbar.setVisible(topLevel);
    myCommonToolbar.setVisible(!topLevel && myStageView.navigationControllersEnabled());
  }

  @NotNull
  public JLayeredPane getComponent() {
    return myLayeredPane;
  }

  /**
   * Installs the {@link ContextMenuItem} common to all profilers.
   *
   * @param component
   */
  public void installCommonMenuItems(@NotNull JComponent component) {
    //ContextMenuInstaller contextMenuInstaller = getIdeProfilerComponents().createContextMenuInstaller();
    //ProfilerContextMenu.createIfAbsent(myStageComponent).getContextMenuItems()
    //                   .forEach(item -> contextMenuInstaller.installGenericContextMenu(component, item));
  }

  @VisibleForTesting
  final JPanel getStageComponent() {
    return myStageComponent;
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
