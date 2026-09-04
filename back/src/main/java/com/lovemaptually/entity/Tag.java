package com.lovemaptually.entity;
import jakarta.persistence.*;import lombok.*;import org.hibernate.annotations.JdbcTypeCode;import org.hibernate.type.SqlTypes;import static com.lovemaptually.entity.Enums.*;
@Entity @Table(name="tags") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Tag {@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long tagId;@Column(nullable=false,unique=true,length=20) private String name;@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(nullable=false,columnDefinition="tag_axis") private TagAxis axis;@Column(nullable=false,length=20) private String highLabel;@Column(nullable=false,length=20) private String lowLabel;}
