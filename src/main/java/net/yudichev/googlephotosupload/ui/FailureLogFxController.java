package net.yudichev.googlephotosupload.ui;

import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import net.yudichev.googlephotosupload.core.KeyedError;

import javax.inject.Inject;
import javax.inject.Provider;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.toCollection;
import static javafx.scene.control.SelectionMode.MULTIPLE;
import static javafx.scene.input.KeyCombination.CONTROL_ANY;
import static javafx.scene.input.KeyCombination.META_ANY;
import static net.yudichev.googlephotosupload.ui.OperatingSystemDetection.OSType;
import static net.yudichev.googlephotosupload.ui.OperatingSystemDetection.getOperatingSystemType;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

public final class FailureLogFxController {
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;

    public TableView<KeyedError> tableView;
    public TableColumn<KeyedError, Object> keyColumn;
    public TableColumn<KeyedError, String> valueColumn;


    @Inject
    FailureLogFxController(Provider<JavafxApplicationResources> javafxApplicationResourcesProvider) {
        this.javafxApplicationResourcesProvider = checkNotNull(javafxApplicationResourcesProvider);
    }

    public void initialize() {
        tableView.getSelectionModel().setSelectionMode(MULTIPLE);
        var keyCodeCopy1 = new KeyCodeCombination(KeyCode.C, getOperatingSystemType() == OSType.MacOS ? META_ANY : CONTROL_ANY);
        var keyCodeCopy2 = new KeyCodeCombination(KeyCode.INSERT, CONTROL_ANY);
        tableView.setOnKeyPressed(event -> {
            if (keyCodeCopy1.match(event) || keyCodeCopy2.match(event)) {
                copySelectionToClipboard(tableView);
            }
        });

        keyColumn.setCellValueFactory(new PropertyValueFactory<>("key"));
        keyColumn.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setText(null);
                } else {
                    if (item instanceof URL || item instanceof Path) {
                        var uri = item instanceof URL ? getAsUnchecked(((URL) item)::toURI) : ((Path) item).toUri();
                        var hyperlink = new Hyperlink(item.toString());
                        hyperlink.setOnAction(event -> javafxApplicationResourcesProvider.get().hostServices().showDocument(uri.toString()));
                        setGraphic(hyperlink);
                    } else {
                        setText(item.toString());
                    }
                }
            }
        });
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("error"));
    }

    public void addFailures(Collection<KeyedError> failures) {
        tableView.getItems().addAll(failures);
    }

    private static void copySelectionToClipboard(TableView<?> table) {
        Set<Integer> rows = table.getSelectionModel().getSelectedCells().stream()
                .map(TablePositionBase::getRow)
                .collect(toCollection(TreeSet::new));
        var stringBuilder = new StringBuilder(rows.size() * 256);
        var firstRow = true;
        for (var row : rows) {
            if (!firstRow) {
                stringBuilder.append(lineSeparator());
            }
            firstRow = false;
            var firstCol = true;
            for (TableColumn<?, ?> column : table.getColumns()) {
                if (!firstCol) {
                    stringBuilder.append('\t');
                }
                firstCol = false;
                var cellData = column.getCellData(row);
                stringBuilder.append(cellData == null ? "" : cellData.toString());
            }
        }
        var clipboardContent = new ClipboardContent();
        clipboardContent.putString(stringBuilder.toString());
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }
}
