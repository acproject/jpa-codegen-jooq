package com.example.gradle_demo.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "students")
//@Getter
public class Students {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String studentId;

    private String gender;

    private String className;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String extensions;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getExtensions() {
        return extensions;
    }

    public void setExtensions(String extensions) {
        this.extensions = extensions;
    }

    public Students(String name, String studentId, String gender, String className, String extensions) {
        this.name = name;
        this.studentId = studentId;
        this.gender = gender;
        this.className = className;
        this.extensions = extensions;
    }

    public Students(Long id, String name, String studentId, String gender, String className, String extensions) {
        this.id = id;
        this.name = name;
        this.studentId = studentId;
        this.gender = gender;
        this.className = className;
        this.extensions = extensions;
    }
}
