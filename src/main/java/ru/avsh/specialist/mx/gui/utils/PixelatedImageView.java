package ru.avsh.specialist.mx.gui.utils;

import com.sun.javafx.geom.BaseBounds;
import com.sun.javafx.geom.transform.BaseTransform;
import com.sun.javafx.scene.ImageViewHelper;
import com.sun.javafx.sg.prism.NGImageView;
import com.sun.javafx.sg.prism.NGNode;
import com.sun.prism.Graphics;
import com.sun.prism.Image;
import com.sun.prism.Texture;
import com.sun.prism.impl.BaseResourceFactory;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

@SuppressWarnings("restriction")
public class PixelatedImageView extends ImageView {

    public PixelatedImageView(javafx.scene.image.Image image) {
        super(image);
        initialize();
    }

    private void initialize() {
        try {
            final Object nodeHelper = FieldUtils.readField(this, "nodeHelper", true);
            FieldUtils.writeField(nodeHelper, "imageViewAccessor", null, true);

            ImageViewHelper.setImageViewAccessor(new ImageViewHelper.ImageViewAccessor() {
                @Override
                public NGNode doCreatePeer(Node node) {
                    return new NGImageView() {
                        private Image image;

                        @Override
                        public void setImage(Object img) {
                            super.setImage(img);
                            image = (Image) img;
                        }

                        @Override
                        protected void renderContent(Graphics g) {
                            final BaseResourceFactory factory = (BaseResourceFactory) g.getResourceFactory();
                            Texture tex = factory.getCachedTexture(image, Texture.WrapMode.CLAMP_TO_EDGE);
                            tex.setLinearFiltering(false);
                            tex.unlock();
                            super.renderContent(g);
                        }
                    };
                }

                @Override
                public void doUpdatePeer(Node node) {
                    try {
                        MethodUtils.invokeMethod(node, true, "doUpdatePeer");
                    } catch (ReflectiveOperationException e) {
                        //
                    }
                }

                @Override
                public BaseBounds doComputeGeomBounds(Node node, BaseBounds bounds, BaseTransform tx) {
                    try {
                        return (BaseBounds) MethodUtils.invokeMethod(node, true, "doComputeGeomBounds", bounds, tx);
                    } catch (ReflectiveOperationException e) {
                        return null;
                    }
                }

                @Override
                public boolean doComputeContains(Node node, double localX, double localY) {
                    try {
                        return (boolean) MethodUtils.invokeMethod(node, true, "doComputeContains", localX, localY);
                    } catch (ReflectiveOperationException e) {
                        return false;
                    }
                }
            });
        } catch (IllegalAccessException e) {
            //
        }
    }
}