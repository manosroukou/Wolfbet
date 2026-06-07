package com.example.ergasiaui;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import android.graphics.Color;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;

import model.enums.PlayGameResultType;
import model.enums.PlayerActions;
import model.enums.Sender;
import serializables.Message;
import serializables.PlayGameRequest;
import serializables.PlayGameResult;
import serializables.RateGameRequest;
import serializables.WorkerGame;

import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import serializables.WorkerGame;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link GameFragment#} factory method to
 * create an instance of this fragment.
 */
public class GameFragment extends Fragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WorkerGame workerGame;
    private float currentBet;
    private int selectedRating = 0;
    private View gameView = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_game, container, false);

        // Get data from Bundle
        workerGame = (WorkerGame) getArguments().getSerializable("game");

        // Bind views
        FrameLayout gameContainer = view.findViewById(R.id.flGameContainer);
        TextView tvName = view.findViewById(R.id.tvGameName);
        TextView tvCategory = view.findViewById(R.id.tvGameCategory);
        TextView tvMinBet = view.findViewById(R.id.tvGameMinBet);
        TextView tvVotes = view.findViewById(R.id.tvGameVotes);
        EditText etBet = view.findViewById(R.id.etBetAmount);
        Button btnHalf = view.findViewById(R.id.btnHalf);
        Button btnDouble = view.findViewById(R.id.btnDouble);
        Button btnFive = view.findViewById(R.id.btnFive);
        Button btnAllIn = view.findViewById(R.id.btnAllIn);
        Button btnPlay = view.findViewById(R.id.btnPlay);

        // Full info
        float minBet = workerGame.getGame().getMinBet();
        tvName.setText(workerGame.getGame().getName().toUpperCase());
        tvCategory.setText(workerGame.getBetCategory().toString());
        tvMinBet.setText(String.format("Min bet: €%.2f", minBet));
        tvVotes.setText(workerGame.getRatingCount() + " votes");

        // Placeholder = minBet
        etBet.setHint(String.format("€%.2f", minBet));
        currentBet = minBet;

        // Multiplier buttons
        btnHalf.setOnClickListener(v -> {
            currentBet = getBetFromField(etBet, minBet);
            currentBet /= 2;
            if (currentBet < minBet) currentBet = minBet;
            etBet.setText(String.format("%.2f", currentBet));
        });

        btnDouble.setOnClickListener(v -> {
            currentBet = getBetFromField(etBet, minBet);
            currentBet *= 2;
            etBet.setText(String.format("%.2f", currentBet));
        });

        btnFive.setOnClickListener(v -> {
            currentBet = getBetFromField(etBet, minBet);
            currentBet *= 5;
            etBet.setText(String.format("%.2f", currentBet));
        });

        btnAllIn.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) requireActivity();
            currentBet = activity.getBalance();
            etBet.setText(String.format("%.2f", currentBet));
        });

        // Load appropriate game view
        loadGameView(gameContainer, workerGame.getGame().getName());

        // Play button
        btnPlay.setOnClickListener(v -> {
            currentBet = getBetFromField(etBet, minBet);
            MainActivity activity = (MainActivity) requireActivity();
            float balance = activity.getBalance();
            if (currentBet > balance) {
                Toast.makeText(requireContext(), "Insufficient balance!", Toast.LENGTH_SHORT).show();
                return;
            }

            activity.setBalance(balance - currentBet);
            playGame();

        });

        setupRating(view);
        return view;
    }
    private void playGame() {
        executor.execute(() -> {
            try {
                InputStream is = getResources().openRawResource(R.raw.config);
                Properties prop = new Properties();
                prop.load(is);

                String masterIp = prop.getProperty("master.ip");
                int masterPort = Integer.parseInt(prop.getProperty("master.port"));

                Message message = createMessageForPlayGame();

                Socket socket = new Socket(masterIp, masterPort);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                out.writeObject(message);
                out.flush();
                Object response = in.readObject();

                if (response instanceof String) {
                    requireActivity().runOnUiThread(() -> {
                        MainActivity act = (MainActivity) requireActivity();
                        act.setBalance(act.getBalance() + currentBet);
                        showNotification((String) response, Color.parseColor("#F44336"));;
                    });
                } else {
                    PlayGameResult playGameResult = (PlayGameResult) response;
                    requireActivity().runOnUiThread(() -> {
                        AnimatedGameView agv = (AnimatedGameView) gameView;
                        float multiplier = playGameResult.getWinAmount() / currentBet;
                        agv.playAnimation(playGameResult.getResultType(), multiplier);
                        agv.setOnAnimationFinished(() -> {
                            applyResult(playGameResult.getResultType(),
                                    playGameResult.getWinAmount(), currentBet);
                        });
                    });
                }

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }


    // ==============
    // === Rating ===
    // ==============
    private void setupRating(View view) {
        ImageView[] stars = {
                view.findViewById(R.id.star1),
                view.findViewById(R.id.star2),
                view.findViewById(R.id.star3),
                view.findViewById(R.id.star4),
                view.findViewById(R.id.star5)
        };

        for (int i = 0; i < stars.length; i++) {
            final int rating = i + 1;
            stars[i].setOnClickListener(v -> {
                selectedRating = rating;
                for (int j = 0; j < stars.length; j++) {
                    stars[j].setImageResource(
                            j < rating
                                    ? android.R.drawable.btn_star_big_on
                                    : android.R.drawable.btn_star_big_off
                    );
                }
            });
        }

        view.findViewById(R.id.btnSubmitRating).setOnClickListener(v -> {
            if (selectedRating == 0) {
                Toast.makeText(requireContext(), "Select a rating first", Toast.LENGTH_SHORT).show();
                return;
            }
            // send rating to master
            sendRating(selectedRating);
        });
    }
    private void sendRating(int selectedRating) {
        executor.execute(() -> {
            try {
                InputStream is = getResources().openRawResource(R.raw.config);
                Properties prop = new Properties();
                prop.load(is);

                String masterIp = prop.getProperty("master.ip");
                int masterPort = Integer.parseInt(prop.getProperty("master.port"));

                Message message = createMessageForRateGame(selectedRating);

                Socket socket = new Socket(masterIp, masterPort);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                out.writeObject(message);
                out.flush();
                String response = (String) in.readObject();

                requireActivity().runOnUiThread(() -> showNotification((String) response, Color.parseColor("#F44336")));

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }


    // ================
    // === Messages ===
    // ================
    private Message createMessageForRateGame(int selectedRating) {
        String gameName = workerGame.getGame().getName();
        RateGameRequest payload = new RateGameRequest(gameName, selectedRating);
        return new Message<>(Sender.PLAYER, PlayerActions.RATE_GAME, payload, null);
    }
    private Message createMessageForPlayGame() {
        String gameName = workerGame.getGame().getName();
        String playerId = ((MainActivity) requireActivity()).getPlayerId();
        PlayGameRequest playGameRequest = new PlayGameRequest(playerId, gameName, (float) currentBet);
        return new Message<>(Sender.PLAYER, PlayerActions.PLAY_GAME, playGameRequest, null);
    }


    // ===============
    // === Helpers ===
    // ===============
    private void loadGameView(FrameLayout container, String gameName) {
        switch (gameName.toLowerCase()) {
            case "fruitparadise":
                gameView = new FruitParadiseView(requireContext());
                break;
            case "luckyspin":
                gameView = new LuckySpinView(requireContext());
                break;
            case "piratetreasure":
                gameView = new PirateTreasureView(requireContext());
                break;
            case "dragonfortune":
                gameView = new DragonFortuneView(requireContext());
                break;
            case "plinkoprince":
                gameView = new PlinkoView(requireContext());
                break;
            default:
                // Fallback
                TextView tv = new TextView(requireContext());
                tv.setText(gameName);
                tv.setTextColor(0xFFFFFFFF);
                tv.setGravity(android.view.Gravity.CENTER);
                gameView = tv;
                break;
        }
        container.addView(gameView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }
    private float getBetFromField(EditText et, float fallback) {
        String text = et.getText().toString().trim();
        if (text.isEmpty()) return fallback;
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
    private void showNotification(String message, int color) {
        TextView tv = getView().findViewById(R.id.tvNotification);
        tv.setText(message);
        tv.setBackgroundColor(color);
        tv.setVisibility(View.VISIBLE);

        tv.setTranslationX(-tv.getWidth() - 100);
        tv.animate()
                .translationX(0)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        tv.postDelayed(() -> {
            tv.animate()
                    .translationX(-tv.getWidth() - 100)
                    .setDuration(300)
                    .withEndAction(() -> tv.setVisibility(View.GONE))
                    .start();
        }, 3000);
    }
    private void applyResult(PlayGameResultType resultType, float winAmount, float betAmount) {
        MainActivity activity = (MainActivity) requireActivity();
        float balance = activity.getBalance();

        String message;
        int color;
        switch (resultType) {
            case JACKPOT:
                balance += winAmount;
                message = "JACKPOT! You won €" + String.format("%.2f", winAmount - betAmount);
                color = Color.parseColor("#FFD700");
                break;
            case WIN:
                balance += winAmount;
                message = "WIN! You won €" + String.format("%.2f", winAmount - betAmount);
                color = Color.parseColor("#4CAF50");
                break;
            case BREAK_EVEN:
                balance += winAmount;
                message = "BREAK EVEN! You got your bet back.";
                color = Color.parseColor("#2196F3");
                break;
            case PARTIAL_LOSS:
                balance += winAmount;
                message = "PARTIAL LOSS. You lost €" + String.format("%.2f", Math.abs(winAmount - betAmount));
                color = Color.parseColor("#FF9800");
                break;
            case LOSS:
            default:
                message = "LOSS. Better luck next time.";
                color = Color.parseColor("#F44336");
                break;
        }

        float finalBalance = balance;
        requireActivity().runOnUiThread(() -> {
            activity.setBalance(finalBalance);
            showNotification(message, color);
        });
    }
}