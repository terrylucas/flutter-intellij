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

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.FpsTimer;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.StopwatchTimer;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import com.android.tools.profilers.*;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import io.flutter.perf.HeapMonitor;
import io.flutter.run.daemon.FlutterApp;
import org.dartlang.vm.service.element.IsolateRef;
import org.dartlang.vm.service.element.VM;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The suite of profilers inside Android Studio. This object is responsible for maintaining the information
 * global across all the profilers, device management, process management, current state of the tool etc.
 */
public class FlutterStudioProfilers extends AspectModel<ProfilerAspect> implements Updatable {

  /**
   * The number of updates per second our simulated object models receive.
   */
  public static final int PROFILERS_UPDATE_RATE = 60;

  @NotNull
  // TODO(terry): ?????
  private FlutterStage myStage;

  private final ProfilerTimeline myTimeline;

  private final List<FlutterStudioProfilers> myProfilers;

  private Updater myUpdater;

  private AxisComponentModel myViewAxis;

  private long myRefreshDevices;

  public Disposable getParentDisposable() {
    return myParentDisposable;
  }

  public FlutterApp getApp() {
    return myApp;
  }

  private Disposable myParentDisposable;
  private FlutterApp myApp;

  /**
   * Whether the profiler should auto-select a process to profile.
   */
  private boolean myAutoProfilingEnabled = true;

  public FlutterStudioProfilers(Disposable parentDisposable, FlutterApp app) {
    this(parentDisposable, app, new FpsTimer(PROFILERS_UPDATE_RATE));
  }

  public FlutterStudioProfilers(Disposable parentDisposable, FlutterApp app, StopwatchTimer timer) {
    myUpdater = new Updater(timer);
    myApp = app;
    myParentDisposable = parentDisposable;

    ImmutableList.Builder<FlutterStudioProfilers> profilersBuilder = new ImmutableList.Builder<>();

    /*
    profilersBuilder.add(new EventProfiler(this));
    profilersBuilder.add(new CpuProfiler(this));
    profilersBuilder.add(new MemoryProfiler(this));
    profilersBuilder.add(new NetworkProfiler(this));
    if (myIdeServices.getFeatureConfig().isEnergyProfilerEnabled()) {
      profilersBuilder.add(new EnergyProfiler(this));
    }
*/

    myProfilers = profilersBuilder.build();

    myTimeline = new ProfilerTimeline(myUpdater);
    syncVmClock();
    myViewAxis = new ResizingAxisComponentModel.Builder(myTimeline.getViewRange(), TimeAxisFormatter.DEFAULT)
      .setGlobalRange(myTimeline.getDataRange()).build();
    setMonitoringStage();
  }
  private void syncVmClock() {
    boolean[] isClockSynced = {false};
    HeapMonitor.HeapListener listener = new HeapMonitor.HeapListener() {
      @Override
      public void handleIsolatesInfo(VM vm, List<HeapMonitor.IsolateObject> isolates) {
        final HeapState heapState = new HeapState(60 * 1000);
        heapState.handleIsolatesInfo(vm,isolates);
        updateModel(heapState);
      }

      @Override
      public void handleGCEvent(IsolateRef iIsolateRef, HeapMonitor.HeapSpace newHeapSpvace, HeapMonitor.HeapSpace oldHeapSpace) {

      }

      private void updateModel(HeapState heapState) {
        if (!isClockSynced[0]) {
          isClockSynced[0] = true;
          long timeUs = TimeUnit.MILLISECONDS.toNanos(heapState.getSamples().get(0).getSampleTime());
          myTimeline.reset(timeUs, timeUs);
        }
      }
    };
    assert myApp.getPerfService() != null;
    myApp.getPerfService().addHeapListener(listener);
    Disposer.register(myParentDisposable, () -> myApp.getPerfService().removeHeapListener(listener));
  }
  public boolean isStopped() {
    return !myUpdater.isRunning();
  }

