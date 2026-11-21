package ru.avsh.specialist.mx.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public class CpuFrequencyDialog extends Dialog<Integer> {
    private static final int STEP_HZ    =   100_000;  // Шаг 100 кГц (0.1 МГц)
    private static final int DEFAULT_HZ = 2_000_000;  // 2.0 МГц по умолчанию

    // Разрешает: пустую строку (при стирании), целое до 3 цифр, и максимум 1 знак после точки/запятой
    private static final Pattern MHz_PATTERN = Pattern.compile("\\d{0,3}([.,]\\d?)?");

    private final int minHz;
    private final int maxHz;
    private       int currentHz;

    private final    Slider   slider;
    private final TextField mhzInput;
    private final     Label  hzLabel;
    private boolean isUpdating = false;

    public CpuFrequencyDialog(int minHz, int maxHz, int initialHz) {
        this.minHz = minHz;
        this.maxHz = maxHz;
        this.currentHz = snapToStep(initialHz);

        setTitle("Тактовая частота CPU");
        setHeaderText("Настройка частоты процессора (шаг 100 кГц)");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // 1. Слайдер в целых шагах по 100 кГц
        int minSteps = minHz / STEP_HZ;
        int maxSteps = maxHz / STEP_HZ;
        int initialSteps = currentHz / STEP_HZ;

        slider = new Slider((double) minSteps, (double) maxSteps, (double) initialSteps);
        slider.setPrefWidth(340);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit(Math.max(1, (maxSteps - minSteps) / 5.0));
        slider.setMinorTickCount(4);
        slider.setBlockIncrement(1);

        slider.setLabelFormatter(new StringConverter<Double>() {
            @Override
            public String toString(Double stepVal) {
                return String.format(Locale.US, "%.1f", (stepVal * STEP_HZ) / 1_000_000.0);
            }

            @Override
            public Double fromString(String s) {
                return Double.parseDouble(s) * 10.0;
            }
        });

        // 2. Текстовое поле с TextFormatter для фильтрации ввода
        mhzInput = new TextField();
        mhzInput.setPrefWidth(75);
        mhzInput.setAlignment(Pos.CENTER_RIGHT);
        mhzInput.setTextFormatter(createFrequencyFormatter());

        // Кнопка сброса на частоту по умолчанию (2 МГц)
        Button defaultBtn = new Button("2 МГц");
        defaultBtn.setTooltip(new Tooltip("Установить частоту по умолчанию (2 000 000 Гц)"));
        defaultBtn.setOnAction(e -> applyFrequency(DEFAULT_HZ));

        // Метка с точным значением в Гц
        hzLabel = new Label();
        hzLabel.setStyle("-fx-text-fill: #555555; -fx-font-size: 12px;");

        // 3. Связываем слайдер и текстовое поле
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdating) return;
            int steps = (int) Math.round(newVal.doubleValue());
            applyFrequency(steps * STEP_HZ);
        });

        mhzInput.setOnAction(e -> parseTextField());
        mhzInput.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) parseTextField();
        });

        applyFrequency(this.currentHz);

        // 4. Компоновка
        HBox inputRow = new HBox(8,
                new Label("Частота:"),
                mhzInput,
                new Label("МГц"),
                defaultBtn
        );
        inputRow.setAlignment(Pos.CENTER);

        VBox content = new VBox(12, inputRow, hzLabel, slider);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(18, 20, 10, 20));
        getDialogPane().setContent(content);

        // 5. Возвращаем точную частоту в Гц при нажатии OK
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                parseTextField();
                return currentHz;
            }
            return null;
        });
    }

    /**
     * Фильтр ввода: пропускает только числа формата "2", "2.4", "16.0", ".5"
     * и автоматически меняет запятую на точку.
     */
    private TextFormatter<String> createFrequencyFormatter() {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            // Автоматически заменяем запятую на точку при вводе
            if (change.getText().contains(",")) {
                change.setText(change.getText().replace(',', '.'));
            }

            String newText = change.getControlNewText();
            if (MHz_PATTERN.matcher(newText).matches()) {
                return change;
            }
            return null; // Отклоняем изменение
        };
        return new TextFormatter<>(filter);
    }

    private void parseTextField() {
        String raw = mhzInput.getText().trim();
        // Если пользователь стер все символы или оставил только точку — возвращаем прошлое значение
        if (raw.isEmpty() || raw.equals(".")) {
            applyFrequency(currentHz);
            return;
        }

        try {
            double mhz = Double.parseDouble(raw);
            int hz = (int) Math.round(mhz * 1_000_000L);
            applyFrequency(hz);
        } catch (NumberFormatException e) {
            applyFrequency(currentHz);
        }
    }

    private void applyFrequency(int targetHz) {
        isUpdating = true;
        try {
            currentHz = snapToStep(targetHz);
            slider.setValue(currentHz / (double) STEP_HZ);
            mhzInput.setText(String.format(Locale.US, "%.1f", currentHz / 1_000_000.0));
            hzLabel.setText(String.format("Точное значение: %,d Гц", currentHz));
        } finally {
            isUpdating = false;
        }
    }

    private int snapToStep(int hz) {
        final int clamped = Math.max(minHz, Math.min(maxHz, hz));
        return (int) Math.round((double) clamped / STEP_HZ) * STEP_HZ;
    }
}

