package com.tonghang.campus.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 动态-话题关联（《12-数据库设计文档》§2.2 post_topic_rel）。
 */
@TableName("post_topic_rel")
public class PostTopicRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long topicId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }
}
