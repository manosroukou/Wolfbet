package com.example.ergasiaui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

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

import model.enums.BetCategory;
import model.enums.PlayerActions;
import model.enums.RiskLevel;
import model.enums.Sender;
import serializables.SearchFilters;
import serializables.Message;
import serializables.WorkerGame;

public class FilterFragment extends Fragment {

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private HomeViewModel   viewModel;

    // ── State ────────────────────────────────────────────────────────────────
    private BetCategory selectedBet     = null;
    private RiskLevel   selectedRisk    = null;
    private int         selectedStars   = 0;

    // ── Star ImageViews ──────────────────────────────────────────────────────
    private ImageView[] starViews = new ImageView[5];

    // ── Results views ────────────────────────────────────────────────────────
    private LinearLayout llFilterResults;
    private HorizontalScrollView hsvResults;
    private TextView tvResultsLabel;
    private TextView tvNoResults;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        selectedBet   = viewModel.getActiveBetCategory();
        selectedRisk  = viewModel.getActiveRiskLevel();
        selectedStars = viewModel.getActiveMinStars();

        // Results section
        llFilterResults = view.findViewById(R.id.llFilterResults);
        hsvResults      = view.findViewById(R.id.hsvResults);
        tvResultsLabel  = view.findViewById(R.id.tvResultsLabel);
        tvNoResults     = view.findViewById(R.id.tvNoResults);

        setupBetButtons(view);
        setupRiskButtons(view);
        setupStarPicker(view);
        setupActionButtons(view);

