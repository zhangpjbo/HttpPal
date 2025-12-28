package com.httppal.util

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.LineBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Helper class for providing visual feedback in UI components
 * Implements requirements 3.1, 3.3: Provide immediate visual feedback after user interaction
 */
object VisualFeedbackHelper {
    
    // Color constants for visual feedback
    private val SELECTION_COLOR = JBColor(Color(173, 216, 230), Color(70, 130, 180))
    private val HOVER_COLOR = JBColor(Color(240, 248, 255), Color(60, 60, 60))
    private val CLICK_COLOR = JBColor(Color(135, 206, 250), Color(100, 149, 237))
    private val SUCCESS_COLOR = JBColor(Color(144, 238, 144), Color(34, 139, 34))
    private val ERROR_COLOR = JBColor(Color(255, 182, 193), Color(178, 34, 34))
    
    /**
     * Add hover effect to a component
     * Implements requirement 3.1: Provide visual feedback within 100ms
     */
    fun addHoverEffect(component: JComponent, hoverColor: Color = HOVER_COLOR) {
        val originalBackground = component.background
        val originalBorder = component.border
        
        component.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                component.background = hoverColor
                component.border = LineBorder(SELECTION_COLOR, 1)
                component.repaint()
            }
            
            override fun mouseExited(e: MouseEvent) {
                component.background = originalBackground
                component.border = originalBorder
                component.repaint()
            }
        })
    }
    
    /**
     * Add click effect to a button
     * Implements requirement 3.3: Show button click effects
     */
    fun addClickEffect(button: AbstractButton) {
        val originalBackground = button.background
        
        button.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                button.background = CLICK_COLOR
                button.repaint()
            }
            
            override fun mouseReleased(e: MouseEvent) {
                // Delay restoration to make effect visible
                Timer(100) {
                    button.background = originalBackground
                    button.repaint()
                }.apply {
                    isRepeats = false
                    start()
                }
            }
        })
    }
    
    /**
     * Highlight selected endpoint in tree
     * Implements requirement 3.3: Highlight selected endpoint
     */
    fun highlightTreeSelection(tree: JTree, path: TreePath) {
        // Ensure selection is visible
        tree.selectionPath = path
        tree.scrollPathToVisible(path)
        
        // Add temporary highlight effect
        val node = path.lastPathComponent as? DefaultMutableTreeNode
        if (node != null) {
            // Flash the selection
            flashComponent(tree, SELECTION_COLOR, 200)
        }
    }
    
    /**
     * Flash a component with a color
     * Implements requirement 3.1: Immediate visual feedback
     */
    fun flashComponent(component: JComponent, flashColor: Color, durationMs: Int = 200) {
        val originalBackground = component.background
        
        AsyncUIHelper.invokeLater {
            component.background = flashColor
            component.repaint()
            
            Timer(durationMs) {
                component.background = originalBackground
                component.repaint()
            }.apply {
                isRepeats = false
                start()
            }
        }
    }
    
    /**
     * Show success feedback on a component
     * Implements requirement 3.1: Provide visual feedback within 100ms
     */
    fun showSuccessFeedback(component: JComponent, message: String? = null) {
        flashComponent(component, SUCCESS_COLOR, 300)
        
        if (message != null && component is JLabel) {
            val originalText = component.text
            component.text = message
            component.foreground = SUCCESS_COLOR.darker()
            
            Timer(2000) {
                component.text = originalText
                component.foreground = Color.BLACK
            }.apply {
                isRepeats = false
                start()
            }
        }
    }
    
    /**
     * Show error feedback on a component
     * Implements requirement 3.1: Provide visual feedback within 100ms
     */
    fun showErrorFeedback(component: JComponent, message: String? = null) {
        flashComponent(component, ERROR_COLOR, 300)
        
        if (message != null && component is JLabel) {
            val originalText = component.text
            component.text = message
            component.foreground = ERROR_COLOR.darker()
            
            Timer(3000) {
                component.text = originalText
                component.foreground = Color.BLACK
            }.apply {
                isRepeats = false
                start()
            }
        }
    }
    
    /**
     * Add ripple effect to a component (simplified version)
     * Implements requirement 3.1: Immediate visual feedback
     */
    fun addRippleEffect(component: JComponent) {
        component.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                // Create a simple ripple effect by changing opacity
                val originalOpacity = (component as? JComponent)?.let { 
                    it.getClientProperty("opacity") as? Float ?: 1.0f 
                } ?: 1.0f
                
                // Animate opacity change
                var opacity = 0.7f
                val timer = Timer(50) { _ ->
                    opacity += 0.1f
                    if (opacity >= originalOpacity) {
                        opacity = originalOpacity
                        (this as Timer).stop()
                    }
                    component.putClientProperty("opacity", opacity)
                    component.repaint()
                }
                timer.start()
            }
        })
    }
    
    /**
     * Highlight text field on focus
     * Implements requirement 3.1: Immediate visual feedback
     */
    fun highlightOnFocus(textField: JTextField) {
        val originalBorder = textField.border
        
        textField.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent) {
                textField.border = LineBorder(SELECTION_COLOR, 2)
                textField.selectAll()
            }
            
            override fun focusLost(e: java.awt.event.FocusEvent) {
                textField.border = originalBorder
            }
        })
    }
    
    /**
     * Add loading animation to a label
     * Implements requirement 3.4: Show progress indicators
     */
    fun addLoadingAnimation(label: JLabel): Timer {
        val dots = arrayOf("", ".", "..", "...")
        var index = 0
        val originalText = label.text
        
        val timer = Timer(500) {
            label.text = "$originalText${dots[index]}"
            index = (index + 1) % dots.size
        }
        timer.start()
        
        return timer
    }
    
    /**
     * Stop loading animation
     */
    fun stopLoadingAnimation(timer: Timer, label: JLabel, finalText: String) {
        timer.stop()
        label.text = finalText
    }
    
    /**
     * Add pulsing effect to a component
     * Implements requirement 3.1: Immediate visual feedback
     */
    fun addPulsingEffect(component: JComponent, color: Color = SELECTION_COLOR): Timer {
        val originalBackground = component.background
        var increasing = false
        var alpha = 0
        
        val timer = Timer(50) {
            if (increasing) {
                alpha += 15
                if (alpha >= 255) {
                    alpha = 255
                    increasing = false
                }
            } else {
                alpha -= 15
                if (alpha <= 0) {
                    alpha = 0
                    increasing = true
                }
            }
            
            val pulseColor = Color(color.red, color.green, color.blue, alpha)
            component.background = pulseColor
            component.repaint()
        }
        timer.start()
        
        return timer
    }
    
    /**
     * Stop pulsing effect
     */
    fun stopPulsingEffect(timer: Timer, component: JComponent, restoreBackground: Color) {
        timer.stop()
        component.background = restoreBackground
        component.repaint()
    }
    
    /**
     * Add selection highlight to list item
     * Implements requirement 3.3: Highlight selected items
     */
    fun highlightListSelection(list: JList<*>, index: Int) {
        if (index >= 0 && index < list.model.size) {
            list.selectedIndex = index
            list.ensureIndexIsVisible(index)
            
            // Flash the list to draw attention
            flashComponent(list, SELECTION_COLOR, 150)
        }
    }
    
    /**
     * Add tooltip with delay for better UX
     * Implements requirement 3.1: Provide helpful feedback
     */
    fun addDelayedTooltip(component: JComponent, tooltipText: String, delayMs: Int = 500) {
        component.toolTipText = tooltipText
        ToolTipManager.sharedInstance().initialDelay = delayMs
        ToolTipManager.sharedInstance().dismissDelay = 5000
    }
    
    /**
     * Show temporary status message
     * Implements requirement 3.1: Immediate visual feedback
     */
    fun showTemporaryStatus(statusLabel: JLabel, message: String, durationMs: Int = 3000, color: Color = Color.BLACK) {
        val originalText = statusLabel.text
        val originalColor = statusLabel.foreground
        
        AsyncUIHelper.invokeLater {
            statusLabel.text = message
            statusLabel.foreground = color
            
            Timer(durationMs) {
                statusLabel.text = originalText
                statusLabel.foreground = originalColor
            }.apply {
                isRepeats = false
                start()
            }
        }
    }
    
    /**
     * Add smooth scroll animation
     * Implements requirement 3.1: Smooth user interactions
     */
    fun smoothScrollTo(scrollPane: JScrollPane, targetY: Int, durationMs: Int = 300) {
        val viewport = scrollPane.viewport
        val startY = viewport.viewPosition.y
        val distance = targetY - startY
        val startTime = System.currentTimeMillis()
        
        val timer = Timer(16) { // ~60 FPS
            val elapsed = System.currentTimeMillis() - startTime
            val progress = Math.min(1.0, elapsed.toDouble() / durationMs)
            
            // Ease-out cubic function for smooth animation
            val easeProgress = 1 - Math.pow(1 - progress, 3.0)
            val currentY = startY + (distance * easeProgress).toInt()
            
            viewport.viewPosition = java.awt.Point(viewport.viewPosition.x, currentY)
            
            if (progress >= 1.0) {
                (this as Timer).stop()
            }
        }
        timer.start()
    }
}
