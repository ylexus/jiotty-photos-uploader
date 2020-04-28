package net.yudichev.googlephotosupload.ui;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.effect.SepiaTone;
import javafx.util.Duration;

import static javafx.animation.Animation.INDEFINITE;

final class SepiaToneEffectAnimatedNode implements AnimatedNode {
    private static final Duration BLINK_PERIOD = Duration.seconds(0.5);
    private final Node node;
    private final Timeline timeline;

    SepiaToneEffectAnimatedNode(Node node) {
        this.node = node;
        var effect = new SepiaTone();
        node.setEffect(effect);
        timeline = new Timeline();
        timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO, new KeyValue(effect.levelProperty(), 0)));
        timeline.getKeyFrames().add(new KeyFrame(BLINK_PERIOD, new KeyValue(effect.levelProperty(), 1.0)));
        timeline.setAutoReverse(true);
        timeline.setCycleCount(INDEFINITE);
    }

    @Override
    public void show() {
        node.setVisible(true);
        timeline.play();
    }

    @Override
    public void hide() {
        node.setVisible(false);
        timeline.stop();
    }
}
