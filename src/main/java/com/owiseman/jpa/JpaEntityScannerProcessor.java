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

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;

import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;

import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({"jakarta.persistence.Entity"})
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
        // 去掉对javax.persistence包下的Entity注解
//        typeElements.add(Entity.class);

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
        List<TableMeta> joinTables = new ArrayList<>();
        // 收集所有中间表元数据
        for (TypeElement entity : entityClasses) {
            joinTables.addAll(parseJoinTables(entity));
        }

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

        // 新增中间表处理
        for (TableMeta joinTable : joinTables) {
            TypeSpec joinTableClass = TypeSpec.classBuilder(joinTable.name().toUpperCase())
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addField(getTableFieldSpec(joinTable.name()))
                    .addFields(getJoinTableFields(joinTable))
                    .build();
            tableClass = tableClass.toBuilder().addType(joinTableClass).build();
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

    private List<FieldSpec> getJoinTableFields(TableMeta joinTable) {
        List<FieldSpec> fields = new ArrayList<>();

        // 生成外键字段
        joinTable.columns().forEach(col -> {
            String type = switch (col.typeName()) {
                case "java.lang.String" -> "SQLDataType.VARCHAR";
                case "java.util.UUID" -> "SQLDataType.UUID";
                default -> "SQLDataType.VARCHAR";
            };

            FieldSpec field = FieldSpec.builder(ClassName.get("org.jooq", "Field"),
                            col.name().toUpperCase(),
                            Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("DSL.field(\"" + col.name() + "\", " + type + ")")
                    .build();
            fields.add(field);
        });

        return fields;
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

    private String getPrimaryKeyColumn(TypeElement entity) {
        for (VariableElement field : ElementFilter.fieldsIn(entity.getEnclosedElements())) {
            if (field.getAnnotation(Id.class) != null) {
                return getColumnName(field);
            }
        }
        return "id"; // 默认主键列名
    }

    private String getJoinColumnName(VariableElement field) {
        // 检查是否有显式的@JoinColumn注解
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        if (joinColumn != null && !joinColumn.name().isEmpty()) {
            return joinColumn.name();
        }

        // 自动生成外键列名：字段名
        return convertFieldNameToColumnName(field.getSimpleName().toString());
    }

    private void processOneToManyField(VariableElement field, List<FieldSpec> fieldSpecs) {
        // 获取当前字段对应的外键列名（通过@JoinColumn或自动生成）
        String fkColumnName = getJoinColumnName(field);


        // 获取关联实体类型
        TypeMirror typeMirror = ((DeclaredType) field.asType()).getTypeArguments().get(0);
        TypeElement targetEntity = (TypeElement) typeUtils.asElement(typeMirror);
        String pkType = getPrimaryKeyType(targetEntity);

        // 生成外键字段定义
        String sqlDataType = getSqlDataTypeForForeignKey(pkType);
        FieldSpec fieldSpec = FieldSpec.builder(ClassName.get("org.jooq", "Field"),
                        field.getSimpleName().toString().toUpperCase(),
                        Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("DSL.field(\"" + fkColumnName + "\", " + sqlDataType + ")")
                .build();
        fieldSpecs.add(fieldSpec);
    }

    private List<FieldSpec> getFieldSpecs(TypeElement typeElement) {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        List<VariableElement> fields = ElementFilter.fieldsIn(typeElement.getEnclosedElements());

        for (var field : fields) {

            // 跳过关联关系字段（由外键处理逻辑生成）
//            if (isRelationshipField(field)) {
//                continue;
//            }

            // 优先处理关联关系字段
            if (field.getAnnotation(OneToMany.class) != null) {
                processOneToManyField(field, fieldSpecs);
                continue;
            }
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
            ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
            JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
            if (manyToOne != null && joinColumn != null) {
                // 获取关联实体的类型
                TypeMirror targetType = field.asType();
                TypeElement targetElement = (TypeElement) typeUtils.asElement(targetType);

                // 获取关联实体的主键类型
                String primaryKeyType = getPrimaryKeyType(targetElement);

                // 使用正确的数据类型
                String sqlDataType = getSqlDataTypeForForeignKey(primaryKeyType);
                boolean isNullable = joinColumn.nullable();

                // 生成正确的字段定义
                FieldSpec fieldSpec = FieldSpec.builder(ClassName.get("org.jooq", "Field"),
                                fieldName.toUpperCase(), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("DSL.field(" + "\"" + joinColumn.name() + "\"," + "\"" + joinColumn.name() + "\"," +
                                sqlDataType + ".nullable(" + isNullable + "))")
                        .build();
                fieldSpecs.add(fieldSpec);
                continue;
            }

            String sqlDataType = getSqlDataType(typeName, field);
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

//    private boolean isRelationshipField(VariableElement field) {
//        return field.getAnnotation(OneToMany.class) != null ||
//                field.getAnnotation(ManyToOne.class) != null ||
//                field.getAnnotation(ManyToMany.class) != null;
//    }

    // 获取实体主键类型的辅助方法
    private String getPrimaryKeyType(TypeElement typeElement) {
        for (VariableElement field : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
            if (field.getAnnotation(Id.class) != null) {
                return typeUtils.erasure(field.asType()).toString();
            }
        }
        return "java.lang.String"; // 默认返回String类型
    }

    // 根据主键类型获取对应的SQL数据类型
    private String getSqlDataTypeForForeignKey(String typeName) {
        switch (typeName) {
            case "java.lang.String":
                return "SQLDataType.VARCHAR(255)"; // 假设UUID存储为字符串
            case "java.lang.Long":
                return "SQLDataType.BIGINT";
            case "java.lang.Integer":
                return "SQLDataType.INTEGER";
            default:
                return "SQLDataType.VARCHAR(255)";
        }
    }

    private int getColumnLength(VariableElement field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null && columnAnnotation.length() > 0) {
            return columnAnnotation.length();
        }
        // 默认根据主键类型设置合理长度
        String typeName = field.asType().toString();
        return switch (typeName) {
            case "java.util.String" -> 255;
            case "java.lang.Long", "long" -> 30;
            default -> 255;
        };
    }

    private boolean isNullable(VariableElement field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null) return columnAnnotation.nullable();
        return true;
    }

    private String getSqlDataTypeForOneToMany(VariableElement field) {
        // 获取关联实体类型
        TypeMirror typeMirror = field.asType();
        TypeElement targetEntity = (TypeElement) ((DeclaredType) typeMirror).getTypeArguments().get(0).accept(new SimpleTypeVisitor8<TypeElement, Void>() {
            @Override
            public TypeElement visitDeclared(DeclaredType t, Void p) {
                return (TypeElement) t.asElement();
            }
        }, null);

        // 获取关联实体主键类型
        String pkType = getPrimaryKeyType(targetEntity);
        return getSqlDataTypeForForeignKey(pkType);
    }

    private String getSqlDataType(String typeName, VariableElement field) {
        // 优先处理关联关系
        if (field.getAnnotation(OneToMany.class) != null) {
            return getSqlDataTypeForOneToMany(field);
        }
        // 处理枚举类型
        if (typeName.endsWith("Enum") || typeName.contains(".enums.")) {
            return "SQLDataType.VARCHAR";
        }

        // 处理集合类型
        if (typeName.contains("Map")
                || typeName.contains("List")
                || typeName.contains("Set")) {
            return "SQLDataType.JSONB";
        }

//        if (typeName.contains("Map")
//                || typeName.contains("List")
//                || typeName.contains("Set")) {
//            return "SQLDataType.JSONB" + ".asConvertedDataType(new com.owiseman.jpa.util.JsonMapBinding())";
//        }

        return switch (typeName) {
            case "Numeric" -> "SQLDataType.NUMERIC";
            case "java.math.BigDecimal", "BigDecimal" -> "SQLDataType.DECIMAL";
            case "int", "java.lang.Integer" -> "SQLDataType.INTEGER";
            case "float", "java.lang.Float" -> "SQLDataType.FLOAT";
            case "double", "java.lang.Double" -> "SQLDataType.DOUBLE";
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
    public static String convertClassNameToTableName(String className) {
        if (className == null || className.isEmpty()) {
            return className;
        }
        StringBuilder result = new StringBuilder();
        char[] chars = className.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char currentChar = chars[i];
            // 处理连续大写字母（如"ColumnABC"转成column_abc）
            if (i > 0 && Character.isUpperCase(currentChar)) {
                char prevChar = chars[i - 1];
                if (Character.isLowerCase(prevChar) ||
                        (i < chars.length - 1 && Character.isLowerCase(chars[i + 1]))) {
                    result.append('_');
                }
            }
            result.append(Character.toLowerCase(currentChar));
        }

        return result.toString();
    }

    private TableMeta parseEntity(TypeElement element, SqlGenerator sqlGen) {
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
        // 处理ManyToMany关联表
        List<TableMeta> joinTables = parseJoinTables(element);
        joinTables.forEach(joinTable -> sqlGen.addTable(joinTable, DataSourceEnum.POSTGRESQL));

        return new TableMeta(tableName, columns, primaryKey, indexes, foreignKeys, true);
    }

    private List<TableMeta> parseJoinTables(TypeElement entity) {
        List<TableMeta> joinTables = new ArrayList<>();

        ElementFilter.fieldsIn(entity.getEnclosedElements()).stream()
                .filter(f -> f.getAnnotation(ManyToMany.class) != null)
                .forEach(field -> {
                    JoinTable joinTable = field.getAnnotation(JoinTable.class);
                    if (joinTable != null) {
                        // 获取当前实体主键类型
                        String sourceType = getPrimaryKeyType(entity);

                        // 获取关联实体主键类型
                        TypeMirror targetTypeMirror = field.asType();
                        TypeElement targetEntity = (TypeElement) typeUtils.asElement(targetTypeMirror);
                        String targetType = getPrimaryKeyType(targetEntity);

                        // 构建关联表元数据
                        TableMeta joinTableMeta = new TableMeta(
                                joinTable.name(),
                                List.of(
                                        new ColumnMeta(
                                                joinTable.joinColumns()[0].name(),
                                                sourceType, // 使用当前实体主键类型
                                                getColumnLength(field),
                                                false,
                                                false
                                        ),
                                        new ColumnMeta(
                                                joinTable.inverseJoinColumns()[0].name(),
                                                targetType, // 使用关联实体主键类型
                                                getColumnLength(field),
                                                false,
                                                false
                                        )
                                ),
                                List.of(
                                        // 联合主键
                                        joinTable.joinColumns()[0].name(),
                                        joinTable.inverseJoinColumns()[0].name()
                                ),
                                List.of(), // 索引
                                List.of(
                                        // 外键
                                        new ForeignKey(
                                                "fk_" + joinTable.name() + "_source",
                                                joinTable.joinColumns()[0].name(),
                                                getTableName(entity),
                                                "id"
                                        ),
                                        new ForeignKey(
                                                "fk_" + joinTable.name() + "_target",
                                                joinTable.inverseJoinColumns()[0].name(),
                                                getReferencedTableName(field.asType()),
                                                "id"
                                        )
                                ),
                                true

                        );
                        joinTables.add(joinTableMeta);
                    }
                });
        return joinTables;
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

        // 处理单向 OneToMany（需要 JoinColumn）
        ElementFilter.fieldsIn(entity.getEnclosedElements()).stream()
                .filter(f -> f.getAnnotation(OneToMany.class) != null)
                .forEach(field -> {
                    JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                    if (joinColumn != null) {
                        foreignKeys.add(new ForeignKey(
                                generateFkName(getTableName(entity), joinColumn.name()),
                                joinColumn.name(),
                                getReferencedTableName(field.asType()),
                                joinColumn.referencedColumnName()
                        ));
                    }
                });

        return foreignKeys;
    }


    private void generateSQLCode(List<TypeElement> entityClasses) {
        SqlGenerator sqlGen = new SqlGenerator();

        for (TypeElement entity : entityClasses) {
            TableMeta table = parseEntity(entity, sqlGen);
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
