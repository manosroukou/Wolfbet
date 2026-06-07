package com.example.ergasiaui;

import model.enums.PlayGameResultType;

public interface AnimatedGameView {
    void playAnimation(PlayGameResultType resultType);
    void playAnimation(PlayGameResultType resultType, float multiplier);
    void setOnAnimationFinished(Runnable callback);
}
