package com.doordashtracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    
    private static final int MAX_ORDERS = 100;
    private static final String PREFS_NAME = "DoordashTrackerPrefs";
    private static final String KEY_ORDER_HISTORY = "orderHistory";
    private static final int GRID_COLUMNS = 10;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1001;
    
    private List<Boolean> orderHistory;
    private TextView acceptanceRateText;
    private TextView ordersNeededText;
    private TextView totalOrdersText;
    private TextView acceptedCountText;
    private TextView declinedCountText;
    private LinearLayout nextFiveContainer;
    private Button resetButton;
    private Button floatingModeButton;
    private boolean showingFullHistory = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        orderHistory = new ArrayList<>();
        loadOrderHistory();
        
        acceptanceRateText = findViewById(R.id.acceptance_rate_text);
        ordersNeededText = findViewById(R.id.orders_needed_text);
        totalOrdersText = findViewById(R.id.total_orders_text);
        acceptedCountText = findViewById(R.id.accepted_count_text);
        declinedCountText = findViewById(R.id.declined_count_text);
        nextFiveContainer = findViewById(R.id.next_five_container);
        resetButton = findViewById(R.id.reset_button);
        floatingModeButton = findViewById(R.id.floating_mode_button);
        
        Button acceptButton = findViewById(R.id.accept_button);
        Button declineButton = findViewById(R.id.decline_button);
        
        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addOrder(true);
            }
        });
        
        declineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addOrder(false);
            }
        });
        
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showResetConfirmation();
            }
        });
        
        floatingModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFloatingMode();
            }
        });
        
        nextFiveContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleHistoryView();
            }
        });
        
        updateUI();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadOrderHistory();
        updateUI();
    }

    private void startFloatingMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
                Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Intent serviceIntent = new Intent(this, FloatingARService.class);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this, "Floating mode activated", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    startFloatingMode();
                } else {
                    Toast.makeText(this, "Permission denied. Cannot start floating mode.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
    
    private void showResetConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset History");
        builder.setMessage("Are you sure you want to clear all order history? This action cannot be undone.");
        
        builder.setPositiveButton("Reset", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                resetHistory();
            }
        });
        
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private void toggleHistoryView() {
        showingFullHistory = !showingFullHistory;
        
        if (showingFullHistory) {
            updateFullHistoryDisplay();
        } else {
            updateNextFiveDisplay();
        }
    }
    
    private void addOrder(boolean accepted) {
        if (orderHistory.size() >= MAX_ORDERS) {
            orderHistory.remove(0);
        }
        
        orderHistory.add(accepted);
        saveOrderHistory();
        updateUI();
        
        String message = accepted ? "✓ Accepted" : "✗ Declined";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    private void resetHistory() {
        orderHistory.clear();
        saveOrderHistory();
        showingFullHistory = false;
        updateUI();
        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
    }
    
    private void updateUI() {
        if (orderHistory.isEmpty()) {
            acceptanceRateText.setText("0%");
            acceptanceRateText.setTextColor(0xFFF44336);
            ordersNeededText.setText("No orders tracked yet");
            totalOrdersText.setText("Total: 0");
            acceptedCountText.setText("Accepted: 0");
            declinedCountText.setText("Declined: 0");
            updateNextFiveDisplay();
            return;
        }
        
        int acceptedCount = 0;
        int declinedCount = 0;
        
        for (Boolean order : orderHistory) {
            if (order) {
                acceptedCount++;
            } else {
                declinedCount++;
            }
        }
        
        int totalOrders = orderHistory.size();
        double acceptanceRate = (acceptedCount * 100.0) / totalOrders;
        
        acceptanceRateText.setText(String.format("%.1f%%", acceptanceRate));
        
        if (acceptanceRate < 50) {
            acceptanceRateText.setTextColor(0xFFF44336);
        } else if (acceptanceRate < 70) {
            acceptanceRateText.setTextColor(0xFFFFB300);
        } else {
            acceptanceRateText.setTextColor(0xFF4CAF50);
        }
        
        totalOrdersText.setText("Total: " + totalOrders);
        acceptedCountText.setText("Accepted: " + acceptedCount);
        declinedCountText.setText("Declined: " + declinedCount);
        
        int ordersUntilNextDeclineFallsOff = calculateOrdersUntilNextDeclineFallsOff();
        
        if (declinedCount == 0) {
            ordersNeededText.setText("Perfect! No declines in your history");
        } else if (ordersUntilNextDeclineFallsOff == -1) {
            ordersNeededText.setText("Track more orders to see when declines fall off");
        } else if (ordersUntilNextDeclineFallsOff == 1) {
            ordersNeededText.setText("Next decline will fall off with your next order!");
        } else {
            ordersNeededText.setText("Next decline falls off in " + ordersUntilNextDeclineFallsOff + " orders");
        }
        
        if (showingFullHistory) {
            updateFullHistoryDisplay();
        } else {
            updateNextFiveDisplay();
        }
    }
    
    private int calculateOrdersUntilNextDeclineFallsOff() {
        if (orderHistory.size() < MAX_ORDERS) {
            return -1;
        }
        
        int firstDeclineIndex = -1;
        for (int i = 0; i < orderHistory.size(); i++) {
            if (!orderHistory.get(i)) {
                firstDeclineIndex = i;
                break;
            }
        }
        
        if (firstDeclineIndex == -1) {
            return -1;
        }
        
        return firstDeclineIndex + 1;
    }
    
    private void updateNextFiveDisplay() {
        nextFiveContainer.removeAllViews();
        
        TextView headerText = new TextView(this);
        headerText.setText("Next 5 Orders to Fall Off (Tap to switch)");
        headerText.setGravity(Gravity.CENTER);
        headerText.setTextColor(0xFFFFFFFF);
        headerText.setTextSize(16);
        headerText.setTypeface(null, Typeface.BOLD);
        headerText.setPadding(0, 0, 0, 24);
        nextFiveContainer.addView(headerText);
        
        int itemsToShow = Math.min(5, orderHistory.size());
        
        if (itemsToShow == 0) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No orders to display");
            emptyText.setTextColor(0xFF888888);
            emptyText.setTextSize(14);
            emptyText.setPadding(20, 20, 20, 20);
            nextFiveContainer.addView(emptyText);
            return;
        }
        
        for (int i = 0; i < itemsToShow; i++) {
            View orderItem = getLayoutInflater().inflate(R.layout.order_item, nextFiveContainer, false);
            
            TextView orderNumber = orderItem.findViewById(R.id.order_number);
            View orderIndicator = orderItem.findViewById(R.id.order_indicator);
            TextView orderStatus = orderItem.findViewById(R.id.order_status);
            
            boolean isAccepted = orderHistory.get(i);
            
            orderNumber.setText(String.valueOf(i + 1));
            
            if (isAccepted) {
                orderIndicator.setBackgroundColor(0xFF4CAF50);
                orderStatus.setText("Accept");
                orderStatus.setTextColor(0xFF4CAF50);
            } else {
                orderIndicator.setBackgroundColor(0xFFF44336);
                orderStatus.setText("Decline");
                orderStatus.setTextColor(0xFFF44336);
            }
            
            nextFiveContainer.addView(orderItem);
        }
    }
    
    private int calculateCellSize() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        
        int horizontalPadding = (int) (72 * displayMetrics.density);
        int availableWidth = screenWidth - horizontalPadding;
        
        int totalMargins = (GRID_COLUMNS + 1) * (int) (8 * displayMetrics.density);
        int widthForCells = availableWidth - totalMargins;
        
        int cellSize = widthForCells / GRID_COLUMNS;
        
        return cellSize;
    }
    
    private void updateFullHistoryDisplay() {
        nextFiveContainer.removeAllViews();
        
        TextView headerText = new TextView(this);
        headerText.setText("Full Order History (Tap to switch)");
        headerText.setGravity(Gravity.CENTER);
        headerText.setTextColor(0xFFFFFFFF);
        headerText.setTextSize(16);
        headerText.setTypeface(null, Typeface.BOLD);
        headerText.setPadding(0, 0, 0, 24);
        nextFiveContainer.addView(headerText);
        
        if (orderHistory.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No orders to display");
            emptyText.setTextColor(0xFF888888);
            emptyText.setTextSize(14);
            emptyText.setPadding(20, 20, 20, 20);
            nextFiveContainer.addView(emptyText);
            return;
        }
        
        int cellSize = calculateCellSize();
        
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int marginSize = (int) (4 * displayMetrics.density);
        
        GridLayout gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(GRID_COLUMNS);
        gridLayout.setRowCount((int) Math.ceil(orderHistory.size() / (double) GRID_COLUMNS));
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        gridLayout.setLayoutParams(params);
        
        for (int i = orderHistory.size() - 1; i >= 0; i--) {
            boolean isAccepted = orderHistory.get(i);
            
            View cellView = new View(this);
            
            GridLayout.LayoutParams cellParams = new GridLayout.LayoutParams();
            cellParams.width = cellSize;
            cellParams.height = cellSize;
            cellParams.setMargins(marginSize, marginSize, marginSize, marginSize);
            cellView.setLayoutParams(cellParams);
            
            if (isAccepted) {
                cellView.setBackgroundColor(0xFF4CAF50);
            } else {
                cellView.setBackgroundColor(0xFFF44336);
            }
            
            gridLayout.addView(cellView);
        }
        
        nextFiveContainer.addView(gridLayout);
        
        TextView legendText = new TextView(this);
        legendText.setText("\n🟢 Green = Accept  |  🔴 Red = Decline");
        legendText.setTextColor(0xFFCCCCCC);
        legendText.setTextSize(12);
        legendText.setPadding(0, 24, 0, 0);
        legendText.setGravity(android.view.Gravity.CENTER);
        nextFiveContainer.addView(legendText);
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
}
