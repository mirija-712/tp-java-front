package com.example.auth_front;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HelloController {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\"email\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CREATED_AT_PATTERN = Pattern.compile("\"createdAt\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String currentToken;

    @FXML
    private TextField backendUrlField;

    @FXML
    private TextField registerEmailField;

    @FXML
    private PasswordField registerPasswordField;

    @FXML
    private PasswordField registerConfirmField;

    @FXML
    private Label registerStrengthLabel;

    @FXML
    private Label registerStatusLabel;

    @FXML
    private TextField loginEmailField;

    @FXML
    private PasswordField loginPasswordField;

    @FXML
    private Label loginStatusLabel;

    @FXML
    private Label tokenLabel;

    @FXML
    private Label profileEmailLabel;

    @FXML
    private Label profileCreatedAtLabel;

    @FXML
    private Label profileStatusLabel;

    @FXML
    public void initialize() {
        backendUrlField.setText("http://localhost:8080");
        registerStrengthLabel.setText("Force: rouge (non conforme)");
        registerStrengthLabel.setTextFill(Color.FIREBRICK);

        registerPasswordField.textProperty().addListener((obs, oldVal, newVal) -> updateStrength(newVal));
    }

    @FXML
    protected void onRegisterClick() {
        String email = registerEmailField.getText();
        String password = registerPasswordField.getText();
        String confirm = registerConfirmField.getText();

        if (!password.equals(confirm)) {
            registerStatusLabel.setTextFill(Color.FIREBRICK);
            registerStatusLabel.setText("Erreur: la confirmation du mot de passe ne correspond pas.");
            return;
        }

        String payload = "{\"email\":\"" + escapeJson(email) + "\",\"password\":\"" + escapeJson(password) + "\"}";
        HttpResponse<String> response = sendPost("/api/auth/register", payload, null);
        if (response == null) {
            registerStatusLabel.setTextFill(Color.FIREBRICK);
            registerStatusLabel.setText("Erreur réseau vers le backend.");
            return;
        }

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            registerStatusLabel.setTextFill(Color.FORESTGREEN);
            registerStatusLabel.setText("Inscription OK.");
        } else {
            registerStatusLabel.setTextFill(Color.FIREBRICK);
            registerStatusLabel.setText("Inscription KO (" + response.statusCode() + "): " + response.body());
        }
    }

    @FXML
    protected void onLoginClick() {
        String email = loginEmailField.getText();
        String password = loginPasswordField.getText();
        String payload = "{\"email\":\"" + escapeJson(email) + "\",\"password\":\"" + escapeJson(password) + "\"}";

        HttpResponse<String> response = sendPost("/api/auth/login", payload, null);
        if (response == null) {
            loginStatusLabel.setTextFill(Color.FIREBRICK);
            loginStatusLabel.setText("Erreur réseau vers le backend.");
            return;
        }

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            String token = extractJsonField(TOKEN_PATTERN, response.body());
            if (token == null || token.isBlank()) {
                loginStatusLabel.setTextFill(Color.FIREBRICK);
                loginStatusLabel.setText("Login KO: token absent dans la réponse.");
                return;
            }
            currentToken = token;
            tokenLabel.setText(token);
            loginStatusLabel.setTextFill(Color.FORESTGREEN);
            loginStatusLabel.setText("Connexion OK.");
        } else {
            loginStatusLabel.setTextFill(Color.FIREBRICK);
            loginStatusLabel.setText("Connexion KO (" + response.statusCode() + "): " + response.body());
        }
    }

    @FXML
    protected void onLoadProfileClick() {
        if (currentToken == null || currentToken.isBlank()) {
            profileStatusLabel.setTextFill(Color.FIREBRICK);
            profileStatusLabel.setText("Aucun token. Connecte-toi d'abord.");
            return;
        }

        HttpResponse<String> response = sendGet("/api/auth/me", currentToken);
        if (response == null) {
            profileStatusLabel.setTextFill(Color.FIREBRICK);
            profileStatusLabel.setText("Erreur réseau vers le backend.");
            return;
        }

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            String email = extractJsonField(EMAIL_PATTERN, response.body());
            String createdAt = extractJsonField(CREATED_AT_PATTERN, response.body());
            profileEmailLabel.setText(email == null ? "-" : email);
            profileCreatedAtLabel.setText(createdAt == null ? "-" : createdAt);
            profileStatusLabel.setTextFill(Color.FORESTGREEN);
            profileStatusLabel.setText("Profil chargé.");
        } else {
            profileStatusLabel.setTextFill(Color.FIREBRICK);
            profileStatusLabel.setText("Accès /me KO (" + response.statusCode() + "): " + response.body());
        }
    }

    @FXML
    protected void onClearTokenClick() {
        currentToken = null;
        tokenLabel.setText("-");
        profileEmailLabel.setText("-");
        profileCreatedAtLabel.setText("-");
        profileStatusLabel.setTextFill(Color.GRAY);
        profileStatusLabel.setText("Token effacé.");
    }

    private void updateStrength(String password) {
        if (!isCompliant(password)) {
            registerStrengthLabel.setText("Force: rouge (non conforme TP2)");
            registerStrengthLabel.setTextFill(Color.FIREBRICK);
            return;
        }

        int score = scorePassword(password);
        if (score < 4) {
            registerStrengthLabel.setText("Force: orange (conforme mais faible)");
            registerStrengthLabel.setTextFill(Color.DARKORANGE);
        } else {
            registerStrengthLabel.setText("Force: vert (conforme et bon niveau)");
            registerStrengthLabel.setTextFill(Color.FORESTGREEN);
        }
    }

    private boolean isCompliant(String password) {
        if (password == null || password.length() < 12) {
            return false;
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private int scorePassword(String password) {
        int score = 0;
        if (password.length() >= 14) {
            score++;
        }
        if (password.chars().distinct().count() >= 8) {
            score++;
        }
        if (password.chars().filter(Character::isUpperCase).count() >= 2) {
            score++;
        }
        if (password.chars().filter(Character::isDigit).count() >= 2) {
            score++;
        }
        if (password.chars().filter(ch -> !Character.isLetterOrDigit(ch)).count() >= 2) {
            score++;
        }
        return score;
    }

    private HttpResponse<String> sendPost(String path, String payload, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedBaseUrl() + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));

            if (token != null && !token.isBlank()) {
                builder.header("Authorization", token);
            }

            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private HttpResponse<String> sendGet(String path, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedBaseUrl() + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", token)
                    .GET()
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizedBaseUrl() {
        String raw = backendUrlField.getText() == null ? "" : backendUrlField.getText().trim();
        if (raw.endsWith("/")) {
            return raw.substring(0, raw.length() - 1);
        }
        return raw;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractJsonField(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body == null ? "" : body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
