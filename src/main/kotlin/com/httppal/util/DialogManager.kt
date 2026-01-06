package com.httppal.util

import java.awt.Component
import javax.swing.JOptionPane
import javax.swing.UIManager

/**
 * Unified dialog manager for HttpPal plugin
 * 
 * Provides centralized dialog creation with internationalization support.
 * All dialogs use HttpPalBundle for internationalization and are thread-safe.
 * 
 * ## Features
 * - Automatic internationalization using HttpPalBundle
 * - Thread-safe execution (automatically switches to EDT if needed)
 * - Consistent API across all dialog types
 * - Support for custom components and options
 * 
 * ## Usage Examples
 * 
 * ### Information Dialog
 * ```kotlin
 * DialogManager.showInfo(
 *     message = "Operation completed successfully",
 *     titleKey = "dialog.title.info",
 *     parent = this
 * )
 * ```
 * 
 * ### Confirmation Dialog
 * ```kotlin
 * val confirmed = DialogManager.showConfirm(
 *     message = "Are you sure you want to delete this item?",
 *     titleKey = "dialog.title.confirm",
 *     parent = this
 * )
 * if (confirmed) {
 *     // Perform deletion
 * }
 * ```
 * 
 * ### Input Dialog
 * ```kotlin
 * val name = DialogManager.showInput(
 *     message = "Enter your name:",
 *     titleKey = "dialog.title.input",
 *     initialValue = "John Doe",
 *     parent = this
 * )
 * if (name != null) {
 *     // Use the input
 * }
 * ```
 * 
 * ### Custom Component Dialog
 * ```kotlin
 * val panel = JPanel()
 * // Add components to panel...
 * val result = DialogManager.showCustom(
 *     component = panel,
 *     title = "Custom Dialog",
 *     optionType = JOptionPane.OK_CANCEL_OPTION,
 *     parent = this
 * )
 * ```
 * 
 * ## Migration from JOptionPane
 * 
 * ### Before (JOptionPane)
 * ```kotlin
 * JOptionPane.showMessageDialog(
 *     parent,
 *     "Message",
 *     "Title",
 *     JOptionPane.INFORMATION_MESSAGE
 * )
 * ```
 * 
 * ### After (DialogManager)
 * ```kotlin
 * DialogManager.showInfo(
 *     message = "Message",
 *     title = "Title",
 *     parent = parent
 * )
 * ```
 * 
 * @see HttpPalBundle
 */
object DialogManager {
    
    /**
     * Result of a Yes/No/Cancel dialog
     */
    enum class DialogResult {
        YES,
        NO,
        CANCEL,
        CLOSED
    }
    
