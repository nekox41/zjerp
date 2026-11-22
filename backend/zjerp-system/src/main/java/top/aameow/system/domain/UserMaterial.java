package top.aameow.system.domain;
import lombok.Data;
@Data
public class UserMaterial {
 private Long id;
 private Long userId;
 private String materialKey;
 private String valueType;
 private String content;
}
