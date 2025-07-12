package com.github.ttl.dancenow;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.gui.ImageWindow;
import ij.gui.ImageCanvas;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * DanceNow plugin for Fiji/ImageJ
 * Provides a persistent window for quick navigation to specific X,Y,Z,T coordinates while preserving zoom level
 */
public class DanceNow implements PlugIn {
    
    private static DanceNowWindow window;
    
    @Override
    public void run(String arg) {
        if (window == null || !window.isDisplayable()) {
            window = new DanceNowWindow();
        }
        window.setVisible(true);
        window.toFront();
    }
    
    private static class DanceNowWindow extends JFrame {
        private JTextField xField, yField, zField, tField;
        private JLabel statusLabel, currentPosLabel;
        private JButton goButton, addHereButton, nextButton, backButton, removeButton, exportButton, loadButton;
        private Timer updateTimer;
        private JTable positionTable;
        private DefaultTableModel tableModel;
        private List<Position> positions;
        private int currentPositionIndex = -1;
        
        private static class Position {
            int x, y, z, t;
            
            Position(int x, int y, int z, int t) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.t = t;
            }
            
            @Override
            public String toString() {
                return String.format("%d,%d,%d,%d", x, y, z, t);
            }
        }
        
        public DanceNowWindow() {
            positions = new ArrayList<>();
            initializeWindow();
            createComponents();
            layoutComponents();
            setupEventHandlers();
            startPositionUpdater();
        }
        
        private void initializeWindow() {
            setTitle("DanceNow");
            setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            setAlwaysOnTop(true);
            setResizable(true);
        }
        
