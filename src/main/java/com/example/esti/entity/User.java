package com.example.esti.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "users", schema = "APP") // "user"는 예약어라 "users"로 지정
public class User {

    // Getter/Setter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Derby는 AUTO 대신 IDENTITY 전략 권장
    private Long id;

    @Setter
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Setter
    @Column(nullable = false, length = 100)
    private String password;

    @Setter
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // 기본 생성자 (JPA 필수)
    protected User() {
    }

    // 생성자
    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
    }

}
