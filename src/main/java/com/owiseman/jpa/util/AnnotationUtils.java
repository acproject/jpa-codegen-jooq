package com.owiseman.jpa.util;

import javax.lang.model.element.Element;
import java.lang.annotation.Annotation;

public class AnnotationUtils {
        public static <A extends Annotation> A getAnnotation(Element element, Class<A> annotationType) {
        A annotation = element.getAnnotation(annotationType);
       if (annotation == null) {
            try {
                Class<?> jakartaType = Class.forName("jakarta.persistence." + annotationType.getSimpleName());
                annotation = element.getAnnotation(jakartaType.asSubclass(annotationType));
            } catch (ClassNotFoundException ignored) {
            }
        }
        return annotation;
    }
}
