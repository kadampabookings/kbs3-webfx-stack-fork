package dev.webfx.stack.authn.login.ui.spi.impl.portal;

import dev.webfx.platform.util.serviceloader.MultipleServiceProviders;
import dev.webfx.stack.authn.login.ui.spi.UiLoginServiceProvider;
import dev.webfx.stack.authn.login.ui.spi.impl.gateway.UiLoginGatewayProvider;
import dev.webfx.stack.ui.util.layout.LayoutUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.ServiceLoader;

/**
 * @author Bruno Salmon
 */
public class UiLoginPortalProvider implements UiLoginServiceProvider {

    private static List<UiLoginGatewayProvider> getProviders() {
        return MultipleServiceProviders.getProviders(UiLoginGatewayProvider.class, () -> ServiceLoader.load(UiLoginGatewayProvider.class));
    }

    @Override
    public Node createLoginUi() {
        BorderPane container = new BorderPane();
        fillContainer(container);
        return container;
    }

    private void fillContainer(BorderPane container) {
        HBox hBox = new HBox(10);
        hBox.setAlignment(Pos.CENTER);
        for (UiLoginGatewayProvider gatewayProvider : getProviders()) {
            Button loginButton = gatewayProvider.createLoginButton();
            loginButton.setMaxWidth(300);
            loginButton.setCursor(Cursor.HAND);
            loginButton.setOnAction(e -> {
                Button cancelButton = new Button("« Use another method to sign in");
                BorderPane.setAlignment(cancelButton, Pos.CENTER);
                BorderPane.setMargin(cancelButton, new Insets(10));
                cancelButton.setCursor(Cursor.HAND);
                cancelButton.setOnAction(e2 -> fillContainer(container));
                container.setBottom(cancelButton);
                container.setCenter(gatewayProvider.createLoginUi());
            });
            hBox.getChildren().add(loginButton);
        }
        container.setBottom(null);
        container.setCenter(LayoutUtil.createGoldLayout(hBox));
    }

}
