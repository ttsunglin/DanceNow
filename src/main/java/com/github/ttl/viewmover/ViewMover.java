package com.github.ttl.viewmover;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.gui.ImageWindow;
import ij.gui.ImageCanvas;

import java.awt.Rectangle;

/**
 * ViewMover plugin for Fiji/ImageJ
 * Allows quick navigation to specific X,Y,Z,T coordinates while preserving zoom level
 */
public class ViewMover implements PlugIn {
    
    @Override
    public void run(String arg) {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) {
            IJ.error("ViewMover", "No image is open.");
            return;
        }
        
        showNavigationDialog(imp);
    }
    
    private void showNavigationDialog(ImagePlus imp) {
        // Get current position and dimensions
        int currentX = imp.getWidth() / 2;
        int currentY = imp.getHeight() / 2;
        int currentZ = imp.getZ();
        int currentT = imp.getT();
        
        // Get image dimensions
        int maxX = imp.getWidth();
        int maxY = imp.getHeight();
        int maxZ = imp.getNSlices();
        int maxT = imp.getNFrames();
        
        // Create dialog
        GenericDialog gd = new GenericDialog("Navigate to Position");
        gd.addMessage("Current image: " + imp.getTitle());
        gd.addMessage("Image dimensions: " + maxX + "x" + maxY + "x" + maxZ + "x" + maxT + " (X,Y,Z,T)");
        
        gd.addNumericField("X coordinate (1-" + maxX + "):", currentX, 0);
        gd.addNumericField("Y coordinate (1-" + maxY + "):", currentY, 0);
        gd.addNumericField("Z slice (1-" + maxZ + "):", currentZ, 0);
        gd.addNumericField("T frame (1-" + maxT + "):", currentT, 0);
        
        gd.showDialog();
        
        if (gd.wasCanceled()) {
            return;
        }
        
        // Get values from dialog
        int newX = (int) gd.getNextNumber();
        int newY = (int) gd.getNextNumber();
        int newZ = (int) gd.getNextNumber();
        int newT = (int) gd.getNextNumber();
        
        // Validate coordinates
        if (newX < 1 || newX > maxX || newY < 1 || newY > maxY ||
            newZ < 1 || newZ > maxZ || newT < 1 || newT > maxT) {
            IJ.error("ViewMover", "Coordinates are out of bounds!");
            return;
        }
        
        navigateToPosition(imp, newX, newY, newZ, newT);
    }
    
    private void navigateToPosition(ImagePlus imp, int x, int y, int z, int t) {
        ImageWindow win = imp.getWindow();
        if (win == null) {
            return;
        }
        
        ImageCanvas canvas = win.getCanvas();
        if (canvas == null) {
            return;
        }
        
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
        // while maintaining the current zoom level
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
        
        IJ.showStatus("Moved to position: X=" + x + ", Y=" + y + ", Z=" + z + ", T=" + t);
    }
}