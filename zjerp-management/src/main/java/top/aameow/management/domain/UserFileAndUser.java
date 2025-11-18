package top.aameow.management.domain;

import lombok.Data;
import top.aameow.common.annotation.Excel;
import top.aameow.common.core.domain.BaseEntity;

public class UserFileAndUser extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 用户ID */
    @Excel(name = "用户ID")
    private Long userId;

    @Excel(name = "用户名")
    private String userName;

    /** 标签 */
    @Excel(name = "名称")
    private String name;

    /** 文件 */
    @Excel(name = "文件")
    private String file;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    @Override
    public String toString() {
        return "UserFileAndUser{" +
                "id=" + id +
                ", userId=" + userId +
                ", userName='" + userName + '\'' +
                ", name='" + name + '\'' +
                ", file='" + file + '\'' +
                ", createBy='" + getCreateBy() + '\'' +
                ", createTime='" + getCreateTime() + '\'' +
                ", updateBy='" + getUpdateBy() + '\'' +
                ", updateTime='" + getUpdateTime() + '\'' +
                '}';
    }
}
