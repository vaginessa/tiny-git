package hamburg.remme.tinygit.gui.builder

import hamburg.remme.tinygit.State
import hamburg.remme.tinygit.TinyGit
import hamburg.remme.tinygit.asPath
import hamburg.remme.tinygit.gui.component.Icons
import hamburg.remme.tinygit.gui.dialog.ChoiceDialog
import hamburg.remme.tinygit.gui.dialog.TextInputDialog
import hamburg.remme.tinygit.homeDir
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Window
import java.nio.file.Path

var chooserDir = homeDir.asPath() // TODO: persist?
private const val INFO_STYLE_CLASS = "info"
private const val WARNING_STYLE_CLASS = "warning"
private const val ERROR_STYLE_CLASS = "error"

fun ButtonType.isOk() = buttonData == ButtonBar.ButtonData.OK_DONE

fun ButtonType.isCancel() = buttonData == ButtonBar.ButtonData.CANCEL_CLOSE

fun confirmAlert(window: Window, header: String, ok: String, text: String): Boolean {
    val alert = alert(window,
            Alert.AlertType.CONFIRMATION,
            header,
            text,
            Icons.questionCircle().addClass(INFO_STYLE_CLASS),
            ButtonType(ok, ButtonBar.ButtonData.OK_DONE),
            ButtonType.CANCEL)
    TinyGit.get<State>().isModal.set(true)
    return alert.showAndWait().get().isOk()
}

fun confirmWarningAlert(window: Window, header: String, ok: String, text: String): Boolean {
    val alert = alert(window,
            Alert.AlertType.CONFIRMATION,
            header,
            text,
            Icons.exclamationTriangle().addClass(WARNING_STYLE_CLASS),
            ButtonType(ok, ButtonBar.ButtonData.OK_DONE),
            ButtonType.CANCEL)
    TinyGit.get<State>().isModal.set(true)
    return alert.showAndWait().get().isOk()
}

fun errorAlert(window: Window, header: String, text: String) {
    val alert = alert(window,
            Alert.AlertType.ERROR,
            header,
            text,
            Icons.timesCircle().addClass(ERROR_STYLE_CLASS),
            ButtonType.OK)
    TinyGit.get<State>().isModal.set(true)
    alert.showAndWait()
}

fun fatalAlert(header: String, text: String) {
    val alert = Alert(Alert.AlertType.ERROR, text, ButtonType.OK)
    alert.headerText = header
    alert.graphic = Icons.timesCircle().addClass(ERROR_STYLE_CLASS)
    alert.showAndWait()
}

private fun alert(window: Window,
                  type: Alert.AlertType,
                  header: String,
                  text: String,
                  icon: Node,
                  vararg button: ButtonType): Alert {
    val alert = Alert(type, text, *button)
    alert.initModality(Modality.WINDOW_MODAL)
    alert.initOwner(window)
    alert.headerText = header
    alert.graphic = icon
    return alert
}

inline fun textInputDialog(window: Window,
                           header: String,
                           ok: String,
                           icon: Node,
                           defaultValue: String = "",
                           block: (String) -> Unit) {
    val dialog = TextInputDialog(ok, false, window)
    dialog.header = header
    dialog.graphic = icon
    dialog.defaultValue = defaultValue
    dialog.showAndWait()?.let(block)
}

inline fun textAreaDialog(window: Window,
                          header: String,
                          ok: String,
                          icon: Node,
                          defaultValue: String = "",
                          description: String = "",
                          block: (String) -> Unit) {
    val dialog = TextInputDialog(ok, true, window)
    dialog.header = header
    dialog.graphic = icon
    dialog.defaultValue = defaultValue
    dialog.description = description
    dialog.showAndWait()?.let(block)
}

inline fun <T> choiceDialog(window: Window,
                            header: String,
                            ok: String,
                            icon: Node,
                            items: List<T>,
                            description: String = "",
                            block: (T) -> Unit) {
    val dialog = ChoiceDialog<T>(ok, window)
    dialog.header = header
    dialog.graphic = icon
    dialog.items = items
    dialog.description = description
    dialog.showAndWait()?.let(block)
}

inline fun fileChooser(window: Window, title: String, block: (Path) -> Unit) {
    val chooser = FileChooser()
    chooser.title = title
    chooser.initialDirectory = chooserDir.toFile()
    TinyGit.get<State>().isModal.set(true)
    chooser.showOpenDialog(window)?.toPath()?.let {
        chooserDir = it
        block(it)
    }
}

inline fun directoryChooser(window: Window, title: String, block: (Path) -> Unit) {
    val chooser = DirectoryChooser()
    chooser.title = title
    chooser.initialDirectory = chooserDir.toFile()
    TinyGit.get<State>().isModal.set(true)
    chooser.showDialog(window)?.toPath()?.let {
        chooserDir = it
        block(it)
    }
}
