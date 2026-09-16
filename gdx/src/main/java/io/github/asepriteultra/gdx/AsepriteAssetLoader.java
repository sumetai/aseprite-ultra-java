package io.github.asepriteultra.gdx;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.asepriteultra.Aseprite;
import io.github.asepriteultra.AsepriteReader;
import java.io.IOException;
import java.io.InputStream;

/** AssetManager integration: decode on its worker thread, upload on the thread calling update(). */
public final class AsepriteAssetLoader extends AsynchronousAssetLoader<AsepriteSheet, AsepriteAssetLoader.Parameters> {
    public static final class Parameters extends AssetLoaderParameters<AsepriteSheet> {
        public int maxAtlasSize = 4096;
        public int padding = 1;
    }

    private Aseprite pending;

    public AsepriteAssetLoader() { this(new InternalFileHandleResolver()); }
    public AsepriteAssetLoader(FileHandleResolver resolver) { super(resolver); }

    @Override public void loadAsync(AssetManager manager, String fileName, FileHandle file, Parameters parameters) {
        pending = null;
        try (InputStream input = file.read()) { pending = new AsepriteReader().read(input); }
        catch (IOException exception) { throw new GdxRuntimeException("Cannot decode " + fileName, exception); }
    }

    @Override public AsepriteSheet loadSync(AssetManager manager, String fileName, FileHandle file, Parameters parameters) {
        Aseprite sprite = pending;
        pending = null;
        if (sprite == null) { throw new GdxRuntimeException("No decoded sprite for " + fileName); }
        return new AsepriteSheet(sprite, parameters == null ? 4096 : parameters.maxAtlasSize,
                parameters == null ? 1 : parameters.padding);
    }

    // libGDX's loader API uses the raw AssetDescriptor type.
    @SuppressWarnings("rawtypes")
    @Override public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, Parameters parameters) {
        return null;
    }
}
