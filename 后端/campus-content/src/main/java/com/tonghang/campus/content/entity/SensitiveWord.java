package com.tonghang.campus.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 敏感词（《12-数据库设计文档》§2.2 sensitive_word）。
 */
@TableName("sensitive_word")
public class SensitiveWord {

    /** 等级：拦截 */
    public static final int LEVEL_BLOCK = 1;
    /** 等级：复审 */
    public static final int LEVEL_REVIEW = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String word;
    /** 1 拦截 2 复审 */
    private Integer level;
    /** 0 停用 1 启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
