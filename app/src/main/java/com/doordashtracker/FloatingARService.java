package com.doordashtracker;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class FloatingARService extends Service {

    private static final int MAX_ORDERS = 100;
    private static final String PREFS_NAME = "DoordashTrackerPrefs";
    private static final String KEY_ORDER_HISTORY = "orderHistory";

    private WindowManager windowManager;
    private View collapsedView;
    private View floatingView;
    private View customToastView;

    private WindowManager.LayoutParams collapsedParams;
    private WindowManager.LayoutParams floatingParams;
    private WindowManager.LayoutParams toastParams;

    private List<Boolean> orderHistory;
    private int lastX;
    private int lastY;

    private static final int STATE_COLLAPSED = 0;
    private static final int STATE_FLOATING = 1;
    private int currentState = STATE_FLOATING;

    private TextView floatingARText;
    private Button floatingAcceptButton;
    private Button floatingDeclineButton;

    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();

        mainHandler = new Handler(Looper.getMainLooper());

        orderHistory = new ArrayList<>();
        loadOrderHistory();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        collapsedView = LayoutInflater.from(this).inflate(R.layout.floating_collapsed_layout, null);
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_ar_layout, null);

        setupWindowParams();
        setupCollapsedView();
        setupFloatingView();

        windowManager.addView(floatingView, floatingParams);
        currentState = STATE_FLOATING;
        updateFloatingUI();
    }

    private void setupWindowParams() {
        collapsedParams = createDefaultParams();
        floatingParams = createDefaultParams();

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int bubbleSize = (int) (displayMetrics.density * 60.0f);

        collapsedParams.gravity = Gravity.TOP | Gravity.START;
        collapsedParams.width = bubbleSize;
        collapsedParams.height = bubbleSize;
        collapsedParams.x = 100;
        collapsedParams.y = 100;

        floatingParams.gravity = Gravity.TOP | Gravity.START;
        floatingParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        floatingParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        floatingParams.x = 0;
        floatingParams.y = 200;

        toastParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        toastParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        toastParams.y = (int) (65 * displayMetrics.density);
    }

    private WindowManager.LayoutParams createDefaultParams() {
        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
        );
    }

    private void setupCollapsedView() {
        setupDrag(collapsedView, collapsedParams);

        collapsedView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                transitionToState(STATE_FLOATING);
            }
        });
    }

    private void setupFloatingView() {
        floatingARText = floatingView.findViewById(R.id.floating_ar_text);
        floatingAcceptButton = floatingView.findViewById(R.id.floating_accept_button);
        floatingDeclineButton = floatingView.findViewById(R.id.floating_decline_button);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final float cornerRadiusDp = 16f;
            final float cornerRadiusPx = cornerRadiusDp * getResources().getDisplayMetrics().density;

            floatingView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                    }
                }
            });
            floatingView.setClipToOutline(true);
        }

        ImageButton minimizeButton = floatingView.findViewById(R.id.floating_minimize_button);
        ImageButton maximizeButton = floatingView.findViewById(R.id.floating_maximize_button);
        ImageButton closeButton = floatingView.findViewById(R.id.floating_close_button);

        floatingAcceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addOrder(true);
            }
        });

        floatingDeclineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addOrder(false);
            }
        });

        minimizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                transitionToState(STATE_COLLAPSED);
            }
        });

        maximizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchMainActivity();
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSelf();
            }
        });

        setupDrag(floatingView, floatingParams);
    }

    private void setupDrag(final View view, final WindowManager.LayoutParams params) {
        View dragHandle;

        if (view == collapsedView) {
            dragHandle = view;
        } else {
            dragHandle = view.findViewById(R.id.floating_drag_handle);
        }

        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private static final long CLICK_TIME_THRESHOLD = 200;
            private float initialTouchX;
            private float initialTouchY;
            private int initialX;
            private int initialY;
            private long lastClickTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        lastClickTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (System.currentTimeMillis() - lastClickTime < CLICK_TIME_THRESHOLD &&
                                view == collapsedView) {
                            transitionToState(STATE_FLOATING);
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (System.currentTimeMillis() - lastClickTime > CLICK_TIME_THRESHOLD) {
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(view, params);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void transitionToState(int newState) {
        if (currentState == newState) {
            return;
        }

        View currentView = getCurrentView();
        WindowManager.LayoutParams currentParams = getCurrentParams();

        if (currentView != null && currentView.getParent() != null) {
            lastX = currentParams.x;
            lastY = currentParams.y;
            windowManager.removeView(currentView);
        }

        currentState = newState;

        View newView = getCurrentView();
        WindowManager.LayoutParams newParams = getCurrentParams();

        newParams.x = lastX;
        newParams.y = lastY;

        windowManager.addView(newView, newParams);

        if (currentState == STATE_FLOATING) {
            updateFloatingUI();
        }
    }

    private View getCurrentView() {
        switch (currentState) {
            case STATE_COLLAPSED:
                return collapsedView;
            case STATE_FLOATING:
                return floatingView;
            default:
                return floatingView;
        }
    }

    private WindowManager.LayoutParams getCurrentParams() {
        switch (currentState) {
            case STATE_COLLAPSED:
                return collapsedParams;
            case STATE_FLOATING:
                return floatingParams;
            default:
                return floatingParams;
        }
    }

    private void launchMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        stopSelf();
    }

    private void showCustomToast(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (customToastView != null && customToastView.getParent() != null) {
                    windowManager.removeView(customToastView);
                }

                customToastView = LayoutInflater.from(FloatingARService.this).inflate(android.R.layout.simple_list_item_1, null);
                TextView textView = customToastView.findViewById(android.R.id.text1);
                textView.setText(message);
                textView.setTextSize(14);
                textView.setTextColor(0xFFFFFFFF);
                textView.setPadding(48, 24, 48, 24);
                textView.setBackgroundColor(0xE6000000);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    final float cornerRadius = 24 * getResources().getDisplayMetrics().density;
                    customToastView.setOutlineProvider(new ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, Outline outline) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
                            }
                        }
                    });
                    customToastView.setClipToOutline(true);
                }

                try {
                    windowManager.addView(customToastView, toastParams);

                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (customToastView != null && customToastView.getParent() != null) {
                                try {
                                    windowManager.removeView(customToastView);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                customToastView = null;
                            }
                        }
                    }, 2000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void addOrder(boolean accepted) {
        if (orderHistory.size() >= MAX_ORDERS) {
            orderHistory.remove(0);
        }

        orderHistory.add(accepted);
        saveOrderHistory();

        updateFloatingUI();

        String message = accepted ? "✓ Accepted" : "✗ Declined";
        showCustomToast(message);
    }

    private void updateFloatingUI() {
        if (orderHistory.isEmpty()) {
            floatingARText.setText("0%");
            floatingARText.setTextColor(0xFFF44336);
            return;
        }

        int acceptedCount = 0;
        for (Boolean order : orderHistory) {
            if (order) {
                acceptedCount++;
            }
        }

        double acceptanceRate = (acceptedCount * 100.0) / orderHistory.size();
        floatingARText.setText(String.format("%.1f%%", acceptanceRate));

        if (acceptanceRate < 50) {
            floatingARText.setTextColor(0xFFF44336);
        } else if (acceptanceRate < 70) {
            floatingARText.setTextColor(0xFFFFB300);
        } else {
            floatingARText.setTextColor(0xFF4CAF50);
        }
    }

    private void saveOrderHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orderHistory.size(); i++) {
            sb.append(orderHistory.get(i) ? "1" : "0");
            if (i < orderHistory.size() - 1) {
                sb.append(",");
            }
        }

        editor.putString(KEY_ORDER_HISTORY, sb.toString());
        editor.apply();
    }

    private void loadOrderHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String historyString = prefs.getString(KEY_ORDER_HISTORY, "");

        orderHistory.clear();

        if (!historyString.isEmpty()) {
            String[] parts = historyString.split(",");
            for (String part : parts) {
                orderHistory.add(part.equals("1"));
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (collapsedView != null && collapsedView.getParent() != null) {
            windowManager.removeView(collapsedView);
        }
        if (floatingView != null && floatingView.getParent() != null) {
            windowManager.removeView(floatingView);
        }
        if (customToastView != null && customToastView.getParent() != null) {
            try {
                windowManager.removeView(customToastView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
