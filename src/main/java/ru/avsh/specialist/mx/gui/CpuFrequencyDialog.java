package ru.avsh.specialist.mx.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import ru.avsh.specialist.mx.gui.utils.AlertUtil;
import ru.avsh.specialist.mx.units.ClockSpeedGenerator;

import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public class CpuFrequencyDialog extends Dialog<Integer> {
    private static final int STEP_HZ = 100_000;  // Шаг 100 кГц (0.1 МГц)

    // Разрешает: пустую строку (при стирании), целое до 3 цифр, и максимум 1 знак после точки/запятой
    private static final Pattern M_HZ_PATTERN = Pattern.compile("\\d{0,3}([.,]\\d?)?");

    private final int     minHz;
    private final int     maxHz;
    private       int currentHz;

    private final    Slider     slider;
    private final TextField   mhzInput;
    private final     Label    hzLabel;
    private         boolean isUpdating = false;

    public CpuFrequencyDialog(int minHz, int maxHz, int initialHz) {
        this.minHz     = minHz;
        this.maxHz     = maxHz;
        this.currentHz = snapToStep(initialHz);

             setTitle("Установка тактовой частота CPU");
        setHeaderText("Настройка тактовой частоты процессора (шаг 100 кГц)");
        getDialogPane().getButtonTypes().addAll(AlertUtil.Option.OK_CANCEL_OPTION.getButtonTypes());

        // 1. Слайдер в целых шагах по 100 кГц
        final int minSteps     =     minHz / STEP_HZ;
        final int maxSteps     =     maxHz / STEP_HZ;
        final int initialSteps = currentHz / STEP_HZ;

        slider = new Slider(minSteps, maxSteps, initialSteps);
        slider.setPrefWidth     (340 );
        slider.setShowTickMarks (true);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit (Math.max(1, (maxSteps - minSteps) / 5.0));
        slider.setMinorTickCount(4);
        slider.setBlockIncrement(1);

        slider.setLabelFormatter(new StringConverter<Double>() {
            @Override
            public String toString(final Double stepVal) {
                return String.format(Locale.US, "%.1f", (stepVal * STEP_HZ) / 1_000_000.0);
            }

            @Override
            public Double fromString(final String s) {
                return Double.parseDouble(s) * 10.0;
            }
        });

        // 2. Текстовое поле с TextFormatter для фильтрации ввода
        mhzInput = new TextField (  );
        mhzInput.setPrefWidth    (75);
        mhzInput.setAlignment    (Pos.CENTER_RIGHT);
        mhzInput.setTextFormatter(createFrequencyFormatter());

        // Кнопка сброса на частоту по умолчанию
        final Button defaultBtn = new Button (String.format(
                Locale.US,"%.1f МГц", ClockSpeedGenerator.CLOCK_SPEED / 1_000_000.0));
        defaultBtn.setTooltip(    new Tooltip(String.format(
                "Установить частоту по умолчанию (%,d Гц)",    ClockSpeedGenerator.CLOCK_SPEED)));
        defaultBtn.setOnAction(e -> applyFrequency(ClockSpeedGenerator.CLOCK_SPEED)) ;

        // Метка с точным значением в Гц
        hzLabel = new Label();
        hzLabel.setStyle("-fx-text-fill: #555555; -fx-font-size: 12px;");

        // 3. Связываем слайдер и текстовое поле
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!isUpdating) {
                applyFrequency((int) Math.round(newVal.doubleValue()) * STEP_HZ);
            }
        });

        mhzInput.setOnAction(e -> parseTextField());
        mhzInput.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (Boolean.FALSE.equals(isNowFocused)) {
                parseTextField();
            }
        });

        applyFrequency(this.currentHz);

        // 4. Компоновка
        final HBox inputRow = new HBox(8,
                new Label("Частота:"),
                mhzInput,
                new Label("МГц"),
                defaultBtn
        );
        inputRow.setAlignment(Pos.CENTER);

        final VBox content = new VBox(12, inputRow, hzLabel, slider);
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
        final UnaryOperator<TextFormatter.Change> filter = change -> {
            // Автоматически заменяем запятую на точку при вводе
            if (change.getText().contains(",")) {
                change.setText(change.getText().replace(',', '.'));
            }

            final String newText = change.getControlNewText();
            if (M_HZ_PATTERN.matcher(newText).matches()) {
                return change;
            }
            return null; // Отклоняем изменение
        };
        return new TextFormatter<>(filter);
    }

    private void parseTextField() {
        final String raw = mhzInput.getText().trim();
        // Если пользователь стер все символы или оставил только точку — возвращаем прошлое значение
        if (raw.isEmpty() || raw.equals(".")) {
            applyFrequency(currentHz);
            return;
        }

        try {
            final double mhz = Double.parseDouble(raw);
            final    int  hz =   (int) Math.round(mhz * 1_000_000L);
            applyFrequency(hz);
        } catch (NumberFormatException e) {
            applyFrequency(currentHz);
        }
    }

    private void applyFrequency(final int targetHz) {
        isUpdating = true;
        try {
            currentHz = snapToStep(targetHz);
               slider.setValue(currentHz / (double) STEP_HZ);
             mhzInput.setText (String.format(Locale.US, "%.1f", currentHz / 1_000_000.0));
              hzLabel.setText (String.format("Точное значение: %,d Гц", currentHz));
        } finally {
            isUpdating = false;
        }
    }

    private int snapToStep(final int hz) {
        return (int) Math.round((double) Math.max(minHz, Math.min(maxHz, hz)) / STEP_HZ) * STEP_HZ;
    }
}

