package com.example.ergasiaui;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

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

import model.enums.PlayerActions;
import model.enums.Sender;
import serializables.Message;
import serializables.WorkerGame;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HomeFragment#} factory method to
 * create an instance of this fragment.
 */
public class HomeFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private HomeViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        Button btnFilter = view.findViewById(R.id.btnSearchFilters);
        btnFilter.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.action_home_to_filter)
        );

        viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        if (viewModel.hasGames()) {
            // if they exist from earlier, show them
            buildGameCards(view, viewModel.getGames());
        } else {
            // first time -> call master
            loadGames(view);
        }

        return view;
    }

    private void loadGames(View view) {
        executor.execute(() -> {
            try {
                InputStream is = getResources().openRawResource(R.raw.config);
                Properties prop = new Properties();
                prop.load(is);

                String masterIp = prop.getProperty("master.ip");
                int masterPort = Integer.parseInt(prop.getProperty("master.port"));

                Message message = createMessageForShowAvailableGames();

                Socket socket = new Socket(masterIp, masterPort);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                out.writeObject(message);
                out.flush();
                List<WorkerGame> games = (List<WorkerGame>) in.readObject();
                viewModel.setGames(games);

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
                return;
            }

            requireActivity().runOnUiThread(() -> buildGameCards(view, viewModel.getGames()));
        });
    }

    private void buildGameCards(View rootView, List<WorkerGame> games) {
        LinearLayout container = rootView.findViewById(R.id.llGameCards);
        container.removeAllViews();

        for (WorkerGame game : games) {
            View card = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_game_card, container, false);

            TextView tvTitle = card.findViewById(R.id.tvGameTitle);
            TextView tvCategory = card.findViewById(R.id.tvBetCategory);
            TextView tvMinBet = card.findViewById(R.id.tvMinBet);
            TextView tvVotes = card.findViewById(R.id.tvVotes);
            ImageView ivBackground = card.findViewById(R.id.ivGameBackground);

            tvTitle.setText(game.getGame().getName().toUpperCase());
            tvCategory.setText(game.getBetCategory().toString());
            tvMinBet.setText(String.format("Min bet: €%.2f", game.getGame().getMinBet()));
            tvVotes.setText(game.getRatingCount() + " votes");

            byte[] imageBytes = game.getGameLogo();
            if (imageBytes != null && imageBytes.length > 0) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                ivBackground.setImageBitmap(bitmap);
            }

            container.addView(card);

            card.setOnClickListener(v -> {
                String infoText = getInfoText(game.getGame().getName());
                Bundle args = new Bundle();
                args.putSerializable("game", game);
                args.putString("gameInfoText", infoText);
                Navigation.findNavController(v)
                        .navigate(R.id.action_home_to_game, args);
            });
        }

        buildFavoriteCards(rootView, games);
    }

    private void buildFavoriteCards(View rootView, List<WorkerGame> games) {
        LinearLayout favContainer = rootView.findViewById(R.id.llGameCardsFavorites);
        favContainer.removeAllViews();

        String[] favorites = {"plinkoprince", "dragonfortune"};

        for (WorkerGame game : games) {
            String name = game.getGame().getName().toLowerCase();
            boolean isFavorite = false;
            for (String fav : favorites) {
                if (name.equals(fav)) { isFavorite = true; break; }
            }
            if (!isFavorite) continue;

            View card = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_game_card, favContainer, false);

            TextView tvTitle = card.findViewById(R.id.tvGameTitle);
            TextView tvCategory = card.findViewById(R.id.tvBetCategory);
            TextView tvMinBet = card.findViewById(R.id.tvMinBet);
            TextView tvVotes = card.findViewById(R.id.tvVotes);
            ImageView ivBackground = card.findViewById(R.id.ivGameBackground);

            tvTitle.setText(game.getGame().getName().toUpperCase());
            tvCategory.setText(game.getBetCategory().toString());
            tvMinBet.setText(String.format("Min bet: €%.2f", game.getGame().getMinBet()));
            tvVotes.setText(game.getRatingCount() + " votes");

            byte[] imageBytes = game.getGameLogo();
            if (imageBytes != null && imageBytes.length > 0) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                ivBackground.setImageBitmap(bitmap);
            }

            card.setOnClickListener(v -> {
                String infoText = getInfoText(game.getGame().getName());
                Bundle args = new Bundle();
                args.putSerializable("game", game);
                args.putString("gameInfoText", infoText);
                Navigation.findNavController(v)
                        .navigate(R.id.action_home_to_game, args);
            });

            favContainer.addView(card);
        }
    }

    private String getInfoText(String gameName) {
        switch (gameName.toLowerCase()) {
            case "dragonfortune":   return getString(R.string.info_dragon_fortune);
            case "fruitparadise":   return getString(R.string.info_fruit_paradise);
            case "plinkoprince":    return getString(R.string.info_plinko_prince);
            case "luckyspin":       return getString(R.string.info_lucky_spin);
            case "piratetreasure":  return getString(R.string.info_pirate_treasure);
            default:                return "";
        }
    }

    private Message createMessageForShowAvailableGames() {
        String requestId = UUID.randomUUID().toString();
        return new Message(Sender.PLAYER,
                PlayerActions.SHOW_AVAILABLE_GAMES, null, requestId);
    }
}