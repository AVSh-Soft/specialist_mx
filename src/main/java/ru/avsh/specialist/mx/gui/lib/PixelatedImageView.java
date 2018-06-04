package ru.avsh.specialist.mx.gui.lib;

import com.sun.javafx.sg.prism.NGImageView;
import com.sun.javafx.sg.prism.NGNode;
import com.sun.prism.Graphics;
import com.sun.prism.Image;
import com.sun.prism.Texture;
import com.sun.prism.impl.BaseResourceFactory;
import javafx.scene.image.ImageView;

@SuppressWarnings("restriction")
public class PixelatedImageView extends ImageView {
    /**
     * Allocates a new ImageView object using the given image.
     *
     * @param image Image that this PixelatedImageView uses
     */
    public PixelatedImageView(javafx.scene.image.Image image) {
        super(image);
    }

    @Override
    protected NGNode impl_createPeer() {
        return new NGImageView() {
            private Image image;

            @Override
            public void setImage(Object img) {
                super.setImage( img);
                image = (Image) img;
            }

            @Override
            protected void renderContent(Graphics g) {
                final BaseResourceFactory factory = (BaseResourceFactory) g.getResourceFactory();
                final Texture tex = factory.getCachedTexture(image, Texture.WrapMode.CLAMP_TO_EDGE);
                tex.setLinearFiltering(false);
                tex.unlock();
                super.renderContent(g);
            }
        };
    }
}