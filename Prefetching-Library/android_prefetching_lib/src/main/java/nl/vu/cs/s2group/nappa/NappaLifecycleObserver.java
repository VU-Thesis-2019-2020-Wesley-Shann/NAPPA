package nl.vu.cs.s2group.nappa;

import android.app.Activity;
import android.util.Log;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import nl.vu.cs.s2group.nappa.nappaexperimentation.MetricPrefetchingAccuracy;

public class NappaLifecycleObserver implements LifecycleObserver {
    private static final String LOG_TAG = NappaLifecycleObserver.class.getSimpleName();

    private Activity activity;

    public NappaLifecycleObserver(Activity activity) {
        this.activity = activity;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void onResume() {
        Log.d(LOG_TAG, activity.getClass().getCanonicalName() + " - onResume");
        Nappa.setCurrentActivity(activity);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause() {
        Log.d(LOG_TAG, activity.getClass().getCanonicalName() + " - onPause");
        Nappa.leavingCurrentActivity();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void onDestroy() {
        Log.d(LOG_TAG, activity.getClass().getCanonicalName() + " - onDestroy");
        Log.d(LOG_TAG, "-------------F1_SCORE #" + Nappa.metricPrefetchingAccuracyID + " -----------");
        Log.d(LOG_TAG, "F1_SCORE - " + "Intercept list (" + Nappa.list_url_intercepted.size() + ")= " + Nappa.list_url_intercepted.toString());
        Log.d(LOG_TAG, "F1_SCORE - " + "Prefetch list (" + Nappa.list_url_prefetched.size() + ")= " + Nappa.list_url_prefetched.toString());
        Log.d(LOG_TAG, "F1_SCORE - " + "TP list (" + Nappa.list_url_tp.size() + ")= " + Nappa.list_url_tp.toString());
        Log.d(LOG_TAG, "F1_SCORE - " + "FN list (" + Nappa.list_url_fn.size() + ")= " + Nappa.list_url_fn.toString());
        Nappa.metricPrefetchingAccuracyID++;
        int truePositive = Nappa.list_url_tp.size();
        int falseNegative = Nappa.list_url_fn.size();
        int falsePositive = 0;
        for (String url : Nappa.list_url_prefetched) {
            if (!Nappa.list_url_intercepted.contains(url)) falsePositive++;
        }
        MetricPrefetchingAccuracy.log(Nappa.metricPrefetchingAccuracyID, truePositive, falsePositive, falseNegative);
    }
}