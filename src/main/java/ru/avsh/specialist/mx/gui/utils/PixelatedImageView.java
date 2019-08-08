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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

@SuppressWarnings("restriction")
public class PixelatedImageView extends ImageView {
    private final MethodHandle        doUpdatePeerMH;
    private final MethodHandle doComputeGeomBoundsMH;
    private final MethodHandle   doComputeContainsMH;

    public PixelatedImageView(javafx.scene.image.Image image) {
        super(image);

        final MethodHandles.Lookup lookup = MethodHandles.lookup();
               doUpdatePeerMH = getImageViewMethodHandle(lookup, "doUpdatePeer");
        doComputeGeomBoundsMH = getImageViewMethodHandle(lookup, "doComputeGeomBounds", BaseBounds.class, BaseTransform.class);
          doComputeContainsMH = getImageViewMethodHandle(lookup, "doComputeContains"  ,     double.class,        double.class);

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
                        if (doUpdatePeerMH != null) {
                            doUpdatePeerMH.invokeExact((ImageView) node);
                        }
                    } catch (Throwable e) {
                        //
                    }
                }

                @Override
                public BaseBounds doComputeGeomBounds(Node node, BaseBounds bounds, BaseTransform tx) {
                    try {
                        if (doComputeGeomBoundsMH != null) {
                            return (BaseBounds) doComputeGeomBoundsMH.invokeExact((ImageView) node, bounds, tx);
                        }
                    } catch (Throwable e) {
                        //
                    }
                    return null;
                }

                @Override
                public boolean doComputeContains(Node node, double localX, double localY) {
                    try {
                        if (doComputeContainsMH != null) {
                            return (boolean) doComputeContainsMH.invokeExact((ImageView) node, localX, localY);
                        }
                    } catch (Throwable e) {
                        //
                    }
                    return false;
                }
            });
        } catch (IllegalAccessException e) {
            //
        }
    }

    private MethodHandle getImageViewMethodHandle(final MethodHandles.Lookup lookup,
                                                  final String name,
                                                  final Class<?>... parameterTypes) {
        MethodHandle result = null;
        try {
            final Method method = ImageView.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            result = lookup.unreflect(method);
        } catch (ReflectiveOperationException e) {
            //
        }
        return result;
    }
}