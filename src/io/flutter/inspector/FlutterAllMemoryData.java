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

import com.android.tools.adtui.model.*;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.MemoryDataSeries;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import io.flutter.perf.HeapMonitor;
import io.flutter.run.daemon.FlutterApp;
import org.dartlang.vm.service.element.IsolateRef;
import org.dartlang.vm.service.element.VM;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class FlutterAllMemoryData {
  public class ThreadSafeData implements DataSeries<Long> {
    List<SeriesData<Long>> mData = new CopyOnWriteArrayList<SeriesData<Long>>();

    public ThreadSafeData() { }

    @Override
    public List<SeriesData<Long>> getDataForXRange(Range xRange) {
      List<SeriesData<Long>> outData = new ArrayList<>();

      // binary search for mData.x >= xRange.getMin() add till max
      Iterator<SeriesData<Long>> it = mData.iterator();
      while (it.hasNext()) {
        SeriesData<Long> data = it.next();

        if (data.x > xRange.getMax()) {
          break;
        }
        if (data.x >= xRange.getMin()) {
          outData.add(data);
        }
      }

      return outData;
    }
  }

  // mMultiData[0] is heap used
  // mMultiData[1] is total heap allocated
  List<ThreadSafeData> mMultiData;

  public FlutterAllMemoryData(Disposable parentDisposable, FlutterApp app) {
    // TODO(terry): MULTI-DATA
    mMultiData = new ArrayList<>();

    mMultiData.add(0, new ThreadSafeData());    // Heap used.
    mMultiData.add(1, new ThreadSafeData());    // Max Heap allocated

    HeapMonitor.HeapListener listener = new HeapMonitor.HeapListener() {
      @Override
      public void handleIsolatesInfo(VM vm, List<HeapMonitor.IsolateObject> isolates) {
        final HeapState heapState = new HeapState(60 * 1000);
        heapState.handleIsolatesInfo(vm, isolates);
        updateModel(heapState);
      }

      @Override
      public void handleGCEvent(IsolateRef iIsolateRef, HeapMonitor.HeapSpace newHeapSpvace, HeapMonitor.HeapSpace oldHeapSpace) {
        // TODO(terry): Add trashcan glyph for GC in timeline.
      }

      private void updateModel(HeapState heapState) {
        List<HeapMonitor.HeapSample> samples = heapState.getSamples();
        // LOG.info("Model Size = " + samples.size());
        //HeapMonitor.HeapSample sample = samples.get(0);
        for(HeapMonitor.HeapSample sample : samples) {
          // Collect # of bytes used in the heap.
          mMultiData.get(0).mData.add(new SeriesData<>(TimeUnit.MILLISECONDS.toMicros(sample.getSampleTime()), (long)sample.getBytes()));
          // Collect allocated size of heap.
          mMultiData.get(1).mData.add(new SeriesData<>(TimeUnit.MILLISECONDS.toMicros(sample.getSampleTime()), (long)heapState.getMaxHeapInBytes()));
        }
      }
    };
    assert app.getPerfService() != null;
    app.getPerfService().addHeapListener(listener);
    Disposer.register(parentDisposable, () -> app.getPerfService().removeHeapListener(listener));
  }

  protected RangedContinuousSeries createRangedSeries(StudioProfilers profilers,
                                                      String name,
                                                      Range range) {
    return new RangedContinuousSeries(name, profilers.getTimeline().getViewRange(), range, getHeapMaxDetails());
  }

  ThreadSafeData getHeapUsedDataSeries() {
    return mMultiData.get(0);
  }

  ThreadSafeData getHeapMaxDetails() {
    return mMultiData.get(1);
  }

}
