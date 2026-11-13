package dev.calendarquiet;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.Window;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Framework-only driver, also usable against a minified predecessor APK. */
public class PerformanceRunner extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Activity activity = null;
        HandlerThread sampler = new HandlerThread("FrameSampler");
        sampler.start();
        ConcurrentLinkedQueue<long[]> frames = new ConcurrentLinkedQueue<>();
        AtomicInteger droppedCallbacks = new AtomicInteger();
        try {
            Activity launched = startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(), "dev.calendarquiet.MainActivity")
                .setAction("dev.calendarquiet.OPEN_TIMER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activity = launched;
            waitForIdleSync(); Thread.sleep(1000);
            Window.OnFrameMetricsAvailableListener listener = (window, metric, dropped) -> {
                droppedCallbacks.addAndGet(dropped);
                if (metric.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0) frames.add(new long[] {
                    metric.getMetric(FrameMetrics.TOTAL_DURATION), metric.getMetric(FrameMetrics.DEADLINE),
                    metric.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION), metric.getMetric(FrameMetrics.DRAW_DURATION)});
            };
            runOnMainSync(() -> launched.getWindow().addOnFrameMetricsAvailableListener(listener, new Handler(sampler.getLooper())));
            Map<String, String> before = Debug.getRuntimeStats();
            long cpuBefore = android.os.Process.getElapsedCpuTime(), started = SystemClock.elapsedRealtime();
            for (int i = 0; i < 6; i++) {
                tap("Calendar"); Thread.sleep(450);
                shell("input swipe 360 1200 360 550 280"); Thread.sleep(150);
                tap("Timer"); Thread.sleep(450);
            }
            long elapsed = SystemClock.elapsedRealtime() - started, cpu = android.os.Process.getElapsedCpuTime() - cpuBefore;
            Map<String, String> after = Debug.getRuntimeStats();
            runOnMainSync(() -> launched.getWindow().removeOnFrameMetricsAvailableListener(listener));
            sampler.quitSafely(); sampler.join();
            Debug.MemoryInfo memory = new Debug.MemoryInfo(); Debug.getMemoryInfo(memory);
            ArrayList<long[]> values = new ArrayList<>(frames);
            JSONArray raw = new JSONArray(); int missed = 0;
            for (long[] value : values) { JSONArray row = new JSONArray(); for (long n : value) row.put(n); raw.put(row); if (value[1] > 0 && value[0] > value[1]) missed++; }
            JSONObject result = new JSONObject().put("journey", "Six Timer/Calendar round trips and timeline scrolls")
                .put("elapsed_ms", elapsed).put("app_cpu_ms", cpu).put("frames", values.size()).put("missed_deadlines", missed)
                .put("frame_p50_ms", percentile(values, 0, .5)).put("frame_p95_ms", percentile(values, 0, .95))
                .put("layout_p95_ms", percentile(values, 2, .95)).put("draw_p95_ms", percentile(values, 3, .95))
                .put("dropped_metric_callbacks", droppedCallbacks.get()).put("process_pss_kb", memory.getTotalPss())
                .put("java_heap_bytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                .put("native_heap_allocated_bytes", Debug.getNativeHeapAllocatedSize())
                .put("runtime_before", new JSONObject(before)).put("runtime_after", new JSONObject(after)).put("frames_ns", raw)
                .put("note", "Emulator; whole-process allocation includes this identical driver. No physical power measurement.");
            Bundle output = new Bundle(); output.putString("stream", "\nPERF_JSON:" + result + "\nOK (performance journey)\n");
            runOnMainSync(launched::finish); activity = null;
            finish(Activity.RESULT_OK, output);
        } catch (Throwable error) {
            Bundle output = new Bundle(); output.putString("stream", "\nFAILED: " + android.util.Log.getStackTraceString(error) + "\n");
            finish(Activity.RESULT_CANCELED, output);
        } finally {
            if (activity != null) { Activity last = activity; runOnMainSync(last::finish); }
            sampler.quitSafely();
        }
    }
    private static double percentile(ArrayList<long[]> rows, int index, double p) {
        ArrayList<Long> sorted = new ArrayList<>(); for (long[] row : rows) sorted.add(row[index]); Collections.sort(sorted);
        return sorted.isEmpty() ? 0 : sorted.get((int)((sorted.size() - 1) * p)) / 1_000_000.0;
    }
    private void shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor fd = getUiAutomation().executeShellCommand(command); FileInputStream input = new FileInputStream(fd.getFileDescriptor())) {
            byte[] buffer = new byte[1024]; while (input.read(buffer) != -1) { }
        }
    }
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo node, String label) {
        if (node == null) return null;
        if (label.contentEquals(node.getText() == null ? "" : node.getText()) || label.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) return node;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo value = find(node.getChild(i), label); if (value != null) return value; }
        return null;
    }
    private static String describe(AccessibilityNodeInfo node) {
        if (node == null) return "<no active window>";
        StringBuilder s = new StringBuilder();
        if (node.getText() != null) s.append(node.getText()).append("|");
        if (node.getContentDescription() != null) s.append(node.getContentDescription()).append("|");
        for (int i = 0; i < node.getChildCount(); i++) s.append(describe(node.getChild(i)));
        return s.toString();
    }
    private void tap(String label) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            AccessibilityNodeInfo node = find(getUiAutomation().getRootInActiveWindow(), label);
            if (node != null) {
                android.graphics.Rect bounds = new android.graphics.Rect(); node.getBoundsInScreen(bounds);
                shell("input tap " + bounds.centerX() + " " + bounds.centerY());
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Could not click " + label + ": " + describe(getUiAutomation().getRootInActiveWindow()));
    }
}
