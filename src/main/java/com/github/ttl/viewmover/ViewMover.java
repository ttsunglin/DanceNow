package com.github.ttl.viewmover;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.gui.ImageWindow;
import ij.gui.ImageCanvas;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * ViewMover plugin for Fiji/ImageJ
 * Provides a persistent window for quick navigation to specific X,Y,Z,T coordinates while preserving zoom level
 */
public class ViewMover implements PlugIn {
    
    private static ViewMoverWindow window;
    
    @Override
    public void run(String arg) {
        if (window == null || !window.isDisplayable()) {
            window = new ViewMoverWindow();
        }
        window.setVisible(true);
        window.toFront();
    }
    
    private static class ViewMoverWindow extends JFrame {
        private JTextField xField, yField, zField, tField;
        private JLabel statusLabel, currentPosLabel;
        private JButton goButton;
        private Timer updateTimer;
        
        public ViewMoverWindow() {
            initializeWindow();
            createComponents();
            layoutComponents();
            setupEventHandlers();
            startPositionUpdater();
        }
        
        private void initializeWindow() {
            setTitle("ViewMover");
            setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            setAlwaysOnTop(true);
            setResizable(false);
        }
        
        private void createComponents() {
            xField = new JTextField(6);
            yField = new JTextField(6);
            zField = new JTextField(6);
            tField = new JTextField(6);
            
            goButton = new JButton("Go");
            statusLabel = new JLabel("No image open");
            currentPosLabel = new JLabel("Current: --");
            
            // Set font for better readability
            Font fieldFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
            xField.setFont(fieldFont);
            yField.setFont(fieldFont);
            zField.setFont(fieldFont);
            tField.setFont(fieldFont);
        }
        
        private void layoutComponents() {
            setLayout(new BorderLayout());
            
            // Main input panel
            JPanel inputPanel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(3, 3, 3, 3);
            
            // Row 1: Labels
            gbc.gridx = 1; gbc.gridy = 0;
            inputPanel.add(new JLabel("X"), gbc);
            gbc.gridx = 2;
            inputPanel.add(new JLabel("Y"), gbc);
            gbc.gridx = 3;
            inputPanel.add(new JLabel("Z"), gbc);
            gbc.gridx = 4;
            inputPanel.add(new JLabel("T"), gbc);
            
            // Row 2: Input fields
            gbc.gridx = 0; gbc.gridy = 1;
            inputPanel.add(new JLabel("Go to:"), gbc);
            gbc.gridx = 1;
            inputPanel.add(xField, gbc);
            gbc.gridx = 2;
            inputPanel.add(yField, gbc);
            gbc.gridx = 3;
            inputPanel.add(zField, gbc);
            gbc.gridx = 4;
            inputPanel.add(tField, gbc);
            gbc.gridx = 5;
            inputPanel.add(goButton, gbc);
            
            add(inputPanel, BorderLayout.CENTER);
            
            // Status panel
            JPanel statusPanel = new JPanel(new BorderLayout());
            statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            statusPanel.add(currentPosLabel, BorderLayout.NORTH);
            statusPanel.add(statusLabel, BorderLayout.SOUTH);
            add(statusPanel, BorderLayout.SOUTH);
            
            pack();
            setLocationRelativeTo(null);
        }
        
        private void setupEventHandlers() {
            // Go button action
            goButton.addActionListener(e -> navigateToPosition());
            
            // Enter key in any field triggers navigation
            KeyAdapter enterKeyHandler = new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        navigateToPosition();
                    }
                }
            };
            
            xField.addKeyListener(enterKeyHandler);
            yField.addKeyListener(enterKeyHandler);
            zField.addKeyListener(enterKeyHandler);
            tField.addKeyListener(enterKeyHandler);
            
            // Update fields when window gains focus
            addWindowFocusListener(new WindowFocusListener() {
                @Override
                public void windowGainedFocus(WindowEvent e) {
                    updateCurrentPosition();
                }
                
                @Override
                public void windowLostFocus(WindowEvent e) {}
            });
        }
        
        private void startPositionUpdater() {
            updateTimer = new Timer(true);
            updateTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> updateCurrentPosition());
                }
            }, 0, 500); // Update every 500ms
        }
        
        private void updateCurrentPosition() {
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp == null) {
                statusLabel.setText("No image open");
                currentPosLabel.setText("Current: --");
                goButton.setEnabled(false);
                return;
            }
            
            goButton.setEnabled(true);
            
            // Get current view center
            ImageWindow win = imp.getWindow();
            if (win != null && win.getCanvas() != null) {
                ImageCanvas canvas = win.getCanvas();
                Rectangle srcRect = canvas.getSrcRect();
                int centerX = srcRect.x + srcRect.width / 2;
                int centerY = srcRect.y + srcRect.height / 2;
                
                currentPosLabel.setText(String.format("Current: X=%d, Y=%d, Z=%d, T=%d", 
                    centerX, centerY, imp.getZ(), imp.getT()));
            }
            
            statusLabel.setText(String.format("%s [%dx%dx%dx%d]", 
                imp.getTitle(), imp.getWidth(), imp.getHeight(), 
                imp.getNSlices(), imp.getNFrames()));
        }
        
        private void navigateToPosition() {
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp == null) {
                statusLabel.setText("No image open");
                return;
            }
            
            try {
                int x = Integer.parseInt(xField.getText().trim());
                int y = Integer.parseInt(yField.getText().trim());
                int z = Integer.parseInt(zField.getText().trim());
                int t = Integer.parseInt(tField.getText().trim());
                
                // Validate coordinates
                if (x < 1 || x > imp.getWidth() || y < 1 || y > imp.getHeight() ||
                    z < 1 || z > imp.getNSlices() || t < 1 || t > imp.getNFrames()) {
                    statusLabel.setText("Coordinates out of bounds!");
                    return;
                }
                
                navigateToPosition(imp, x, y, z, t);
                statusLabel.setText("Moved to: X=" + x + ", Y=" + y + ", Z=" + z + ", T=" + t);
                
            } catch (NumberFormatException e) {
                statusLabel.setText("Please enter valid numbers");
            }
        }
        
        private void navigateToPosition(ImagePlus imp, int x, int y, int z, int t) {
            ImageWindow win = imp.getWindow();
            if (win == null) return;
            
            ImageCanvas canvas = win.getCanvas();
            if (canvas == null) return;
            
            // Store current zoom and view settings
            double magnification = canvas.getMagnification();
            Rectangle srcRect = canvas.getSrcRect();
            
            // Set Z and T position
            if (imp.getNSlices() > 1) {
                imp.setZ(z);
            }
            if (imp.getNFrames() > 1) {
                imp.setT(t);
            }
            
            // Calculate new source rectangle to center on the specified coordinates
            int newSrcX = x - srcRect.width / 2;
            int newSrcY = y - srcRect.height / 2;
            
            // Ensure the source rectangle stays within image bounds
            newSrcX = Math.max(0, Math.min(newSrcX, imp.getWidth() - srcRect.width));
            newSrcY = Math.max(0, Math.min(newSrcY, imp.getHeight() - srcRect.height));
            
            // Set the new source rectangle
            srcRect.x = newSrcX;
            srcRect.y = newSrcY;
            
            // Apply the changes
            canvas.setSourceRect(srcRect);
            canvas.setMagnification(magnification);
            
            // Update the display
            imp.updateAndDraw();
        }
        
        @Override
        public void dispose() {
            if (updateTimer != null) {
                updateTimer.cancel();
            }
            super.dispose();
        }
    }
}