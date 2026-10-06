package vn.edu.quiz.common.util;

import jakarta.persistence.*;

@MappedSuperclass
public abstract class IdentityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() { return id; }
}