        refreshBetUI(view);
        refreshRiskUI(view);
        refreshStarsUI();
    }

    // ────────────────────────────────────────────────────────────────────────
    // BET CATEGORY
    // ────────────────────────────────────────────────────────────────────────

    private void setupBetButtons(View view) {
        Button btnBetAll  = view.findViewById(R.id.btnBetAll);
        Button btnBetLow  = view.findViewById(R.id.btnBetLow);
        Button btnBetMid  = view.findViewById(R.id.btnBetMid);
        Button btnBetHigh = view.findViewById(R.id.btnBetHigh);

        btnBetAll.setOnClickListener(v -> {
            selectedBet = null;
            refreshBetUI(view);
        });
        btnBetLow.setOnClickListener(v -> {
            selectedBet = BetCategory.LOW;
            refreshBetUI(view);
        });
        btnBetMid.setOnClickListener(v -> {
            selectedBet = BetCategory.MEDIUM;
            refreshBetUI(view);
        });
        btnBetHigh.setOnClickListener(v -> {
            selectedBet = BetCategory.HIGH;
            refreshBetUI(view);
        });
    }

    private void refreshBetUI(View view) {
        int[] btnIds = {
                R.id.btnBetAll, R.id.btnBetLow,
                R.id.btnBetMid, R.id.btnBetHigh
        };
        BetCategory[] vals = { null, BetCategory.LOW, BetCategory.MEDIUM, BetCategory.HIGH };

        for (int i = 0; i < btnIds.length; i++) {
            Button btn = view.findViewById(btnIds[i]);
            boolean active = (vals[i] == selectedBet);
            applyButtonStyle(btn, active);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // RISK LEVEL
    // ────────────────────────────────────────────────────────────────────────

    private void setupRiskButtons(View view) {
        Button btnRiskAll  = view.findViewById(R.id.btnRiskAll);
        Button btnRiskLow  = view.findViewById(R.id.btnRiskLow);
        Button btnRiskMed  = view.findViewById(R.id.btnRiskMed);
        Button btnRiskHigh = view.findViewById(R.id.btnRiskHigh);

        btnRiskAll.setOnClickListener(v -> {
            selectedRisk = null;
            refreshRiskUI(view);
        });
        btnRiskLow.setOnClickListener(v -> {
            selectedRisk = RiskLevel.LOW;
            refreshRiskUI(view);
        });
        btnRiskMed.setOnClickListener(v -> {
            selectedRisk = RiskLevel.MEDIUM;
            refreshRiskUI(view);
        });
        btnRiskHigh.setOnClickListener(v -> {
            selectedRisk = RiskLevel.HIGH;
            refreshRiskUI(view);
        });
    }

    private void refreshRiskUI(View view) {
        int[] btnIds = {
                R.id.btnRiskAll, R.id.btnRiskLow,
                R.id.btnRiskMed, R.id.btnRiskHigh
        };
        RiskLevel[] vals = { null, RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH };

        for (int i = 0; i < btnIds.length; i++) {
            Button btn = view.findViewById(btnIds[i]);
            boolean active = (vals[i] == selectedRisk);
            applyButtonStyle(btn, active);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // STAR PICKER
    // ────────────────────────────────────────────────────────────────────────

    private void setupStarPicker(View view) {
        int[] starIds = {
                R.id.star1, R.id.star2, R.id.star3,
                R.id.star4, R.id.star5
        };

        for (int i = 0; i < starIds.length; i++) {
            starViews[i] = view.findViewById(starIds[i]);
            final int rating = i + 1;
            starViews[i].setOnClickListener(v -> {
                if (selectedStars == rating) {
                    selectedStars = 0;
                } else {
                    selectedStars = rating;
                }
                refreshStarsUI();
            });
        }
        refreshStarsUI();
    }

    private void refreshStarsUI() {
        for (int i = 0; i < starViews.length; i++) {
            if (starViews[i] == null) continue;
            boolean filled = (i < selectedStars);
            starViews[i].setImageResource(
                    filled
                            ? android.R.drawable.btn_star_big_on
                            : android.R.drawable.btn_star_big_off
            );
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // ACTION BUTTONS (Apply / Reset)
    // ────────────────────────────────────────────────────────────────────────

    private void setupActionButtons(View view) {
        Button btnApply = view.findViewById(R.id.btnApplyFilter);
        Button btnReset = view.findViewById(R.id.btnResetFilter);

        btnApply.setOnClickListener(v -> applyFilters(view));

        btnReset.setOnClickListener(v -> {
            selectedBet   = null;
            selectedRisk  = null;
            selectedStars = 0;
            refreshBetUI(view);
            refreshRiskUI(view);
            refreshStarsUI();
        });
    }

    // ────────────────────────────────────────────────────────────────────────
    // APPLY FILTERS → call Master → show results inline
    // ────────────────────────────────────────────────────────────────────────

    private void applyFilters(View view) {
        viewModel.setActiveFilters(selectedBet, selectedRisk, selectedStars);

        Button btnApply = view.findViewById(R.id.btnApplyFilter);
        btnApply.setEnabled(false);
        btnApply.setText("Searching…");

        // Hide previous results
        hsvResults.setVisibility(View.GONE);
        tvResultsLabel.setVisibility(View.GONE);
        tvNoResults.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                InputStream is = getResources().openRawResource(R.raw.config);
                Properties prop = new Properties();
                prop.load(is);

                String masterIp   = prop.getProperty("master.ip");
                int    masterPort = Integer.parseInt(prop.getProperty("master.port"));

                String requestId = UUID.randomUUID().toString();

                SearchFilters filterRequest = new SearchFilters(
                        selectedStars, selectedBet, selectedRisk, requestId);

                Message<SearchFilters> message = new Message<>(
                        Sender.PLAYER,
                        PlayerActions.FILTER_GAMES,
                        filterRequest,
                        UUID.randomUUID().toString()
                );

                Socket socket = new Socket(masterIp, masterPort);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream());

                out.writeObject(message);
                out.flush();

                List<WorkerGame> filteredGames = (List<WorkerGame>) in.readObject();
                socket.close();

                // Store in ViewModel so Home can use them too
                //viewModel.setGames(filteredGames);

                requireActivity().runOnUiThread(() -> {
                    btnApply.setEnabled(true);
                    btnApply.setText("Apply Filters");
                    showResults(filteredGames);
                });

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    btnApply.setEnabled(true);
                    btnApply.setText("Apply Filters");
                    Toast.makeText(requireContext(),
                            "Connection error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────
    // SHOW RESULTS — inline horizontal scroll with scaled-down cards
    // ────────────────────────────────────────────────────────────────────────

    private void showResults(List<WorkerGame> games) {
        llFilterResults.removeAllViews();
        tvResultsLabel.setVisibility(View.VISIBLE);


        if (games == null || games.isEmpty()) {
            hsvResults.setVisibility(View.GONE);
            tvNoResults.setVisibility(View.VISIBLE);
            return;
        }

        tvNoResults.setVisibility(View.GONE);
        hsvResults.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        float density = getResources().getDisplayMetrics().density;

        for (WorkerGame wg : games) {
            View card = inflater.inflate(R.layout.item_game_card, llFilterResults, false);

            // Scale down to 70%: 140x196dp instead of 200x280dp
            int cardWidth  = (int) (140 * density);
            int cardHeight = (int) (196 * density);
            ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(cardWidth, cardHeight);
            params.setMarginEnd((int) (10 * density));
            card.setLayoutParams(params);

            // Populate with smaller text
            TextView tvTitle  = card.findViewById(R.id.tvGameTitle);
            TextView tvBetCat = card.findViewById(R.id.tvBetCategory);
            TextView tvMinBet = card.findViewById(R.id.tvMinBet);
            TextView tvVotes  = card.findViewById(R.id.tvVotes);
            ImageView ivBackground = card.findViewById(R.id.ivGameBackground);

            tvTitle.setText(wg.getGame().getName());
            tvTitle.setTextSize(14);
            tvBetCat.setText(wg.getBetCategory().name());
            tvBetCat.setTextSize(11);
            tvMinBet.setText(String.format("Min: €%.2f", wg.getGame().getMinBet()));
            tvMinBet.setTextSize(11);
            tvVotes.setText(wg.getRatingCount() + " votes");
            tvVotes.setTextSize(11);

            byte[] imageBytes = wg.getGameLogo();
            if (imageBytes != null && imageBytes.length > 0) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                ivBackground.setImageBitmap(bitmap);
            }

            // Click → navigate to game
            card.setOnClickListener(v -> {
                String gameType = wg.getGame().getName()
                        .toLowerCase().replaceAll("\\s+", "");

                String infoText = getInfoText(gameType);

                Bundle args = new Bundle();
                args.putString("gameInfoText", infoText);
                args.putSerializable("game", wg);
                Navigation.findNavController(v).navigate(
                        R.id.action_filter_to_game, args);
            });

            llFilterResults.addView(card);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // GAME INFO TEXT — maps game name to description string
    // ────────────────────────────────────────────────────────────────────────

    private String getInfoText(String gameType) {
        switch (gameType) {
            case "dragonfortune":
                return getString(R.string.info_dragon_fortune);
            case "fruitparadise":
                return getString(R.string.info_fruit_paradise);
            case "plinkoprince":
                return getString(R.string.info_plinko_prince);
            case "luckyspin":
                return getString(R.string.info_lucky_spin);
            default:
                return "";
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // HELPER: button style (active / inactive)
    // ────────────────────────────────────────────────────────────────────────

    private void applyButtonStyle(Button btn, boolean active) {
        if (active) {
            btn.setBackgroundResource(R.drawable.bg_filter_btn_active);
            btn.setTextColor(0xFF1A1A2E);
        } else {
            btn.setBackgroundResource(R.drawable.bg_filter_btn_inactive);
            btn.setTextColor(0xFF4FC3F7);
        }
    }
}