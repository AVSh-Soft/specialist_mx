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

import java.lang.reflect.Method;

@SuppressWarnings("restriction")
public class PixelatedImageView extends ImageView {

    private final Method doUpdatePeerMethod;
    private final Method doComputeGeomBoundsMethod;
    private final Method doComputeContainsMethod;

    public PixelatedImageView(javafx.scene.image.Image image) {
        super(image);
        doUpdatePeerMethod = getImageViewMethod("doUpdatePeer");
        doComputeGeomBoundsMethod = getImageViewMethod("doComputeGeomBounds", BaseBounds.class, BaseTransform.class);
        doComputeContainsMethod = getImageViewMethod("doComputeContains", double.class, double.class);

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
                            final Texture tex = factory.getCachedTexture(image, Texture.WrapMode.CLAMP_TO_EDGE);
                            tex.setLinearFiltering(false);
                            tex.unlock();
                            super.renderContent(g);
                        }
                    };
                }

                @Override
                public void doUpdatePeer(Node node) {
                    try {
                        if (doUpdatePeerMethod != null) {
                            doUpdatePeerMethod.invoke(node);
                        }
                    } catch (ReflectiveOperationException e) {
                        //
                    }
                }

                @Override
                public BaseBounds doComputeGeomBounds(Node node, BaseBounds bounds, BaseTransform tx) {
                    try {
                        if (doComputeGeomBoundsMethod != null) {
                            return (BaseBounds) doComputeGeomBoundsMethod.invoke(node, bounds, tx);
                        }
                    } catch (ReflectiveOperationException e) {
                        //
                    }
                    return null;
                }

                @Override
                public boolean doComputeContains(Node node, double localX, double localY) {
                    try {
                        if (doComputeContainsMethod != null) {
                            return (boolean) doComputeContainsMethod.invoke(node, localX, localY);
                        }
                    } catch (ReflectiveOperationException e) {
                        //
                    }
                    return false;
                }
            });
        } catch (IllegalAccessException e) {
            //
        }
    }

    private Method getImageViewMethod(final String name, final Class<?>... parameterTypes) {
        Method result = null;
        try {
            result = ImageView.class.getDeclaredMethod(name, parameterTypes);
            result.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            //
        }
        return result;
    }
}