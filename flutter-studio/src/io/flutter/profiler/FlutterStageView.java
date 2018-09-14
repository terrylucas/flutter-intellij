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

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.*;
import com.intellij.ui.components.JBPanel;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.ProfilerFonts.STANDARD_FONT;

// Refactored from Android 3.3 adt-ui code.
public abstract class FlutterStageView<T extends FlutterStage> extends AspectObserver {
  private final T stage;
  private final JPanel component;
  private final FlutterStudioProfilersView profilersView;

  /**
   * Container for the tooltip.
   */
  private final JPanel tooltipPanel;

  /**
   * View of the active tooltip for stages that contain more than one tooltips.
   */
  private ProfilerTooltipView activeTooltipView;

  /**
   * Binder to bind a tooltip to its view.
   */
  private final ViewBinder<FlutterStageView, ProfilerTooltip, ProfilerTooltipView> tooltipBinder;

  /**
   * A common component for showing the current selection range.
   */
  @NotNull private final JLabel selectionTimeLabel;

  // TODO (b/77709239): All Stages currently have a Panel that defines a tabular layout, and a tooltip.
  // we should refactor this so common functionality is in the base class to avoid more duplication.
  public FlutterStageView(@NotNull FlutterStudioProfilersView theProfilersView, @NotNull T theStage) {
    profilersView = theProfilersView;
    stage = theStage;
    component = new JBPanel(new BorderLayout());
    component.setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    // Use FlowLayout instead of the usual BorderLayout since BorderLayout doesn't respect min/preferred sizes.
    tooltipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    tooltipPanel.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    tooltipBinder = new ViewBinder<>();

    selectionTimeLabel = createSelectionTimeLabel();
    stage.getStudioProfilers().addDependency(this).onChange(ProfilerAspect.TOOLTIP, this::tooltipChanged);
    stage.getStudioProfilers().getTimeline().getSelectionRange().addDependency(this).onChange(Range.Aspect.RANGE, this::selectionChanged);
    selectionChanged();
  }

  @NotNull
  public T getStage() {
    return stage;
  }

  @NotNull
  public FlutterStudioProfilersView getProfilersView() {
    return profilersView;
  }

  @NotNull
  public final JComponent getComponent() {
    return component;
  }

  @NotNull
  public final ProfilerTimeline getTimeline() {
    return stage.getStudioProfilers().getTimeline();
  }

  public ViewBinder<FlutterStageView, ProfilerTooltip, ProfilerTooltipView> getTooltipBinder() {
    return tooltipBinder;
  }

  public JPanel getTooltipPanel() {
    return tooltipPanel;
  }

  @NotNull
  public JLabel getSelectionTimeLabel() {
    return selectionTimeLabel;
  }

  @NotNull
  protected JComponent buildTimeAxis(FlutterStudioProfilers profilers) {
    JPanel axisPanel = new JPanel(new BorderLayout());
    axisPanel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    AxisComponent timeAxis = new AxisComponent(profilers.getViewAxis(), AxisComponent.AxisOrientation.BOTTOM);
    timeAxis.setShowAxisLine(false);
    timeAxis.setMinimumSize(new Dimension(0, ProfilerLayout.TIME_AXIS_HEIGHT));
    timeAxis.setPreferredSize(new Dimension(Integer.MAX_VALUE, ProfilerLayout.TIME_AXIS_HEIGHT));
    axisPanel.add(timeAxis, BorderLayout.CENTER);
    return axisPanel;
  }

  abstract public JComponent getToolbar();

  public boolean isToolbarVisible() {
    return true;
  }

  /**
   * Whether navigation controllers (i.e. Jump to Live, Profilers Combobox, and Back arrow) are enabled/visible.
   */
  public boolean navigationControllersEnabled() {
    return true;
  }

  /**
   * A purely visual concept as to whether this stage wants the "process and devices" selection being shown to the user.
   * It is not possible to assume processes won't change while a stage is running. For example: a process dying.
   */
  public boolean needsProcessSelection() {
    return false;
  }

  protected void tooltipChanged() {
    if (activeTooltipView != null) {
      activeTooltipView.dispose();
      activeTooltipView = null;
    }
    tooltipPanel.removeAll();
    tooltipPanel.setVisible(false);

    if (stage.getTooltip() != null) {
      activeTooltipView = tooltipBinder.build(this, stage.getTooltip());
      tooltipPanel.add(activeTooltipView.createComponent());
      tooltipPanel.setVisible(true);
    }
    tooltipPanel.invalidate();
    tooltipPanel.repaint();
  }

  private void selectionChanged() {
    ProfilerTimeline timeline = stage.getStudioProfilers().getTimeline();
    Range selectionRange = timeline.getSelectionRange();
    if (selectionRange.isEmpty()) {
      selectionTimeLabel.setIcon(null);
      selectionTimeLabel.setText("");
      return;
    }

    // Note - relative time conversion happens in nanoseconds
    long selectionMinUs = timeline.convertToRelativeTimeUs(TimeUnit.MICROSECONDS.toNanos((long)selectionRange.getMin()));
    long selectionMaxUs = timeline.convertToRelativeTimeUs(TimeUnit.MICROSECONDS.toNanos((long)selectionRange.getMax()));
    selectionTimeLabel.setIcon(StudioIcons.Profiler.Toolbar.CLOCK);
    if (selectionRange.isPoint()) {
      selectionTimeLabel.setText(TimeFormatter.getSimplifiedClockString(selectionMinUs));
    }
    else {
      selectionTimeLabel.setText(String.format("%s - %s",
                                               TimeFormatter.getSimplifiedClockString(selectionMinUs),
                                               TimeFormatter.getSimplifiedClockString(selectionMaxUs)));
    }
  }

  @NotNull
  private JLabel createSelectionTimeLabel() {
    JLabel selectionTimeLabel = new JLabel("");
    selectionTimeLabel.setFont(STANDARD_FONT);
    selectionTimeLabel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
    selectionTimeLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        ProfilerTimeline timeline = getStage().getStudioProfilers().getTimeline();
        timeline.frameViewToRange(timeline.getSelectionRange());
      }
    });
    selectionTimeLabel.setToolTipText("Selected range");
    selectionTimeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return selectionTimeLabel;
  }
}
