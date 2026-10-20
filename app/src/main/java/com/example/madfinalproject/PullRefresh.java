package com.example.madfinalproject;

import android.app.Activity;
import android.widget.ScrollView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.function.Consumer;

/** Pull starts only when the page is already at its top; ordinary scrolling stays intact. */
final class PullRefresh {
  private PullRefresh() {}
  static SwipeRefreshLayout wrap(Activity activity, ScrollView scroll, Consumer<Runnable> refresh) {
    SwipeRefreshLayout wrapper=new SwipeRefreshLayout(activity);
    wrapper.setColorSchemeColors(Ui.palette(activity).accent);
    wrapper.setProgressBackgroundColorSchemeColor(Ui.palette(activity).surface);
    wrapper.setOnChildScrollUpCallback((parent,child)->scroll.canScrollVertically(-1));
    wrapper.addView(scroll);
    wrapper.setOnRefreshListener(()->refresh.accept(()->wrapper.post(()->wrapper.setRefreshing(false))));
    return wrapper;
  }
}
