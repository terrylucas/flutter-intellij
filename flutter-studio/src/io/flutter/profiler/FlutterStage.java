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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.ProfilerTooltip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * This name matches the Android Profiling tool Stage mechanism.  They use the
 * notion of a Stage for basic inheritance (we might be able to eliminate
 * if they componentize for now we need some basic overrides and return some
 * basic interfaces.  For now we use their structure and I've maintained their
 * naming mechanism.
 * <p>
 * TODO(terry): adt-ui folks looking at componentizing their notion of a stage.
 * <p>
 * One of the stages the profiler tool goes through. It models a "state" in
 * the Android 3.3 adt-ui code.
 */
public abstract class FlutterStage extends AspectObserver {

  protected static final long PROFILING_INSTRUCTIONS_EASE_OUT_NS =
    TimeUnit.SECONDS.toNanos(3);

  private final FlutterStudioProfilers profilers;

  private ProfilerMode profilerMode = ProfilerMode.NORMAL;

  /**
   * The active tooltip for stages that contain more than one tooltips.
   */
  @Nullable
  private ProfilerTooltip tooltip;

  public FlutterStage(@NotNull FlutterStudioProfilers profilers) {
    this.profilers = profilers;
  }

  public FlutterStudioProfilers getStudioProfilers() {
    return profilers;
  }

  abstract public void enter();

  abstract public void exit();

  @NotNull
  public final ProfilerMode getProfilerMode() { return profilerMode; }

  @Nullable
  public ProfilerTooltip getTooltip() {
    return tooltip;
  }

  /**
   * Allow inheriting classes to modify the {@link ProfilerMode}.
   * <p>
   * Note that this method is intentionally not public, as only the stages
   * themselves should contain the logic for setting their profiler mode.
   * If a view finds itself needing to toggle the profiler mode, it should
   * do it indirectly, either by modifying a model class inside the stage
   * or by calling a public method on the stage which changes the mode as
   * a side effect.
   */
  protected final void setProfilerMode(
    @NotNull ProfilerMode profilerMode) {
    if (profilerMode != profilerMode) {
      profilerMode = profilerMode;
      getStudioProfilers().modeChanged();
    }
  }

  /**
   * Changes the active tooltip to the given type.
   *
   * @param theTooltip
   */
  public void setTooltip(ProfilerTooltip theTooltip) {
    // TODO(terry): Need to get this working not quite there.
    if (theTooltip != null && tooltip != null && theTooltip.getClass().equals(tooltip.getClass())) {
      return;
    }
    if (tooltip != null) {
      tooltip.dispose();
    }
    tooltip = theTooltip;
    getStudioProfilers().changed(ProfilerAspect.TOOLTIP);
  }
}
