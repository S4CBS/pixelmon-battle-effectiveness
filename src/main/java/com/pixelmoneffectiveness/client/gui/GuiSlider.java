package com.pixelmoneffectiveness.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

public class GuiSlider extends AbstractSliderButton {
    private final double min;
    private final double max;
    private final double step;
    private final String prefix;
    private final String suffix;
    private final Consumer<Double> onValueChange;

    public GuiSlider(int x, int y, int width, int height, String prefix, String suffix, double min, double max, double current, double step, Consumer<Double> onValueChange) {
        super(x, y, width, height, Component.empty(), (Math.max(min, Math.min(max, current)) - min) / (max - min));
        this.min = min;
        this.max = max;
        this.step = step;
        this.prefix = prefix;
        this.suffix = suffix;
        this.onValueChange = onValueChange;
        updateMessage();
    }

    public double getActualValue() {
        double val = min + this.value * (max - min);
        if (step > 0) {
            val = Math.round(val / step) * step;
        }
        return Math.max(min, Math.min(max, val));
    }

    @Override
    protected void updateMessage() {
        double val = getActualValue();
        String str = (step >= 1.0) ? String.format(Locale.ROOT, "%.0f", val) : String.format(Locale.ROOT, "%.2f", val);
        this.setMessage(Component.literal(prefix + ": " + str + suffix));
    }

    @Override
    protected void applyValue() {
        if (onValueChange != null) {
            onValueChange.accept(getActualValue());
        }
    }
}
