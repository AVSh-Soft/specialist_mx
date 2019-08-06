package javafx.stage;

public final class WindowHelper {

    private WindowHelper() {
        //
    }

    public static void setEnabled(final Stage stage, final boolean enabled) {
        if (stage != null) {
            stage.getPeer().setEnabled(enabled);
        }
    }
}
