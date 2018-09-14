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

import com.android.tools.profilers.*;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

public class FlutterStudioMonitorStage extends FlutterStage {

  @NotNull
  private final List<ProfilerMonitor> myMonitors;

  public FlutterAllMemoryData.ThreadSafeData getMemoryUsedDataSeries() {
    return myAllMemoryData.getHeapUsedDataSeries();
  }

  public FlutterAllMemoryData.ThreadSafeData getMemoryMaxDataSeries() {
    return myAllMemoryData.getHeapMaxDetails();
  }

  private final FlutterAllMemoryData myAllMemoryData;

  // TODO(terry): Constructor must take a StudioProfilers???
  public FlutterStudioMonitorStage(@NotNull FlutterStudioProfilers profiler) {
    super(profiler);
    myMonitors = new LinkedList<>();
    myAllMemoryData = new FlutterAllMemoryData(profiler.getParentDisposable(), profiler.getApp());
  }

  @Override
  public void enter() {
    // Clear the selection
    getStudioProfilers().getTimeline().getSelectionRange().clear();

    // TODO(terry)
/*
    Common.Session session = getStudioProfilers().getSession();
    if (session != Common.Session.getDefaultInstance()) {
      for (StudioProfiler profiler : getStudioProfilers().getProfilers()) {
        myMonitors.add(profiler.newMonitor());
      }
    }
*/
    myMonitors.forEach(ProfilerMonitor::enter);
  }

  @Override
  public void exit() {
    myMonitors.forEach(ProfilerMonitor::exit);
    myMonitors.clear();
  }

  @NotNull
  public List<ProfilerMonitor> getMonitors() {
    return myMonitors;
  }

  @Override
  public void setTooltip(ProfilerTooltip tooltip) {
    super.setTooltip(tooltip);
    myMonitors.forEach(monitor -> monitor
      .setFocus(getTooltip() instanceof ProfilerMonitorTooltip && ((ProfilerMonitorTooltip)getTooltip()).getMonitor() == monitor));
  }
}

