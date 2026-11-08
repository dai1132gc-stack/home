package com.example.realestate.modules.content.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 教程 / 网签指引 / 公告：纯文本正文，不使用富文本，前端按换行渲染。 */
@Data
@NoArgsConstructor
@TableName("guide_article")
public class GuideArticle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String category;

    private String summary;

    private String content;

    private String status;

    private Integer sort;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    private Instant createdAt;

    private Instant updatedAt;
}
