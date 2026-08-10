package GUI;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {

    public static void main(String[] args) {
        // 1. Set the Look and Feel to match the operating system
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Launch the Dashboard GUI securely
        SwingUtilities.invokeLater(() -> {
            DashboardGUI dashboard = new DashboardGUI();

            // Center the window on screen
            dashboard.setLocationRelativeTo(null);

            dashboard.setVisible(true);
        });
    }
}