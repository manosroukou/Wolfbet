package com.example.ergasiaui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.appcompat.app.AlertDialog;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.drawerlayout.widget.DrawerLayout;

import model.enums.PlayerActions;
import model.enums.Sender;
import serializables.WorkerGame;
import serializables.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private String playerId;
    private float currentBalance = 100;
    private TextView tvBalance;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvBalance = findViewById(R.id.tvBalance);
        TextView tvUsername = findViewById(R.id.tvUsername);
        ImageButton btnAddBalance = findViewById(R.id.btnAddBalance);
        ImageButton btnBack = findViewById(R.id.btnBack);
        LinearLayout backBar = findViewById(R.id.backBar);
        ImageButton btnInfo = findViewById(R.id.btnInfo);

        // Drawer
        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        TextView tvDrawerUsername = findViewById(R.id.tvDrawerUsername);
        Button btnLogout = findViewById(R.id.btnLogout);

        tvDrawerUsername.setText(playerId);

        // Tap username to open drawer from right
        tvUsername.setOnClickListener(v -> drawerLayout.openDrawer(findViewById(R.id.drawerPanel)));

        // Logout
        btnLogout.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        playerId = getIntent().getStringExtra("USERNAME");
        if (playerId != null && !playerId.isEmpty()) {
            tvUsername.setText(playerId);
        }

        tvBalance.setText(String.format("€%.2f", currentBalance));

        // Navigation setup
        NavHostFragment navHost = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = navHost.getNavController();

        navController.addOnDestinationChangedListener((controller, dest, args) -> {
            if (dest.getId() == R.id.homeFragment) {
                backBar.setVisibility(View.GONE);
            } else {
                backBar.setVisibility(View.VISIBLE);

                // Show info icon only if gameInfoText is provided
                String infoText = null;
                if (args != null) {
                    infoText = args.getString("gameInfoText");
                }

                if (infoText != null && !infoText.isEmpty()) {
                    btnInfo.setVisibility(View.VISIBLE);
                    String finalText = infoText;
                    btnInfo.setOnClickListener(v -> {
                        new AlertDialog.Builder(this)
                                .setTitle("Πώς παίζεται")
                                .setMessage(finalText)
                                .setPositiveButton("OK", null)
                                .show();
                    });
                } else {
                    btnInfo.setVisibility(View.GONE);
                }
            }
        });

        btnBack.setOnClickListener(v -> navController.navigateUp());

        // Add balance
        btnAddBalance.setOnClickListener(v -> showAddBalanceDialog());
    }
    public String getPlayerId() { return playerId; }

    private void showAddBalanceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Balance");
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Enter amount");
        input.setPadding(50, 40, 50, 40);
        builder.setView(input);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String amountStr = input.getText().toString().trim();
            if (!amountStr.isEmpty()) {
                currentBalance += Double.parseDouble(amountStr);
                tvBalance.setText(String.format("€%.2f", currentBalance));
            }
        });
        builder.setNegativeButton("Cancel", (d, w) -> d.cancel());
        builder.show();
    }

    public float getBalance() { return currentBalance; }
    public void setBalance(float balance) {
        this.currentBalance = balance;
        tvBalance.setText(String.format("€%.2f", balance));
    }
}