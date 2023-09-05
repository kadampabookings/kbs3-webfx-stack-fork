package dev.webfx.stack.ui.json;

import dev.webfx.extras.imagestore.ImageStore;
import dev.webfx.platform.ast.json.Json;
import dev.webfx.platform.ast.json.ReadOnlyJsonObject;
import dev.webfx.platform.util.Strings;
import javafx.scene.image.ImageView;

/**
 * @author Bruno Salmon
 */
public final class JsonImageView {

    public static ImageView createImageView(Object urlOrJson) {
        if (urlOrJson == null || "".equals(urlOrJson))
            return null;
        if (urlOrJson instanceof ReadOnlyJsonObject)
            return createImageView((ReadOnlyJsonObject) urlOrJson);
        return createImageView(urlOrJson.toString());
    }

    public static ImageView createImageView(String urlOrJson) {
        if (!Strings.startsWith(urlOrJson, "{"))
            return ImageStore.createImageView(urlOrJson);
        return createImageView(Json.parseObject(urlOrJson));
    }

    public static ImageView createImageView(ReadOnlyJsonObject json) {
        return ImageStore.createImageView(json.getString("url"), json.getDouble("width"), json.getDouble("height"));
    }

}
