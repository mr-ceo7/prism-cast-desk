package com.example.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prism Cast Desktop — Polished screen broadcasting application.
 * Uses Robot for screen capture and an embedded HTTP server for MJPEG streaming.
 */
public class PrismCastApp {
    // State
    private boolean streaming = false;
    private int port = 8080;
    private boolean passwordEnabled = false;
    private String passcode = "admin";
    private String resolution = "Medium";
    private float jpegQuality = 0.70f;

    // Components
    private JFrame frame;
    private StreamServer server;
    private Robot robot;
    private Timer captureTimer;
    private Timer statsTimer;

    // UI references
    private JLabel statusLabel;
    private JLabel fpsValue;
    private JLabel viewersValue;
    private JLabel durationValue;
    private JTextField urlField;
    private JLabel previewLabel;
    private JButton toggleBtn;
    private JPanel previewCard;

    // Metrics
    private final AtomicInteger frameCount = new AtomicInteger(0);
    private final AtomicInteger fps = new AtomicInteger(0);
    private final AtomicLong streamStartTime = new AtomicLong(0);

    public void launch() {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            JOptionPane.showMessageDialog(null,
                "Cannot access screen capture API.\nCheck display permissions.",
                "Prism Cast Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        server = new StreamServer();
        frame = new JFrame("Prism Cast Desktop");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(1100, 780);
        frame.setMinimumSize(new Dimension(800, 600));
        frame.setLocationRelativeTo(null);

        frame.setContentPane(buildUI());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
                frame.dispose();
                System.exit(0);
            }
        });

        // FPS tracker
        statsTimer = new Timer(1000, e -> {
            fps.set(frameCount.getAndSet(0));
            updateStats();
        });
        statsTimer.start();

        frame.setVisible(true);
    }

    private JPanel buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Theme.MIDNIGHT);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildBottom(), BorderLayout.SOUTH);

        return root;
    }

    // ── Header ─────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MIDNIGHT);
        header.setBorder(new EmptyBorder(18, 24, 12, 24));

        // Left: Brand
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        brand.setOpaque(false);

        JPanel iconCircle = new RoundedPanel(20, Theme.LAVENDER);
        iconCircle.setPreferredSize(new Dimension(40, 40));
        iconCircle.setLayout(new GridBagLayout());
        JLabel iconLbl = new JLabel("\uD83D\uDCE1");
        iconLbl.setFont(new Font("SansSerif", Font.PLAIN, 18));
        iconCircle.add(iconLbl);
        brand.add(iconCircle);
        brand.add(Box.createHorizontalStrut(12));

        JPanel brandText = new JPanel();
        brandText.setLayout(new BoxLayout(brandText, BoxLayout.Y_AXIS));
        brandText.setOpaque(false);
        JLabel titleLbl = new JLabel("Prism Cast");
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setFont(Theme.TITLE);
        brandText.add(titleLbl);

        statusLabel = new JLabel("\u2022 Stream Standby");
        statusLabel.setForeground(Theme.TEXT_SECONDARY);
        statusLabel.setFont(Theme.BODY);
        brandText.add(statusLabel);

        brand.add(brandText);
        header.add(brand, BorderLayout.WEST);

        return header;
    }

    // ── Center content ─────────────────────────────────────
    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(16, 0));
        center.setBackground(Theme.MIDNIGHT);
        center.setBorder(new EmptyBorder(0, 24, 0, 24));

        center.add(buildPreviewAndStats(), BorderLayout.CENTER);
        center.add(buildSidebar(), BorderLayout.EAST);

        return center;
    }

    private JPanel buildPreviewAndStats() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        // Preview card
        previewCard = new RoundedPanel(20, Theme.CARD);
        previewCard.setLayout(new BorderLayout());
        previewCard.setBorder(new EmptyBorder(0, 0, 0, 0));

        previewLabel = new JLabel("Desktop Preview — Start streaming to begin capture", SwingConstants.CENTER);
        previewLabel.setForeground(Theme.MUTED);
        previewLabel.setFont(Theme.BODY);
        previewCard.add(previewLabel, BorderLayout.CENTER);

        panel.add(previewCard, BorderLayout.CENTER);

        // Stats row
        JPanel statsRow = new JPanel(new GridLayout(1, 3, 10, 0));
        statsRow.setOpaque(false);
        statsRow.setPreferredSize(new Dimension(0, 70));

        fpsValue = new JLabel("\u2014", SwingConstants.CENTER);
        statsRow.add(buildStatCard(fpsValue, "FPS"));

        viewersValue = new JLabel("0", SwingConstants.CENTER);
        statsRow.add(buildStatCard(viewersValue, "VIEWERS"));

        durationValue = new JLabel("\u2014", SwingConstants.CENTER);
        statsRow.add(buildStatCard(durationValue, "DURATION"));

        panel.add(statsRow, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildStatCard(JLabel valueLabel, String label) {
        JPanel card = new RoundedPanel(16, Theme.CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(12, 8, 12, 8));

        valueLabel.setForeground(Theme.LAVENDER);
        valueLabel.setFont(Theme.STAT_VALUE);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(valueLabel);

        card.add(Box.createVerticalStrut(2));

        JLabel lbl = new JLabel(label, SwingConstants.CENTER);
        lbl.setForeground(Theme.MUTED);
        lbl.setFont(Theme.STAT_LABEL);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(lbl);

        return card;
    }

    // ── Sidebar ────────────────────────────────────────────
    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setOpaque(false);
        sidebar.setPreferredSize(new Dimension(280, 0));

        // Connection settings
        sidebar.add(buildSettingsSection());
        sidebar.add(Box.createVerticalStrut(12));

        // Security
        sidebar.add(buildSecuritySection());
        sidebar.add(Box.createVerticalGlue());

        return sidebar;
    }

    private JPanel buildSettingsSection() {
        JPanel card = new RoundedPanel(16, Theme.CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel heading = new JLabel("Stream Settings");
        heading.setForeground(Theme.LAVENDER);
        heading.setFont(Theme.HEADING);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(heading);
        card.add(Box.createVerticalStrut(14));

        // Port
        card.add(makeLabel("Port"));
        card.add(Box.createVerticalStrut(4));
        JTextField portField = makeTextField(String.valueOf(port));
        portField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    int p = Integer.parseInt(portField.getText().trim());
                    if (p >= 1024 && p <= 65535) port = p;
                    else portField.setText(String.valueOf(port));
                } catch (NumberFormatException ex) {
                    portField.setText(String.valueOf(port));
                }
            }
        });
        card.add(portField);
        card.add(Box.createVerticalStrut(12));

        // Resolution
        card.add(makeLabel("Resolution"));
        card.add(Box.createVerticalStrut(4));
        JPanel resRow = new JPanel(new GridLayout(1, 3, 6, 0));
        resRow.setOpaque(false);
        resRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        resRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        String[] resOptions = {"Low", "Medium", "High"};
        String[] resLabels = {"360p", "720p", "Native"};
        JButton[] resBtns = new JButton[3];

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            resBtns[i] = makeResButton(resLabels[i], resolution.equals(resOptions[i]));
            resBtns[i].addActionListener(e -> {
                resolution = resOptions[idx];
                for (int j = 0; j < 3; j++) {
                    styleResButton(resBtns[j], j == idx);
                }
            });
            resRow.add(resBtns[i]);
        }
        card.add(resRow);
        card.add(Box.createVerticalStrut(12));

        // Quality
        card.add(makeLabel("JPEG Quality: " + Math.round(jpegQuality * 100) + "%"));
        card.add(Box.createVerticalStrut(4));
        JSlider qualitySlider = new JSlider(20, 100, Math.round(jpegQuality * 100));
        qualitySlider.setOpaque(false);
        qualitySlider.setForeground(Theme.LAVENDER);
        qualitySlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        qualitySlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JLabel qualityLabel = (JLabel) card.getComponent(card.getComponentCount() - 2);
        qualitySlider.addChangeListener(e -> {
            jpegQuality = qualitySlider.getValue() / 100f;
            qualityLabel.setText("JPEG Quality: " + qualitySlider.getValue() + "%");
        });
        card.add(qualitySlider);

        return card;
    }

    private JPanel buildSecuritySection() {
        JPanel card = new RoundedPanel(16, Theme.CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel heading = new JLabel("Security");
        heading.setForeground(Theme.LAVENDER);
        heading.setFont(Theme.HEADING);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(heading);
        card.add(Box.createVerticalStrut(14));

        // Password toggle
        JCheckBox pwToggle = new JCheckBox("Password Protection");
        pwToggle.setSelected(passwordEnabled);
        pwToggle.setForeground(Color.WHITE);
        pwToggle.setFont(Theme.BODY);
        pwToggle.setOpaque(false);
        pwToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(pwToggle);
        card.add(Box.createVerticalStrut(8));

        // Passcode field
        JLabel pwLabel = makeLabel("Stream Passcode");
        pwLabel.setVisible(passwordEnabled);
        card.add(pwLabel);
        card.add(Box.createVerticalStrut(4));

        JPasswordField pwField = new JPasswordField(passcode);
        styleTextField(pwField);
        pwField.setVisible(passwordEnabled);
        pwField.setAlignmentX(Component.LEFT_ALIGNMENT);
        pwField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        pwField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                passcode = new String(pwField.getPassword());
            }
        });
        card.add(pwField);

        pwToggle.addActionListener(e -> {
            passwordEnabled = pwToggle.isSelected();
            pwLabel.setVisible(passwordEnabled);
            pwField.setVisible(passwordEnabled);
            card.revalidate();
        });

        return card;
    }

    // ── Bottom bar ─────────────────────────────────────────
    private JPanel buildBottom() {
        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setBackground(Theme.CARD);
        bottom.setBorder(new EmptyBorder(14, 24, 14, 24));

        // URL display
        urlField = new JTextField("http://localhost:" + port);
        urlField.setEditable(false);
        urlField.setBackground(Theme.INPUT_BG);
        urlField.setForeground(Theme.MUTED);
        urlField.setFont(Theme.MONO);
        urlField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(8, 14, 8, 14)
        ));
        urlField.setCaretColor(Theme.LAVENDER);
        bottom.add(urlField, BorderLayout.CENTER);

        // Button row
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);

        JButton copyBtn = makeButton("Copy URL", Theme.CARD_HOVER, Theme.TEXT_PRIMARY);
        copyBtn.addActionListener(e -> {
            String url = urlField.getText();
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(url), null);
            copyBtn.setText("\u2713 Copied!");
            Timer reset = new Timer(1500, ev -> copyBtn.setText("Copy URL"));
            reset.setRepeats(false);
            reset.start();
        });
        btnRow.add(copyBtn);

        JButton browserBtn = makeButton("Open Browser", Theme.CARD_HOVER, Theme.TEXT_PRIMARY);
        browserBtn.addActionListener(e -> {
            String url = urlField.getText();
            if (!url.isBlank() && Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(new java.net.URI(url));
                } catch (Exception ignored) {}
            }
        });
        btnRow.add(browserBtn);

        toggleBtn = makeButton("Start Stream", Theme.LAVENDER, Theme.STATUS_TEXT);
        toggleBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        toggleBtn.setPreferredSize(new Dimension(150, 38));
        toggleBtn.addActionListener(e -> toggleStreaming());
        btnRow.add(toggleBtn);

        bottom.add(btnRow, BorderLayout.EAST);

        return bottom;
    }

    // ── Streaming logic ────────────────────────────────────
    private void toggleStreaming() {
        if (streaming) {
            stopStreaming();
        } else {
            startStreaming();
        }
    }

    private void startStreaming() {
        server.setPort(port);
        server.setPasswordEnabled(passwordEnabled);
        server.setPasscode(passcode);
        server.setJpegQuality(jpegQuality);

        float scale = switch (resolution) {
            case "Low" -> 0.35f;
            case "High" -> 1.0f;
            default -> 0.5f;
        };
        server.setResolutionScale(scale);

        try {
            server.start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame,
                "Cannot start server on port " + port + ".\n" + ex.getMessage(),
                "Server Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice().getDefaultConfiguration().getBounds();

        captureTimer = new Timer(33, e -> {
            BufferedImage capture = robot.createScreenCapture(bounds);
            server.updateFrame(capture);
            frameCount.incrementAndGet();

            // Update preview
            int pw = previewCard.getWidth() - 16;
            int ph = previewCard.getHeight() - 16;
            if (pw > 0 && ph > 0) {
                Image scaled = capture.getScaledInstance(pw, ph, Image.SCALE_FAST);
                previewLabel.setIcon(new ImageIcon(scaled));
                previewLabel.setText(null);
            }
        });
        captureTimer.start();

        streaming = true;
        streamStartTime.set(System.currentTimeMillis());
        String url = server.getLocalUrl();
        urlField.setText(url);
        urlField.setForeground(Theme.LAVENDER);
        toggleBtn.setText("Stop Stream");
        toggleBtn.setBackground(Theme.STOP_RED);
        toggleBtn.setForeground(Color.WHITE);
        statusLabel.setText("\u2022 Stream Active \u2022 " + url);
        statusLabel.setForeground(Theme.LIME);
    }

    private void stopStreaming() {
        if (captureTimer != null) {
            captureTimer.stop();
            captureTimer = null;
        }
        server.stop();

        streaming = false;
        streamStartTime.set(0);
        urlField.setText("http://localhost:" + port);
        urlField.setForeground(Theme.MUTED);
        toggleBtn.setText("Start Stream");
        toggleBtn.setBackground(Theme.LAVENDER);
        toggleBtn.setForeground(Theme.STATUS_TEXT);
        statusLabel.setText("\u2022 Stream Standby");
        statusLabel.setForeground(Theme.TEXT_SECONDARY);
        previewLabel.setIcon(null);
        previewLabel.setText("Desktop Preview — Start streaming to begin capture");
        fpsValue.setText("\u2014");
        viewersValue.setText("0");
        durationValue.setText("\u2014");
    }

    private void updateStats() {
        if (!streaming) return;
        fpsValue.setText(String.valueOf(fps.get()));
        viewersValue.setText(String.valueOf(server.getViewerCount()));

        long elapsed = System.currentTimeMillis() - streamStartTime.get();
        long secs = elapsed / 1000;
        long mins = secs / 60;
        long hrs = mins / 60;
        if (hrs > 0) {
            durationValue.setText(String.format("%d:%02d:%02d", hrs, mins % 60, secs % 60));
        } else {
            durationValue.setText(String.format("%d:%02d", mins, secs % 60));
        }

        // Live-update server settings
        server.setPasswordEnabled(passwordEnabled);
        server.setPasscode(passcode);
        server.setJpegQuality(jpegQuality);
        float scale = switch (resolution) {
            case "Low" -> 0.35f;
            case "High" -> 1.0f;
            default -> 0.5f;
        };
        server.setResolutionScale(scale);
    }

    private void shutdown() {
        if (streaming) stopStreaming();
        if (statsTimer != null) statsTimer.stop();
    }

    // ── UI helpers ─────────────────────────────────────────
    private JLabel makeLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(Theme.MUTED);
        lbl.setFont(Theme.LABEL);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JTextField makeTextField(String text) {
        JTextField field = new JTextField(text);
        styleTextField(field);
        return field;
    }

    private void styleTextField(JTextField field) {
        field.setBackground(Theme.INPUT_BG);
        field.setForeground(Color.WHITE);
        field.setCaretColor(Theme.LAVENDER);
        field.setFont(Theme.BODY);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(8, 12, 8, 12)
        ));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
    }

    private JButton makeButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setPreferredSize(new Dimension(130, 38));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton makeResButton(String label, boolean active) {
        JButton btn = new JButton(label);
        styleResButton(btn, active);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 30));
        return btn;
    }

    private void styleResButton(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(new Color(Theme.LAVENDER.getRed(), Theme.LAVENDER.getGreen(), Theme.LAVENDER.getBlue(), 40));
            btn.setForeground(Theme.LAVENDER);
            btn.setBorder(BorderFactory.createLineBorder(Theme.LAVENDER, 1));
        } else {
            btn.setBackground(Theme.INPUT_BG);
            btn.setForeground(Theme.MUTED);
            btn.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        }
    }

    /** A JPanel with rounded corners and a solid fill. */
    static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color fill;

        RoundedPanel(int radius, Color fill) {
            this.radius = radius;
            this.fill = fill;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
