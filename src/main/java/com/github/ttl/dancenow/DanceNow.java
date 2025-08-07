package com.github.ttl.dancenow;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.gui.ImageWindow;
import ij.gui.ImageCanvas;
import ij.gui.Overlay;
import ij.gui.Line;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.process.ImageProcessor;
import ij.plugin.ChannelSplitter;
import ij.plugin.RGBStackMerge;
import ij.CompositeImage;
import ij.process.LUT;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.awt.datatransfer.StringSelection;
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
        private JTextField xField, yField, zField, tField, noteField;
        private JLabel statusLabel, currentPosLabel;
        private JButton goButton, addHereButton, nextButton, backButton, removeButton, clearButton, exportButton, loadButton, snapshotButton;
        private Timer updateTimer;
        private JTable positionTable;
        private DefaultTableModel tableModel;
        private List<Position> positions;
        private int currentPositionIndex = -1;
        private boolean isUpdatingTable = false; // Flag to prevent infinite recursion
        private boolean sortAscending = true; // Track sort direction
        private Overlay crosshairOverlay; // Overlay for center crosshair
        private boolean showCrosshair = true; // Toggle for crosshair visibility
        private ImageWindow lastImageWindow; // Track last image window for cleanup
        private MouseMotionListener crosshairMouseListener; // Mouse listener for crosshair
        private MouseWheelListener crosshairWheelListener; // Wheel listener for crosshair
        
        private static class Position {
            int x, y, z, t;
            String note;
            
            Position(int x, int y, int z, int t) {
                this(x, y, z, t, "");
            }
            
            Position(int x, int y, int z, int t, String note) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.t = t;
                this.note = note != null ? note : "";
            }
            
            @Override
            public String toString() {
                return String.format("%d,%d,%d,%d", x, y, z, t);
            }
            
            public String toStringWithNote() {
                if (note != null && !note.isEmpty()) {
                    return String.format("%d,%d,%d,%d,%s", x, y, z, t, note);
                }
                return toString();
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
            
            // Add window listener to clean up crosshair when hiding
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    // Remove mouse listeners from image window
                    if (lastImageWindow != null && lastImageWindow.getCanvas() != null) {
                        if (crosshairMouseListener != null) {
                            lastImageWindow.getCanvas().removeMouseMotionListener(crosshairMouseListener);
                        }
                        if (crosshairWheelListener != null) {
                            lastImageWindow.getCanvas().removeMouseWheelListener(crosshairWheelListener);
                        }
                    }
                    
                    // Remove crosshair overlay when hiding window
                    ImagePlus imp = WindowManager.getCurrentImage();
                    if (imp != null && crosshairOverlay != null) {
                        imp.setOverlay(null);
                    }
                }
            });
        }
        
        private void createComponents() {
            xField = new JTextField(3);
            yField = new JTextField(3);
            zField = new JTextField(3);
            tField = new JTextField(3);
            noteField = new JTextField(8);
            
            goButton = new JButton("Go");
            addHereButton = new JButton("Add");
            nextButton = new JButton("Next >");
            backButton = new JButton("< Back");
            removeButton = new JButton("Remove");
            clearButton = new JButton("Clear All");
            exportButton = new JButton("Export");
            snapshotButton = new JButton("Snapshot");
            loadButton = new JButton("Load");
            
            statusLabel = new JLabel("No image open");
            currentPosLabel = new JLabel("Current: --");
            
            // Set font for better readability
            Font fieldFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
            xField.setFont(fieldFont);
            yField.setFont(fieldFont);
            zField.setFont(fieldFont);
            tField.setFont(fieldFont);
            noteField.setFont(fieldFont);
            
            // Create table for positions with three columns (including row number)
            String[] columnNames = {"#", "X,Y,Z,T", "Note"};
            tableModel = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    // Row number column (column 0) is not editable
                    return column > 0;
                }
                
                @Override
                public void setValueAt(Object value, int row, int column) {
                    super.setValueAt(value, row, column);
                    // Add new row if editing the last row and it's not empty
                    if (row == getRowCount() - 1 && column == 1 && value != null && !value.toString().trim().isEmpty()) {
                        addRow(new Object[]{getRowCount() + 1, "", ""});
                    }
                }
            };
            
            positionTable = new JTable(tableModel);
            positionTable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            positionTable.setFont(fieldFont);
            positionTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            
            // Set column widths
            positionTable.getColumnModel().getColumn(0).setPreferredWidth(25);  // Row number column
            positionTable.getColumnModel().getColumn(1).setPreferredWidth(100); // Position column
            positionTable.getColumnModel().getColumn(2).setPreferredWidth(150); // Note column
            
            // Add initial empty rows for user convenience
            for (int i = 0; i < 5; i++) {
                tableModel.addRow(new Object[]{i + 1, "", ""});
                positions.add(null);  // Add corresponding null positions
            }
            
            // Set table to be focusable for paste operations
            positionTable.setFocusable(true);
            
            // Set table cell selection behavior
            positionTable.setCellSelectionEnabled(true);
            positionTable.setRowSelectionAllowed(true);
            positionTable.setColumnSelectionAllowed(false);
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
            gbc.insets = new Insets(2, 2, 2, 2);  // Reduce spacing
            
            // Row 1: Labels
            gbc.gridx = 0; gbc.gridy = 0;
            inputPanel.add(new JLabel("X"), gbc);
            gbc.gridx = 1;
            inputPanel.add(new JLabel("Y"), gbc);
            gbc.gridx = 2;
            inputPanel.add(new JLabel("Z"), gbc);
            gbc.gridx = 3;
            inputPanel.add(new JLabel("T"), gbc);
            gbc.gridx = 4;
            inputPanel.add(new JLabel("Note"), gbc);
            
            // Row 2: Input fields
            gbc.gridx = 0; gbc.gridy = 1;
            inputPanel.add(xField, gbc);
            gbc.gridx = 1;
            inputPanel.add(yField, gbc);
            gbc.gridx = 2;
            inputPanel.add(zField, gbc);
            gbc.gridx = 3;
            inputPanel.add(tField, gbc);
            gbc.gridx = 4;
            inputPanel.add(noteField, gbc);
            gbc.gridx = 5;
            inputPanel.add(goButton, gbc);
            gbc.gridx = 6;
            inputPanel.add(addHereButton, gbc);
            
            topPanel.add(inputPanel, BorderLayout.CENTER);
            
            // Create a panel to hold navigation buttons and crosshair toggle
            JPanel navContainer = new JPanel(new BorderLayout());
            
            // Navigation buttons panel (Back and Next)
            JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            navPanel.add(backButton);
            navPanel.add(nextButton);
            navContainer.add(navPanel, BorderLayout.NORTH);
            
            // Crosshair toggle panel (separate row)
            JPanel crosshairPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JCheckBox crosshairToggle = new JCheckBox("Show center +", showCrosshair);
            crosshairToggle.addActionListener(e -> {
                showCrosshair = crosshairToggle.isSelected();
                if (!showCrosshair) {
                    // Remove crosshair overlay when disabled
                    ImagePlus imp = WindowManager.getCurrentImage();
                    if (imp != null) {
                        imp.setOverlay(null);
                    }
                } else {
                    // Update crosshair when enabled
                    updateCrosshairOverlay();
                }
            });
            crosshairPanel.add(crosshairToggle);
            navContainer.add(crosshairPanel, BorderLayout.SOUTH);
            
            topPanel.add(navContainer, BorderLayout.SOUTH);
            
            mainPanel.add(topPanel, BorderLayout.NORTH);
            
            // Position list panel
            JPanel listPanel = new JPanel(new BorderLayout());
            listPanel.setBorder(BorderFactory.createTitledBorder("Position List"));
            JScrollPane scrollPane = new JScrollPane(positionTable);
            scrollPane.setPreferredSize(new Dimension(280, 180));  // Compact size
            listPanel.add(scrollPane, BorderLayout.CENTER);
            
            // List management buttons panel (Remove, Clear, Snapshot, Export, Load) - below the list
            JPanel listButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            listButtonPanel.add(removeButton);
            listButtonPanel.add(clearButton);
            listButtonPanel.add(snapshotButton);
            listButtonPanel.add(exportButton);
            listButtonPanel.add(loadButton);
            listPanel.add(listButtonPanel, BorderLayout.SOUTH);
            
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
            
            // Clear button action
            clearButton.addActionListener(e -> clearAllPositions());
            
            // Export button action
            exportButton.addActionListener(e -> exportPositions());
            
            // Snapshot button action
            snapshotButton.addActionListener(e -> openSnapshotDialog());
            
            // Load button action
            loadButton.addActionListener(e -> loadPositions());
            
            // Add paste functionality to the table
            setupTablePasteHandler();
            
            // Add table header click listener for sorting
            setupTableHeaderSorting();
            
            // Table selection listener
            positionTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    updateFieldsFromSelectedRow();
                }
            });
            
            // Table edit listener
            tableModel.addTableModelListener(e -> {
                if (e.getType() == TableModelEvent.UPDATE && !isUpdatingTable) {
                    updatePositionsFromTable();
                    // Also update the fields if the edited row is selected
                    int selectedRow = positionTable.getSelectedRow();
                    if (selectedRow >= 0 && selectedRow == e.getFirstRow()) {
                        updateFieldsFromSelectedRow();
                    }
                }
            });
            
            // Removed Enter key handler - only mouse clicks allowed for navigation
            
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
        
        private void setupTableHeaderSorting() {
            positionTable.getTableHeader().addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int column = positionTable.columnAtPoint(e.getPoint());
                    if (column >= 0) {
                        sortByColumn(column);
                    }
                }
            });
            
            // Set cursor to hand when hovering over headers
            positionTable.getTableHeader().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        
        private void sortByColumn(int column) {
            // Skip sorting if it's the row number column
            if (column == 0) {
                return;
            }
            // Create a list of positions with their table row data
            List<PositionWithRow> sortablePositions = new ArrayList<>();
            for (int i = 0; i < positions.size(); i++) {
                Position pos = positions.get(i);
                if (pos != null) {
                    Object noteObj = tableModel.getValueAt(i, 2);
                    String note = noteObj != null ? noteObj.toString() : "";
                    sortablePositions.add(new PositionWithRow(pos, note, i));
                }
            }
            
            if (sortablePositions.isEmpty()) {
                statusLabel.setText("No positions to sort");
                return;
            }
            
            // Sort based on column
            if (column == 1) {
                // Sort by X,Y,Z,T
                sortablePositions.sort((a, b) -> {
                    int result = Integer.compare(a.position.x, b.position.x);
                    if (result == 0) result = Integer.compare(a.position.y, b.position.y);
                    if (result == 0) result = Integer.compare(a.position.z, b.position.z);
                    if (result == 0) result = Integer.compare(a.position.t, b.position.t);
                    return sortAscending ? result : -result;
                });
                statusLabel.setText("Sorted by position " + (sortAscending ? "(ascending)" : "(descending)"));
            } else if (column == 2) {
                // Sort by Note
                sortablePositions.sort((a, b) -> {
                    if (a.note.isEmpty() && b.note.isEmpty()) return 0;
                    if (a.note.isEmpty()) return sortAscending ? 1 : -1;
                    if (b.note.isEmpty()) return sortAscending ? -1 : 1;
                    int result = a.note.compareToIgnoreCase(b.note);
                    return sortAscending ? result : -result;
                });
                statusLabel.setText("Sorted by note " + (sortAscending ? "(ascending)" : "(descending)"));
            }
            
            // Toggle sort direction for next click
            sortAscending = !sortAscending;
            
            // Clear and rebuild table
            positions.clear();
            tableModel.setRowCount(0);
            
            for (PositionWithRow pwr : sortablePositions) {
                positions.add(pwr.position);
                tableModel.addRow(new Object[]{positions.size(), pwr.position.toString(), pwr.note});
            }
            
            // Add empty rows back
            while (tableModel.getRowCount() < 5) {
                tableModel.addRow(new Object[]{tableModel.getRowCount() + 1, "", ""});
                positions.add(null);
            }
            
            currentPositionIndex = -1;
        }
        
        private void setupTablePasteHandler() {
            // All keyboard shortcuts removed - only mouse interactions allowed
            
            // Add right-click context menu
            JPopupMenu popupMenu = new JPopupMenu();
            
            JMenuItem copyItem = new JMenuItem("Copy");
            copyItem.addActionListener(e -> handleCopy());
            popupMenu.add(copyItem);
            
            JMenuItem cutItem = new JMenuItem("Cut");
            cutItem.addActionListener(e -> handleCut());
            popupMenu.add(cutItem);
            
            JMenuItem pasteItem = new JMenuItem("Paste Positions");
            pasteItem.addActionListener(e -> handleDirectPaste());
            popupMenu.add(pasteItem);
            
            popupMenu.addSeparator();
            
            JMenuItem insertItem = new JMenuItem("Insert Row");
            insertItem.addActionListener(e -> handleInsertRow());
            popupMenu.add(insertItem);
            
            JMenuItem deleteItem = new JMenuItem("Delete Row");
            deleteItem.addActionListener(e -> handleDelete());
            popupMenu.add(deleteItem);
            
            positionTable.setComponentPopupMenu(popupMenu);
            
            // Also add popup to scroll pane for easier access
            Container parent = positionTable.getParent();
            if (parent instanceof JViewport) {
                parent = parent.getParent();
                if (parent instanceof JScrollPane) {
                    ((JScrollPane) parent).setComponentPopupMenu(popupMenu);
                }
            }
        }
        
        private void handleDirectPaste() {
            try {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                Transferable contents = clipboard.getContents(null);
                
                if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    String clipboardData = (String) contents.getTransferData(DataFlavor.stringFlavor);
                    
                    if (clipboardData != null && !clipboardData.trim().isEmpty()) {
                        // If cell is being edited, paste into cell
                        if (positionTable.isEditing()) {
                            int row = positionTable.getEditingRow();
                            int col = positionTable.getEditingColumn();
                            positionTable.setValueAt(clipboardData.trim(), row, col);
                            positionTable.getCellEditor().stopCellEditing();
                        } else {
                            // Otherwise, parse as bulk positions
                            parseBulkPositions(clipboardData);
                            statusLabel.setText("Pasted positions from clipboard");
                        }
                    } else {
                        statusLabel.setText("Clipboard is empty");
                    }
                } else {
                    statusLabel.setText("No text data in clipboard");
                }
            } catch (Exception ex) {
                statusLabel.setText("Paste error: " + ex.getClass().getSimpleName());
                ex.printStackTrace();
            }
        }
        
        private void handleCopy() {
            int[] selectedRows = positionTable.getSelectedRows();
            if (selectedRows.length == 0) {
                statusLabel.setText("No rows selected to copy");
                return;
            }
            
            StringBuilder sb = new StringBuilder();
            for (int row : selectedRows) {
                Object value = tableModel.getValueAt(row, 1);
                if (value != null && !value.toString().trim().isEmpty()) {
                    sb.append(value.toString()).append("\n");
                }
            }
            
            if (sb.length() > 0) {
                StringSelection selection = new StringSelection(sb.toString());
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, null);
                statusLabel.setText("Copied " + selectedRows.length + " position(s)");
            }
        }
        
        private void handleCut() {
            handleCopy();
            handleDelete();
        }
        
        private void handleDelete() {
            int[] selectedRows = positionTable.getSelectedRows();
            if (selectedRows.length == 0) {
                statusLabel.setText("No rows selected to delete");
                return;
            }
            
            // Stop editing if in progress
            if (positionTable.isEditing()) {
                positionTable.getCellEditor().stopCellEditing();
            }
            
            // Clear selected rows (don't remove them, just clear content)
            for (int row : selectedRows) {
                tableModel.setValueAt("", row, 1);  // Clear position
                tableModel.setValueAt("", row, 2);  // Clear note
            }
            
            updatePositionsFromTable();
            statusLabel.setText("Cleared " + selectedRows.length + " row(s)");
        }
        
        private void handleInsertRow() {
            int selectedRow = positionTable.getSelectedRow();
            if (selectedRow >= 0) {
                tableModel.insertRow(selectedRow, new Object[]{""});
            } else {
                tableModel.addRow(new Object[]{tableModel.getRowCount() + 1, "", ""});
            }
            updatePositionsFromTable();
            statusLabel.setText("Inserted new row");
        }
        
        private void startPositionUpdater() {
            updateTimer = new Timer(true);
            updateTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> {
                        updateCurrentPosition();
                        updateCrosshairOverlay();
                        
                        // Also try to add mouse listeners to current image window for real-time updates
                        addImageWindowListeners();
                    });
                }
            }, 0, 50); // Update every 50ms for more responsive crosshair
        }
        
        private void addImageWindowListeners() {
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp != null) {
                ImageWindow win = imp.getWindow();
                if (win != null && win != lastImageWindow) {
                    // Remove listeners from old window if exists
                    if (lastImageWindow != null && lastImageWindow.getCanvas() != null) {
                        if (crosshairMouseListener != null) {
                            lastImageWindow.getCanvas().removeMouseMotionListener(crosshairMouseListener);
                        }
                        if (crosshairWheelListener != null) {
                            lastImageWindow.getCanvas().removeMouseWheelListener(crosshairWheelListener);
                        }
                    }
                    
                    // Add listeners to new window
                    ImageCanvas canvas = win.getCanvas();
                    if (canvas != null) {
                        // Create mouse motion listener for dragging
                        crosshairMouseListener = new MouseMotionListener() {
                            @Override
                            public void mouseDragged(MouseEvent e) {
                                updateCrosshairOverlay();
                            }
                            
                            @Override
                            public void mouseMoved(MouseEvent e) {
                                // Not needed for crosshair
                            }
                        };
                        
                        // Create mouse wheel listener for zooming
                        crosshairWheelListener = new MouseWheelListener() {
                            @Override
                            public void mouseWheelMoved(MouseWheelEvent e) {
                                updateCrosshairOverlay();
                            }
                        };
                        
                        canvas.addMouseMotionListener(crosshairMouseListener);
                        canvas.addMouseWheelListener(crosshairWheelListener);
                        lastImageWindow = win;
                    }
                }
            }
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
        
        private void updateCrosshairOverlay() {
            if (!showCrosshair) return;
            
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp != null) {
                ImageWindow win = imp.getWindow();
                if (win != null && win.getCanvas() != null) {
                    ImageCanvas canvas = win.getCanvas();
                    Rectangle srcRect = canvas.getSrcRect();
                    double mag = canvas.getMagnification();
                    
                    // Calculate the center of the current view in image coordinates
                    int centerX = srcRect.x + srcRect.width / 2;
                    int centerY = srcRect.y + srcRect.height / 2;
                    
                    // Create or update the overlay
                    if (crosshairOverlay == null) {
                        crosshairOverlay = new Overlay();
                    } else {
                        crosshairOverlay.clear();
                    }
                    
                    // Calculate crosshair size that remains constant on screen
                    // The crosshair size should be smaller when zoomed in (larger mag)
                    int baseSize = 5; // Base size in image pixels at 1:1 zoom (reduced from 15)
                    double crosshairSize = baseSize / Math.sqrt(mag); // Scale inversely with zoom
                    
                    // Ensure minimum size
                    crosshairSize = Math.max(2, crosshairSize);
                    
                    // Horizontal line
                    Line hLine = new Line(centerX - crosshairSize, centerY, 
                                          centerX + crosshairSize, centerY);
                    hLine.setStrokeColor(Color.GREEN);
                    hLine.setStrokeWidth(Math.max(1, Math.min(2, 1.5 / Math.sqrt(mag)))); // Thinner stroke for smaller crosshair
                    crosshairOverlay.add(hLine);
                    
                    // Vertical line
                    Line vLine = new Line(centerX, centerY - crosshairSize, 
                                          centerX, centerY + crosshairSize);
                    vLine.setStrokeColor(Color.GREEN);
                    vLine.setStrokeWidth(Math.max(1, Math.min(2, 1.5 / Math.sqrt(mag)))); // Thinner stroke for smaller crosshair
                    crosshairOverlay.add(vLine);
                    
                    // Apply the overlay to the image
                    imp.setOverlay(crosshairOverlay);
                }
            }
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
                
                // Validate X,Y coordinates
                if (x < 1 || x > imp.getWidth() || y < 1 || y > imp.getHeight()) {
                    JOptionPane.showMessageDialog(this,
                        "X,Y coordinates (" + x + "," + y + ") are out of image bounds!\n" +
                        "Image size: " + imp.getWidth() + " x " + imp.getHeight(),
                        "Navigation Error", JOptionPane.WARNING_MESSAGE);
                    statusLabel.setText("Coordinates out of bounds!");
                    return;
                }
                
                // Validate and adjust Z,T if needed
                if (z < 1 || z > imp.getNSlices()) {
                    z = Math.max(1, Math.min(z, imp.getNSlices()));
                    statusLabel.setText("Z adjusted to valid range");
                }
                if (t < 1 || t > imp.getNFrames()) {
                    t = Math.max(1, Math.min(t, imp.getNFrames()));
                    statusLabel.setText("T adjusted to valid range");
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
                
                String note = noteField.getText().trim();
                Position pos = new Position(centerX, centerY, imp.getZ(), imp.getT(), note);
                
                // Check for duplicate position (same X, Y, Z, T)
                boolean isDuplicate = false;
                int duplicateRow = -1;
                for (int i = 0; i < positions.size(); i++) {
                    Position existingPos = positions.get(i);
                    if (existingPos != null && 
                        existingPos.x == pos.x && 
                        existingPos.y == pos.y && 
                        existingPos.z == pos.z && 
                        existingPos.t == pos.t) {
                        isDuplicate = true;
                        duplicateRow = i + 1; // Row number for display (1-based)
                        break;
                    }
                }
                
                // Show warning if duplicate found
                if (isDuplicate) {
                    int result = JOptionPane.showConfirmDialog(this,
                        String.format("Position %d,%d,%d,%d already exists at row %d.\nDo you still want to add it?", 
                                    pos.x, pos.y, pos.z, pos.t, duplicateRow),
                        "Duplicate Position Warning",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                    
                    if (result != JOptionPane.YES_OPTION) {
                        statusLabel.setText("Duplicate position not added");
                        return;
                    }
                }
                
                // Find first empty row or add at the end
                int insertRow = -1;
                for (int i = 0; i < positions.size(); i++) {
                    Position existingPos = positions.get(i);
                    if (existingPos == null) {
                        insertRow = i;
                        break;
                    }
                    // Also check if the position string is empty (another way a row can be empty)
                    String posStr = tableModel.getValueAt(i, 1).toString().trim();
                    if (posStr.isEmpty()) {
                        insertRow = i;
                        break;
                    }
                }
                
                if (insertRow >= 0) {
                    // Replace empty position
                    positions.set(insertRow, pos);
                    tableModel.setValueAt(pos.toString(), insertRow, 1);
                    tableModel.setValueAt(note, insertRow, 2);
                } else {
                    // Add new row at the end
                    positions.add(pos);
                    tableModel.addRow(new Object[]{tableModel.getRowCount() + 1, pos.toString(), note});
                }
                
                // Clear coordinate fields but NOT the note field
                xField.setText("");
                yField.setText("");
                zField.setText("");
                tField.setText("");
                // noteField.setText(""); // DON'T clear note field for rapid entry
                
                statusLabel.setText("Added position: " + pos.toString() + 
                    (note.isEmpty() ? "" : " [" + note + "]"));
            }
        }
        
        private void navigateToNextPosition() {
            if (positions.isEmpty()) {
                statusLabel.setText("No positions in list");
                return;
            }
            
            // Find next valid position
            int startIndex = currentPositionIndex;
            do {
                currentPositionIndex++;
                if (currentPositionIndex >= positions.size()) {
                    currentPositionIndex = 0;
                }
                
                Position pos = positions.get(currentPositionIndex);
                if (pos != null) {
                    positionTable.setRowSelectionInterval(currentPositionIndex, currentPositionIndex);
                    navigateToPosition(pos);
                    return;
                }
            } while (currentPositionIndex != startIndex);
            
            statusLabel.setText("No valid positions in list");
        }
        
        private void navigateToPreviousPosition() {
            if (positions.isEmpty()) {
                statusLabel.setText("No positions in list");
                return;
            }
            
            // Find previous valid position
            int startIndex = currentPositionIndex;
            do {
                currentPositionIndex--;
                if (currentPositionIndex < 0) {
                    currentPositionIndex = positions.size() - 1;
                }
                
                Position pos = positions.get(currentPositionIndex);
                if (pos != null) {
                    positionTable.setRowSelectionInterval(currentPositionIndex, currentPositionIndex);
                    navigateToPosition(pos);
                    return;
                }
            } while (currentPositionIndex != startIndex);
            
            statusLabel.setText("No valid positions in list");
        }
        
        private void navigateToPosition(Position pos) {
            if (pos == null) {
                statusLabel.setText("Empty position - skipping");
                return;
            }
            
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp == null) return;
            
            // Check if X,Y are out of bounds
            if (pos.x < 1 || pos.x > imp.getWidth() || pos.y < 1 || pos.y > imp.getHeight()) {
                JOptionPane.showMessageDialog(this,
                    "Position " + pos.toString() + " is out of image bounds!\n" +
                    "Image size: " + imp.getWidth() + " x " + imp.getHeight() + "\n" +
                    "Navigation cancelled.",
                    "Navigation Error", JOptionPane.WARNING_MESSAGE);
                statusLabel.setText("Position out of bounds - navigation cancelled");
                return;
            }
            
            // Adjust Z,T if out of bounds
            int adjustedZ = Math.max(1, Math.min(pos.z, imp.getNSlices()));
            int adjustedT = Math.max(1, Math.min(pos.t, imp.getNFrames()));
            
            navigateToPosition(imp, pos.x, pos.y, adjustedZ, adjustedT);
            
            if (adjustedZ != pos.z || adjustedT != pos.t) {
                statusLabel.setText("Moved to: " + pos.x + "," + pos.y + "," + adjustedZ + "," + adjustedT + " (Z/T adjusted)");
            } else {
                statusLabel.setText("Moved to: " + pos.toString() + 
                    (pos.note != null && !pos.note.isEmpty() ? " [" + pos.note + "]" : ""));
            }
        }
        
        private void parseBulkPositions(String text) {
            String[] lines = text.split("\n");
            int addedCount = 0;
            int errorCount = 0;
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    try {
                        // Split by comma, space, or tab (handle up to 5 parts for note)
                        String[] parts = line.split("[,\\s\\t]+", 5);
                        if (parts.length >= 4) {
                            int x = Integer.parseInt(parts[0].trim());
                            int y = Integer.parseInt(parts[1].trim());
                            int z = Integer.parseInt(parts[2].trim());
                            int t = Integer.parseInt(parts[3].trim());
                            
                            // Get note if present
                            String note = "";
                            if (parts.length >= 5) {
                                note = parts[4].trim();
                            }
                            
                            // Validate coordinates
                            ImagePlus imp = WindowManager.getCurrentImage();
                            if (imp != null) {
                                if (x < 1 || x > imp.getWidth() || y < 1 || y > imp.getHeight() ||
                                    z < 1 || z > imp.getNSlices() || t < 1 || t > imp.getNFrames()) {
                                    errorCount++;
                                    continue;
                                }
                            }
                            
                            Position pos = new Position(x, y, z, t, note);
                            
                            // Find first empty row or add at the end
                            int insertRow = -1;
                            for (int j = 0; j < positions.size(); j++) {
                                if (positions.get(j) == null) {
                                    insertRow = j;
                                    break;
                                }
                            }
                            
                            if (insertRow >= 0) {
                                // Replace empty position
                                positions.set(insertRow, pos);
                                tableModel.setValueAt(pos.toString(), insertRow, 1);
                                tableModel.setValueAt(note, insertRow, 2);
                            } else {
                                // Add new row at the end
                                positions.add(pos);
                                tableModel.addRow(new Object[]{tableModel.getRowCount() + 1, pos.toString(), note});
                            }
                            
                            addedCount++;
                        } else {
                            errorCount++;
                        }
                    } catch (NumberFormatException e) {
                        errorCount++;
                    }
                }
            }
            
            String message = "Added " + addedCount + " positions.";
            if (errorCount > 0) {
                message += " " + errorCount + " lines had errors and were skipped.";
            }
            statusLabel.setText(message);
        }
        
        private void updateFieldsFromSelectedRow() {
            int selectedRow = positionTable.getSelectedRow();
            if (selectedRow >= 0 && selectedRow < positions.size()) {
                currentPositionIndex = selectedRow;
                Position pos = positions.get(selectedRow);
                if (pos != null) {
                    xField.setText(String.valueOf(pos.x));
                    yField.setText(String.valueOf(pos.y));
                    zField.setText(String.valueOf(pos.z));
                    tField.setText(String.valueOf(pos.t));
                    noteField.setText(pos.note != null ? pos.note : "");
                } else {
                    // Clear fields for empty rows
                    xField.setText("");
                    yField.setText("");
                    zField.setText("");
                    tField.setText("");
                    noteField.setText("");
                }
            }
        }
        
        private void updatePositionsFromTable() {
            positions.clear();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                Object valueObj = tableModel.getValueAt(i, 1);
                if (valueObj == null) {
                    positions.add(null); // Add null for empty rows
                    continue;
                }
                
                String value = valueObj.toString().trim();
                if (value.isEmpty()) {
                    positions.add(null); // Add null for empty rows
                    continue;
                }
                
                try {
                    String[] parts = value.split(",");
                    if (parts.length == 4) {
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int z = Integer.parseInt(parts[2].trim());
                        int t = Integer.parseInt(parts[3].trim());
                        
                        // Get note from second column
                        Object noteObj = tableModel.getValueAt(i, 2);
                        String note = noteObj != null ? noteObj.toString() : "";
                        
                        positions.add(new Position(x, y, z, t, note));
                    } else {
                        positions.add(null); // Invalid format, treat as empty
                    }
                } catch (NumberFormatException e) {
                    positions.add(null); // Invalid number format, treat as empty
                }
            }
        }
        
        private void removeSelectedPosition() {
            int selectedRow = positionTable.getSelectedRow();
            if (selectedRow >= 0 && selectedRow < positions.size()) {
                positions.remove(selectedRow);
                tableModel.removeRow(selectedRow);
                
                // Update row numbers for all remaining rows
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    tableModel.setValueAt(i + 1, i, 0);
                }
                
                // Update current position index if needed
                if (currentPositionIndex >= positions.size()) {
                    currentPositionIndex = positions.size() - 1;
                }
                
                statusLabel.setText("Removed position at row " + (selectedRow + 1));
            } else {
                statusLabel.setText("Please select a position to remove");
            }
        }
        
        private void clearAllPositions() {
            // Check if there are any positions to clear
            if (positions.isEmpty() || positions.stream().allMatch(p -> p == null)) {
                statusLabel.setText("No positions to clear");
                return;
            }
            
            // Count non-null positions
            int validPositions = (int) positions.stream().filter(p -> p != null).count();
            
            // Show confirmation dialog
            int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to clear all " + validPositions + " positions?",
                "Clear All Positions",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            
            if (result == JOptionPane.YES_OPTION) {
                // Clear positions list
                positions.clear();
                
                // Clear table
                tableModel.setRowCount(0);
                
                // Add back empty rows
                for (int i = 0; i < 5; i++) {
                    tableModel.addRow(new Object[]{tableModel.getRowCount() + 1, "", ""});
                    positions.add(null);
                }
                
                // Reset current position index
                currentPositionIndex = -1;
                
                // Clear input fields
                xField.setText("");
                yField.setText("");
                zField.setText("");
                tField.setText("");
                // Keep note field as is for user convenience
                
                statusLabel.setText("Cleared all positions");
            } else {
                statusLabel.setText("Clear cancelled");
            }
        }
        
        
        private static class PositionWithRow {
            Position position;
            String note;
            int originalRow;
            
            PositionWithRow(Position position, String note, int originalRow) {
                this.position = position;
                this.note = note;
                this.originalRow = originalRow;
            }
        }
        
        private void exportPositions() {
            if (positions.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No positions to export", "Export", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Create file chooser with file filters
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setAcceptAllFileFilterUsed(false);
            
            // Add file filters for TXT and CSV
            javax.swing.filechooser.FileNameExtensionFilter txtFilter = 
                new javax.swing.filechooser.FileNameExtensionFilter("Text Files (*.txt)", "txt");
            javax.swing.filechooser.FileNameExtensionFilter csvFilter = 
                new javax.swing.filechooser.FileNameExtensionFilter("CSV Files (*.csv)", "csv");
            
            fileChooser.addChoosableFileFilter(txtFilter);
            fileChooser.addChoosableFileFilter(csvFilter);
            fileChooser.setFileFilter(txtFilter); // Set default to TXT
            fileChooser.setSelectedFile(new File("positions.txt"));
            
            // Add property change listener to update filename when filter changes
            fileChooser.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY, evt -> {
                File selectedFile = fileChooser.getSelectedFile();
                if (selectedFile == null) {
                    // If no file is selected, set a default
                    if (fileChooser.getFileFilter() == csvFilter) {
                        fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), "positions.csv"));
                    } else if (fileChooser.getFileFilter() == txtFilter) {
                        fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), "positions.txt"));
                    }
                    return;
                }
                
                String currentName = selectedFile.getName();
                if (fileChooser.getFileFilter() == csvFilter) {
                    if (currentName.endsWith(".txt")) {
                        currentName = currentName.substring(0, currentName.length() - 4) + ".csv";
                        fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), currentName));
                    } else if (!currentName.endsWith(".csv")) {
                        fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), "positions.csv"));
                    }
                } else if (fileChooser.getFileFilter() == txtFilter) {
                    if (currentName.endsWith(".csv")) {
                        currentName = currentName.substring(0, currentName.length() - 4) + ".txt";
                        fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), currentName));
                    } else if (!currentName.endsWith(".txt")) {
                        fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), "positions.txt"));
                    }
                }
            });
            
            int result = fileChooser.showSaveDialog(this);
            
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                String fileName = file.getName().toLowerCase();
                
                // Determine format based on selected filter or file extension
                boolean isCSV = false;
                if (fileChooser.getFileFilter() == csvFilter) {
                    isCSV = true;
                    // Add .csv extension if not present
                    if (!fileName.endsWith(".csv")) {
                        file = new File(file.getAbsolutePath() + ".csv");
                    }
                } else {
                    // Default to TXT format
                    if (!fileName.endsWith(".txt") && !fileName.endsWith(".csv")) {
                        file = new File(file.getAbsolutePath() + ".txt");
                    } else if (fileName.endsWith(".csv")) {
                        isCSV = true;
                    }
                }
                
                // Check if file exists and auto-rename if necessary
                if (file.exists()) {
                    String baseName = file.getName();
                    String extension = "";
                    int dotIndex = baseName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        extension = baseName.substring(dotIndex);
                        baseName = baseName.substring(0, dotIndex);
                    }
                    
                    File parentDir = file.getParentFile();
                    int counter = 2;
                    while (file.exists()) {
                        file = new File(parentDir, baseName + " (" + counter + ")" + extension);
                        counter++;
                    }
                }
                
                try (FileWriter writer = new FileWriter(file)) {
                    int exportCount = 0;
                    
                    if (isCSV) {
                        // CSV format with headers including Note column
                        writer.write("X,Y,Z,T,Note\n");
                        for (int i = 0; i < positions.size(); i++) {
                            Position pos = positions.get(i);
                            if (pos != null) {
                                Object noteObj = tableModel.getValueAt(i, 2);
                                String note = (noteObj != null ? noteObj.toString() : "").replace(",", ";"); // Escape commas in notes
                                writer.write(String.format("%d,%d,%d,%d,%s\n", 
                                    pos.x, pos.y, pos.z, pos.t, note));
                                exportCount++;
                            }
                        }
                    } else {
                        // TXT format (original format - no notes)
                        for (Position pos : positions) {
                            if (pos != null) {
                                writer.write(pos.toString() + "\n");
                                exportCount++;
                            }
                        }
                    }
                    
                    JOptionPane.showMessageDialog(this, 
                        String.format("Exported %d positions to %s (%s format)", 
                            exportCount, file.getName(), isCSV ? "CSV" : "TXT"),
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
            // Create file chooser with file filters
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setAcceptAllFileFilterUsed(false);
            
            // Add file filters for TXT and CSV
            javax.swing.filechooser.FileNameExtensionFilter txtFilter = 
                new javax.swing.filechooser.FileNameExtensionFilter("Text Files (*.txt)", "txt");
            javax.swing.filechooser.FileNameExtensionFilter csvFilter = 
                new javax.swing.filechooser.FileNameExtensionFilter("CSV Files (*.csv)", "csv");
            javax.swing.filechooser.FileNameExtensionFilter allFilter = 
                new javax.swing.filechooser.FileNameExtensionFilter("All Position Files (*.txt, *.csv)", "txt", "csv");
            
            fileChooser.addChoosableFileFilter(allFilter);
            fileChooser.addChoosableFileFilter(txtFilter);
            fileChooser.addChoosableFileFilter(csvFilter);
            fileChooser.setFileFilter(allFilter); // Set default to show all
            fileChooser.setSelectedFile(new File("positions.txt"));
            
            int result = fileChooser.showOpenDialog(this);
            
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                String fileName = file.getName().toLowerCase();
                boolean isCSV = fileName.endsWith(".csv");
                
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    // Clear existing positions
                    positions.clear();
                    tableModel.setRowCount(0);
                    
                    String line;
                    int lineNumber = 0;
                    boolean skipFirstLine = false;
                    int loadedCount = 0;
                    
                    // Check if first line is a header (for CSV files)
                    if (isCSV) {
                        line = reader.readLine();
                        lineNumber++;
                        if (line != null && (line.trim().equalsIgnoreCase("X,Y,Z,T") || 
                                            line.trim().equalsIgnoreCase("X,Y,Z,T,Note"))) {
                            skipFirstLine = true; // Skip header
                        } else {
                            // Not a header, process this line
                            skipFirstLine = false;
                            // Reset reader to beginning if possible
                            reader.close();
                            BufferedReader newReader = new BufferedReader(new FileReader(file));
                            processPositionLine(newReader.readLine(), lineNumber, isCSV);
                            loadedCount++;
                            
                            // Continue with new reader
                            while ((line = newReader.readLine()) != null) {
                                lineNumber++;
                                if (processPositionLine(line, lineNumber, isCSV)) {
                                    loadedCount++;
                                }
                            }
                            newReader.close();
                        }
                    }
                    
                    // If header was skipped or not CSV, continue reading
                    if (!isCSV || skipFirstLine) {
                        while ((line = reader.readLine()) != null) {
                            lineNumber++;
                            if (processPositionLine(line, lineNumber, isCSV)) {
                                loadedCount++;
                            }
                        }
                    }
                    
                    // Add empty rows to maintain minimum row count
                    while (tableModel.getRowCount() < 5) {
                        tableModel.addRow(new Object[]{tableModel.getRowCount() + 1, "", ""});
                    }
                    
                    JOptionPane.showMessageDialog(this, 
                        String.format("Loaded %d positions from %s (%s format)", 
                            loadedCount, file.getName(), isCSV ? "CSV" : "TXT"),
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
        
        private boolean processPositionLine(String line, int lineNumber, boolean isCSV) {
            if (line == null) return false;
            line = line.trim();
            if (line.isEmpty()) return false;
            
            try {
                String[] parts;
                if (isCSV) {
                    // For CSV, split only by comma (handle up to 5 fields for Note)
                    parts = line.split(",", 5);
                } else {
                    // For TXT, split by comma, space, or tab (handle up to 5 fields)
                    parts = line.split("[,\\s\\t]+", 5);
                }
                
                // Check if we have at least X,Y values
                if (parts.length < 2) {
                    return false; // Not enough data
                }
                
                // Try to parse X and Y (required)
                Integer x = null, y = null;
                try {
                    x = Integer.parseInt(parts[0].trim());
                    y = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    // Missing or invalid X,Y - skip this position with warning
                    JOptionPane.showMessageDialog(this,
                        "Line " + lineNumber + ": Missing or invalid X,Y values - position skipped",
                        "Import Warning", JOptionPane.WARNING_MESSAGE);
                    return false;
                }
                
                // Parse Z (default to 1 if missing or invalid)
                int z = 1;
                boolean zWarning = false;
                if (parts.length >= 3) {
                    try {
                        z = Integer.parseInt(parts[2].trim());
                        // Check if Z is valid for current image
                        ImagePlus imp = WindowManager.getCurrentImage();
                        if (imp != null && (z < 1 || z > imp.getNSlices())) {
                            z = 1;
                            zWarning = true;
                        }
                    } catch (NumberFormatException e) {
                        zWarning = true;
                    }
                } else {
                    zWarning = true;
                }
                
                // Parse T (default to 1 if missing or invalid)
                int t = 1;
                boolean tWarning = false;
                if (parts.length >= 4) {
                    try {
                        t = Integer.parseInt(parts[3].trim());
                        // Check if T is valid for current image
                        ImagePlus imp = WindowManager.getCurrentImage();
                        if (imp != null && (t < 1 || t > imp.getNFrames())) {
                            t = 1;
                            tWarning = true;
                        }
                    } catch (NumberFormatException e) {
                        tWarning = true;
                    }
                } else {
                    tWarning = true;
                }
                
                // Show warnings for Z,T if needed
                if (zWarning || tWarning) {
                    String warningMsg = "Line " + lineNumber + ": ";
                    if (zWarning && tWarning) {
                        warningMsg += "Invalid or missing Z,T values - set to 1,1";
                    } else if (zWarning) {
                        warningMsg += "Invalid or missing Z value - set to 1";
                    } else {
                        warningMsg += "Invalid or missing T value - set to 1";
                    }
                    JOptionPane.showMessageDialog(this, warningMsg, "Import Warning", JOptionPane.WARNING_MESSAGE);
                }
                
                // Check if X,Y are out of bounds (but still import them)
                ImagePlus imp = WindowManager.getCurrentImage();
                if (imp != null && (x < 1 || x > imp.getWidth() || y < 1 || y > imp.getHeight())) {
                    JOptionPane.showMessageDialog(this,
                        "Line " + lineNumber + ": X,Y coordinates (" + x + "," + y + 
                        ") are out of image bounds - imported anyway",
                        "Import Warning", JOptionPane.WARNING_MESSAGE);
                }
                
                // Parse note if present
                String note = "";
                if (isCSV && parts.length >= 5) {
                    note = parts[4].trim();
                } else if (!isCSV && parts.length >= 5) {
                    // For TXT files, anything after the 4th field is considered note
                    note = parts[4].trim();
                }
                
                Position pos = new Position(x, y, z, t, note);
                positions.add(pos);
                tableModel.addRow(new Object[]{tableModel.getRowCount() + 1, pos.toString(), note});
                return true;
                
            } catch (Exception e) {
                // Unexpected error - skip line
                return false;
            }
        }
        
        private void openSnapshotDialog() {
            // Check if there are positions to snapshot
            if (positions.isEmpty() || positions.stream().allMatch(p -> p == null)) {
                JOptionPane.showMessageDialog(this, 
                    "No positions to snapshot. Please add positions first.",
                    "No Positions", 
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Check if an image is open
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp == null) {
                JOptionPane.showMessageDialog(this,
                    "No image open. Please open an image first.",
                    "No Image",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Open the snapshot dialog
            SnapshotDialog dialog = new SnapshotDialog(this, imp);
            dialog.setVisible(true);
        }
        
        // Inner class for Snapshot Dialog
        private class SnapshotDialog extends JDialog {
            private JCheckBox includeCrossCheckBox;
            private JCheckBox horizontalReverseCheckBox;
            private JCheckBox annotationTextCheckBox;
            private JTextField widthField, heightField;
            private JCheckBox[] channelCheckBoxes;
            private ImagePlus targetImage;
            private JProgressBar progressBar;
            private JLabel progressLabel;
            
            public SnapshotDialog(JFrame parent, ImagePlus imp) {
                super(parent, "Snapshot Settings", true);
                this.targetImage = imp;
                initializeUI();
                pack();
                setLocationRelativeTo(parent);
            }
            
            private void initializeUI() {
                setLayout(new BorderLayout());
                
                JPanel mainPanel = new JPanel();
                mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
                mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                
                // Include cross, horizontal reverse, and annotation options
                JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                includeCrossCheckBox = new JCheckBox("Include center cross");
                includeCrossCheckBox.setSelected(false);
                horizontalReverseCheckBox = new JCheckBox("Horizontal reverse");
                horizontalReverseCheckBox.setSelected(false);
                annotationTextCheckBox = new JCheckBox("Annotation text");
                annotationTextCheckBox.setSelected(false);
                optionsPanel.add(includeCrossCheckBox);
                optionsPanel.add(Box.createHorizontalStrut(15));
                optionsPanel.add(horizontalReverseCheckBox);
                optionsPanel.add(Box.createHorizontalStrut(15));
                optionsPanel.add(annotationTextCheckBox);
                mainPanel.add(optionsPanel);
                
                // Snapshot area settings
                JPanel areaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                areaPanel.setBorder(BorderFactory.createTitledBorder("Snapshot Area"));
                areaPanel.add(new JLabel("Width:"));
                widthField = new JTextField("200", 5);
                areaPanel.add(widthField);
                areaPanel.add(new JLabel("pixels"));
                areaPanel.add(Box.createHorizontalStrut(10));
                areaPanel.add(new JLabel("Height:"));
                heightField = new JTextField("200", 5);
                areaPanel.add(heightField);
                areaPanel.add(new JLabel("pixels"));
                mainPanel.add(areaPanel);
                
                // Channel selection
                JPanel channelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                channelPanel.setBorder(BorderFactory.createTitledBorder("Channels to Include"));
                
                int nChannels = targetImage.getNChannels();
                channelCheckBoxes = new JCheckBox[Math.min(nChannels, 4)];
                
                for (int i = 0; i < channelCheckBoxes.length; i++) {
                    channelCheckBoxes[i] = new JCheckBox("Ch" + (i + 1));
                    channelCheckBoxes[i].setSelected(true); // Default all channels on
                    channelPanel.add(channelCheckBoxes[i]);
                }
                
                mainPanel.add(channelPanel);
                
                // Progress panel (initially hidden)
                JPanel progressPanel = new JPanel(new BorderLayout());
                progressPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
                progressBar = new JProgressBar(0, 100);
                progressBar.setStringPainted(true);
                progressLabel = new JLabel("Ready to snapshot");
                progressPanel.add(progressLabel, BorderLayout.NORTH);
                progressPanel.add(progressBar, BorderLayout.CENTER);
                progressPanel.setVisible(false);
                mainPanel.add(progressPanel);
                
                add(mainPanel, BorderLayout.CENTER);
                
                // Button panel
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                JButton snapshotButton = new JButton("Take Snapshots");
                JButton cancelButton = new JButton("Cancel");
                
                snapshotButton.addActionListener(e -> takeSnapshots(progressPanel));
                cancelButton.addActionListener(e -> dispose());
                
                buttonPanel.add(snapshotButton);
                buttonPanel.add(cancelButton);
                add(buttonPanel, BorderLayout.SOUTH);
            }
            
            private void takeSnapshots(JPanel progressPanel) {
                // Validate input
                int width, height;
                try {
                    width = Integer.parseInt(widthField.getText().trim());
                    height = Integer.parseInt(heightField.getText().trim());
                    if (width <= 0 || height <= 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this,
                        "Please enter valid positive integers for width and height.",
                        "Invalid Input",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                // Check if at least one channel is selected
                boolean hasChannel = false;
                for (JCheckBox cb : channelCheckBoxes) {
                    if (cb.isSelected()) {
                        hasChannel = true;
                        break;
                    }
                }
                
                if (!hasChannel) {
                    JOptionPane.showMessageDialog(this,
                        "Please select at least one channel.",
                        "No Channel Selected",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                // Ask for save location
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Select Directory to Save Snapshots");
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                
                if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                
                File saveDir = fileChooser.getSelectedFile();
                
                // Show progress
                progressPanel.setVisible(true);
                progressBar.setValue(0);
                
                // Process snapshots in background
                SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        List<Position> validPositions = new ArrayList<>();
                        for (Position pos : positions) {
                            if (pos != null) {
                                validPositions.add(pos);
                            }
                        }
                        
                        int total = validPositions.size();
                        for (int i = 0; i < total; i++) {
                            Position pos = validPositions.get(i);
                            
                            // Take snapshot at this position
                            captureSnapshot(pos, width, height, includeCrossCheckBox.isSelected(), 
                                          horizontalReverseCheckBox.isSelected(), annotationTextCheckBox.isSelected(),
                                          channelCheckBoxes, saveDir, i + 1);
                            
                            publish((int)((i + 1) * 100.0 / total));
                        }
                        
                        // Also export the CSV file
                        exportPositionsToCSV(saveDir);
                        
                        return null;
                    }
                    
                    @Override
                    protected void process(List<Integer> chunks) {
                        for (Integer progress : chunks) {
                            progressBar.setValue(progress);
                            progressLabel.setText(String.format("Processing... %d%%", progress));
                        }
                    }
                    
                    @Override
                    protected void done() {
                        try {
                            get(); // Check for exceptions
                            JOptionPane.showMessageDialog(SnapshotDialog.this,
                                "Snapshots saved successfully!",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                            dispose();
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(SnapshotDialog.this,
                                "Error during snapshot: " + ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
                
                worker.execute();
            }
            
            private void captureSnapshot(Position pos, int width, int height, boolean includeCross,
                                        boolean horizontalReverse, boolean includeAnnotation, 
                                        JCheckBox[] channels, File saveDir, int index) {
                // Navigate to the position
                targetImage.setZ(pos.z);
                targetImage.setT(pos.t);
                
                // Calculate ROI bounds
                int x = pos.x - width / 2;
                int y = pos.y - height / 2;
                
                // Ensure bounds are within image
                x = Math.max(0, Math.min(x, targetImage.getWidth() - width));
                y = Math.max(0, Math.min(y, targetImage.getHeight() - height));
                
                // Set ROI
                Roi roi = new Roi(x, y, width, height);
                targetImage.setRoi(roi);
                
                // Crop and duplicate
                ImagePlus cropped = targetImage.crop("snapshot");
                
                // Process channels if multi-channel
                if (targetImage.getNChannels() > 1) {
                    // Create composite image with selected channels
                    ImagePlus mergedImage = mergeSelectedChannels(cropped, channels);
                    if (mergedImage != null) {
                        cropped = mergedImage;
                    }
                }
                
                // Create overlay for cross and/or annotation
                Overlay overlay = null;
                boolean needsFlattening = false;
                
                // Add cross if requested
                if (includeCross) {
                    if (overlay == null) overlay = new Overlay();
                    int centerX = width / 2;
                    int centerY = height / 2;
                    
                    Line hLine = new Line(centerX - 5, centerY, centerX + 5, centerY);
                    hLine.setStrokeColor(Color.GREEN);
                    hLine.setStrokeWidth(1);
                    overlay.add(hLine);
                    
                    Line vLine = new Line(centerX, centerY - 5, centerX, centerY + 5);
                    vLine.setStrokeColor(Color.GREEN);
                    vLine.setStrokeWidth(1);
                    overlay.add(vLine);
                    needsFlattening = true;
                }
                
                // Add annotation text if requested
                if (includeAnnotation) {
                    if (overlay == null) overlay = new Overlay();
                    
                    // Prepare annotation text: position number and note
                    String annotationText = String.format("#%d", index);
                    if (pos.note != null && !pos.note.trim().isEmpty()) {
                        annotationText += ": " + pos.note;
                    }
                    
                    // Create text ROI in top-left corner with some padding
                    Font font = new Font("Arial", Font.BOLD, 12);
                    TextRoi textRoi = new TextRoi(5, 5, annotationText, font);
                    textRoi.setStrokeColor(Color.YELLOW);
                    textRoi.setFillColor(null);
                    textRoi.setAntialiased(true);
                    overlay.add(textRoi);
                    needsFlattening = true;
                }
                
                // Apply horizontal flip first if requested so text remains readable (not mirrored)
                if (horizontalReverse) {
                    ImageProcessor ip = cropped.getProcessor();
                    ip.flipHorizontal();
                    cropped.setProcessor(ip);
                }

                // Now apply overlay (text/cross) and flatten onto the already-reversed image
                if (overlay != null && needsFlattening) {
                    cropped.setOverlay(overlay);
                    cropped = cropped.flatten();
                }
                
                // Save as PNG with appropriate filename
                String filename;
                if (horizontalReverse) {
                    filename = String.format("Position_reverse_%03d.png", index);
                } else {
                    filename = String.format("Position_%03d.png", index);
                }
                File outputFile = new File(saveDir, filename);
                IJ.save(cropped, outputFile.getAbsolutePath());
                
                // Clear ROI
                targetImage.deleteRoi();
            }
            
            private ImagePlus mergeSelectedChannels(ImagePlus imp, JCheckBox[] channelBoxes) {
                if (imp.getNChannels() == 1) {
                    return imp;
                }
                
                // Split channels
                ImagePlus[] channels = ChannelSplitter.split(imp);
                ImagePlus[] selectedChannels = new ImagePlus[channels.length];
                
                // Keep only selected channels
                for (int i = 0; i < channelBoxes.length && i < channels.length; i++) {
                    if (channelBoxes[i].isSelected()) {
                        selectedChannels[i] = channels[i];
                    }
                }
                
                // Merge selected channels
                if (imp instanceof CompositeImage) {
                    CompositeImage comp = (CompositeImage) imp;
                    ImagePlus merged = RGBStackMerge.mergeChannels(selectedChannels, false);
                    
                    // Apply original LUTs and display settings
                    if (merged instanceof CompositeImage) {
                        CompositeImage mergedComp = (CompositeImage) merged;
                        for (int i = 0; i < channelBoxes.length && i < channels.length; i++) {
                            if (channelBoxes[i].isSelected() && selectedChannels[i] != null) {
                                mergedComp.setChannelLut(comp.getChannelLut(i + 1), i + 1);
                                mergedComp.setDisplayRange(comp.getDisplayRangeMin(), 
                                                         comp.getDisplayRangeMax(), i + 1);
                            }
                        }
                        mergedComp.setMode(CompositeImage.COMPOSITE);
                        return mergedComp.flatten();
                    }
                }
                
                return RGBStackMerge.mergeChannels(selectedChannels, true);
            }
            
            private void exportPositionsToCSV(File saveDir) {
                File csvFile = new File(saveDir, "positions.csv");
                try (FileWriter writer = new FileWriter(csvFile)) {
                    writer.write("X,Y,Z,T,Note\n");
                    for (Position pos : positions) {
                        if (pos != null) {
                            writer.write(String.format("%d,%d,%d,%d,%s\n",
                                pos.x, pos.y, pos.z, pos.t,
                                pos.note != null ? pos.note : ""));
                        }
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this,
                        "Failed to export CSV: " + ex.getMessage(),
                        "Export Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        @Override
        public void dispose() {
            if (updateTimer != null) {
                updateTimer.cancel();
            }
            
            // Remove mouse listeners from image window
            if (lastImageWindow != null && lastImageWindow.getCanvas() != null) {
                if (crosshairMouseListener != null) {
                    lastImageWindow.getCanvas().removeMouseMotionListener(crosshairMouseListener);
                }
                if (crosshairWheelListener != null) {
                    lastImageWindow.getCanvas().removeMouseWheelListener(crosshairWheelListener);
                }
            }
            
            // Remove crosshair overlay when closing
            ImagePlus imp = WindowManager.getCurrentImage();
            if (imp != null && crosshairOverlay != null) {
                imp.setOverlay(null);
            }
            super.dispose();
        }
    }
}