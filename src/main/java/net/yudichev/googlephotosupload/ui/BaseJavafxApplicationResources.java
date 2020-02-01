package net.yudichev.googlephotosupload.ui;

import javafx.application.HostServices;
import javafx.stage.Stage;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import org.immutables.value.Value.Immutable;

@Immutable
@PackagePrivateImmutablesStyle
interface BaseJavafxApplicationResources {
    Stage primaryStage();

    HostServices hostServices();
}