        private void createComponents() {
            xField = new JTextField(6);
            yField = new JTextField(6);
            zField = new JTextField(6);
            tField = new JTextField(6);
            
            goButton = new JButton("Go");
            addHereButton = new JButton("Add here");
            nextButton = new JButton("Next>");
            backButton = new JButton("<Back");
            removeButton = new JButton("Remove");
            exportButton = new JButton("Export");
            loadButton = new JButton("Load");
            
            statusLabel = new JLabel("No image open");
            currentPosLabel = new JLabel("Current: --");
            
            // Set font for better readability
            Font fieldFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
            xField.setFont(fieldFont);
            yField.setFont(fieldFont);
            zField.setFont(fieldFont);
            tField.setFont(fieldFont);
            
            // Create table for positions
            String[] columnNames = {"X,Y,Z,T"};
            tableModel = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return true;
                }
            };
            positionTable = new JTable(tableModel);
            positionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            positionTable.setFont(fieldFont);
        }
        
        private void layoutComponents() {
            setLayout(new BorderLayout());
            
            // Main panel
            JPanel mainPanel = new JPanel(new BorderLayout());
            
            // Top panel with input fields
            JPanel topPanel = new JPanel(new BorderLayout());
            
            // Input panel
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
            gbc.gridx = 6;
            inputPanel.add(addHereButton, gbc);
            
            topPanel.add(inputPanel, BorderLayout.CENTER);
            
            // Navigation buttons panel
            JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            navPanel.add(backButton);
            navPanel.add(nextButton);
            navPanel.add(removeButton);
            navPanel.add(exportButton);
            navPanel.add(loadButton);
            topPanel.add(navPanel, BorderLayout.SOUTH);
            
            mainPanel.add(topPanel, BorderLayout.NORTH);
            
            // Position list panel
            JPanel listPanel = new JPanel(new BorderLayout());
            listPanel.setBorder(BorderFactory.createTitledBorder("Position List"));
            JScrollPane scrollPane = new JScrollPane(positionTable);
            scrollPane.setPreferredSize(new Dimension(300, 150));
            listPanel.add(scrollPane, BorderLayout.CENTER);
            
            mainPanel.add(listPanel, BorderLayout.CENTER);
            
            add(mainPanel, BorderLayout.CENTER);
            
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
            
            // Add here button action
            addHereButton.addActionListener(e -> addCurrentPosition());
            
            // Next button action
            nextButton.addActionListener(e -> navigateToNextPosition());
            
            // Back button action
            backButton.addActionListener(e -> navigateToPreviousPosition());
            
            // Remove button action
            removeButton.addActionListener(e -> removeSelectedPosition());
            
            // Export button action
            exportButton.addActionListener(e -> exportPositions());
            
            // Load button action
            loadButton.addActionListener(e -> loadPositions());
            
            // Table selection listener
            positionTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    updateFieldsFromSelectedRow();
                }
            });
            
            // Table edit listener
            tableModel.addTableModelListener(e -> {
                if (e.getType() == TableModelEvent.UPDATE) {
                    updatePositionsFromTable();
                }
            });
            
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
        
        private void addCurrentPosition() {
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp == null) {
                statusLabel.setText("No image open");
                return;
            }
            
            ImageWindow win = imp.getWindow();
            if (win != null && win.getCanvas() != null) {
                ImageCanvas canvas = win.getCanvas();
                Rectangle srcRect = canvas.getSrcRect();
                int centerX = srcRect.x + srcRect.width / 2;
                int centerY = srcRect.y + srcRect.height / 2;
                
                Position pos = new Position(centerX, centerY, imp.getZ(), imp.getT());
                positions.add(pos);
                tableModel.addRow(new Object[]{pos.toString()});
                
                statusLabel.setText("Added position: " + pos.toString());
            }
        }
        
        private void navigateToNextPosition() {
            if (positions.isEmpty()) {
                statusLabel.setText("No positions in list");
                return;
            }
            
            currentPositionIndex++;
            if (currentPositionIndex >= positions.size()) {
                currentPositionIndex = 0;
            }
            
            positionTable.setRowSelectionInterval(currentPositionIndex, currentPositionIndex);
            Position pos = positions.get(currentPositionIndex);
            navigateToPosition(pos);
        }
        
        private void navigateToPreviousPosition() {
            if (positions.isEmpty()) {
                statusLabel.setText("No positions in list");
                return;
            }
            
            currentPositionIndex--;
            if (currentPositionIndex < 0) {
                currentPositionIndex = positions.size() - 1;
            }
            
            positionTable.setRowSelectionInterval(currentPositionIndex, currentPositionIndex);
            Position pos = positions.get(currentPositionIndex);
            navigateToPosition(pos);
        }
        
        private void navigateToPosition(Position pos) {
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp == null) return;
            
            navigateToPosition(imp, pos.x, pos.y, pos.z, pos.t);
            statusLabel.setText("Moved to: " + pos.toString());
        }
        
        private void updateFieldsFromSelectedRow() {
            int selectedRow = positionTable.getSelectedRow();
            if (selectedRow >= 0 && selectedRow < positions.size()) {
                currentPositionIndex = selectedRow;
                Position pos = positions.get(selectedRow);
                xField.setText(String.valueOf(pos.x));
                yField.setText(String.valueOf(pos.y));
                zField.setText(String.valueOf(pos.z));
                tField.setText(String.valueOf(pos.t));
            }
        }
        
        private void updatePositionsFromTable() {
            positions.clear();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String value = (String) tableModel.getValueAt(i, 0);
                try {
                    String[] parts = value.split(",");
                    if (parts.length == 4) {
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int z = Integer.parseInt(parts[2].trim());
                        int t = Integer.parseInt(parts[3].trim());
                        positions.add(new Position(x, y, z, t));
                    }
                } catch (NumberFormatException e) {
                    statusLabel.setText("Invalid position format at row " + (i + 1));
                }
            }
        }
        
        private void removeSelectedPosition() {
            int selectedRow = positionTable.getSelectedRow();
            if (selectedRow >= 0 && selectedRow < positions.size()) {
                positions.remove(selectedRow);
                tableModel.removeRow(selectedRow);
                
                // Update current position index if needed
                if (currentPositionIndex >= positions.size()) {
                    currentPositionIndex = positions.size() - 1;
                }
                
                statusLabel.setText("Removed position at row " + (selectedRow + 1));
            } else {
                statusLabel.setText("Please select a position to remove");
            }
        }
        
        private void exportPositions() {
            if (positions.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No positions to export", "Export", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File("positions.txt"));
            int result = fileChooser.showSaveDialog(this);
            
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try (FileWriter writer = new FileWriter(file)) {
                    for (Position pos : positions) {
                        writer.write(pos.toString() + "\n");
                    }
                    JOptionPane.showMessageDialog(this, 
                        "Exported " + positions.size() + " positions to " + file.getName(),
                        "Export Successful", 
                        JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, 
                        "Error exporting positions: " + e.getMessage(),
                        "Export Error", 
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        private void loadPositions() {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File("positions.txt"));
            int result = fileChooser.showOpenDialog(this);
            
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    // Clear existing positions
                    positions.clear();
                    tableModel.setRowCount(0);
                    
                    String line;
                    int lineNumber = 0;
                    while ((line = reader.readLine()) != null) {
                        lineNumber++;
                        line = line.trim();
                        if (!line.isEmpty()) {
                            try {
                                String[] parts = line.split(",");
                                if (parts.length == 4) {
                                    int x = Integer.parseInt(parts[0].trim());
                                    int y = Integer.parseInt(parts[1].trim());
                                    int z = Integer.parseInt(parts[2].trim());
                                    int t = Integer.parseInt(parts[3].trim());
                                    Position pos = new Position(x, y, z, t);
                                    positions.add(pos);
                                    tableModel.addRow(new Object[]{pos.toString()});
                                } else {
                                    JOptionPane.showMessageDialog(this,
                                        "Invalid format at line " + lineNumber + ": " + line,
                                        "Load Warning",
                                        JOptionPane.WARNING_MESSAGE);
                                }
                            } catch (NumberFormatException e) {
                                JOptionPane.showMessageDialog(this,
                                    "Invalid number format at line " + lineNumber + ": " + line,
                                    "Load Warning",
                                    JOptionPane.WARNING_MESSAGE);
                            }
                        }
                    }
                    
                    JOptionPane.showMessageDialog(this, 
                        "Loaded " + positions.size() + " positions from " + file.getName(),
                        "Load Successful", 
                        JOptionPane.INFORMATION_MESSAGE);
                    
                    currentPositionIndex = -1;
                    
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, 
                        "Error loading positions: " + e.getMessage(),
                        "Load Error", 
                        JOptionPane.ERROR_MESSAGE);
                }
            }
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