package com.owiseman.jpa;

import com.owiseman.jpa.model.ColumnMeta;
import com.owiseman.jpa.model.DataSourceEnum;
import com.owiseman.jpa.model.ForeignKey;

import com.owiseman.jpa.model.Index;

import com.owiseman.jpa.model.TableMeta;
import com.owiseman.jpa.util.AnnotationUtils;
import com.owiseman.jpa.util.SqlGenerator;
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
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;

import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({"javax.persistence.Entity", "jakarta.persistence.Entity"})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class JpaEntityScannerProcessor extends AbstractProcessor {
    private Elements elementUtils;
    private Types typeUtils;

    public JpaEntityScannerProcessor() {

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

        try {
            String fileName = javaFile.packageName.isEmpty() ? javaFile.typeSpec.name : javaFile.packageName + "." + javaFile.typeSpec.name;
            List<Element> originatingElements = javaFile.typeSpec.originatingElements;
            JavaFileObject filerSourceFile = processingEnv.getFiler().createSourceFile(fileName, (Element[]) originatingElements.toArray(new Element[originatingElements.size()]));
            Writer writer = filerSourceFile.openWriter();
            writer.write(writeFile);
            writer.close();
            // 生成SQL代码
            generateSQLCode(entityClasses);
            System.out.println("Generated file: " + fileName);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

    }

    private String getTableName(TypeElement typeElement) {
        Table tableAnnotation = typeElement.getAnnotation(Table.class);
         // 修复点：明确处理注解不存在的情况
        if (tableAnnotation == null || tableAnnotation.name().isEmpty()) {
            return convertClassNameToTableName(typeElement.getSimpleName().toString());
        }

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
            String columnName = (columnAnnotation != null && !columnAnnotation.name().isEmpty())
                    ? columnAnnotation.name()
                    : convertClassNameToTableName(fieldName);
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

                    .initializer("DSL.field(" + "\"" + columnName + "\"," + "\"" + columnName + "\"," + dataTypeWithLength +
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
        // 处理枚举类型
        if (typeName.endsWith("Enum") || typeName.contains(".enums.")) {
            return "SQLDataType.VARCHAR";
        }

        // 处理集合类型
        if (typeName.startsWith("java.util.Map")
                || typeName.startsWith("java.util.List")
                || typeName.startsWith("java.util.Set")) {
            return "SQLDataType.JSONB";
        }

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

    private TableMeta parseEntity(TypeElement element) {
        var tableAnnotation = AnnotationUtils.getAnnotation(element, Table.class);


        String tableName = (tableAnnotation != null && !tableAnnotation.name().isEmpty())
                ? tableAnnotation.name()
                : convertClassNameToTableName(element.getSimpleName().toString());

        // 解析字段
        List<ColumnMeta> columns = ElementFilter.fieldsIn(element.getEnclosedElements())
                .stream()
                .map(field -> {
                    Column column = field.getAnnotation(Column.class);
                    return new ColumnMeta(
                            getColumnName(field),
                            field.asType().toString(),
                            column != null ? column.length() : 255,
                            column == null || column.nullable(),
                            column != null && column.unique()
                    );
                })
                .collect(Collectors.toList());

        // 解析主键
        List<String> primaryKey = ElementFilter.fieldsIn(element.getEnclosedElements())
                .stream()
                .filter(f -> f.getAnnotation(Id.class) != null)
                .map(this::getColumnName)
                .collect(Collectors.toList());

        // 解析索引
        List<Index> indexes = parseIndexes(element);

        // 解析外键
        List<ForeignKey> foreignKeys = parseForeignKeys(element);

        return new TableMeta(tableName, columns, primaryKey, indexes, foreignKeys);
    }

    private List<ForeignKey> parseForeignKeys(TypeElement entity) {
        List<ForeignKey> foreignKeys = new ArrayList<>();

        ElementFilter.fieldsIn(entity.getEnclosedElements()).stream()
                .filter(field -> {
                    // 同时支持ManyToOne和JoinColumn注解
                    return AnnotationUtils.getAnnotation(field, ManyToOne.class) != null
                            || AnnotationUtils.getAnnotation(field, JoinColumn.class) != null;
                })
                .forEach(field -> {
                    JoinColumn joinColumn = AnnotationUtils.getAnnotation(field, JoinColumn.class);

                    // 获取关联表信息
                    TypeMirror targetType = field.asType();
                    String refTable = getTableName((TypeElement) typeUtils.asElement(targetType));

                    // 生成外键约束名
                    String fkName = generateFkName(
                            getTableName(entity),
                            getColumnName(field)
                    );

                    foreignKeys.add(new ForeignKey(
                            fkName,
                            getColumnName(field), // 当前表字段名
                            refTable, // 关联表名
                            (joinColumn != null && !joinColumn.referencedColumnName().isEmpty()) ?
                                    joinColumn.referencedColumnName() : "id" // 关联字段
                    ));
                });

        return foreignKeys;
    }


    private void generateSQLCode(List<TypeElement> entityClasses) {
        SqlGenerator sqlGen = new SqlGenerator();

        for (TypeElement entity : entityClasses) {
            TableMeta table = parseEntity(entity);
            sqlGen.addTable(table, DataSourceEnum.POSTGRESQL);
        }

        writeSqlFile(sqlGen.generate());
    }

    private void writeSqlFile(String sql) {
        try {
            FileObject file = processingEnv.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT,
                            "schema",
                            "schemas.sql");
            try (Writer writer = file.openWriter()) {
                writer.write(sql);
            }
            System.out.println("Generated file: schemas.sql");
        } catch (IOException e) {
            // 错误处理
            throw new RuntimeException("file created fail!");
        }
    }


    private List<Index> parseIndexes(TypeElement entity) {
        Table tableAnnotation = entity.getAnnotation(Table.class);
        if (tableAnnotation == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(tableAnnotation.indexes())
                .map(idx -> new Index(
                        idx.name(),
                        Arrays.stream(idx.columnList().split(","))
                                .map(String::trim)
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());
    }

    // 外键名称生成优化（防止重复）
    private String generateFkName(String tableName, String columnName) {
        String baseName = "fk_" + tableName + "_" + columnName;
        return baseName.toLowerCase() + "_" +
                Integer.toHexString(System.identityHashCode(this));
    }

    // 新增引用表名获取方法
    private String getReferencedTableName(TypeMirror typeMirror) {
        Element targetElement = typeUtils.asElement(typeMirror);
        if (targetElement instanceof TypeElement) {
            return getTableName((TypeElement) targetElement);
        }
        throw new IllegalArgumentException("Invalid relationship mapping");
    }

    private String getColumnName(VariableElement field) {
        // 使用改进后的注解获取方式
        Column columnAnnotation = AnnotationUtils.getAnnotation(field, Column.class);

        // 优先使用注解指定的列名
        if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
            return columnAnnotation.name().toLowerCase();
        }

        // 默认转换逻辑（驼峰转下划线）
        return convertFieldNameToColumnName(field.getSimpleName().toString());
    }

    private static String convertFieldNameToColumnName(String fieldName) {
        return convertClassNameToTableName(fieldName);
    }

}