    /**
     * Configuration for dialog display
     */
    data class DialogConfig(
        val message: String,
        val title: String,
        val messageType: Int,
        val optionType: Int,
        val parent: Component?,
        val options: Array<String>? = null,
        val initialValue: Any? = null,
        val customComponent: Any? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as DialogConfig
            
            if (message != other.message) return false
            if (title != other.title) return false
            if (messageType != other.messageType) return false
            if (optionType != other.optionType) return false
            if (parent != other.parent) return false
            if (options != null) {
                if (other.options == null) return false
                if (!options.contentEquals(other.options)) return false
            } else if (other.options != null) return false
            if (initialValue != other.initialValue) return false
            if (customComponent != other.customComponent) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = message.hashCode()
            result = 31 * result + title.hashCode()
            result = 31 * result + messageType
            result = 31 * result + optionType
            result = 31 * result + (parent?.hashCode() ?: 0)
            result = 31 * result + (options?.contentHashCode() ?: 0)
            result = 31 * result + (initialValue?.hashCode() ?: 0)
            result = 31 * result + (customComponent?.hashCode() ?: 0)
            return result
        }
    }
    
    /**
     * Dialog message types
     */
    private object DialogTypes {
        const val INFO = JOptionPane.INFORMATION_MESSAGE
        const val WARNING = JOptionPane.WARNING_MESSAGE
        const val ERROR = JOptionPane.ERROR_MESSAGE
        const val QUESTION = JOptionPane.QUESTION_MESSAGE
        const val PLAIN = JOptionPane.PLAIN_MESSAGE
    }
    
    /**
     * Dialog option types
     */
    private object OptionTypes {
        const val OK_CANCEL = JOptionPane.OK_CANCEL_OPTION
        const val YES_NO = JOptionPane.YES_NO_OPTION
        const val YES_NO_CANCEL = JOptionPane.YES_NO_CANCEL_OPTION
        const val DEFAULT = JOptionPane.DEFAULT_OPTION
    }
    
    /**
     * Dialog result constants
     */
    private object DialogResults {
        const val OK = JOptionPane.OK_OPTION
        const val YES = JOptionPane.YES_OPTION
        const val NO = JOptionPane.NO_OPTION
        const val CANCEL = JOptionPane.CANCEL_OPTION
        const val CLOSED = JOptionPane.CLOSED_OPTION
    }
    
    /**
     * Internationalization keys for default dialog titles and buttons
     */
    private object I18nKeys {
        // Default titles
        const val TITLE_INFO = "dialog.title.info"
        const val TITLE_WARNING = "dialog.title.warning"
        const val TITLE_ERROR = "dialog.title.error"
        const val TITLE_CONFIRM = "dialog.title.confirm"
        const val TITLE_INPUT = "dialog.title.input"
        
        // Default buttons
        const val BUTTON_OK = "button.ok"
        const val BUTTON_CANCEL = "button.cancel"
        const val BUTTON_YES = "button.yes"
        const val BUTTON_NO = "button.no"
    }
    
    /**
     * Show an information dialog
     * 
     * @param message The message to display (used if messageKey is not found)
     * @param title The dialog title (used if titleKey is not found)
     * @param parent The parent component for the dialog (optional)
     * @param messageKey The internationalization key for the message (optional)
     * @param titleKey The internationalization key for the title (optional)
     * @param messageParams Parameters to substitute in the message (optional)
     */
    fun showInfo(
        message: String,
        title: String? = null,
        parent: Component? = null,
        messageKey: String? = null,
        titleKey: String? = null,
        vararg messageParams: Any
    ) {
        ensureEDT {
            withLocalizedButtons {
                val resolvedMessage = resolveMessage(messageKey, message, *messageParams)
                val resolvedTitle = resolveTitle(titleKey, title, I18nKeys.TITLE_INFO, "Information")
                
                JOptionPane.showMessageDialog(
                    parent,
                    resolvedMessage,
                    resolvedTitle,
                    DialogTypes.INFO
                )
            }
        }
    }
    
    /**
     * Show a warning dialog
     * 
     * @param message The message to display (used if messageKey is not found)
     * @param title The dialog title (used if titleKey is not found)
     * @param parent The parent component for the dialog (optional)
     * @param messageKey The internationalization key for the message (optional)
     * @param titleKey The internationalization key for the title (optional)
     * @param messageParams Parameters to substitute in the message (optional)
     */
    fun showWarning(
        message: String,
        title: String? = null,
        parent: Component? = null,
        messageKey: String? = null,
        titleKey: String? = null,
        vararg messageParams: Any
    ) {
        ensureEDT {
            withLocalizedButtons {
                val resolvedMessage = resolveMessage(messageKey, message, *messageParams)
                val resolvedTitle = resolveTitle(titleKey, title, I18nKeys.TITLE_WARNING, "Warning")
                
                JOptionPane.showMessageDialog(
                    parent,
                    resolvedMessage,
                    resolvedTitle,
                    DialogTypes.WARNING
                )
            }
        }
    }
    
    /**
     * Show an error dialog
     * 
     * @param message The message to display (used if messageKey is not found)
     * @param title The dialog title (used if titleKey is not found)
     * @param parent The parent component for the dialog (optional)
     * @param messageKey The internationalization key for the message (optional)
     * @param titleKey The internationalization key for the title (optional)
     * @param messageParams Parameters to substitute in the message (optional)
     */
    fun showError(
        message: String,
        title: String? = null,
        parent: Component? = null,
        messageKey: String? = null,
        titleKey: String? = null,
        vararg messageParams: Any
    ) {
        ensureEDT {
            withLocalizedButtons {
                val resolvedMessage = resolveMessage(messageKey, message, *messageParams)
                val resolvedTitle = resolveTitle(titleKey, title, I18nKeys.TITLE_ERROR, "Error")
                
                JOptionPane.showMessageDialog(
                    parent,
                    resolvedMessage,
                    resolvedTitle,
                    DialogTypes.ERROR
                )
            }
        }
    }
    
    /**
     * Show a confirmation dialog (OK/Cancel)
     * 
     * @param message The message to display (used if messageKey is not found)
     * @param title The dialog title (used if titleKey is not found)
     * @param parent The parent component for the dialog (optional)
     * @param messageKey The internationalization key for the message (optional)
     * @param titleKey The internationalization key for the title (optional)
     * @param messageParams Parameters to substitute in the message (optional)
     * @return true if user clicked OK, false if user clicked Cancel or closed the dialog
     */
    fun showConfirm(
        message: String,
        title: String? = null,
        parent: Component? = null,
        messageKey: String? = null,
        titleKey: String? = null,
        vararg messageParams: Any
    ): Boolean {
        var result = false
        ensureEDT {
            withLocalizedButtons {
                val resolvedMessage = resolveMessage(messageKey, message, *messageParams)
                val resolvedTitle = resolveTitle(titleKey, title, I18nKeys.TITLE_CONFIRM, "Confirm")
                
                val choice = JOptionPane.showConfirmDialog(
                    parent,
                    resolvedMessage,
                    resolvedTitle,
                    OptionTypes.OK_CANCEL,
                    DialogTypes.QUESTION
                )
                
                result = choice == DialogResults.OK
            }
        }
        return result
    }
    
    /**
     * Show a Yes/No dialog
     * 
     * @param message The message to display (used if messageKey is not found)
     * @param title The dialog title (used if titleKey is not found)
     * @param parent The parent component for the dialog (optional)
     * @param messageKey The internationalization key for the message (optional)
     * @param titleKey The internationalization key for the title (optional)
     * @param messageParams Parameters to substitute in the message (optional)
     * @return true if user clicked Yes, false if user clicked No or closed the dialog
     */
    fun showYesNo(
        message: String,
        title: String? = null,
        parent: Component? = null,
        messageKey: String? = null,
        titleKey: String? = null,
        vararg messageParams: Any
    ): Boolean {
        var result = false
        ensureEDT {
            withLocalizedButtons {
                val resolvedMessage = resolveMessage(messageKey, message, *messageParams)
                val resolvedTitle = resolveTitle(titleKey, title, I18nKeys.TITLE_CONFIRM, "Confirm")
                
                val choice = JOptionPane.showConfirmDialog(
                    parent,
                    resolvedMessage,
                    resolvedTitle,
                    OptionTypes.YES_NO,
                    DialogTypes.QUESTION
                )
                
                result = choice == DialogResults.YES
            }
        }
        return result
    }
    
    /**
     * Show a Yes/No/Cancel dialog
     * 
     * @param message The message to display (used if messageKey is not found)
     * @param title The dialog title (used if titleKey is not found)
     * @param parent The parent component for the dialog (optional)
     * @param messageKey The internationalization key for the message (optional)
     * @param titleKey The internationalization key for the title (optional)
     * @param messageParams Parameters to substitute in the message (optional)
     * @return DialogResult indicating user's choice
     */
    fun showYesNoCancel(
        message: String,
        title: String? = null,
        parent: Component? = null,
        messageKey: String? = null,
        titleKey: String? = null,
        vararg messageParams: Any
    ): DialogResult {
        var result = DialogResult.CLOSED
        ensureEDT {
            withLocalizedButtons {
                val resolvedMessage = resolveMessage(messageKey, message, *messageParams)
                val resolvedTitle = resolveTitle(titleKey, title, I18nKeys.TITLE_CONFIRM, "Confirm")
                
                val choice = JOptionPane.showConfirmDialog(
                    parent,
                    resolvedMessage,
                    resolvedTitle,
                    OptionTypes.YES_NO_CANCEL,
                    DialogTypes.QUESTION
                )
                
                result = when (choice) {
                    DialogResults.YES -> DialogResult.YES
                    DialogResults.NO -> DialogResult.NO
                    DialogResults.CANCEL -> DialogResult.CANCEL
                    else -> DialogResult.CLOSED
                }
            }
        }
        return result
    }
    
    /**
     * Show an input dialog
     * 
     * @param message The message to display (used if messageKey is not found)
     * @param title The dialog title (used if titleKey is not found)
     * @param initialValue The initial value in the input field (optional)
     * @param parent The parent component for the dialog (optional)
     * @param messageKey The internationalization key for the message (optional)
     * @param titleKey The internationalization key for the title (optional)
     * @param messageParams Parameters to substitute in the message (optional)
     * @return The user's input string, or null if user cancelled or closed the dialog
     */
    fun showInput(
        message: String,
        title: String? = null,
        initialValue: String? = null,
        parent: Component? = null,
        messageKey: String? = null,
        titleKey: String? = null,
        vararg messageParams: Any
    ): String? {
        var result: String? = null
        ensureEDT {
            withLocalizedButtons {
                val resolvedMessage = resolveMessage(messageKey, message, *messageParams)
                val resolvedTitle = resolveTitle(titleKey, title, I18nKeys.TITLE_INPUT, "Input")
                
                result = JOptionPane.showInputDialog(
                    parent,
                    resolvedMessage,
                    resolvedTitle,
                    DialogTypes.QUESTION,
                    null,
                    null,
                    initialValue
                ) as? String
            }
        }
        return result
    }
    
    /**
     * Show an options dialog with custom buttons
     * 
     * @param message The message to display (used if messageKey is not found)
     * @param title The dialog title (used if titleKey is not found)
     * @param options Array of option button texts
     * @param defaultOption The default selected option (optional)
     * @param parent The parent component for the dialog (optional)
     * @param messageKey The internationalization key for the message (optional)
     * @param titleKey The internationalization key for the title (optional)
     * @param optionKeys Array of internationalization keys for options (optional)
     * @param messageParams Parameters to substitute in the message (optional)
     * @return The index of the selected option, or -1 if user closed the dialog
     */
    fun showOptions(
        message: String,
        title: String? = null,
        options: Array<String>,
        defaultOption: String? = null,
        parent: Component? = null,
        messageKey: String? = null,
        titleKey: String? = null,
        optionKeys: Array<String>? = null,
        vararg messageParams: Any
    ): Int {
        var result = -1
        ensureEDT {
            withLocalizedButtons {
                val resolvedMessage = resolveMessage(messageKey, message, *messageParams)
                val resolvedTitle = resolveTitle(titleKey, title, I18nKeys.TITLE_CONFIRM, "Select")
                val resolvedOptions = resolveOptions(options, optionKeys)
                
                result = JOptionPane.showOptionDialog(
                    parent,
                    resolvedMessage,
                    resolvedTitle,
                    OptionTypes.DEFAULT,
                    DialogTypes.QUESTION,
                    null,
                    resolvedOptions,
                    defaultOption ?: resolvedOptions.firstOrNull()
                )
            }
        }
        return result
    }
    
    /**
     * Show a dialog with custom component
     * 
     * @param component The custom Swing component to display
     * @param title The dialog title (used if titleKey is not found)
     * @param optionType The option type (OK_CANCEL, YES_NO, etc.)
     * @param messageType The message type (INFO, WARNING, ERROR, etc.)
     * @param parent The parent component for the dialog (optional)
     * @param titleKey The internationalization key for the title (optional)
     * @return The option selected by the user
     */
    fun showCustom(
        component: Any,
        title: String? = null,
        optionType: Int = OptionTypes.OK_CANCEL,
        messageType: Int = DialogTypes.PLAIN,
        parent: Component? = null,
        titleKey: String? = null
    ): Int {
        var result = DialogResults.CLOSED
        ensureEDT {
            withLocalizedButtons {
                val resolvedTitle = resolveTitle(titleKey, title, I18nKeys.TITLE_CONFIRM, "")
                
                result = JOptionPane.showConfirmDialog(
                    parent,
                    component,
                    resolvedTitle,
                    optionType,
                    messageType
                )
            }
        }
        return result
    }
    
    /**
     * Resolve options with internationalization support
     */
    private fun resolveOptions(
        options: Array<String>,
        optionKeys: Array<String>?
    ): Array<String> {
        if (optionKeys == null || optionKeys.size != options.size) {
            return options
        }
        
        return optionKeys.mapIndexed { index, key ->
            HttpPalBundle.getMessageOrDefault(key, options[index])
        }.toTypedArray()
    }
    
    /**
     * Ensure the action runs on the EDT thread
     */
    private fun ensureEDT(action: () -> Unit) {
        AsyncUIHelper.ensureEDT(action)
    }
    
    /**
     * Set button labels to use plugin internationalization
     */
    private fun setButtonLabels() {
        UIManager.put("OptionPane.okButtonText", HttpPalBundle.message(I18nKeys.BUTTON_OK))
        UIManager.put("OptionPane.cancelButtonText", HttpPalBundle.message(I18nKeys.BUTTON_CANCEL))
        UIManager.put("OptionPane.yesButtonText", HttpPalBundle.message(I18nKeys.BUTTON_YES))
        UIManager.put("OptionPane.noButtonText", HttpPalBundle.message(I18nKeys.BUTTON_NO))
    }
    
    /**
     * Execute dialog with internationalized buttons
     */
    private inline fun <T> withLocalizedButtons(action: () -> T): T {
        // Save original button texts
        val originalOk = UIManager.get("OptionPane.okButtonText")
        val originalCancel = UIManager.get("OptionPane.cancelButtonText")
        val originalYes = UIManager.get("OptionPane.yesButtonText")
        val originalNo = UIManager.get("OptionPane.noButtonText")
        
        try {
            // Set localized button texts
            setButtonLabels()
            return action()
        } finally {
            // Restore original button texts
            UIManager.put("OptionPane.okButtonText", originalOk)
            UIManager.put("OptionPane.cancelButtonText", originalCancel)
            UIManager.put("OptionPane.yesButtonText", originalYes)
            UIManager.put("OptionPane.noButtonText", originalNo)
        }
    }
    
    /**
     * Resolve message with internationalization support
     */
    private fun resolveMessage(
        messageKey: String?,
        defaultMessage: String,
        vararg params: Any
    ): String {
        return if (messageKey != null) {
            HttpPalBundle.getMessageOrDefault(messageKey, defaultMessage, *params)
        } else {
            if (params.isNotEmpty()) {
                String.format(defaultMessage, *params)
            } else {
                defaultMessage
            }
        }
    }
    
    /**
     * Resolve title with internationalization support
     */
    private fun resolveTitle(
        titleKey: String?,
        defaultTitle: String?,
        fallbackKey: String,
        fallbackDefault: String
    ): String {
        return when {
            titleKey != null -> HttpPalBundle.getMessageOrDefault(titleKey, defaultTitle ?: fallbackDefault)
            defaultTitle != null -> defaultTitle
            else -> HttpPalBundle.getMessageOrDefault(fallbackKey, fallbackDefault)
        }
    }
}