  public void stop() {
    if (isStopped()) {
      // Profiler is already stopped. Nothing to do. Ideally, this method shouldn't be called when the profiler is already stopped.
      // However, some exceptions might be thrown when listeners are notified about ProfilerAspect.STAGE aspect change and react
      // accordingly. In this case, we could end up with an inconsistent model and allowing to try to call stop and notify the listeners
      // again can only make it worse. Therefore, we return early to avoid making the model problem bigger.
      return;
    }
    // The following line can't throw an exception, will stop the updater's timer and guarantees future calls to isStopped() return true.
    myUpdater.stop();
    changed(ProfilerAspect.STAGE);
  }

  @Override
  public void update(long elapsedNs) {
    myRefreshDevices += elapsedNs;
    if (myRefreshDevices < TimeUnit.SECONDS.toNanos(1)) {
      return;
    }
    myRefreshDevices = 0;

        // These need to be fired every time the process list changes so that the device/process dropdown always reflects the latest.
        changed(ProfilerAspect.DEVICES);
        changed(ProfilerAspect.PROCESSES);

  }

  public void setMonitoringStage() {
    setStage(new FlutterStudioMonitorStage(this));
  }

  @NotNull
  public FlutterStage getStage() {
    return myStage;
  }

  @Nullable
  public ProfilerClient getClient() {
    return null;
  }

  /**
   * Return the selected app's package name if present, otherwise returns empty string.
   * <p>
   * <p>TODO (78597376): Clean up the method to make it reusable.</p>
   */
  @NotNull
  public String getSelectedAppName() {
    return "";
  }

  public void setStage(@NotNull FlutterStage stage) {
    if (myStage != null) {
      myStage.exit();
    }
    getTimeline().getSelectionRange().clear();
    myStage = stage;
    myStage.getStudioProfilers().getUpdater().reset();
    myStage.enter();
    this.changed(ProfilerAspect.STAGE);
  }

  @NotNull
  public ProfilerTimeline getTimeline() {
    return myTimeline;
  }

  public List<FlutterStudioProfilers> getProfilers() {
    return myProfilers;
  }

  public ProfilerMode getMode() {
    return myStage.getProfilerMode();
  }

  public void modeChanged() {
    changed(ProfilerAspect.MODE);
  }

  public Updater getUpdater() {
    return myUpdater;
  }

  public AxisComponentModel getViewAxis() {
    return myViewAxis;
  }

  /**
   * Return the list of stages that target a specific profiler, which a user might want to jump
   * between. This should exclude things like the top-level profiler stage, null stage, etc.
   */
  public List<Class<? extends FlutterStage>> getDirectStages() {
    ImmutableList.Builder<Class<? extends FlutterStage>> listBuilder = ImmutableList.builder();
/*
    listBuilder.add(CpuProfilerStage.class);
    listBuilder.add(MemoryProfilerStage.class);
    listBuilder.add(NetworkProfilerStage.class);
    // Show the energy stage in the list only when the session has JVMTI enabled or the device is above O.
    boolean hasSession = mySelectedSession.getSessionId() != 0;
    boolean isEnergyStageEnabled = hasSession ? mySessionsManager.getSelectedSessionMetaData().getJvmtiEnabled()
                                              : myDevice != null && myDevice.getFeatureLevel() >= AndroidVersion.VersionCodes.O;
    if (getIdeServices().getFeatureConfig().isEnergyProfilerEnabled() && isEnergyStageEnabled) {
      listBuilder.add(EnergyProfilerStage.class);
    }
*/
    return listBuilder.build();
  }

  @NotNull
  public Class<? extends FlutterStage> getStageClass() {
    return myStage.getClass();
  }

  // TODO: Unify with how monitors expand.
  public void setNewStage(Class<? extends FlutterStage> clazz) {
    try {
      Constructor<? extends FlutterStage> constructor = clazz.getConstructor(FlutterStudioProfilers.class);
      FlutterStage stage = constructor.newInstance(this);
      setStage(stage);
    }
    catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
      // will not happen
    }
  }

}
