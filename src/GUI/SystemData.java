package GUI;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException; // Added for precise network failure detection
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class SystemData {

    // ==========================================
    // CLASS 1: LOCATION
    // ==========================================
    // Represents a physical geographical or structural location (e.g., a specific building or block).
    public static class Location {
        private String name;

        public Location(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    // ==========================================
    // CLASS 2: ROOM
    // ==========================================
    // Represents a specific room bound to a parent Location entity.
    public static class Room {
        private String name;
        private Location location;

        public Room(String name, Location location) {
            this.name = name;
            this.location = location;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Location getLocation() {
            return location;
        }
    }

    // ==========================================
    // CLASS 3: SENSOR (Abstract Base Class)
    // ==========================================
    // Defines the foundational attributes and blueprint for all IoT sensor types within the system.
    public static abstract class Sensor {
        protected String id;
        protected String model;
        protected Room room;
        protected String status;

        public Sensor(String id, String model, Room room) {
            this.id = id;
            this.model = model;
            this.room = room;
            this.status = "Offline";
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Room getRoom() {
            return room;
        }

        public String getStatus() {
            return status;
        }

        public abstract String getReading();
    }

    // ==========================================
    // CLASS 4: TEMPERATURE SENSOR
    // ==========================================
    // Concrete implementation of a Temperature Sensor. Includes signal debouncing (5-second stability delay)
    // to prevent alert spam from brief, anomalous reading spikes.
    public static class TemperatureSensor extends Sensor {

        private SensorReading sensorReading;

        // State variables for stability validation
        private String confirmedStatus = "Offline"; 
        private String pendingStatus = null; 

        // Debounce timer configurations
        private long stabilityStartTime = 0; 
        private final long STABILITY_DELAY = 5000; // 5-second validation threshold

        // Routine update timer configurations
        private long lastHourlyTime = 0;
        private final long HOURLY_DELAY = 60 * 60 * 1000; // 1-hour interval

        public TemperatureSensor(String locName, String roomName, String id, String model) {
            super(id, model, new Room(roomName, new Location(locName)));
            this.sensorReading = new SensorReading();
            this.status = "Offline";
            this.confirmedStatus = "Offline";
        }

        public void updateData() {
            // Generate simulated reading updates
            sensorReading.generateNewValue();
            double currentVal = sensorReading.getValue();

            // Evaluate the current raw threshold state
            String rawStatus;
            if (currentVal < 18.0) {
                rawStatus = "Low";
            } else if (currentVal < 28.0) {
                rawStatus = "Normal";
            } else if (currentVal < 38.0) {
                rawStatus = "High";
            } else {
                rawStatus = "Critical";
            }

            // --- Signal Debouncing & Stability Evaluation ---

            // Reset pending state if the raw reading aligns with the currently confirmed system state
            if (rawStatus.equals(confirmedStatus)) {
                pendingStatus = null; 
                stabilityStartTime = 0;
            }
            // Process potential state transitions
            else {
                // Initialize the stability timer upon detecting a new, unconfirmed state
                if (pendingStatus == null || !pendingStatus.equals(rawStatus)) {
                    pendingStatus = rawStatus;
                    stabilityStartTime = System.currentTimeMillis(); 
                }

                // Calculate duration of the current unconfirmed state
                long timeElapsed = System.currentTimeMillis() - stabilityStartTime;

                // Validate the state transition if it persists beyond the stability threshold
                if (timeElapsed >= STABILITY_DELAY) {

                    // Trigger recovery events if returning to normal parameters from a high/critical state
                    if (confirmedStatus.equals("High") || confirmedStatus.equals("Critical")) {
                        if (rawStatus.equals("Normal")) {
                            AlertSystem.sendRecoveryMessage(this.id, currentVal);
                        }
                    }

                    // Trigger emergency events if escalating to a high/critical state
                    if (rawStatus.equals("High") || rawStatus.equals("Critical")) {
                        AlertSystem.sendEmergencyAlert(this.id, rawStatus, currentVal);
                    }

                    // Commit the validated state to the system
                    confirmedStatus = rawStatus;
                    this.status = confirmedStatus; 

                    // Flush validation logic
                    pendingStatus = null;
                    stabilityStartTime = 0;
                }
            }

            // --- Routine Hourly Reporting ---
            long currentTime = System.currentTimeMillis();
            if (lastHourlyTime == 0 || (currentTime - lastHourlyTime) >= HOURLY_DELAY) {
                String msg = "🕒 HOURLY UPDATE: " + this.id + " is " + confirmedStatus + " ("
                        + String.format("%.2f", currentVal) + " C)";
                AlertSystem.sendRoutineUpdate(msg);
                lastHourlyTime = currentTime;
            }
        }

        @Override
        public String getReading() {
            if (confirmedStatus.equals("Offline"))
                return "--";

            return String.format("%.2f °C", sensorReading.getValue());
        }
    }

    // ==========================================
    // CLASS 5: SENSOR READING GENERATOR
    // ==========================================
    // Simulates physical environment temperature fluctuations. Employs fractional increments 
    // to mirror real-world thermodynamic transitions rather than erratic randomization.
    public static class SensorReading {
        private double value;

        public SensorReading() {
            // Initialize at a standard baseline (15.0 - 35.0 C)
            this.value = 15.0 + (Math.random() * 20.0);
        }

        public void generateNewValue() {
            // Calculate a localized variance to simulate continuous environmental shifts
            double change = (Math.random() * 0.1) + (Math.random() * 4.0) - 2.0; 

            this.value = this.value + change;

            // Enforce hard hardware simulation limits
            if (this.value < 10.0)
                this.value = 10.0;
            if (this.value > 50.0)
                this.value = 50.0;
        }

        public double getValue() {
            return value;
        }
    }

    // ==========================================
    // CLASS 6: ALERT & NOTIFICATION SYSTEM
    // ==========================================
    // Manages external API integrations (Telegram) and local GUI logging for system events.
    public static class AlertSystem {

        private static String telegramToken = ""; // put your telegram token here
        private static String telegramChatID = ""; // put your telegram group id here
        private static JTextArea logMonitor;

        public static void setConfiguration(String token, String chatID, JTextArea monitor) {
            telegramToken = token;
            telegramChatID = chatID;
            logMonitor = monitor;
        }

        // Processes escalation alerts for threshold breaches
        public static void sendEmergencyAlert(String sensorID, String status, double reading) {
            String msg = "🚨 URGENT: " + sensorID + " spiked to " + status + " (Temp: " + String.format("%.2f", reading)
                    + " C)";
            sendToNetwork(msg);
        }

        // Processes de-escalation alerts upon returning to safe operating parameters
        public static void sendRecoveryMessage(String sensorID, double reading) {
            String msg = "✅ RECOVERY: " + sensorID + " returned to Normal (Temp: " + String.format("%.2f", reading)
                    + " C)";
            sendToNetwork(msg);
        }

        // Processes scheduled system health checks
        public static void sendRoutineUpdate(String message) {
            sendToNetwork(message);
        }

        // Dispatches formatted timestamped logs to the local GUI Serial Monitor component safely
        private static void log(String msg) {
            if (logMonitor != null) {
                SwingUtilities.invokeLater(() -> {
                    String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    logMonitor.append("[" + time + "] " + msg + "\n");
                    logMonitor.setCaretPosition(logMonitor.getDocument().getLength());
                });
            } else {
                System.out.println(msg);
            }
        }

        // Asynchronous network payload dispatcher with comprehensive HTTP response validation
        private static void sendToNetwork(String message) {
            new Thread(() -> {
                try {
                    if (telegramToken.isEmpty() || telegramChatID.isEmpty()) {
                        log("[ERROR] API Credentials missing. Cannot dispatch payload.");
                        return;
                    }

                    String encodedMsg = URLEncoder.encode(message, "UTF-8");
                    String urlString = "https://api.telegram.org/bot" + telegramToken + "/sendMessage?chat_id="
                            + telegramChatID + "&text=" + encodedMsg;

                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000); // 5-second timeout threshold

                    int responseCode = conn.getResponseCode();

                    // Evaluate Telegram API response codes
                    if (responseCode == 200) {
                        log("[SYSTEM] Telegram Payload Dispatched Successfully.");
                    } else if (responseCode == 401) {
                        log("[ERROR] HTTP 401 Unauthorized: Invalid Bot Token.");
                    } else if (responseCode == 400 || responseCode == 404) {
                        log("[ERROR] HTTP 400/404: Invalid Telegram Chat ID.");
                    } else {
                        log("[ERROR] Unhandled API Exception. Code: " + responseCode);
                    }

                } catch (UnknownHostException e) {
                    // Triggers upon total network failure (No DNS resolution/Internet)
                    log("[CRITICAL] Network Unreachable! Verify local internet connectivity.");
                } catch (Exception e) {
                    // Catch-all for secondary execution failures
                    log("[ERROR] Thread Execution Failure: " + e.getMessage());
                }
            }).start();
        }
    }
}