package com.owiseman.jpa;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.hibernate.annotations.Type;


import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.tools.JavaFileObject;

import java.io.Writer;
import java.lang.annotation.Annotation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({"javax.persistence.Entity", "jakarta.persistence.Entity"})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class JpaEntityScannerProcessor extends AbstractProcessor {
    private Elements elementUtils;
    private Types typeUtils;

    public JpaEntityScannerProcessor(){

    }
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<Class<? extends Annotation>> typeElements = new HashSet<>();
        // 在Set中添加需要去扫描的类型
        typeElements.add(jakarta.persistence.Entity.class);
        typeElements.add(Entity.class);
        System.out.println("================== process start ===========================");

        List<TypeElement> entityClasses = roundEnv.getElementsAnnotatedWithAny(typeElements)
                    .stream()
                    .filter(element -> element instanceof TypeElement)
                    .map(element -> (TypeElement) element)
                    .collect(Collectors.toList());

        if (!entityClasses.isEmpty()) {
            try {
                generateCode(entityClasses);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("================== process end ===========================");
        return true;
    }

    private void generateCode(List<TypeElement> entityClasses) throws ClassNotFoundException {
        assert !entityClasses.isEmpty();
        String packageName = elementUtils.getPackageOf(entityClasses.getFirst()).toString();

        // 定义类及其名字
        TypeSpec tableClass = TypeSpec.classBuilder("Tables")

                .addModifiers(Modifier.PUBLIC)
                .build();

        for (var typeElement : entityClasses) {
            String className = typeElement.getSimpleName().toString();
            String tableName = getTableName(typeElement);

            TypeSpec tableInnerClass = TypeSpec.classBuilder(className.toUpperCase())
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addField(getTableFieldSpec(tableName))
                    .addFields(getFieldSpecs(typeElement))
                    .addMethod(MethodSpec.constructorBuilder()
                            .addModifiers(Modifier.PRIVATE)
                            .build())
                    .build();
            tableClass = tableClass.toBuilder().addType(tableInnerClass).build();
        }

        JavaFile javaFile = JavaFile.builder(packageName, tableClass)
                .build();

        String writeFile = injectImports(javaFile, List.of("org.jooq.impl.DSL", "org.jooq.impl.SQLDataType"));

        try  {
            String fileName = javaFile.packageName.isEmpty() ? javaFile.typeSpec.name : javaFile.packageName + "." + javaFile.typeSpec.name;
            List<Element> originatingElements = javaFile.typeSpec.originatingElements;
            JavaFileObject filerSourceFile =  processingEnv.getFiler().createSourceFile(fileName, (Element[])originatingElements.toArray(new Element[originatingElements.size()]));
            Writer writer = filerSourceFile.openWriter();
            writer.write(writeFile);
            writer.close();
//            javaFile.writeTo(processingEnv.getFiler());
        } catch( java.io.IOException e) {
            e.printStackTrace();
        }

    }

    private String getTableName(TypeElement typeElement) {
        Table tableAnnotation = typeElement.getAnnotation(Table.class);
        String tableName = tableAnnotation != null && !tableAnnotation.name().isEmpty() ? tableAnnotation.name()
                : convertClassNameToTableName(typeElement.getSimpleName().toString());
        return tableName;
    }

//    private String pluralize(String word) {
//        if (word.endsWith("y")) {
//            return word.substring(0, word.length() - 1) + "ies";
//        }
//        return word + "s";
//    }

    private FieldSpec getTableFieldSpec(String tableName) {

        return FieldSpec.builder(ClassName.get("org.jooq", "Table"),
                        "TABLE", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("DSL.table(\"" + tableName + "\")").build();
    }

    private List<FieldSpec> getFieldSpecs(TypeElement typeElement) {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        List<VariableElement> fields = ElementFilter.fieldsIn(typeElement.getEnclosedElements());

        for (var field : fields) {
            String fieldName = field.getSimpleName().toString();
            Column columnAnnotation = field.getAnnotation(Column.class);
            String columnName = columnAnnotation != null ? columnAnnotation.name()
                    : fieldName;
            TypeMirror typeMirror = field.asType();
            String typeName = typeUtils.erasure(typeMirror).toString();

            if (field.getAnnotation(Type.class) != null &&
                    "jsonb".equals(field.getAnnotation(Type.class).type())) {
                typeName = "org.jooq.JSONB";
            }


            String sqlDataType = getSqlDataType(typeName);
            String dataTypeWithLength = typeName.equals("java.lang.String") ?
                    sqlDataType + ".length(" + getColumnLength(field) + ")" : sqlDataType;

            FieldSpec fieldSpec = FieldSpec.builder(ClassName.get("org.jooq", "Field"),
                            fieldName.toUpperCase(), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)

                    .initializer("DSL.field(String.valueOf(TABLE), \"" + columnName + "\", " + dataTypeWithLength +
                            ".nullable(" + isNullable(field) + "))")
                    .build();
            fieldSpecs.add(fieldSpec);
        }
        return fieldSpecs;
    }

    private int getColumnLength(VariableElement field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null && columnAnnotation.length() > 0) {
            return columnAnnotation.length();
        }
        return 255; // default 255
    }

    private boolean isNullable(VariableElement field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null) return columnAnnotation.nullable();
        return true;
    }

    private String getSqlDataType(String typeName) {
        return switch (typeName) {
            case "int", "java.lang.Integer" -> "SQLDataType.INTEGER";
            case "long", "java.lang.Long" -> "SQLDataType.BIGINT";
            case "java.lang.String" -> "SQLDataType.VARCHAR";
            case "java.time.LocalDate" -> "SQLDataType.LOCALDATE";
            case "java.time.LocalDateTime" -> "SQLDataType.LOCALDATETIME";
            case "java.time.LocalTime" -> "SQLDataType.LOCALTIME";
            case "java.time.OffsetDateTime", "java.util.Date" -> "SQLDataType.DATE";
            case "org.jooq.JSONB" -> "SQLDataType.JSONB";
            case "java.lang.Boolean" -> "SQLDataType.BOOLEAN";
            case "org.jooq.JSON" -> "SQLDataType.JSON";
            default -> "SQLDataType.OTHER";
        };
    }

    // 添加导入
    private String injectImports(JavaFile javaFile, List<String> imports) {
        String rawSource = javaFile.toString();

        List<String> result = new ArrayList<>();
        for (String s : rawSource.split("\n", -1)) {
            result.add(s);
            if (s.startsWith("package ")) {
                result.add("");
                for (String i : imports) {
                    result.add("import " + i + ";");
                }
            }
        }

        return String.join("\n", result);
    }


    // 我们遵循默认的驼峰命名法到下划线分隔的小写形式的转换
    private static String convertClassNameToTableName(String className) {
        if (className == null || className.isEmpty()) {
            return className;
        }
        StringBuilder result = new StringBuilder();
        char[] chars = className.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char ch = chars[i];
            if (Character.isUpperCase(ch)) {
                if (i > 0) result.append("_");
                result.append(Character.toLowerCase(ch));
            } else {
                result.append(ch);
            }
        }
        return result.toString().toLowerCase();
    }
}
